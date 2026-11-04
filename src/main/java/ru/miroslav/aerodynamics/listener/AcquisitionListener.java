package ru.miroslav.aerodynamics.listener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Ghast;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.view.EnchantmentView;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.item.EnchantmentManager;
import ru.miroslav.aerodynamics.item.EnchantmentManager.Origin;

public final class AcquisitionListener implements Listener {
    private final AerodynamicsPlugin plugin;
    private final EnchantmentManager items;
    private final Set<UUID> enchanting = new HashSet<>();
    public AcquisitionListener(AerodynamicsPlugin plugin, EnchantmentManager items) { this.plugin = plugin; this.items = items; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepareEnchant(PrepareItemEnchantEvent event) {
        // Для седла/упряжки ваниль изначально отменяет событие. Другие плагины
        // смогут отменить его позднее; здесь разрешаем только наш конкретный предмет.
        if (!plugin.settings().tableEnabled() || !items.isTarget(event.getItem())) return;
        if (items.isOwned(event.getItem()) || !event.getItem().getEnchantments().isEmpty()) return;
        Arrays.fill(event.getOffers(), null);
        if (event.getEnchantmentBonus() < 15) { event.setCancelled(true); return; }
        event.getOffers()[2] = new EnchantmentOffer(items.enchantment(), 1, 30);
        event.setCancelled(false);
        var chance = plugin.settings().table();
        event.getEnchanter().sendActionBar(Component.text(String.format(java.util.Locale.ROOT,
                "Аэродинамика: I %.1f%% · II %.1f%%. Попытка: 3 уровня и 3 лазурита",
                chance.levelOne() * 100, chance.levelTwo() * 100), NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) {
        if (items.isOwned(event.getItem())) { event.setCancelled(true); return; }
        // Защита и при постороннем datapack, добавившем наши чары в общий тег.
        event.getEnchantsToAdd().remove(items.enchantment());
        if (!plugin.settings().tableEnabled() || event.getExpLevelCost() != 30 || event.whichButton() != 2) return;
        boolean book = event.getItem().getType() == Material.BOOK;
        if (!book && (!items.isTarget(event.getItem()) || !event.getItem().getEnchantments().isEmpty())) return;
        var player = event.getEnchanter();
        if (enchanting.contains(player.getUniqueId())) { event.setCancelled(true); return; }
        int level = plugin.settings().table().roll();
        // Неудачная попытка на обычной книге остаётся обычным ванильным зачарованием.
        if (book && level == 0) return;
        event.setCancelled(true);
        if (!(event.getView() instanceof EnchantmentView view)) return;
        var inventory = view.getTopInventory();
        ItemStack expected = event.getItem().clone();
        enchanting.add(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (!player.isOnline() || !player.getOpenInventory().getTopInventory().equals(inventory)
                        || !expected.equals(inventory.getItem(0))) return;
                boolean creative = player.getGameMode() == GameMode.CREATIVE;
                ItemStack lapis = inventory.getItem(1);
                if (!creative && (player.getLevel() < 30 || EnchantmentManager.empty(lapis)
                        || lapis.getType() != Material.LAPIS_LAZULI || lapis.getAmount() < 3)) return;
                // Всё проверено. Дальше выполняется одна транзакция в основном потоке.
                if (!creative) {
                    player.giveExpLevels(-3);
                    inventory.setItem(1, lapis.getAmount() == 3 ? null : lapis.asQuantity(lapis.getAmount() - 3));
                }
                int seed = ThreadLocalRandom.current().nextInt();
                player.setEnchantmentSeed(seed); view.setEnchantmentSeed(seed);
                ItemStack result = level == 0 ? expected : book ? items.book(level, Origin.TABLE) : items.harness(expected, level, Origin.TABLE);
                inventory.setItem(0, result);
                player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1, 1);
                player.sendMessage(Component.text(level == 0
                        ? "Попытка не удалась: потрачено 3 уровня и 3 лазурита."
                        : "Получена Аэродинамика " + EnchantmentManager.roman(level) + ".",
                        level == 0 ? NamedTextColor.GRAY : NamedTextColor.AQUA));
            } finally { enchanting.remove(player.getUniqueId()); }
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void death(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;
        int level = event.getEntity() instanceof Ghast ? plugin.settings().ghast().roll()
                : event.getEntity() instanceof Phantom ? plugin.settings().phantom().roll() : 0;
        if (level > 0) event.getDrops().add(items.book(level, Origin.HUNT));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void barter(PiglinBarterEvent event) {
        int level = plugin.settings().barter().roll();
        if (level > 0) event.getOutcome().add(items.book(level, Origin.BARTER));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void generatedLoot(LootGenerateEvent event) {
        // Не перехватываем ItemSpawnEvent: легальные книги III–V разрешено ронять.
        event.getLoot().removeIf(items::isOwned);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void acquire(VillagerAcquireTradeEvent event) {
        if (items.isOwned(event.getRecipe().getResult()) && items.level(event.getRecipe().getResult()) > 2)
            event.setCancelled(true);
        if (event.getEntity() instanceof Villager villager)
            plugin.getServer().getScheduler().runTask(plugin, () -> ensureMasterTrade(villager));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void oldVillager(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager villager) ensureMasterTrade(villager);
    }

    public void ensureMasterTrade(Villager villager) {
        if (!plugin.settings().villagers() || !villager.isValid()
                || villager.getProfession() != Villager.Profession.LIBRARIAN || villager.getVillagerLevel() != 5) return;
        var recipes = new ArrayList<>(villager.getRecipes());
        if (recipes.stream().anyMatch(recipe -> items.isOwned(recipe.getResult()))) return;
        var cfg = plugin.settings();
        MerchantRecipe recipe = new MerchantRecipe(items.book(2, Origin.TRADE), 0, cfg.tradeUses(), true, 10, 0.0f, true);
        recipe.addIngredient(ItemStack.of(Material.EMERALD, cfg.emeralds()));
        recipe.addIngredient(ItemStack.of(Material.GHAST_TEAR, cfg.tears()));
        recipes.add(recipe);
        villager.setRecipes(recipes);
    }
}
