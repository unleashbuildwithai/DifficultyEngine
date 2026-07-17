package com.yourname.difficulty.skills;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Arrays;
import java.util.List;

/**
 * SkillCapeManager — creates and awards Level 99 Skill Capes.
 *
 * Capes are dyed leather chestplates with custom names/lore.
 * They are physical items given to the player's inventory.
 * The Max Cape is given when ALL skills reach level 99.
 *
 * Cape identification uses item display name matching.
 */
public final class SkillCapeManager {

    private SkillCapeManager() {}

    // ── Cape colours per skill ────────────────────────────────────────────────

    private static Color capeColor(SkillType skill) {
        return switch (skill) {
            case MELEE       -> Color.fromRGB(180,  30,  30); // deep red
            case RANGED      -> Color.fromRGB( 30, 160,  30); // forest green
            case DEFENCE     -> Color.fromRGB( 30,  60, 200); // royal blue
            case WOODCUTTING -> Color.fromRGB( 60, 110,  40); // dark green
            case FISHING     -> Color.fromRGB( 30, 160, 200); // cyan-blue
            case FARMING     -> Color.fromRGB(210, 170,  20); // golden wheat
        };
    }

    /** Unique display name used to identify a cape in the player's inventory. */
    public static String capeName(SkillType skill) {
        return "§6✦ §" + colorChar(skill) + "Cape of " + skill.getDisplayName() + " §6✦";
    }

    public static final String MAX_CAPE_NAME = "§6✦ §5Max Cape §6✦";

    // ── Item builders ─────────────────────────────────────────────────────────

    /** Builds the Level 99 Skill Cape for the given skill. */
    public static ItemStack buildSkillCape(SkillType skill) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setColor(capeColor(skill));
        meta.setDisplayName(capeName(skill));
        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§7Skill: " + skill.colored(),
            "§7Level: §a99 §8(MAX)",
            "§8" + "─".repeat(28),
            "§6Awarded for reaching §aLevel 99",
            "§6in §" + colorChar(skill) + skill.getDisplayName() + "§6.",
            "§8" + "─".repeat(28),
            "§5Wear this cape with pride."
        ));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Builds the Max Cape (all skills 99). */
    public static ItemStack buildMaxCape() {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setColor(Color.fromRGB(130, 0, 200)); // purple
        meta.setDisplayName(MAX_CAPE_NAME);

        StringBuilder skills = new StringBuilder("§7");
        for (SkillType s : SkillType.values()) {
            skills.append(s.getColorCode()).append(s.getDisplayName()).append("§8, ");
        }

        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§5All Skills: §699",
            "§8" + "─".repeat(28),
            "§6This cape represents mastery",
            "§6of §fevery §6skill.",
            "§8" + "─".repeat(28),
            skills.toString().replaceAll(", $", ""),
            "§8" + "─".repeat(28),
            "§d✦ True Max Cape ✦"
        ));
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    // ── Award logic ───────────────────────────────────────────────────────────

    /**
     * Awards a skill cape to a player if they just reached level 99 in that skill
     * and don't already own one. Also checks for Max Cape eligibility.
     *
     * @param player  the player
     * @param skill   the skill that reached 99
     * @param manager the SkillManager to check other skill levels
     */
    public static void checkAndAward(Player player, SkillType skill, SkillManager manager) {
        // Award skill-specific cape
        if (!hasCape(player, capeName(skill))) {
            ItemStack cape = buildSkillCape(skill);
            player.getInventory().addItem(cape);
            player.sendMessage("");
            player.sendMessage("§6✦ §eCONGRATULATIONS! §6✦");
            player.sendMessage("§7You have reached §aLevel 99 §7in §"
                    + colorChar(skill) + skill.getDisplayName() + "§7!");
            player.sendMessage("§6You have been awarded the §"
                    + colorChar(skill) + "Cape of " + skill.getDisplayName() + "§6!");
            player.sendMessage("§7Equip it in your chestplate slot.");
            player.sendMessage("");
        }

        // Check Max Cape
        if (manager.isMaxed(player.getUniqueId()) && !hasCape(player, MAX_CAPE_NAME)) {
            ItemStack maxCape = buildMaxCape();
            player.getInventory().addItem(maxCape);
            player.sendMessage("");
            player.sendMessage("§5✦✦✦ §6MAX CAPE UNLOCKED! §5✦✦✦");
            player.sendMessage("§7You have achieved §aLevel 99 §7in §fevery skill§7!");
            player.sendMessage("§5The Max Cape has been added to your inventory.");
            player.sendMessage("");
        }
    }

    /** Returns true if the player has a cape with the given display name in any inventory slot. */
    public static boolean hasCape(Player player, String capeName) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.hasItemMeta() && capeName.equals(item.getItemMeta().getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static char colorChar(SkillType skill) {
        String code = skill.getColorCode();
        return code.length() >= 2 ? code.charAt(1) : 'f';
    }

    /** Returns a lore list describing all available capes. */
    public static List<String> getCapeGuideLines() {
        List<String> lines = new java.util.ArrayList<>();
        lines.add("§8" + "─".repeat(28));
        lines.add("§6Level 99 Skill Capes:");
        for (SkillType s : SkillType.values()) {
            lines.add("  §8- §" + colorChar(s) + s.getDisplayName() + " §7→ " + capeName(s));
        }
        lines.add("  §8- §5All Skills 99 §7→ " + MAX_CAPE_NAME);
        lines.add("§8" + "─".repeat(28));
        return lines;
    }
}
