package com.yourname.difficulty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * CureCosmeticCommand — /curecosmetic
 *
 * Removes all cosmetic/visual negative effects from a player:
 *   Blindness, Nausea, Darkness, Hunger, Mining Fatigue, Slowness
 *
 * Usage:
 *   /curecosmetic            — cures yourself
 *   /curecosmetic <player>   — cures another player (requires .others permission)
 *
 * Permission: difficultyengine.curecosmetic (default: op)
 */
public class CureCosmeticCommand implements CommandExecutor {

    /** All effects considered "cosmetic" and safe to remove. */
    private static final List<PotionEffectType> COSMETIC_EFFECTS = List.of(
        PotionEffectType.BLINDNESS,
        PotionEffectType.NAUSEA,
        PotionEffectType.DARKNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.SLOWNESS
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("difficultyengine.curecosmetic.others")) {
                sender.sendMessage("§cYou don't have permission to cure other players.");
                return true;
            }
            target = sender.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: §f" + args[0]);
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cConsole must specify a player: /curecosmetic <player>");
            return true;
        }

        int removed = 0;
        for (PotionEffectType effect : COSMETIC_EFFECTS) {
            if (target.hasPotionEffect(effect)) {
                target.removePotionEffect(effect);
                removed++;
            }
        }

        boolean self = sender instanceof Player p && p.equals(target);

        if (removed > 0) {
            target.sendMessage("§b✦ §7All cosmetic effects have been cured. §b(" + removed + " removed)");
            if (!self) {
                sender.sendMessage("§8[§6DifficultyEngine§8] §aCured §f" + removed
                        + " §acosmetic effect(s) from §f" + target.getName() + "§a.");
            }
        } else {
            if (self) {
                sender.sendMessage("§7You have no active cosmetic effects to cure.");
            } else {
                sender.sendMessage("§7" + target.getName() + " has no active cosmetic effects.");
            }
        }
        return true;
    }
}
