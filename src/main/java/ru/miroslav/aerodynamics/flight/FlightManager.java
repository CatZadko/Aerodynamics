package ru.miroslav.aerodynamics.flight;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.config.AeroConfig;
import ru.miroslav.aerodynamics.item.EnchantmentManager;
import ru.miroslav.aerodynamics.logic.FlightMath;
import ru.miroslav.aerodynamics.logic.LoadGuard;

/** Все обращения к Bukkit происходят синхронно; загруженные сущности мира не сканируются каждый тик. */
public final class FlightManager {
    private static final double CHUNK_RESERVE = 12;
    private final AerodynamicsPlugin plugin;
    private final EnchantmentManager items;
    private final NamespacedKey modifierKey;
    private final Map<UUID, FlightState> active = new HashMap<>();
    private LoadGuard loadGuard = new LoadGuard();
    private BukkitTask task;
    private long previousTickNanos;
    private double tickIntervalMs = 50;
    private double workMs = 0;
    private double recentTps = 20;
    private int elapsedTicks;

    private static final class FlightState {
        final HappyGhast ghast;
        double current;
        double target;
        double appliedAttribute = -1;
        int level;
        Location previousPosition;
        long previousPositionNanos;
        double measured;
        FlightState(HappyGhast ghast, double initial) { this.ghast = ghast; this.current = initial; }
    }
    public FlightManager(AerodynamicsPlugin plugin, EnchantmentManager items) {
        this.plugin = plugin; this.items = items; modifierKey = new NamespacedKey(plugin, "flight_speed");
    }
    public void start() {
        loadGuard = new LoadGuard(); previousTickNanos = 0; tickIntervalMs = 50; workMs = 0; elapsedTicks = 0;
        discoverRiders();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 1, plugin.settings().periodTicks());
    }
    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        for (FlightState state : active.values()) removeModifier(state.ghast);
        active.clear();
    }
    public void observeTick(double durationMs) {
        long now = System.nanoTime();
        if (previousTickNanos != 0) {
            double interval = Math.max(1, Math.min(5000, (now - previousTickNanos) / 1_000_000.0));
            tickIntervalMs += 0.1 * (interval - tickIntervalMs);
        }
        previousTickNanos = now;
        workMs += 0.1 * (durationMs - workMs);
    }
    public void watch(HappyGhast ghast) {
        if (!ghast.isValid() || !ghast.isAdult() || pilot(ghast) == null) return;
        active.computeIfAbsent(ghast.getUniqueId(), ignored -> new FlightState(ghast, plugin.settings().speed(0)));
    }
    public void forget(HappyGhast ghast) {
        active.remove(ghast.getUniqueId()); removeModifier(ghast);
    }
    private void discoverRiders() {
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getVehicle() instanceof HappyGhast ghast) watch(ghast);
    }
    private Player pilot(HappyGhast ghast) {
        var passengers = ghast.getPassengers();
        return !passengers.isEmpty() && passengers.getFirst() instanceof Player player ? player : null;
    }

    private void update() {
        AeroConfig cfg = plugin.settings();
        elapsedTicks += cfg.periodTicks();
        if (elapsedTicks >= 20) { elapsedTicks %= 20; discoverRiders(); }
        double dt = cfg.periodTicks() / 20.0;
        recentTps = Math.min(20, Math.min(Bukkit.getTPS()[0], 1000.0 / Math.max(50, tickIntervalMs)));
        boolean limited = loadGuard.update(recentTps, workMs, dt, cfg.enterTps(), cfg.exitTps(),
                cfg.enterMspt(), cfg.exitMspt(), cfg.recoverySeconds());
        Iterator<FlightState> iterator = active.values().iterator();
        while (iterator.hasNext()) {
            FlightState state = iterator.next(); HappyGhast ghast = state.ghast;
            Player pilot = pilot(ghast);
            if (!ghast.isValid() || ghast.isDead() || pilot == null || !ghast.isAdult()) {
                removeModifier(ghast); iterator.remove(); continue;
            }
            var harness = ghast.getEquipment().getItem(EquipmentSlot.BODY);
            if (!items.isHarness(harness)) { removeModifier(ghast); iterator.remove(); continue; }
            state.level = items.level(harness);
            double desired = cfg.speed(state.level);
            if (cfg.lagEnabled() && limited && state.level >= 4) desired = Math.min(desired, cfg.safeSpeed());
            if (cfg.chunkGuard() && state.level >= 4) {
                double braking = state.current * state.current / (2 * cfg.deceleration());
                double lookahead = Math.min(cfg.maxLookahead(), Math.max(48,
                        Math.max(state.current * cfg.lookaheadSeconds(), braking + CHUNK_RESERVE + state.current * dt + 8)));
                Vector velocity = ghast.getVelocity(); velocity.setY(0);
                Vector heading = pilot.getLocation().getDirection().setY(0);
                if (heading.lengthSquared() < 0.001) heading = new Vector(0, 0, 1);
                heading.normalize();
                Vector direction = velocity.lengthSquared() > 0.001 ? velocity.normalize() : heading;
                double distance = availableDistance(ghast, direction, lookahead, cfg.requireSent());
                // На повороте проверяем и старое направление импульса, и новый курс.
                if (direction.dot(heading) < 0.9 && pilot.getCurrentInput().isForward())
                    distance = Math.min(distance, availableDistance(ghast, heading, lookahead, cfg.requireSent()));
                desired = Math.min(desired, FlightMath.safeSpeed(distance, cfg.deceleration(), CHUNK_RESERVE));
                if (distance <= CHUNK_RESERVE + state.current * dt) {
                    // Аварийная остановка только перед неготовой границей. Обычное
                    // снижение из-за TPS остаётся плавным и работает через атрибут.
                    state.current = 0; desired = 0; ghast.setVelocity(new Vector());
                }
            }
            state.target = desired;
            state.current = FlightMath.approach(state.current, desired, cfg.acceleration(), cfg.deceleration(), dt);
            applyAttribute(state, FlightMath.attributeForBps(state.current * cfg.speedCalibration()));
            measure(state);
            if (elapsedTicks == 0 && state.level >= 4 && desired < cfg.speed(state.level) - 0.1)
                pilot.sendActionBar(Component.text(String.format(Locale.ROOT,
                        "Аэродинамика: ограничение %.1f б/с · TPS %.1f", desired, recentTps), NamedTextColor.YELLOW));
        }
    }

    private void applyAttribute(FlightState state, double desired) {
        if (Math.abs(state.appliedAttribute - desired) < 0.000001) return;
        AttributeInstance attribute = state.ghast.getAttribute(Attribute.FLYING_SPEED);
        if (attribute == null) return;
        attribute.removeModifier(modifierKey);
        double unmodified = attribute.getValue();
        AttributeModifier modifier = unmodified > 0
                ? new AttributeModifier(modifierKey, desired / unmodified - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
                : new AttributeModifier(modifierKey, desired, AttributeModifier.Operation.ADD_NUMBER);
        attribute.addTransientModifier(modifier);
        state.appliedAttribute = desired;
    }
    private void removeModifier(HappyGhast ghast) {
        AttributeInstance attribute = ghast.getAttribute(Attribute.FLYING_SPEED);
        if (attribute != null) attribute.removeModifier(modifierKey);
    }

    private double availableDistance(HappyGhast ghast, Vector direction, double limit, boolean requireSent) {
        Location start = ghast.getLocation();
        Set<Long> checked = new HashSet<>();
        List<Player> passengers = ghast.getPassengers().stream().filter(Player.class::isInstance).map(Player.class::cast).toList();
        double half = ghast.getWidth() / 2 + 1;
        for (double d = 0; d <= limit; d += 4) {
            double x = start.getX() + direction.getX() * d, z = start.getZ() + direction.getZ() * d;
            for (int sideX : new int[]{-1, 1}) for (int sideZ : new int[]{-1, 1}) {
                int cx = ((int) Math.floor(x + sideX * half)) >> 4;
                int cz = ((int) Math.floor(z + sideZ * half)) >> 4;
                long key = FlightMath.chunkKey(cx, cz);
                if (!checked.add(key)) continue;
                if (!ghast.getWorld().isChunkLoaded(cx, cz)
                        || (requireSent && passengers.stream().anyMatch(player -> !player.isChunkSent(key))))
                    return Math.max(0, d - 4);
            }
        }
        return limit;
    }

    /** Последняя защита для неожиданного поворота между двумя обновлениями. */
    public boolean canMoveTo(HappyGhast ghast, Location to) {
        FlightState state = active.get(ghast.getUniqueId());
        if (state == null || state.level < 4 || !plugin.settings().chunkGuard()) return true;
        double half = ghast.getWidth() / 2 + 0.5;
        for (int sx : new int[]{-1, 1}) for (int sz : new int[]{-1, 1}) {
            int x = ((int) Math.floor(to.getX() + sx * half)) >> 4;
            int z = ((int) Math.floor(to.getZ() + sz * half)) >> 4;
            if (!to.getWorld().isChunkLoaded(x, z)) return false;
        }
        return true;
    }
    public void emergencyStop(HappyGhast ghast) {
        FlightState state = active.get(ghast.getUniqueId());
        if (state == null) return;
        state.current = 0; state.target = 0; applyAttribute(state, 0); ghast.setVelocity(new Vector());
    }
    private void measure(FlightState state) {
        long now = System.nanoTime(); Location pos = state.ghast.getLocation();
        if (state.previousPosition != null && state.previousPosition.getWorld().equals(pos.getWorld())) {
            double seconds = (now - state.previousPositionNanos) / 1_000_000_000.0;
            double distance = pos.distance(state.previousPosition);
            state.measured = seconds > 0 && distance < 100 ? distance / seconds : 0;
        }
        state.previousPosition = pos; state.previousPositionNanos = now;
    }
    public String status(Player player) {
        if (!(player.getVehicle() instanceof HappyGhast ghast)) return "Сначала сядь на счастливого гаста.";
        FlightState state = active.get(ghast.getUniqueId());
        if (state == null) return "Ожидание обновления полёта…";
        var attribute = ghast.getAttribute(Attribute.FLYING_SPEED);
        return String.format(Locale.ROOT, "Аэродинамика %s | цель %.1f | ограничение %.1f | факт %.1f б/с | FLYING_SPEED %.5f | TPS %.1f | MSPT %.1f",
                EnchantmentManager.roman(state.level), plugin.settings().speed(state.level), state.target,
                state.measured, attribute == null ? 0 : attribute.getValue(), recentTps, workMs);
    }
    public String serverStatus() { return String.format(Locale.ROOT, "Гастов: %d · TPS %.1f · MSPT %.1f · ограничитель: %s", active.size(), recentTps, workMs, loadGuard.limited()); }
}
