package com.yourname.difficulty.skills;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * SkillCapeManager — creates, identifies, and awards Level 99 Skill Capes.
 *
 * Capes are dyed leather chestplates tagged with PDC for reliable identity.
 * Symbols are embedded in the display name so the cape "shows" its skill.
 *
 * PDC key format:  difficultyengine:skill_cape_<lowercase_skill_name>
 *                  difficultyengine:skill_cape_max
 *
 * Admin perk: equipping a cape when you have difficultyengine.cape.admin
 *             instantly sets that skill to Level 99.
 */
public class SkillCapeManager {

    // ── Skill symbols embedded in display name ────────────────────────────────
    /** Symbols shown in the cape item name — visible in-game on hover and in hand. */
    public static String symbol(SkillType skill) {
        return switch (skill) {
            case MELEE       -> "⚔";
            case RANGED      -> "➤";
            case DEFENCE     -> "⛨";
            case WOODCUTTING -> "⛏";
            case FISHING     -> "≋";
            case FARMING     -> "✿";
        };
    }

    // ── PDC keys ──────────────────────────────────────────────────────────────

    private final JavaPlugin plugin;
    private final Map<SkillType, NamespacedKey> capeKeys = new EnumMap<>(SkillType.class);
    private final NamespacedKey maxCapeKey;

    public SkillCapeManager(JavaPlugin plugin) {
        this.plugin    = plugin;
        this.maxCapeKey = new NamespacedKey(plugin, "skill_cape_max");
        for (SkillType skill : SkillType.values()) {
            capeKeys.put(skill,
                new NamespacedKey(plugin, "skill_cape_" + skill.name().toLowerCase()));
        }
    }

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

    // ── Display names ─────────────────────────────────────────────────────────

    /** Full display name including symbol — used as the item's visible name. */
    public String capeName(SkillType skill) {
        return "§6✦ §" + colorChar(skill) + symbol(skill) + " Cape of "
                + skill.getDisplayName() + " §6✦";
    }

    public static final String MAX_CAPE_DISPLAY = "§6✦ §5★ Max Cape §6✦";

    // ── Item builders ─────────────────────────────────────────────────────────

    /** Builds the Level 99 Skill Cape for the given skill with PDC tag. */
    public ItemStack buildSkillCape(SkillType skill) {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setColor(capeColor(skill));
        meta.setDisplayName(capeName(skill));
        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§7Skill: " + skill.colored(),
            "§7Symbol: §f" + symbol(skill),
            "§7Level: §a99 §8(MAX)",
            "§8" + "─".repeat(28),
            "§6Awarded for reaching §aLevel 99",
            "§6in §" + colorChar(skill) + skill.getDisplayName() + "§6.",
            "§8" + "─".repeat(28),
            "§5Wear this cape with pride.",
            "§8Admin: equipping grants §aLv 99 §8instantly."
        ));
        meta.setUnbreakable(true);
        // PDC tag for reliable identity
        meta.getPersistentDataContainer()
            .set(capeKeys.get(skill), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Builds the Max Cape (all skills 99) with PDC tag. */
    public ItemStack buildMaxCape() {
        ItemStack item = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setColor(Color.fromRGB(130, 0, 200)); // purple
        meta.setDisplayName(MAX_CAPE_DISPLAY);

        StringBuilder skillLine = new StringBuilder("§7");
        for (SkillType s : SkillType.values()) {
            skillLine.append(s.getColorCode()).append(symbol(s)).append("§8 ");
        }

        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§5All Skills: §699 §8(MAX)",
            "§8" + "─".repeat(28),
            skillLine.toString().trim(),
            "§8" + "─".repeat(28),
            "§6This cape represents mastery",
            "§6of §fevery §6skill.",
            "§8" + "─".repeat(28),
            "§d★ True Max Cape ★",
            "§8Admin: equipping grants §aall skills Lv 99§8."
        ));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer()
            .set(maxCapeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── Identity checks ───────────────────────────────────────────────────────

    /** Returns the SkillType this item is a cape for, or null if it isn't a skill cape. */
    public SkillType getCapeSkill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (Map.Entry<SkillType, NamespacedKey> entry : capeKeys.entrySet()) {
            if (pdc.has(entry.getValue(), PersistentDataType.BYTE)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Returns true if this item is a skill cape (any skill). */
    public boolean isSkillCape(ItemStack item) {
        return getCapeSkill(item) != null;
    }

    /** Returns true if this item is the Max Cape. */
    public boolean isMaxCape(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(maxCapeKey, PersistentDataType.BYTE);
    }

    /** Returns true if the item is any cape (skill or max). */
    public boolean isAnyCape(ItemStack item) {
        return isSkillCape(item) || isMaxCape(item);
    }

    // ── Award logic ───────────────────────────────────────────────────────────

    /**
     * Awards a skill cape to a player if they just reached level 99 in that skill
     * and don't already own one. Also checks for Max Cape eligibility.
     */
    public void checkAndAward(Player player, SkillType skill, SkillManager manager) {
        // Award skill-specific cape
        if (!hasCapePdc(player, capeKeys.get(skill))) {
            ItemStack cape = buildSkillCape(skill);
            player.getInventory().addItem(cape);
            player.sendMessage("");
            player.sendMessage("§6✦ §eCONGRATULATIONS! §6✦");
            player.sendMessage("§7You have reached §aLevel 99 §7in §"
                    + colorChar(skill) + skill.getDisplayName() + "§7!");
            player.sendMessage("§6You have been awarded the §"
                    + colorChar(skill) + symbol(skill) + " Cape of "
                    + skill.getDisplayName() + "§6!");
            player.sendMessage("§7Equip it in your chestplate slot.");
            player.sendMessage("");
        }

        // Check Max Cape
        if (manager.isMaxed(player.getUniqueId()) && !hasCapePdc(player, maxCapeKey)) {
            ItemStack maxCape = buildMaxCape();
            player.getInventory().addItem(maxCape);
            player.sendMessage("");
            player.sendMessage("§5✦✦✦ §6MAX CAPE UNLOCKED! §5✦✦✦");
            player.sendMessage("§7You have achieved §aLevel 99 §7in §fevery skill§7!");
            player.sendMessage("§5The §d★ Max Cape §5has been added to your inventory.");
            player.sendMessage("");
        }
    }

    // ── PDC cape presence check ───────────────────────────────────────────────

    /** Returns true if the player has a cape tagged with the given PDC key anywhere in their inventory. */
    private boolean hasCapePdc(Player player, NamespacedKey key) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer()
                    .has(key, PersistentDataType.BYTE)) return true;
        }
        return false;
    }

    // ── Registry helper ───────────────────────────────────────────────────────

    /** Returns a list of all cape items (for registry registration). */
    public List<ItemStack> buildAllCapes() {
        List<ItemStack> list = new ArrayList<>();
        for (SkillType skill : SkillType.values()) {
            list.add(buildSkillCape(skill));
        }
        list.add(buildMaxCape());
        return list;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public NamespacedKey getCapeKey(SkillType skill) { return capeKeys.get(skill); }
    public NamespacedKey getMaxCapeKey()              { return maxCapeKey; }

    private static char colorChar(SkillType skill) {
        String code = skill.getColorCode();
        return code.length() >= 2 ? code.charAt(1) : 'f';
    }
}
