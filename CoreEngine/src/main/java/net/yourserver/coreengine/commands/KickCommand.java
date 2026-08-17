package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/** {@code /kick <player> [reason]} - kick a player. */
public class KickCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public KickCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /kick <player> [reason]");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Kicked by an admin.";
        target.kickPlayer("§c" + reason);
        sender.sendMessage("§aKicked §e" + target.getName() + "§a: §f" + reason);
        return true;
    }
}
