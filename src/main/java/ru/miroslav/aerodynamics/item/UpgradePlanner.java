package ru.miroslav.aerodynamics.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.logic.Progression;

/** План не изменяет инвентарь. Его заново вычисляют непосредственно перед списанием. */
public final class UpgradePlanner {
    public record Plan(ItemStack result, int levels, int leftCount, int rightCount) {}
    private final AerodynamicsPlugin plugin;
    private final EnchantmentManager items;
    public UpgradePlanner(AerodynamicsPlugin plugin, EnchantmentManager items) { this.plugin = plugin; this.items = items; }

    public Plan anvil(ItemStack left, ItemStack right) {
        if (items.isPureBook(left) && items.isPureBook(right)) {
            int output = Progression.merge(items.level(left), items.level(right));
            if (output == 0) return null;
            return new Plan(items.book(output, EnchantmentManager.Origin.ANVIL), plugin.settings().mergeCost(output), 1, 1);
        }
        if (items.isTarget(left) && left.getAmount() == 1 && items.isPureBook(right)) {
            if (items.isOwned(left) && items.level(left) == 0) return null;
            int level = items.level(right);
            if (!Progression.canApply(items.level(left), level)) return null;
            return new Plan(items.harness(left, level, EnchantmentManager.Origin.ANVIL), plugin.settings().applyCost(level), 1, 1);
        }
        return null;
    }
    public Plan smith(ItemStack template, ItemStack base, ItemStack addition) {
        if (!items.isPureBook(base) || !Progression.canSmith(items.level(base),
                !EnchantmentManager.empty(addition) && addition.getType() == Material.NETHERITE_INGOT,
                EnchantmentManager.empty(template))) return null;
        return new Plan(items.book(5, EnchantmentManager.Origin.SMITHING), 0, 1, 1);
    }
}
