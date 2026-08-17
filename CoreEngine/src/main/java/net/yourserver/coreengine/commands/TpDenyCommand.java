package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.teleport.TeleportRequestManager.Pending;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /tpdeny} - decline the pending teleport request sent to you. */
public class TpDenyCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpDenyCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tpdeny.");
            return true;
        }
        Pending req = plugin.getTeleportRequestManager().poll(player.getUniqueId());
        if (req == null) {
            player.sendMessage("§cYou have no pending teleport requests to decline.");
            return true;
        }
        player.sendMessage("§cDeclined the teleport request.");
        return true;
    }
}
