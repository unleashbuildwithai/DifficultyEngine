package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.rank.PlayerRank;
import net.yourserver.coreengine.rank.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /givemember <user> <1|2|3>} - admin command to assign donor ranks:
 * 1 = Member, 2 = Member+, 3 = Member++.
 */
public class GiveMemberCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public GiveMemberCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length != 2) {
            sender.sendMessage("§cUsage: /givemember <player> <1|2|3>");
            return true;
        }

        // Resolve the target player: try online first, then offline player by name.
        OfflinePlayer target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.hasPlayedBefore()) {
            target = Bukkit.getOfflinePlayer(args[0]);
        }
        if (!target.hasPlayedBefore() && target.getUniqueId() == null) {
            sender.sendMessage("§cPlayer §e" + args[0] + "§c was not found.");
            return true;
        }

        int tier;
        try {
            tier = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            sender.sendMessage("§cUsage: /givemember <player> <1|2|3>");
            return true;
        }
        PlayerRank rank = switch (tier) {
            case 1 -> PlayerRank.MEMBER;
            case 2 -> PlayerRank.MEMBER_PLUS;
            case 3 -> PlayerRank.MEMBER_PLUS_PLUS;
            default -> {
                sender.sendMessage("§cTier must be 1 (Member), 2 (Member+), or 3 (Member++).");
                yield null;
            }
        };
        if (rank == null) {
            return true;
        }

        UUID targetUuid = target.getUniqueId();
        RankManager ranks = plugin.getRankManager();
        if (ranks == null) {
            sender.sendMessage("§cThe rank system is not available yet.");
            return true;
        }
        if (!ranks.setRank(targetUuid, rank.getTier())) {
            sender.sendMessage("§cFailed to update rank for §e" + args[0] + "§c.");
            return true;
        }

        sender.sendMessage("§aGranted §e" + rank.getDisplayName() + "§a to §e"
                + target.getName() + "§a.");
        Player online = target.getPlayer();
        if (online != null) {
            online.sendMessage("§aYou have been granted the donor rank: §e" + rank.getDisplayName() + "§a!");
        }
        return true;
    }
}
