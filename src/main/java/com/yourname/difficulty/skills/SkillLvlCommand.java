package com.yourname.difficulty.skills;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SkillLvlCommand — Admin command to manually set or add levels to player skills.
 *
 * Usage:
 *   /skilllvl <player> <skill> set <1-99>    — Set skill to exact level
 *   /skilllvl <player> <skill> add <amount>  — Add N levels to skill
 *   /skilllvl <player> <skill> reset         — Reset skill to level 1
 *   /skilllvl <player> all set <1-99>        — Set ALL skills to a level
 *   /skilllvl <player> all reset             — Reset ALL skills to level 1
 *
 * Skill name aliases (case-insensitive):
 *   melee / attack / attk / atk → MELEE
 *   ranged / range              → RANGED
 *   defence / defense / def     → DEFENCE
 *   prayer / pray               → PRAYER
 *   magic / mage                → MAGIC
 *   woodcutting / wood / wc     → WOODCUTTING
 *   fishing / fish              → FISHING
 *   farming / farm              → FARMING
 *   all                         → all skills at once
 *
 * Requires: difficultyengine.cape.admin permission (OP by default).
 */
public class SkillLvlCommand implements CommandExecutor, TabCompleter {

    private final SkillManager skillManager;
    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    public SkillLvlCommand(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // ── Command handling ──────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        // Permission check
        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§c✗ §7You don't have permission to use §e/skilllvl§7.");
            return true;
        }

        // Minimum args: /skilllvl <player> <skill> <action> [value]
        if (args.length < 3) {
            sendUsage(sender, label);
            return true;
        }

        // ── Resolve target player ─────────────────────────────────────────────
        String playerName = args[0];
        Player target = Bukkit.getPlayerExact(playerName);
        UUID targetUUID = null;

        if (target != null) {
            targetUUID = target.getUniqueId();
        } else {
            // Try offline player by name
            @SuppressWarnings("deprecation")
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            if (offline.hasPlayedBefore()) {
                targetUUID = offline.getUniqueId();
                playerName = offline.getName() != null ? offline.getName() : playerName;
            } else {
                sender.sendMessage("§c✗ §7Player not found: §e" + args[0]);
                return true;
            }
        }

        String skillArg  = args[1].toLowerCase(Locale.ROOT);
        String action    = args[2].toLowerCase(Locale.ROOT);

        // ── ALL skills shortcut ───────────────────────────────────────────────
        if (skillArg.equals("all")) {
            handleAll(sender, target, targetUUID, playerName, action, args);
            return true;
        }

        // ── Resolve single skill ──────────────────────────────────────────────
        SkillType skill = resolveSkill(skillArg);
        if (skill == null) {
            sender.sendMessage("§c✗ §7Unknown skill: §e" + args[1]);
            sender.sendMessage("§7Valid skills: §emelee§7, §eranged§7, §edefence§7, §eprayer§7, §emagic§7, §ewoodcutting§7, §efishing§7, §efarming§7, §eall");
            return true;
        }

        // ── Handle action ─────────────────────────────────────────────────────
        switch (action) {
            case "set" -> {
                if (args.length < 4) { sendUsage(sender, label); return true; }
                int level = parseLevel(sender, args[3]);
                if (level < 0) return true;
                int oldLevel = skillManager.getLevel(targetUUID, skill);
                skillManager.setLevel(targetUUID, skill, level);
                long xp = SkillLevel.getXpForLevel(level);
                sender.sendMessage("§6✦ §7Set §e" + playerName + "§7's "
                        + skill.colored() + " §7to level §e" + level
                        + " §8(was " + oldLevel + ", XP: " + NF.format(xp) + ")§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin set your "
                            + skill.colored() + " §7to level §e" + level + "§7.");
                }
            }
            case "add" -> {
                if (args.length < 4) { sendUsage(sender, label); return true; }
                int amount = parseLevelAmount(sender, args[3]);
                if (amount < 0) return true;
                int oldLevel = skillManager.getLevel(targetUUID, skill);
                int newLevel = Math.min(99, oldLevel + amount);
                skillManager.setLevel(targetUUID, skill, newLevel);
                int gained = newLevel - oldLevel;
                sender.sendMessage("§6✦ §7Added §e" + gained + " §7level(s) to §e"
                        + playerName + "§7's " + skill.colored()
                        + " §8(" + oldLevel + " → " + newLevel + ")§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin added §e" + gained
                            + " §7level(s) to your " + skill.colored()
                            + " §8(" + oldLevel + " → " + newLevel + ")§7.");
                }
            }
            case "reset" -> {
                int oldLevel = skillManager.getLevel(targetUUID, skill);
                skillManager.setLevel(targetUUID, skill, 1);
                sender.sendMessage("§6✦ §7Reset §e" + playerName + "§7's "
                        + skill.colored() + " §7to level §e1 §8(was " + oldLevel + ")§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin reset your "
                            + skill.colored() + " §7to level §e1§7.");
                }
            }
            default -> sendUsage(sender, label);
        }
        return true;
    }

    // ── ALL skills handler ─────────────────────────────────────────────────────

    private void handleAll(CommandSender sender, Player target, UUID uuid,
                           String playerName, String action, String[] args) {
        switch (action) {
            case "set" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §e/skilllvl <player> all set <1-99>"); return; }
                int level = parseLevel(sender, args[3]);
                if (level < 0) return;
                for (SkillType s : SkillType.values()) {
                    skillManager.setLevel(uuid, s, level);
                }
                sender.sendMessage("§6✦ §7Set §6ALL §7skills for §e" + playerName
                        + " §7to level §e" + level + "§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin set §6ALL §7your skills to level §e" + level + "§7.");
                }
            }
            case "reset" -> {
                for (SkillType s : SkillType.values()) {
                    skillManager.setLevel(uuid, s, 1);
                }
                sender.sendMessage("§6✦ §7Reset §6ALL §7skills for §e" + playerName + " §7to level §e1§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin reset §6ALL §7your skills to level §e1§7.");
                }
            }
            case "add" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §e/skilllvl <player> all add <amount>"); return; }
                int amount = parseLevelAmount(sender, args[3]);
                if (amount < 0) return;
                for (SkillType s : SkillType.values()) {
                    int oldLvl = skillManager.getLevel(uuid, s);
                    skillManager.setLevel(uuid, s, Math.min(99, oldLvl + amount));
                }
                sender.sendMessage("§6✦ §7Added §e" + amount + " §7level(s) to §6ALL §7skills for §e"
                        + playerName + "§7.");
                if (target != null && !target.equals(sender)) {
                    target.sendMessage("§6✦ §7An admin added §e" + amount
                            + " §7level(s) to §6ALL §7of your skills.");
                }
            }
            default -> sender.sendMessage("§cUsage: §e/skilllvl <player> all <set|add|reset> [value]");
        }
    }

    // ── Tab completion ─────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) return List.of();

        return switch (args.length) {
            case 1 -> Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
            case 2 -> Arrays.asList("melee", "ranged", "defence", "prayer",
                                     "magic", "woodcutting", "fishing", "farming", "all")
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
            case 3 -> Arrays.asList("set", "add", "reset")
                    .stream()
                    .filter(a -> a.startsWith(args[2].toLowerCase()))
                    .toList();
            case 4 -> {
                String action = args[2].toLowerCase();
                if (action.equals("set"))
                    yield Arrays.asList("1", "10", "20", "30", "40", "50", "60", "70", "80", "90", "99");
                if (action.equals("add"))
                    yield Arrays.asList("1", "5", "10", "20", "30", "50");
                yield List.of();
            }
            default -> List.of();
        };
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Resolves a typed skill name (including common abbreviations) to a SkillType.
     * Returns null if unrecognised.
     */
    private static SkillType resolveSkill(String input) {
        return switch (input) {
            case "melee", "attack", "attk", "atk", "combat"   -> SkillType.MELEE;
            case "ranged", "range", "archery"                   -> SkillType.RANGED;
            case "defence", "defense", "def"                    -> SkillType.DEFENCE;
            case "prayer", "pray"                               -> SkillType.PRAYER;
            case "magic", "mage"                                -> SkillType.MAGIC;
            case "woodcutting", "wood", "wc"                    -> SkillType.WOODCUTTING;
            case "fishing", "fish"                              -> SkillType.FISHING;
            case "farming", "farm"                              -> SkillType.FARMING;
            default                                             -> null;
        };
    }

    /** Parses a 1–99 level integer from user input, sends an error if invalid. */
    private int parseLevel(CommandSender sender, String input) {
        try {
            int level = Integer.parseInt(input);
            if (level < 1 || level > 99) {
                sender.sendMessage("§c✗ §7Level must be between §e1 §7and §e99§7.");
                return -1;
            }
            return level;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c✗ §7Invalid number: §e" + input);
            return -1;
        }
    }

    /** Parses a positive integer amount (levels to add). */
    private int parseLevelAmount(CommandSender sender, String input) {
        try {
            int amount = Integer.parseInt(input);
            if (amount < 1) {
                sender.sendMessage("§c✗ §7Amount must be at least §e1§7.");
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c✗ §7Invalid number: §e" + input);
            return -1;
        }
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage("§8" + "═".repeat(44));
        sender.sendMessage("  §6✦ /" + label + " — Skill Level Admin Tool");
        sender.sendMessage("§8" + "═".repeat(44));
        sender.sendMessage("  §e/" + label + " §7<player> <skill> set <1-99>");
        sender.sendMessage("  §e/" + label + " §7<player> <skill> add <levels>");
        sender.sendMessage("  §e/" + label + " §7<player> <skill> reset");
        sender.sendMessage("  §e/" + label + " §7<player> all set <1-99>");
        sender.sendMessage("  §e/" + label + " §7<player> all add <levels>");
        sender.sendMessage("  §e/" + label + " §7<player> all reset");
        sender.sendMessage("§8" + "─".repeat(44));
        sender.sendMessage("  §7Skills: §emelee§7, §eranged§7, §edefence§7, §eprayer§7,");
        sender.sendMessage("           §emagic§7, §ewoodcutting§7, §efishing§7, §efarming§7, §eall");
        sender.sendMessage("  §7Aliases: §eattk §8= §emelee§7, §edef §8= §edefence§7, §ewc §8= §ewoodcutting");
        sender.sendMessage("§8" + "═".repeat(44));
    }
}
