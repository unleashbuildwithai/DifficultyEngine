package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /settings} - opens the Create-a-Ville hub (all special menus). */
public class SettingsCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public SettingsCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /settings.");
            return true;
        }
        plugin.getSettingsGuiManager().openHub(player);
        return true;
    }
}
