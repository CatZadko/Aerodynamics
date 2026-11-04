package ru.miroslav.aerodynamics.listener;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.view.AnvilView;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.item.EnchantmentManager;
import ru.miroslav.aerodynamics.item.UpgradePlanner;
import ru.miroslav.aerodynamics.item.UpgradePlanner.Plan;

/** Подготовка показывает результат. Забор отдельно проверяет и атомарно оплачивает его. */
public final class WorkstationListener implements Listener {
    private final AerodynamicsPlugin plugin;
    private final EnchantmentManager items;
    private final UpgradePlanner planner;
    private final Set<UUID> pending = new HashSet<>();
    public WorkstationListener(AerodynamicsPlugin plugin, EnchantmentManager items, UpgradePlanner planner) {
        this.plugin = plugin; this.items = items; this.planner = planner;
    }

    private boolean ownsInputs(Inventory inventory) { return items.isOwned(inventory.getItem(0)) || items.isOwned(inventory.getItem(1)); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareAnvil(PrepareAnvilEvent event) {
        if (!ownsInputs(event.getInventory())) return;
        AnvilView view = event.getView();
        Plan plan = anvilPlan(view);
        event.setResult(plan == null ? null : plan.result());
        view.setRepairItemCountCost(plan == null ? 0 : plan.rightCount());
        view.setRepairCost(plan == null ? -1 : plan.levels());
        // В некоторых отказных ветках ваниль сбрасывает стоимость ПОСЛЕ PrepareAnvilEvent.
        // Повторно считаем план на следующем тике, не возвращая устаревший результат.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!view.getPlayer().getOpenInventory().getTopInventory().equals(view.getTopInventory())) return;
            if (!ownsInputs(view.getTopInventory())) return;
            Plan current = anvilPlan(view);
            view.setRepairCost(current == null ? -1 : current.levels());
            view.setRepairItemCountCost(current == null ? 0 : current.rightCount());
        });
    }

    private Plan anvilPlan(AnvilView view) {
        var inventory = view.getTopInventory();
        var left = inventory.getItem(0); var right = inventory.getItem(1);
        Plan plan = planner.anvil(left, right);
        String rename = view.getRenameText();
        if (plan != null) {
            // Автоматически подставленное имя книги II не должно переименовать III обратно в II.
            if (left.getItemMeta().displayName() != null
                    && rename != null && !rename.isBlank() && !rename.equals(visibleName(left)))
                plan.result().editMeta(meta -> meta.displayName(Component.text(rename)));
            return plan;
        }
        if (items.level(left) > 0 && left.getAmount() == 1 && EnchantmentManager.empty(right)
                && rename != null && !rename.isBlank() && !rename.equals(visibleName(left))) {
            var renamed = left.clone(); renamed.editMeta(meta -> meta.displayName(Component.text(rename)));
            return new Plan(renamed, plugin.settings().renameCost(), 1, 0);
        }
        return null;
    }
    private String visibleName(ItemStack item) {
        if (EnchantmentManager.empty(item)) return "";
        Component name = item.getItemMeta().displayName();
        return name == null ? "" : PlainTextComponentSerializer.plainText().serialize(name);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareSmith(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        // Зарегистрированный рецепт охватывает все enchanted_book, поэтому всегда
        // отсекаем неверную книгу, даже если в ней вообще нет наших PDC-ключей.
        if (!isOurSmith(inventory)) return;
        Plan plan = planner.smith(inventory.getInputTemplate(), inventory.getInputEquipment(), inventory.getInputMineral());
        event.setResult(plan == null ? null : plan.result());
    }
    private boolean isOurSmith(SmithingInventory inventory) {
        if (inventory.getRecipe() instanceof org.bukkit.Keyed keyed && keyed.key().equals(plugin.smithRecipe())) return true;
        return items.isOwned(inventory.getInputTemplate()) || items.isOwned(inventory.getInputEquipment())
                || items.isOwned(inventory.getInputMineral()) || items.isOwned(inventory.getResult());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void grind(PrepareGrindstoneEvent event) {
        if (ownsInputs(event.getInventory())) event.setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top instanceof GrindstoneInventory && event.getRawSlot() == 2 && ownsInputs(top)) { event.setCancelled(true); return; }
        boolean anvil = top instanceof AnvilInventory && (ownsInputs(top) || items.isOwned(top.getItem(2)));
        boolean smith = top instanceof SmithingInventory inventory && isOurSmith(inventory);
        if (!anvil && !smith) return;
        int outputSlot = anvil ? 2 : 3;
        // Двойной клик способен собирать результат в курсор из другого слота.
        if (event.getClick() == ClickType.DOUBLE_CLICK || pending.contains(player.getUniqueId())) {
            event.setCancelled(true); return;
        }
        if (event.getRawSlot() != outputSlot) return;
        event.setCancelled(true);
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
        Plan initial = anvil ? anvilPlan((AnvilView) event.getView())
                : planner.smith(top.getItem(0), top.getItem(1), top.getItem(2));
        if (initial == null) return;
        ItemStack[] snapshot = new ItemStack[outputSlot];
        for (int i = 0; i < outputSlot; i++) snapshot[i] = copy(top.getItem(i));
        String rename = anvil ? ((AnvilView) event.getView()).getRenameText() : null;
        boolean shift = event.isShiftClick();
        pending.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(top)) return;
                for (int i = 0; i < outputSlot; i++) if (!Objects.equals(snapshot[i], copy(top.getItem(i)))) return;
                if (anvil && !Objects.equals(rename, ((AnvilView) player.getOpenInventory()).getRenameText())) return;
                Plan plan = anvil ? anvilPlan((AnvilView) player.getOpenInventory())
                        : planner.smith(top.getItem(0), top.getItem(1), top.getItem(2));
                if (plan == null || !plan.result().equals(initial.result())) return;
                boolean creative = player.getGameMode() == GameMode.CREATIVE;
                if (!creative && player.getLevel() < plan.levels()) {
                    player.sendMessage(Component.text("Нужно уровней опыта: " + plan.levels(), NamedTextColor.RED)); return;
                }
                int emptySlot = player.getInventory().firstEmpty();
                if (shift ? emptySlot < 0 : !EnchantmentManager.empty(player.getItemOnCursor())) return;
                // После этих проверок всё меняется синхронно; отменённый ванильный
                // клик не сможет второй раз списать опыт или продублировать результат.
                top.setItem(outputSlot, null);
                consume(top, anvil ? 0 : 1, plan.leftCount());
                consume(top, anvil ? 1 : 2, plan.rightCount());
                if (!creative && plan.levels() > 0) player.giveExpLevels(-plan.levels());
                if (shift) player.getInventory().setItem(emptySlot, plan.result().clone());
                else player.setItemOnCursor(plan.result().clone());
                player.playSound(player.getLocation(), anvil ? Sound.BLOCK_ANVIL_USE : Sound.BLOCK_SMITHING_TABLE_USE, 1, 1);
            } finally { pending.remove(player.getUniqueId()); }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (pending.contains(event.getWhoClicked().getUniqueId())) { event.setCancelled(true); return; }
        if ((top instanceof AnvilInventory && event.getRawSlots().contains(2) && ownsInputs(top))
                || (top instanceof SmithingInventory inventory && event.getRawSlots().contains(3) && isOurSmith(inventory)))
            event.setCancelled(true);
    }
    private static ItemStack copy(ItemStack item) { return EnchantmentManager.empty(item) ? null : item.clone(); }
    private static void consume(Inventory inventory, int slot, int amount) {
        if (amount == 0) return;
        ItemStack input = inventory.getItem(slot);
        if (input == null || input.getAmount() < amount) throw new IllegalStateException("Вход изменился во время транзакции");
        inventory.setItem(slot, input.getAmount() == amount ? null : input.asQuantity(input.getAmount() - amount));
    }
}
