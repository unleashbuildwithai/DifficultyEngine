package com.yourname.difficulty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /difficulty [peaceful|easy|medium|hard|nightmare]
 *
 * Usage:
 *   /difficulty           â†’ shows current difficulty
 *   /difficulty easy      â†’ sets difficulty to Easy
 *   /difficulty medium    â†’ sets difficulty to Medium
 *   /difficulty nightmare â†’ sets difficulty to Nightmare
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
            sender.sendMessage("Â§cOnly players can use this command.");
            return true;
        }

        // No argument â€” show current difficulty
        if (args.length == 0) {
            DifficultyLevel current = manager.getDifficulty(player.getUniqueId());
            player.sendMessage("Â§8[Â§6DifficultyEngineÂ§8] Â§7Your current difficulty: " + current.getDisplayName());
            player.sendMessage("Â§7Usage: Â§f/difficulty <peaceful|easy|medium|hard|nightmare>");
            return true;
        }

        // Parse the requested difficulty
        DifficultyLevel requested = DifficultyLevel.fromString(args[0]);
        if (requested == null) {
            player.sendMessage("Â§cUnknown difficulty. Choose: Â§fpeaceful/easy/medium/hard/nightmare");
            player.sendMessage("Â§7Usage: Â§f/difficulty <peaceful|easy|medium|hard|nightmare>");
            return true;
        }

        boolean isAdmin = player.isOp() || player.hasPermission("difficultyengine.admin");
        DifficultyLevel current = manager.getDifficulty(player.getUniqueId());

        // â”€â”€ Nightmare only admins can change to/from â”€â”€
        if ((requested == DifficultyLevel.NIGHTMARE || current == DifficultyLevel.NIGHTMARE) && !isAdmin) {
            player.sendMessage("Â§cboxup make a ticket quickly and logf out of the game**");
            player.sendMessage("Â§5Â§lâœ¦ Â§7Join our Discord: Â§bÂ§nhttps://discord.gg/SreKERPhNB");
            return true;
        }

        // â”€â”€ Other stats only when out of combat, full HP, full hunger â”€â”€
        if (!isAdmin) {
            // Check combat status
            if (player.hasMetadata("last_combat_time")) {
                long lastCombat = player.getMetadata("last_combat_time").get(0).asLong();
                if (System.currentTimeMillis() - lastCombat < 10000L) { // 10s combat tag
                    long secondsLeft = 10L - (System.currentTimeMillis() - lastCombat) / 1000L;
                    player.sendMessage("Â§câœ— Â§7You cannot change difficulty while in combat! Â§e(" + secondsLeft + "s left)");
                    return true;
                }
            }
            
            // Check HP
            double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            if (player.getHealth() < maxHp) {
                player.sendMessage("Â§câœ— Â§7You must be at Â§afull HPÂ§7 to change difficulty!");
                return true;
            }
            
            // Check hunger
            if (player.getFoodLevel() < 20) {
                player.sendMessage("Â§câœ— Â§7You must be at Â§ffull hunger (20)Â§7 to change difficulty!");
                return true;
            }
        }

        // Apply it â€” this also syncs the PDC nightmare tag if applicable
        manager.setDifficulty(player.getUniqueId(), requested);

        player.sendMessage("Â§8[Â§6DifficultyEngineÂ§8] Â§7Difficulty set to: " + requested.getDisplayName());

        // Send a flavour message explaining what changed
        switch (requested) {
            case PEACEFUL ->
                player.sendMessage("Â§a  Hostile mobs will ignore you. Enjoy the peace.");
            case EASY ->
                player.sendMessage("Â§2  Vanilla experience. No changes to mobs near you.");
            case MEDIUM ->
                player.sendMessage("Â§e  Mobs near you have +10% HP and +8% damage. A step up from Easy.");
            case HARD ->
                player.sendMessage("Â§c  Mobs near you spawn with +25% HP, +15% damage, and longer follow range.");
            case NIGHTMARE ->
                player.sendMessage("Â§4  â˜  You asked for this. Mobs have +50% HP, +25% damage, +15% speed,\n" +
                                   "Â§4  64-block follow range, increased spawn rates, and they PREFER targeting you.\n" +
                                   "Â§4  Your presence pulls aggro onto nearby party members.");
        }

        return true;
    }
}
