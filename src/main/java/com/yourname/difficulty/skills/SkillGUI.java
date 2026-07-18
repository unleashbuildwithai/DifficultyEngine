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
 * SkillGUI — Redesigned 54-slot /mystats panel.
 *
 * Layout (54 slots = 6 rows × 9 cols):
 * Row 0 (0-8):   border  |  "⚔ Combat" label @4
 * Row 1 (9-17):  [MELEE@10]  [RANGED@12]  [DEFENCE@14]  [PRAYER@16]
 * Row 2 (18-26): border  |  "✦ Skills" label @22
 * Row 3 (27-35): [MAGIC@28]  [WOODCUT@30]  [FISHING@32]  [FARMING@34]
 * Row 4 (36-44): border
 * Row 5 (45-53): [TOTAL@46]  ─────────  [PLAYTIME@52]
 *
 * Stack count on each skill item = current level (capped at 64).
 * Hover tooltip shows: rank, XP, progress, gear/bonus unlocks per level.
 *
 * Note: addSkillTips uses plain if-else chains (no switch arrow blocks)
 * to avoid the SkillGUI$1 synthetic inner-class crash on Java 21+.
 */
public class SkillGUI {

    public static final String TITLE = "§8✦ §6My Stats §8✦";

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
                : "§8✦ §6" + name + "'s Stats §8✦";

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill every slot with glass pane
        ItemStack glass = filler();
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // Section labels
        inv.setItem(4,  label("§6⚔ §lCombat Skills",   Material.NETHERITE_SWORD));
        inv.setItem(22, label("§d✦ §lGathering & Magic", Material.BLAZE_POWDER));

        // Skill items — stack amount = level
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

        // Stack count = level (1–64, visual corner number)
        int amount = Math.max(1, Math.min(64, level));
        ItemStack item = new ItemStack(skill.getIcon(), amount);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        // Name line: coloured skill name + current level
        String maxTag = (level >= 99) ? " §6★MAX" : "";
        meta.setDisplayName(skill.getColorCode() + "§l" + skill.getDisplayName()
                + " §r§8│ §aLv " + level + maxTag);

        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(30));
        lore.add("§7Rank: " + rank);
        lore.add("§8" + "─".repeat(30));
        lore.add("§7Total XP: §f" + NF.format(xp));
        if (level < 99) {
            lore.add("§7XP to next level: §e" + NF.format(toNext));
        }
        lore.add("§7Progress: " + bar);
        lore.add("§8" + "─".repeat(30));

        addSkillTips(lore, skill, level);

        lore.add("§8" + "─".repeat(30));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Skill-specific tooltips (plain if-else to avoid synthetic inner classes) ─

    private void addSkillTips(List<String> lore, SkillType skill, int level) {

        if (skill == SkillType.MELEE) {
            lore.add("§6How to train:");
            lore.add("§8  Kill mobs with a sword or axe.");
            lore.add("§8  Netherite sword gives 2× XP.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Gear & Damage Bonuses:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— All melee weapons usable");
            lore.add(check(level >= 20) + " §7Lv §f20 §8— Iron weapon bonus damage");
            lore.add(check(level >= 40) + " §7Lv §f40 §8— Diamond weapon bonus damage");
            lore.add(check(level >= 60) + " §7Lv §f60 §8— Netherite weapon bonus damage");
            lore.add(check(level >= 80) + " §7Lv §f80 §8— +2 hearts melee damage");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum damage bonus ★");

        } else if (skill == SkillType.RANGED) {
            lore.add("§6How to train:");
            lore.add("§8  Kill mobs with a bow or crossbow.");
            lore.add("§8  Higher-tier mobs give more XP.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Gear & Range Bonuses:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— Bow & crossbow usable");
            lore.add(check(level >= 25) + " §7Lv §f25 §8— +10% arrow damage");
            lore.add(check(level >= 50) + " §7Lv §f50 §8— Crossbow burst damage");
            lore.add(check(level >= 75) + " §7Lv §f75 §8— +25% arrow damage");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum ranged bonus ★");

        } else if (skill == SkillType.DEFENCE) {
            lore.add("§6How to train:");
            lore.add("§8  Block attacks with a shield.");
            lore.add("§8  XP awarded per successful block.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Armour Tier Unlocks:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— Leather armour");
            lore.add(check(level >= 15) + " §7Lv §f15 §8— Gold / Chain armour");
            lore.add(check(level >= 30) + " §7Lv §f30 §8— Iron armour");
            lore.add(check(level >= 50) + " §7Lv §f50 §8— Diamond armour");
            lore.add(check(level >= 70) + " §7Lv §f70 §8— Netherite armour");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum defence bonus ★");

        } else if (skill == SkillType.PRAYER) {
            lore.add("§6How to train:");
            lore.add("§8  Right-click a bone on dirt/grass.");
            lore.add("§8  Bone = 4 XP  |  Bone Meal = 2 XP.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Hit-Block Chance:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— 1% chance to negate a hit");
            lore.add(check(level >= 20) + " §7Lv §f20 §8— 5% block chance");
            lore.add(check(level >= 40) + " §7Lv §f40 §8— 10% block chance");
            lore.add(check(level >= 60) + " §7Lv §f60 §8— 18% block chance");
            lore.add(check(level >= 80) + " §7Lv §f80 §8— 25% block chance");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §630% block chance ★");

        } else if (skill == SkillType.MAGIC) {
            lore.add("§6How to train:");
            lore.add("§8  Cast with elemental staffs.");
            lore.add("§8  10 XP/cast · 5 XP/hit · 25 XP/combo.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Spell Cooldown: §e" + String.format("%.1f", magicCooldownSec(level)) + "s §8(base, no gear)");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Combo Unlock Guide:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— All 4 staffs, basic spells");
            lore.add(check(level >= 20) + " §7Lv §f20 §8— Water+Earth = §6Muddy");
            lore.add(check(level >= 35) + " §7Lv §f35 §8— Fire+Fire = §cBlazing");
            lore.add(check(level >= 50) + " §7Lv §f50 §8— Water+Earth+Fire = §eStatue");
            lore.add(check(level >= 60) + " §7Lv §f60 §8— Water+Air+Air = §bFrozen");
            lore.add(check(level >= 75) + " §7Lv §f75 §8— Water+Fire+Air = §dTempest");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Instant 4-element chains ★");
            lore.add("§8" + "─".repeat(30));
            lore.add("§d✦ §7Mage gear (2+ pieces):");
            lore.add("§8  5% chance: §5Mind Bomb §8on hit");
            lore.add("§8  Mind Bomb: nausea + blind + §cfallen");
            lore.add("§8  Press §fSPACE §8to get up when fallen!");

        } else if (skill == SkillType.WOODCUTTING) {
            lore.add("§6How to train:");
            lore.add("§8  Chop logs with any axe.");
            lore.add("§8  Better axes give more XP.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Axe Bonuses:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— Wooden / Stone axe");
            lore.add(check(level >= 15) + " §7Lv §f15 §8— Iron axe bonus XP");
            lore.add(check(level >= 35) + " §7Lv §f35 §8— Diamond axe bonus XP");
            lore.add(check(level >= 60) + " §7Lv §f60 §8— Netherite axe bonus XP");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum WC XP rate ★");

        } else if (skill == SkillType.FISHING) {
            lore.add("§6How to train:");
            lore.add("§8  Catch fish with a fishing rod.");
            lore.add("§8  10 XP per catch.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Fishing Bonuses:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— Basic fishing");
            lore.add(check(level >= 30) + " §7Lv §f30 §8— Increased treasure rate");
            lore.add(check(level >= 60) + " §7Lv §f60 §8— Rare fish bonus XP");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum fishing XP ★");

        } else if (skill == SkillType.FARMING) {
            lore.add("§6How to train:");
            lore.add("§8  Harvest fully-grown crops.");
            lore.add("§8  Rarer crops = more XP.");
            lore.add("§8  Glow berries & bamboo = 10 XP.");
            lore.add("§8" + "─".repeat(30));
            lore.add("§6Farming Bonuses:");
            lore.add(check(level >= 1)  + " §7Lv §f1  §8— Basic crop harvesting");
            lore.add(check(level >= 25) + " §7Lv §f25 §8— Bonus harvest drops");
            lore.add(check(level >= 50) + " §7Lv §f50 §8— Rare crop bonus XP");
            lore.add(check(level >= 99) + " §7Lv §f99 §8— §6Maximum farming XP ★");
        }
    }

    /** Returns §a✔ if unlocked, §c✗ if not. */
    private String check(boolean unlocked) {
        return unlocked ? "§a✔" : "§c✗";
    }

    /** Approximate spell cooldown in seconds for the given magic level (no gear). */
    private double magicCooldownSec(int level) {
        long ms = 3000L - (long) ((level / 99.0) * 2000L);
        return Math.max(1000L, ms) / 1000.0;
    }

    // ── Total Level item ──────────────────────────────────────────────────────

    private ItemStack totalLevelItem(UUID uuid) {
        int total = skillManager.getTotalLevel(uuid);
        int max   = SkillType.values().length * 99;

        // Stack count shows total level compressed to 1-64 range
        int amount = Math.max(1, Math.min(64, total / 10 + 1));
        ItemStack item = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§6§l✦ Total Level ✦  §r§e" + total + " §8/ §f" + max);

        double pct    = (double) total / max;
        int    filled = (int) Math.round(pct * 14);
        String bar    = "§a" + "█".repeat(filled) + "§8" + "░".repeat(14 - filled);

        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(30));
        lore.add("§7Overall: " + bar + " §e" + String.format("%.1f", pct * 100) + "%");
        lore.add("§8" + "─".repeat(30));
        for (SkillType s : SkillType.values()) {
            int lvl  = skillManager.getLevel(uuid, s);
            String star = (lvl >= 99) ? "§6★" : "§8·";
            lore.add(" " + star + " " + s.getColorCode() + s.getDisplayName()
                    + " §8» §a" + lvl + (lvl >= 99 ? " §6MAX" : ""));
        }
        lore.add("§8" + "─".repeat(30));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Playtime item ─────────────────────────────────────────────────────────

    private ItemStack playtimeItem(Player player) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§b⏱ §lPlaytime");

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
        lore.add("§8" + "─".repeat(30));
        lore.add("§7Total time on the server:");
        lore.add("§e" + hours + "h §7" + minutes + "m");
        lore.add("§8" + "─".repeat(30));
        lore.add("§7Keep grinding! Every hour counts.");
        lore.add("§8" + "─".repeat(30));

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
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of("§8" + "─".repeat(22)));
            item.setItemMeta(meta);
        }
        return item;
    }
}
