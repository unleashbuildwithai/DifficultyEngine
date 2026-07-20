package com.yourname.difficulty.skills;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * SkillGUI — 54-slot /mystats panel.
 *
 * Layout (54 slots = 6 rows × 9 cols):
 * Row 0 (0-8):   border  |  "Combat" label @4
 * Row 1 (9-17):  [MELEE@10]  [RANGED@12]  [DEFENCE@14]  [PRAYER@16]
 * Row 2 (18-26): border  |  "Gathering" label @22
 * Row 3 (27-35): [MAGIC@28]  [WOODCUT@30]  [FISHING@32]  [FARMING@34]
 * Row 4 (36-44): border
 * Row 5 (45-53): [TOTAL@46]  ─────────  [PLAYTIME@52]
 *
 * All items use amount = 1 (no misleading stack numbers).
 * Hover tooltip shows: rank, XP, progress bar, and ONLY mechanics
 * that are actually implemented in the plugin.
 */
public class SkillGUI {

    public static final String TITLE = "§8[ §6My Stats §8]";

    private static final int SLOT_MELEE       = 10;
    private static final int SLOT_RANGED      = 12;
    private static final int SLOT_DEFENCE     = 14;
    private static final int SLOT_PRAYER      = 16;
    private static final int SLOT_MAGIC       = 28;
    private static final int SLOT_WOODCUTTING = 30;
    private static final int SLOT_FISHING     = 32;
    private static final int SLOT_FARMING     = 34;
    private static final int SLOT_TOTAL       = 46;
    private static final int SLOT_PLAYTIME    = 52;

    private final SkillManager skillManager;
    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    public SkillGUI(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /** Opens the stats GUI for the viewer showing the target's stats. */
    public void open(Player viewer, Player target) {
        UUID   uuid  = target.getUniqueId();
        String name  = target.getName();
        String title = viewer.equals(target)
                ? TITLE
                : "§8[ §6" + name + "'s Stats §8]";

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill with glass border
        ItemStack glass = filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // Section labels (replaced with clean gray glass border to remove blank items)
        inv.setItem(4,  filler());
        inv.setItem(22, filler());

        // Skill items — all amount 1 (no stack numbers)
        inv.setItem(SLOT_MELEE,       buildSkillItem(uuid, SkillType.MELEE));
        inv.setItem(SLOT_RANGED,      buildSkillItem(uuid, SkillType.RANGED));
        inv.setItem(SLOT_DEFENCE,     buildSkillItem(uuid, SkillType.DEFENCE));
        inv.setItem(SLOT_PRAYER,      buildSkillItem(uuid, SkillType.PRAYER));
        inv.setItem(SLOT_MAGIC,       buildSkillItem(uuid, SkillType.MAGIC));
        inv.setItem(SLOT_WOODCUTTING, buildSkillItem(uuid, SkillType.WOODCUTTING));
        inv.setItem(SLOT_FISHING,     buildSkillItem(uuid, SkillType.FISHING));
        inv.setItem(SLOT_FARMING,     buildSkillItem(uuid, SkillType.FARMING));

        // Summary items
        inv.setItem(SLOT_TOTAL,    totalLevelItem(uuid));
        inv.setItem(SLOT_PLAYTIME, playtimeItem(target));

        viewer.openInventory(inv);
    }

    // ── Skill item builder ────────────────────────────────────────────────────

    private ItemStack buildSkillItem(UUID uuid, SkillType skill) {
        long   xp     = skillManager.getXp(uuid, skill);
        int    level  = SkillLevel.getLevelForXp(xp);
        long   toNext = SkillLevel.getXpToNextLevel(xp);
        String bar    = SkillLevel.getProgressBar(xp);
        String rank   = SkillLevel.getRank(level);

        // Always amount 1 — no confusing corner numbers
        ItemStack item = new ItemStack(skill.getIcon(), 1);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String maxTag = (level >= 99) ? " §6(MAX)" : "";
        meta.setDisplayName(skill.getColorCode() + "§l" + skill.getDisplayName()
                + " §r§7- §aLevel " + level + maxTag);

        List<String> lore = new ArrayList<>();
        lore.add("§7Rank: " + rank);
        lore.add("§7XP: §f" + NF.format(xp)
                + (level < 99 ? "  §8(+" + NF.format(toNext) + " to next)" : ""));
        lore.add("§7Progress: " + bar);
        lore.add("§8" + "─".repeat(26));
        addSkillTips(lore, skill, level);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Skill-specific tooltips ───────────────────────────────────────────────
    // Only shows mechanics that are ACTUALLY implemented in the plugin.

    private void addSkillTips(List<String> lore, SkillType skill, int level) {

        if (skill == SkillType.MELEE) {
            lore.add("§6Train: §7Kill mobs with a sword or axe.");
            lore.add("§7Better sword tier = more XP per kill.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Bonuses (always active):");
            lore.add("§7Bonus damage: §a+" + String.format("%.2f", level * 0.02) + " §7per hit");
            lore.add("§7Crit chance: §a" + String.format("%.1f", level * 0.3) + "% §7(deals §c1.5x §7damage)");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Weapon level requirements:");
            lore.add(check(level >= 1)  + " §7Lv  1  - Wood, Stone, Gold swords");
            lore.add(check(level >= 20) + " §7Lv 20  - Iron sword / axe");
            lore.add(check(level >= 40) + " §7Lv 40  - Diamond sword / axe");
            lore.add(check(level >= 70) + " §7Lv 70  - Netherite sword / axe");
            lore.add(check(level >= 99) + " §7Lv 99  - §6MAX: +1.98 dmg, ~30% crit");

        } else if (skill == SkillType.RANGED) {
            lore.add("§6Train: §7Kill mobs with a bow or crossbow.");
            lore.add("§7Better mobs (more HP) = more XP per kill.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Bonuses (always active):");
            lore.add("§7Bonus arrow damage: §a+" + String.format("%.2f", level * 0.015) + " §7per shot");
            lore.add("§7Tipped arrow duration: §a" + String.format("%.1f", 1.0 + level / 99.0) + "x §7base");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Weapon level requirements:");
            lore.add(check(level >= 1)  + " §7Lv  1  - Bow usable");
            lore.add(check(level >= 30) + " §7Lv 30  - Crossbow usable");
            lore.add(check(level >= 99) + " §7Lv 99  - §6MAX: +1.49 dmg, 2x arrow effects");

        } else if (skill == SkillType.DEFENCE) {
            lore.add("§6Train: §7Block attacks with a shield.");
            lore.add("§7XP awarded on each successful block.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Bonuses (always active):");
            lore.add("§7Damage reduction: §a" + String.format("%.1f", level * 0.2) + "% §7of all incoming hits");
            lore.add("§7Bonus max HP: §a+" + String.format("%.1f", level / 10.0 > 5 ? 5.0 : Math.floor(level / 10.0) * 0.5)
                    + " §7hearts (every 10 levels)");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Armour level requirements:");
            lore.add(check(level >= 1)  + " §7Lv  1  - Leather armour");
            lore.add(check(level >= 15) + " §7Lv 15  - Gold / Chainmail armour");
            lore.add(check(level >= 30) + " §7Lv 30  - Iron armour");
            lore.add(check(level >= 50) + " §7Lv 50  - Diamond armour");
            lore.add(check(level >= 70) + " §7Lv 70  - Netherite armour");
            lore.add(check(level >= 99) + " §7Lv 99  - §6MAX: ~19.8% reduction, +5 hearts");

        } else if (skill == SkillType.PRAYER) {
            lore.add("§6Train: §7Right-click a bone on dirt or grass.");
            lore.add("§7Bone = 4 XP  |  Bone Meal = 2 XP");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Passive bonus:");
            double blockChance = Math.pow(level / 99.0, 1.5) * 30.0;
            lore.add("§7Hit-negate chance: §a" + String.format("%.1f", blockChance) + "%");
            lore.add("§7Each incoming hit has this chance to be");
            lore.add("§7completely negated (0 damage taken).");
            lore.add("§8" + "─".repeat(26));
            lore.add(check(level >= 1)  + " §7Lv  1  - ~0.03% negate chance");
            lore.add(check(level >= 40) + " §7Lv 40  - ~6% negate chance");
            lore.add(check(level >= 70) + " §7Lv 70  - ~17% negate chance");
            lore.add(check(level >= 99) + " §7Lv 99  - §630% negate chance");

        } else if (skill == SkillType.MAGIC) {
            lore.add("§6Train: §7Cast spells with elemental staffs.");
            lore.add("§7Craft staffs using an Enchanted Shard");
            lore.add("§7(5% drop from any mob) + element + stick.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Bonuses:");
            lore.add("§7Spell cooldown: §a" + String.format("%.1f", magicCooldownSec(level)) + "s §8(Lv1 = 3s, Lv99 = 1s)");
            int dmgBonus = (int) Math.floor(level / 33.0);
            lore.add("§7Extra spell damage: §a+" + dmgBonus + " §7heart"
                    + (dmgBonus == 1 ? "" : "s") + " §8(+1 per 33 levels)");
            lore.add("§8" + "─".repeat(26));
            lore.add("§7Staffs: Fire, Water, Earth, Air");
            lore.add("§7Mage Gear set (4 pieces): §a-1s §7cooldown");
            lore.add(check(level >= 99) + " §7Lv 99  - §61s cooldown + max damage");

        } else if (skill == SkillType.WOODCUTTING) {
            lore.add("§6Train: §7Chop logs with any axe.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Axe XP multipliers:");
            lore.add("§7Wood / Stone / Gold: §a0.8x");
            lore.add("§7Iron axe:            §a1.0x");
            lore.add("§7Diamond axe:         §a1.5x");
            lore.add("§7Netherite axe:       §a2.0x");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Passive bonus:");
            double ddChance = Math.pow(level / 99.0, 1.5) * 33.0;
            lore.add("§7Double-log drop chance: §a" + String.format("%.1f", ddChance) + "%");
            lore.add(check(level >= 99) + " §7Lv 99  - §633% double-drop chance");

        } else if (skill == SkillType.FISHING) {
            lore.add("§6Train: §7Catch fish with a fishing rod.");
            lore.add("§7Every catch = §a10 XP§7.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§7Train to Lv 99 to earn the");
            lore.add("§b~ Fishing Cape§7.");
            lore.add(check(level >= 99) + " §7Lv 99  - §6Fishing Cape awarded");

        } else if (skill == SkillType.FARMING) {
            lore.add("§6Train: §7Harvest fully-grown crops.");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Crop XP values:");
            lore.add("§7Wheat / Carrot / Potato:  §a2 XP");
            lore.add("§7Beetroot / Melon / Pumpkin: §a4 XP");
            lore.add("§7Cocoa / Nether Wart:       §a6 XP");
            lore.add("§7Bamboo / Glow Berries:    §a10 XP");
            lore.add("§8" + "─".repeat(26));
            lore.add("§6Passive bonus:");
            double farmChance = Math.pow(level / 99.0, 1.5) * 50.0;
            lore.add("§7Double-crop drop chance: §a" + String.format("%.1f", farmChance) + "%");
            lore.add(check(level >= 99) + " §7Lv 99  - §650% double-crop chance");
        }
    }

    /** Returns §a+ if unlocked, §c- if not. */
    private String check(boolean unlocked) {
        return unlocked ? "§a[+]" : "§7[ ]";
    }

    /** Spell cooldown in seconds at the given magic level (no gear bonus). */
    private double magicCooldownSec(int level) {
        long ms = 3000L - (long) ((level / 99.0) * 2000L);
        return Math.max(1000L, ms) / 1000.0;
    }

    // ── Total Level item ──────────────────────────────────────────────────────

    private ItemStack totalLevelItem(UUID uuid) {
        int total = skillManager.getTotalLevel(uuid);
        int max   = SkillType.values().length * 99;

        ItemStack item = new ItemStack(Material.NETHER_STAR, 1);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§6§lTotal Level  §r§e" + total + " §8/ §f" + max);

        double pct    = (double) total / max;
        int    filled = (int) Math.round(pct * 16);
        String bar    = "§a" + "█".repeat(filled) + "§8" + "░".repeat(16 - filled);

        List<String> lore = new ArrayList<>();
        lore.add("§7Overall: " + bar + " §e" + String.format("%.1f", pct * 100) + "%");
        lore.add("§8" + "─".repeat(26));
        for (SkillType s : SkillType.values()) {
            int lvl  = skillManager.getLevel(uuid, s);
            String star = (lvl >= 99) ? "§6★ " : "§8  ";
            lore.add(star + s.getColorCode() + s.getDisplayName()
                    + " §8- §a" + lvl + (lvl >= 99 ? " §6MAX" : ""));
        }
        lore.add("§8" + "─".repeat(26));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Playtime item ─────────────────────────────────────────────────────────

    private ItemStack playtimeItem(Player player) {
        ItemStack item = new ItemStack(Material.CLOCK, 1);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§b§lPlaytime");

        long ticks = 0L;
        try {
            ticks = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        } catch (Exception ignored) {
            try { ticks = player.getTicksLived(); } catch (Exception e2) { ticks = 0L; }
        }

        long seconds = ticks / 20;
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        List<String> lore = new ArrayList<>();
        lore.add("§7Time on server:");
        lore.add("§e" + hours + " hours  " + minutes + " min");
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Keep grinding for those Lv 99 capes!");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── GUI helpers ───────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  meta  = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    private ItemStack label(String name, Material mat) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of());
            item.setItemMeta(meta);
        }
        return item;
    }
}
