package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /tp <player>} - teleport to another player (respects their TP privacy). */
public class TpCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tp.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /tp <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cYou are already there.");
            return true;
        }
        if (!plugin.getTeleportManager().canTeleportTo(target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " has teleports disabled for you.");
            return true;
        }
        player.teleport(target);
        player.sendMessage("§aTeleported to §e" + target.getName() + "§a.");
        return true;
    }
}
