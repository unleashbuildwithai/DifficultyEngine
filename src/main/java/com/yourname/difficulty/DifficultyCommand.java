package com.yourname.difficulty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /difficulty [peaceful|easy|hard|nightmare]
 *
 * Usage:
 *   /difficulty           → shows current difficulty
 *   /difficulty easy      → sets difficulty to Easy
 *   /difficulty nightmare → sets difficulty to Nightmare
 */
public class DifficultyCommand implements CommandExecutor {

    private final PlayerDifficultyManager manager;

    public DifficultyCommand(PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // No argument — show current difficulty
        if (args.length == 0) {
            DifficultyLevel current = manager.getDifficulty(player.getUniqueId());
            player.sendMessage("§8[§6DifficultyEngine§8] §7Your current difficulty: " + current.getDisplayName());
            player.sendMessage("§7Usage: §f/difficulty <peaceful|easy|hard|nightmare>");
            return true;
        }

        // Parse the requested difficulty
        DifficultyLevel requested = DifficultyLevel.fromString(args[0]);
        if (requested == null) {
            player.sendMessage("§cUnknown difficulty. Choose: §fpersonal/easy/hard/nightmare");
            player.sendMessage("§7Usage: §f/difficulty <peaceful|easy|hard|nightmare>");
            return true;
        }

        // Apply it
        manager.setDifficulty(player.getUniqueId(), requested);

        player.sendMessage("§8[§6DifficultyEngine§8] §7Difficulty set to: " + requested.getDisplayName());

        // Send a flavour message explaining what changed
        switch (requested) {
            case PEACEFUL ->
                player.sendMessage("§a  Hostile mobs will ignore you. Enjoy the peace.");
            case EASY ->
                player.sendMessage("§2  Vanilla experience. No changes to mobs near you.");
            case HARD ->
                player.sendMessage("§c  Mobs near you spawn with +25% HP, +15% damage, and longer follow range.");
            case NIGHTMARE ->
                player.sendMessage("§4  ☠ You asked for this. Mobs have +50% HP, +25% damage, +15% speed,\n" +
                                   "§4  64-block follow range, increased spawn rates, and they PREFER targeting you.");
        }

        return true;
    }
}
