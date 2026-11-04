package ru.miroslav.aerodynamics;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import ru.miroslav.aerodynamics.command.AeroCommand;
import ru.miroslav.aerodynamics.config.AeroConfig;
import ru.miroslav.aerodynamics.flight.FlightManager;
import ru.miroslav.aerodynamics.item.EnchantmentManager;
import ru.miroslav.aerodynamics.item.UpgradePlanner;
import ru.miroslav.aerodynamics.listener.AcquisitionListener;
import ru.miroslav.aerodynamics.listener.FlightListener;
import ru.miroslav.aerodynamics.listener.WorkstationListener;

public final class AerodynamicsPlugin extends JavaPlugin {
    private AeroConfig settings;
    private EnchantmentManager items;
    private FlightManager flight;
    private NamespacedKey smithRecipe;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = AeroConfig.load(getConfig());
        items = new EnchantmentManager(this);
        flight = new FlightManager(this, items);
        var manager = getServer().getPluginManager();
        manager.registerEvents(new AcquisitionListener(this, items), this);
        manager.registerEvents(new WorkstationListener(this, items, new UpgradePlanner(this, items)), this);
        manager.registerEvents(new FlightListener(this, flight), this);
        smithRecipe = new NamespacedKey(this, "aerodynamics_v");
        // Пустой шаблон — реальная возможность Paper RecipeChoice.empty(), без NMS.
        getServer().removeRecipe(smithRecipe);
        getServer().addRecipe(new SmithingTransformRecipe(smithRecipe,
                items.book(5, EnchantmentManager.Origin.SMITHING), RecipeChoice.empty(),
                RecipeChoice.itemType(ItemType.ENCHANTED_BOOK),
                RecipeChoice.itemType(ItemType.NETHERITE_INGOT), false));
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> event.registrar().register("aerodynamics", "Управление Аэродинамикой", List.of("aero"), new AeroCommand(this)));
        flight.start();
        getLogger().info("Аэродинамика I–V включена. Paper " + getServer().getMinecraftVersion());
    }
    @Override
    public void onDisable() {
        if (flight != null) flight.stop();
        if (smithRecipe != null) getServer().removeRecipe(smithRecipe);
    }
    public void reloadSettings() {
        reloadConfig();
        AeroConfig next = AeroConfig.load(getConfig());
        flight.stop(); settings = next; flight.start();
    }
    public AeroConfig settings() { return settings; }
    public EnchantmentManager items() { return items; }
    public FlightManager flight() { return flight; }
    public NamespacedKey smithRecipe() { return smithRecipe; }
}
