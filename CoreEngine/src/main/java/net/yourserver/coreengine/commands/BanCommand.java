package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/** {@code /ban <player> [reason]} - ban a player. */
public class BanCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public BanCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /ban <player> [reason]");
            return true;
        }
        String name = args[0];
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Banned by an admin.";
        Bukkit.getBanList(BanList.Type.NAME).addBan(name, reason, null, null);
        Player target = Bukkit.getPlayerExact(name);
        if (target != null) {
            target.kickPlayer("§cYou have been banned: §f" + reason);
        }
        sender.sendMessage("§aBanned §e" + name + "§a: §f" + reason);
        return true;
    }
}
