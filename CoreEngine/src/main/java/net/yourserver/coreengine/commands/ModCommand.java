package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /mod [player]} - toggle spectator mode (to watch players). */
public class ModCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public ModCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /mod.");
            return true;
        }
        if (args.length == 0) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage("§aSpectator mode §cOFF§a.");
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§aSpectator mode §2ON§a. Use /mod again to return.");
            }
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(target);
        player.sendMessage("§aSpectating §e" + target.getName() + "§a.");
        return true;
    }
}
