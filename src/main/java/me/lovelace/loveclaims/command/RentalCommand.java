package me.lovelace.loveclaims.command;

import me.lovelace.loveclaims.LoveClaims;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /rental отключена по решению проекта.
 * RentalManager, Rental*GUI и API аренды не удалялись — только вход через команду.
 */
public class RentalCommand implements CommandExecutor, TabCompleter {
    private final LoveClaims plugin;

    public RentalCommand(LoveClaims plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        sender.sendMessage(Component.text("Команда /rental временно отключена.", NamedTextColor.GRAY));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return List.of();
    }
}
