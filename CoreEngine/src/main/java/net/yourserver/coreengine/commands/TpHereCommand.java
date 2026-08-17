package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /tphere <player>} - teleport a player to you. If the target has
 * {@code /tpauto} enabled, it happens immediately; otherwise a request is
 * sent (the target can enable /tpauto to auto-accept).
 */
public class TpHereCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpHereCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tphere.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /tphere <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cThat's you.");
            return true;
        }
        PlayerSettingsManager.PlayerSettings targetSettings =
                plugin.getPlayerSettingsManager().get(target.getUniqueId());
        if (targetSettings.tpAuto) {
            target.teleport(player);
            player.sendMessage("§aTeleported §e" + target.getName() + "§a to you (auto).");
            target.sendMessage("§e" + player.getName() + "§a teleported you to them.");
        } else {
            target.sendMessage("§e" + player.getName() + "§a wants to teleport you to them. "
                    + "Enable §e/tpauto§a to allow it.");
            player.sendMessage("§aSent a teleport request to §e" + target.getName() + "§a.");
        }
        return true;
    }
}
