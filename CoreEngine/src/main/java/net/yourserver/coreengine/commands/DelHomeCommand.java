package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /delhome <slot>} - delete a saved home. */
public class DelHomeCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public DelHomeCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /delhome.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /delhome <slot>");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cUsage: /delhome <slot>");
            return true;
        }
        boolean deleted = plugin.getHomeDao().deleteHome(player.getUniqueId(), slot);
        player.sendMessage(deleted
                ? "§aDeleted home slot §e" + slot + "§a."
                : "§cNo home saved in slot §e" + slot + "§c.");
        return true;
    }
}
