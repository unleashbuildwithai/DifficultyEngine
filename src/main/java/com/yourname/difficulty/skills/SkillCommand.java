package com.yourname.difficulty.skills;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * SkillCommand — handles both /skills (chat text summary) and /mystats (GUI).
 *
 * Usage:
 *   /mystats           — Opens your skill tree GUI
 *   /mystats <player>  — Opens another player's skill tree GUI (OP only)
 *   /skills            — Prints your skill levels in chat
 *   /skills <player>   — Prints another player's levels (OP only)
 */
public class SkillCommand implements CommandExecutor {

    private final SkillManager skillManager;
    private final SkillGUI     skillGUI;
    private final boolean      isGuiCommand;

    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    public SkillCommand(SkillManager skillManager, SkillGUI skillGUI, boolean isGuiCommand) {
        this.skillManager  = skillManager;
        this.skillGUI      = skillGUI;
        this.isGuiCommand  = isGuiCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Resolve target player
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission("difficultyengine.skills.others")) {
                sender.sendMessage("§cYou don't have permission to view other players' skills.");
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
            sender.sendMessage("§cConsole must specify a player: /" + label + " <player>");
            return true;
        }

        if (isGuiCommand) {
            // GUI version (/mystats)
            if (!(sender instanceof Player viewer)) {
                sender.sendMessage("§cOnly players can open the skill tree GUI.");
                return true;
            }
            skillGUI.open(viewer, target);
        } else {
            // Text version (/skills)
            printSkills(sender, target);
        }
        return true;
    }

    // ── Text output ───────────────────────────────────────────────────────────

    private void printSkills(CommandSender sender, Player target) {
        UUID uuid = target.getUniqueId();
        boolean self = sender instanceof Player p && p.getUniqueId().equals(uuid);
        String who = self ? "Your" : target.getName() + "'s";

        sender.sendMessage("§8" + "═".repeat(40));
        sender.sendMessage("  §6✦ " + who + " Skill Levels §6✦");
        sender.sendMessage("§8" + "═".repeat(40));

        for (SkillType skill : SkillType.values()) {
            long xp    = skillManager.getXp(uuid, skill);
            int  level = SkillLevel.getLevelForXp(xp);
            long toNext = SkillLevel.getXpToNextLevel(xp);
            String bar = SkillLevel.getProgressBar(xp);

            String levelStr = level >= 99 ? "§699" : "§a" + level;
            String nextStr  = level >= 99 ? "§6MAX" : "§e" + NF.format(toNext) + " XP to go";

            sender.sendMessage("  " + skill.getColorCode() + "✦ " + skill.getDisplayName()
                    + " §8» " + levelStr
                    + " §8| " + bar
                    + " §8| " + nextStr);
        }

        int totalLevel = skillManager.getTotalLevel(uuid);
        int maxTotal   = SkillType.values().length * 99;
        sender.sendMessage("§8" + "─".repeat(40));
        sender.sendMessage("  §7Total Level: §e" + totalLevel + " §8/ §f" + maxTotal);
        sender.sendMessage("§8" + "═".repeat(40));

        if (!self) {
            sender.sendMessage("  §7Tip: Use §e/mystats " + target.getName() + " §7for the GUI.");
        } else {
            sender.sendMessage("  §7Tip: Use §e/mystats §7to open the visual skill tree GUI.");
        }
    }
}
