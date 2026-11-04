package ru.miroslav.aerodynamics.listener;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.HappyGhast;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.flight.FlightManager;

public final class FlightListener implements Listener {
    private final AerodynamicsPlugin plugin;
    private final FlightManager flight;
    public FlightListener(AerodynamicsPlugin plugin, FlightManager flight) { this.plugin = plugin; this.flight = flight; }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerMove(PlayerMoveEvent event) {
        if (event.getPlayer().getVehicle() instanceof HappyGhast ghast) flight.watch(ghast);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void vehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle() instanceof HappyGhast ghast) flight.watch(ghast);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (event.getMount() instanceof HappyGhast ghast)
            plugin.getServer().getScheduler().runTask(plugin, () -> flight.watch(ghast));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof HappyGhast ghast)
            plugin.getServer().getScheduler().runTask(plugin, () -> { if (ghast.getPassengers().isEmpty()) flight.forget(ghast); });
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void equipment(EntityEquipmentChangedEvent event) {
        if (event.getEntity() instanceof HappyGhast ghast) flight.watch(ghast);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void entityMove(EntityMoveEvent event) {
        if (event.getEntity() instanceof HappyGhast ghast && !flight.canMoveTo(ghast, event.getTo())) {
            event.setCancelled(true); flight.emergencyStop(ghast);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void unload(EntitiesUnloadEvent event) {
        for (var entity : event.getEntities()) if (entity instanceof HappyGhast ghast) flight.forget(ghast);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        if (event.getEntity() instanceof HappyGhast ghast) flight.forget(ghast);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void tick(ServerTickEndEvent event) { flight.observeTick(event.getTickDuration()); }
}
