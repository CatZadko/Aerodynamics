package ru.miroslav.aerodynamics;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.set.RegistrySet;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

/** Регистрация выполняется до загрузки миров; чужие записи не изменяются. */
public final class AeroBootstrap implements PluginBootstrap {
    public static final TypedKey<Enchantment> ENCHANTMENT_KEY =
            EnchantmentKeys.create(Key.key("aerodynamics:aerodynamics"));

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose(), event -> {
            List<TypedKey<ItemType>> supported = new ArrayList<>();
            supported.add(TypedKey.create(RegistryKey.ITEM, "minecraft:saddle"));
            for (String color : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime",
                    "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
                supported.add(TypedKey.create(RegistryKey.ITEM, "minecraft:" + color + "_harness"));
            }
            event.registry().register(ENCHANTMENT_KEY, builder -> builder
                    .description(Component.text("Аэродинамика"))
                    .supportedItems(RegistrySet.keySet(RegistryKey.ITEM, supported))
                    .weight(1).maxLevel(5).anvilCost(2)
                    .minimumCost(EnchantmentCost.of(1, 10))
                    .maximumCost(EnchantmentCost.of(100, 10))
                    .activeSlots(EquipmentSlotGroup.BODY));
            // Намеренно НЕ добавляем запись в теги столов, торговли и случайного лута.
            // Вероятности и допустимые уровни контролируются нашими слушателями.
        });
    }
}
