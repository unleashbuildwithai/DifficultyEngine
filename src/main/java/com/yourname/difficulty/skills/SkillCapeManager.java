package com.yourname.difficulty.skills;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * Capes are ELYTRA items so they appear visually on the player's back as
 * folded wings/cape — much closer to a real cape than a chestplate.
 *
 * Each cape has:
 *   • A unique Custom Model Data ID (1001–1007) for resource pack support
 *   • A skill symbol embedded in the display name
 *   • A PDC tag for reliable identity
 *   • An enchantment glint (Unbreaking 1, hidden) for a legendary look
 *
 * Custom Model Data IDs:
 *   MELEE       1001
 *   RANGED      1002
 *   DEFENCE     1003
 *   WOODCUTTING 1004
 *   FISHING     1005
 *   FARMING     1006
 *   MAX CAPE    1007
 *
 * Admin perk: equipping a cape when you have difficultyengine.cape.admin
 *             instantly sets that skill to Level 99.
 */
public class SkillCapeManager {

    // ── Skill symbols ─────────────────────────────────────────────────────────
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

    // ── Custom Model Data IDs ─────────────────────────────────────────────────
    private static int modelData(SkillType skill) {
        return switch (skill) {
            case MELEE       -> 1001;
            case RANGED      -> 1002;
            case DEFENCE     -> 1003;
            case WOODCUTTING -> 1004;
            case FISHING     -> 1005;
            case FARMING     -> 1006;
        };
    }
    private static final int MAX_CAPE_MODEL = 1007;

    // ── PDC keys ──────────────────────────────────────────────────────────────
    private final JavaPlugin plugin;
    private final Map<SkillType, NamespacedKey> capeKeys = new EnumMap<>(SkillType.class);
    private final NamespacedKey maxCapeKey;

    public SkillCapeManager(JavaPlugin plugin) {
        this.plugin     = plugin;
        this.maxCapeKey = new NamespacedKey(plugin, "skill_cape_max");
        for (SkillType skill : SkillType.values()) {
            capeKeys.put(skill,
                new NamespacedKey(plugin, "skill_cape_" + skill.name().toLowerCase()));
        }
    }

    // ── Display names ─────────────────────────────────────────────────────────
    public String capeName(SkillType skill) {
        return "§6✦ §" + colorChar(skill) + symbol(skill) + " Cape of "
                + skill.getDisplayName() + " §6✦";
    }
    public static final String MAX_CAPE_DISPLAY = "§6✦ §5★ Max Cape §6✦";

    // ── Item builders ─────────────────────────────────────────────────────────

    /** Builds a Level 99 Skill Cape as an ELYTRA (appears on back as a cape). */
    public ItemStack buildSkillCape(SkillType skill) {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(capeName(skill));
        meta.setCustomModelData(modelData(skill));
        meta.setUnbreakable(true);
        // Enchantment glint for legendary appearance (hidden enchant)
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE,
                          ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§7Skill: " + skill.colored() + " §7" + symbol(skill),
            "§7Level: §a99 §8(MAX)",
            "§8" + "─".repeat(28),
            "§6Awarded for reaching §aLevel 99",
            "§6in §" + colorChar(skill) + skill.getDisplayName() + "§6.",
            "§8" + "─".repeat(28),
            "§7Equip via §e/cape §7to wear on your back.",
            "§8Custom Model: §7" + modelData(skill),
            "§8" + "─".repeat(28),
            "§5Wear this cape with pride."
        ));
        // PDC identity tag
        meta.getPersistentDataContainer()
            .set(capeKeys.get(skill), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Builds the Max Cape as an ELYTRA. */
    public ItemStack buildMaxCape() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        StringBuilder skillLine = new StringBuilder("§7");
        for (SkillType s : SkillType.values()) {
            skillLine.append(s.getColorCode()).append(symbol(s)).append("§8 ");
        }

        meta.setDisplayName(MAX_CAPE_DISPLAY);
        meta.setCustomModelData(MAX_CAPE_MODEL);
        meta.setUnbreakable(true);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE,
                          ItemFlag.HIDE_ATTRIBUTES);
        meta.setLore(Arrays.asList(
            "§8" + "─".repeat(28),
            "§5All Skills: §699 §8(MAX)",
            "§8" + "─".repeat(28),
            skillLine.toString().trim(),
            "§8" + "─".repeat(28),
            "§6This cape represents mastery",
            "§6of §fevery §6skill.",
            "§8" + "─".repeat(28),
            "§7Equip via §e/cape §7to wear on your back.",
            "§8Custom Model: §7" + MAX_CAPE_MODEL,
            "§8" + "─".repeat(28),
            "§d★ True Max Cape ★"
        ));
        meta.getPersistentDataContainer()
            .set(maxCapeKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    public SkillType getCapeSkill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (Map.Entry<SkillType, NamespacedKey> entry : capeKeys.entrySet()) {
            if (pdc.has(entry.getValue(), PersistentDataType.BYTE)) return entry.getKey();
        }
        return null;
    }

    public boolean isSkillCape(ItemStack item) { return getCapeSkill(item) != null; }

    public boolean isMaxCape(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(maxCapeKey, PersistentDataType.BYTE);
    }

    public boolean isAnyCape(ItemStack item) {
        return isSkillCape(item) || isMaxCape(item);
    }

    // ── Award logic ───────────────────────────────────────────────────────────

    public void checkAndAward(Player player, SkillType skill, SkillManager manager) {
        if (!hasCapePdc(player, capeKeys.get(skill))) {
            player.getInventory().addItem(buildSkillCape(skill));
            player.sendMessage("");
            player.sendMessage("§6✦ §eCONGRATULATIONS! §6✦");
            player.sendMessage("§7You have reached §aLevel 99 §7in §"
                    + colorChar(skill) + skill.getDisplayName() + "§7!");
            player.sendMessage("§6You have been awarded the §"
                    + colorChar(skill) + symbol(skill) + " Cape of "
                    + skill.getDisplayName() + "§6!");
            player.sendMessage("§7Use §e/cape §7to equip it on your back.");
            player.sendMessage("");
        }
        if (manager.isMaxed(player.getUniqueId()) && !hasCapePdc(player, maxCapeKey)) {
            player.getInventory().addItem(buildMaxCape());
            player.sendMessage("");
            player.sendMessage("§5✦✦✦ §6MAX CAPE UNLOCKED! §5✦✦✦");
            player.sendMessage("§5The §d★ Max Cape §5has been added to your inventory.");
            player.sendMessage("§7Use §e/cape §7to equip it.");
            player.sendMessage("");
        }
    }

    // ── Registry / helpers ────────────────────────────────────────────────────

    public List<ItemStack> buildAllCapes() {
        List<ItemStack> list = new ArrayList<>();
        for (SkillType skill : SkillType.values()) list.add(buildSkillCape(skill));
        list.add(buildMaxCape());
        return list;
    }

    public NamespacedKey getCapeKey(SkillType skill) { return capeKeys.get(skill); }
    public NamespacedKey getMaxCapeKey()              { return maxCapeKey; }

    private boolean hasCapePdc(Player player, NamespacedKey key) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            if (item.getItemMeta().getPersistentDataContainer()
                    .has(key, PersistentDataType.BYTE)) return true;
        }
        // Also check elytra slot
        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra != null && elytra.hasItemMeta()) {
            if (elytra.getItemMeta().getPersistentDataContainer()
                    .has(key, PersistentDataType.BYTE)) return true;
        }
        return false;
    }

    private static char colorChar(SkillType skill) {
        String code = skill.getColorCode();
        return code.length() >= 2 ? code.charAt(1) : 'f';
    }
}
