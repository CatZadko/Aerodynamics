package ru.miroslav.aerodynamics.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.miroslav.aerodynamics.AerodynamicsPlugin;
import ru.miroslav.aerodynamics.item.EnchantmentManager.Origin;

public final class AeroCommand implements BasicCommand {
    private final AerodynamicsPlugin plugin;
    public AeroCommand(AerodynamicsPlugin plugin) { this.plugin = plugin; }
    @Override public String permission() { return "aerodynamics.admin"; }
    @Override public void execute(CommandSourceStack source, String[] args) {
        var sender = source.getSender();
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            try { plugin.reloadSettings(); sender.sendMessage(Component.text("Настройки перечитаны.", NamedTextColor.GREEN)); }
            catch (IllegalArgumentException e) { sender.sendMessage(Component.text("Ошибка конфигурации: " + e.getMessage(), NamedTextColor.RED)); }
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(Component.text(sender instanceof Player player ? plugin.flight().status(player) : plugin.flight().serverStatus(), NamedTextColor.AQUA)); return;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(Component.text("Игрок должен быть онлайн.", NamedTextColor.RED)); return; }
            int level;
            try { level = Integer.parseInt(args[3]); } catch (NumberFormatException e) { level = 0; }
            if (level < 1 || level > 5) { sender.sendMessage(Component.text("Уровень: 1–5.", NamedTextColor.RED)); return; }
            ItemStack item;
            if (args[2].equalsIgnoreCase("book")) item = plugin.items().book(level, Origin.ADMIN);
            else if (args[2].equalsIgnoreCase("harness")) item = plugin.items().harness(ItemStack.of(Material.BLUE_HARNESS), level, Origin.ADMIN);
            else { sender.sendMessage(Component.text("Тип: book или harness.", NamedTextColor.RED)); return; }
            int slot = target.getInventory().firstEmpty();
            if (slot < 0) { sender.sendMessage(Component.text("У игрока заполнен инвентарь.", NamedTextColor.RED)); return; }
            target.getInventory().setItem(slot, item);
            sender.sendMessage(Component.text("Предмет выдан игроку " + target.getName() + ".", NamedTextColor.GREEN)); return;
        }
        sender.sendMessage(Component.text("/aero give <игрок> <book|harness> <1–5>\n/aero status\n/aero reload", NamedTextColor.AQUA));
    }
    @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> candidates = switch (args.length) {
            case 1 -> List.of("give", "status", "reload");
            case 2 -> args[0].equalsIgnoreCase("give") ? plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList() : List.of();
            case 3 -> args[0].equalsIgnoreCase("give") ? List.of("book", "harness") : List.of();
            case 4 -> args[0].equalsIgnoreCase("give") ? List.of("1", "2", "3", "4", "5") : List.of();
            default -> List.of();
        };
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return candidates.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
