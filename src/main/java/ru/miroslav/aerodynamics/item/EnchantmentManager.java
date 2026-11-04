package ru.miroslav.aerodynamics.item;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.persistence.PersistentDataType;
import ru.miroslav.aerodynamics.AeroBootstrap;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;

/** Фабрика предметов и единственная точка чтения уровня. Названия и lore не являются идентификаторами. */
public final class EnchantmentManager {
    public enum Origin { TABLE, HUNT, BARTER, TRADE, ANVIL, SMITHING, ADMIN }
    private final AerodynamicsPlugin plugin;
    private final NamespacedKey levelKey;
    private final NamespacedKey kindKey;
    private final NamespacedKey originKey;
    private final Enchantment enchantment;

    public EnchantmentManager(AerodynamicsPlugin plugin) {
        this.plugin = plugin;
        this.levelKey = new NamespacedKey(plugin, "level");
        this.kindKey = new NamespacedKey(plugin, "kind");
        this.originKey = new NamespacedKey(plugin, "origin");
        this.enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                .getOrThrow(AeroBootstrap.ENCHANTMENT_KEY);
    }
    public Enchantment enchantment() { return enchantment; }
    public boolean isHarness(ItemStack item) {
        return !empty(item) && item.getType().name().endsWith("_HARNESS");
    }
    public boolean isTarget(ItemStack item) { return isHarness(item) || (!empty(item) && item.getType() == Material.SADDLE); }
    public boolean isOwned(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(levelKey) || nativeLevel(meta) > 0;
    }
    public int level(ItemStack item) {
        if (empty(item) || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        Integer level = meta.getPersistentDataContainer().get(levelKey, PersistentDataType.INTEGER);
        String kind = meta.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        String origin = meta.getPersistentDataContainer().get(originKey, PersistentDataType.STRING);
        if (level == null || level < 1 || level > 5 || nativeLevel(meta) != level || origin == null) return 0;
        try { Origin.valueOf(origin); } catch (IllegalArgumentException e) { return 0; }
        boolean book = item.getType() == Material.ENCHANTED_BOOK && "book".equals(kind);
        boolean harness = isHarness(item) && "harness".equals(kind);
        return book || harness ? level : 0;
    }
    public boolean isBook(ItemStack item) { return !empty(item) && item.getType() == Material.ENCHANTED_BOOK && level(item) > 0; }
    public boolean isPureBook(ItemStack item) {
        return isBook(item) && item.getAmount() == 1 && item.getItemMeta() instanceof EnchantmentStorageMeta meta
                && meta.getStoredEnchants().size() == 1 && meta.getEnchants().isEmpty();
    }
    public ItemStack book(int level, Origin origin) {
        validateOrigin(level, origin);
        if (level == 5 && origin != Origin.SMITHING && origin != Origin.ADMIN)
            throw new IllegalArgumentException("Книга V создаётся только кузнечным столом");
        ItemStack result = ItemStack.of(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) result.getItemMeta();
        meta.addStoredEnchant(enchantment, level, false);
        stamp(meta, level, "book", origin);
        meta.displayName(text("Книга «Аэродинамика " + roman(level) + "»", NamedTextColor.AQUA));
        meta.lore(List.of(text("Для упряжки счастливого гаста", NamedTextColor.GRAY), speedLine(level)));
        result.setItemMeta(meta);
        return result;
    }
    public ItemStack harness(ItemStack input, int level, Origin origin) {
        validateOrigin(level, origin);
        if (!isTarget(input)) throw new IllegalArgumentException("Требуется упряжка или седло");
        ItemStack result = isHarness(input) ? input.asQuantity(1) : ItemStack.of(Material.BLUE_HARNESS);
        ItemMeta meta = result.getItemMeta();
        meta.addEnchant(enchantment, level, false);
        stamp(meta, level, "harness", origin);
        meta.displayName(text("Упряжка счастливого гаста", NamedTextColor.AQUA));
        meta.lore(List.of(speedLine(level), text("ПКМ по взрослому гасту · снять ножницами", NamedTextColor.GRAY)));
        var model = meta.getCustomModelDataComponent();
        model.setFloats(List.of(plugin.settings().modelData()));
        meta.setCustomModelDataComponent(model);
        result.setItemMeta(meta);
        return result;
    }
    private void stamp(ItemMeta meta, int level, String kind, Origin origin) {
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        meta.getPersistentDataContainer().set(originKey, PersistentDataType.STRING, origin.name());
        meta.setMaxStackSize(1);
        if (meta instanceof Repairable repairable) repairable.setRepairCost(0);
    }
    private Component speedLine(int level) {
        return text(String.format(Locale.ROOT, "Скорость: %.1f блоков/с", plugin.settings().speed(level)), NamedTextColor.GRAY);
    }
    private int nativeLevel(ItemMeta meta) {
        return meta instanceof EnchantmentStorageMeta storage ? storage.getStoredEnchantLevel(enchantment) : meta.getEnchantLevel(enchantment);
    }
    private void validateOrigin(int level, Origin origin) {
        if (level < 1 || level > 5) throw new IllegalArgumentException("Уровень должен быть I–V");
        if (origin == Origin.ADMIN) return;
        if (level == 5 && origin != Origin.SMITHING && origin != Origin.ANVIL)
            throw new IllegalArgumentException("V создаётся кузнечным столом");
        if (level > 2 && origin != Origin.ANVIL && origin != Origin.SMITHING)
            throw new IllegalArgumentException("Естественные источники ограничены уровнем II");
    }
    public static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
    public static String roman(int level) { return new String[]{"—", "I", "II", "III", "IV", "V"}[level]; }
    public static boolean empty(ItemStack item) { return item == null || item.isEmpty(); }
}
