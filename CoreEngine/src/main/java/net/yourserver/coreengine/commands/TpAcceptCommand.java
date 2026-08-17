package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.teleport.TeleportRequestManager.RequestType;
import net.yourserver.coreengine.teleport.TeleportRequestManager.Pending;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /tpaccept} - accept the pending teleport request sent to you. */
public class TpAcceptCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpAcceptCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tpaccept.");
            return true;
        }
        Pending req = plugin.getTeleportRequestManager().poll(player.getUniqueId());
        if (req == null) {
            player.sendMessage("§cYou have no pending teleport requests.");
            return true;
        }
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester == null || !requester.isOnline()) {
            player.sendMessage("§cThe requester is no longer online.");
            return true;
        }
        if (plugin.getTeleportRequestManager().isInCombat(player.getUniqueId())
                || plugin.getTeleportRequestManager().isInCombat(req.requester())) {
            player.sendMessage("§cTeleport cancelled - you or the requester are in combat.");
            return true;
        }
        if (req.type() == RequestType.TP) {
            // The requester comes to you.
            requester.teleport(player);
            requester.sendMessage("§e" + player.getName() + "§a accepted your teleport request.");
            player.sendMessage("§aAccepted §e" + requester.getName() + "'s§a teleport request.");
        } else {
            // /tphere: you go to the requester.
            player.teleport(requester);
            requester.sendMessage("§e" + player.getName() + "§a accepted your §dtphere§a request.");
            player.sendMessage("§aAccepted §e" + requester.getName() + "'s§a tphere request.");
        }
        return true;
    }
}
