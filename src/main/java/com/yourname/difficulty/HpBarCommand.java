package com.yourname.difficulty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /hpbar — Toggles the live HP display above mob heads for the using player.
 *
 * When ON:  every mob you hit shows  §c❤ §f{current} §7/ §f{max}  above it.
 * When OFF: no names are shown (vanilla behaviour).
 *
 * The toggle is per-player and resets when the server restarts.
 * Permission: difficultyengine.use  (same as /difficulty — default: true)
 */
public class HpBarCommand implements CommandExecutor {

    private final PlayerDifficultyManager manager;

    public HpBarCommand(PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        boolean nowOn = manager.toggleHpDisplay(player.getUniqueId());

        if (nowOn) {
            player.sendMessage("§8[§6DifficultyEngine§8] §aHP display §2ON " +
                    "§7— hit a mob to see its health above its head.");
        } else {
            player.sendMessage("§8[§6DifficultyEngine§8] §cHP display §4OFF§7.");
        }

        return true;
    }
}
