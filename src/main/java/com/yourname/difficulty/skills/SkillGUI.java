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
 * SkillGUI — RuneScape-style 54-slot /mystats panel.
 *
 * Layout (54 slots = 6 rows × 9 cols):
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  G  G  G  G  G  G  G  G  G   ← row 0: header border (gray)   │
 * │  G [MEL] G [RNG] G [DEF] G  G  G   ← row 1: combat skills    │
 * │  G  G  G  G  G  G  G  G  G   ← row 2: separator              │
 * │  G [WCT] G [FSH] G [FRM] G  G  G   ← row 3: gathering skills │
 * │  G  G  G  G  G  G  G  G  G   ← row 4: separator              │
 * │  G  G  G [TOT] G [PLY] G  G  G  G  ← row 5: totals           │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * G  = Gray stained glass pane (filler)
 * Skill slots are at positions: 10, 13, 16, 28, 31, 34
 * Total Level slot: 40
 * Playtime slot: 42
 */
public class SkillGUI {

    public static final String TITLE = "§8✦ §6Skill Tree §8✦";

    // Slot positions for each skill
    private static final int SLOT_MELEE       = 10;
    private static final int SLOT_RANGED      = 13;
    private static final int SLOT_DEFENCE     = 16;
    private static final int SLOT_WOODCUTTING = 28;
    private static final int SLOT_FISHING     = 31;
    private static final int SLOT_FARMING     = 34;
    private static final int SLOT_TOTAL       = 40;
    private static final int SLOT_PLAYTIME    = 42;

    private final SkillManager skillManager;
    private static final NumberFormat NF = NumberFormat.getInstance(Locale.US);

    public SkillGUI(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    /** Opens the skill tree GUI for the given player, showing their own stats. */
    public void open(Player viewer, Player target) {
        UUID   uuid = target.getUniqueId();
        String name = target.getName();

        String title = viewer.equals(target)
                ? TITLE
                : "§8✦ §6" + name + "'s Skills §8✦";

        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Fill border / filler slots with glass
        ItemStack glass = filler();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        // ── Row 1: separator label ────────────────────────────────────────────
        inv.setItem(4, label("§6⚔ Combat Skills", Material.NETHERITE_SWORD));
        inv.setItem(22, label("§2⛏ Gathering Skills", Material.IRON_PICKAXE));
        inv.setItem(40, totalLevelItem(uuid));
        inv.setItem(42, playtimeItem(target));

        // ── Skill items ───────────────────────────────────────────────────────
        inv.setItem(SLOT_MELEE,       buildSkillItem(uuid, SkillType.MELEE));
        inv.setItem(SLOT_RANGED,      buildSkillItem(uuid, SkillType.RANGED));
        inv.setItem(SLOT_DEFENCE,     buildSkillItem(uuid, SkillType.DEFENCE));
        inv.setItem(SLOT_WOODCUTTING, buildSkillItem(uuid, SkillType.WOODCUTTING));
        inv.setItem(SLOT_FISHING,     buildSkillItem(uuid, SkillType.FISHING));
        inv.setItem(SLOT_FARMING,     buildSkillItem(uuid, SkillType.FARMING));

        viewer.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack buildSkillItem(UUID uuid, SkillType skill) {
        long xp       = skillManager.getXp(uuid, skill);
        int  level    = SkillLevel.getLevelForXp(xp);
        long toNext   = SkillLevel.getXpToNextLevel(xp);
        String bar    = SkillLevel.getProgressBar(xp);
        String rank   = SkillLevel.getRank(level);
        char  cc      = colorChar(skill);

        ItemStack item = new ItemStack(skill.getIcon());
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(skill.getColorCode() + "✦ " + skill.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Level: §a" + level + (level >= 99 ? " §6(MAX)" : ""));
        lore.add("§7Total XP: §f" + NF.format(xp));
        if (level < 99) {
            lore.add("§7XP to next: §e" + NF.format(toNext));
        }
        lore.add("§7Progress: " + bar);
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Rank: " + rank);
        lore.add("§8" + "─".repeat(26));
        addSkillTips(lore, skill);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack totalLevelItem(UUID uuid) {
        int total = skillManager.getTotalLevel(uuid);
        int max   = SkillType.values().length * 99;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§6✦ Total Level ✦");

        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Combined skill levels:");
        lore.add("§e" + total + " §7/ §f" + max);
        lore.add("§8" + "─".repeat(26));

        // Progress bar for total
        double pct = (double) total / max;
        int filled = (int) Math.round(pct * 10);
        String bar = "§a" + "█".repeat(filled) + "§8" + "░".repeat(10 - filled);
        lore.add("§7Overall: " + bar);
        lore.add("§8" + "─".repeat(26));

        // Per-skill summary
        for (SkillType s : SkillType.values()) {
            int lvl = skillManager.getLevel(uuid, s);
            String mark = lvl >= 99 ? "§6★" : "§7  ";
            lore.add(mark + " §" + colorChar(s) + s.getDisplayName()
                    + " §8» §a" + lvl);
        }
        lore.add("§8" + "─".repeat(26));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playtimeItem(Player player) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§b⏱ Playtime");

        // Bukkit stores ticks played; convert to hours
        long ticksPlayed  = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        long secondsPlayed = ticksPlayed / 20;
        long hours  = secondsPlayed / 3600;
        long minutes = (secondsPlayed % 3600) / 60;

        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Total time played:");
        lore.add("§e" + hours + "h §7" + minutes + "m");
        lore.add("§8" + "─".repeat(26));
        lore.add("§7This tracks your overall");
        lore.add("§7dedication to the server.");
        lore.add("§8" + "─".repeat(26));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── Skill-specific tips ───────────────────────────────────────────────────

    private void addSkillTips(List<String> lore, SkillType skill) {
        switch (skill) {
            case MELEE ->  {
                lore.add("§7How to train:");
                lore.add("§8  Kill mobs with a sword or axe.");
                lore.add("§8  Higher tier mobs give more XP.");
                lore.add("§8  Netherite sword = 2× XP bonus.");
            }
            case RANGED -> {
                lore.add("§7How to train:");
                lore.add("§8  Kill mobs with a bow or crossbow.");
                lore.add("§8  Higher tier mobs give more XP.");
            }
            case DEFENCE -> {
                lore.add("§7How to train:");
                lore.add("§8  Block attacks with a shield.");
                lore.add("§8  XP per successful block.");
            }
            case WOODCUTTING -> {
                lore.add("§7How to train:");
                lore.add("§8  Chop logs with an axe.");
                lore.add("§8  Better axes give more XP.");
            }
            case FISHING -> {
                lore.add("§7How to train:");
                lore.add("§8  Catch fish with a fishing rod.");
                lore.add("§8  10 XP per catch.");
            }
            case FARMING -> {
                lore.add("§7How to train:");
                lore.add("§8  Harvest fully-grown crops.");
                lore.add("§8  Rarer crops give more XP.");
                lore.add("§8  Epic (glow berries, bamboo) = 10 XP.");
            }
        }
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
            meta.setLore(List.of("§8" + "─".repeat(20)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static char colorChar(SkillType skill) {
        String code = skill.getColorCode();
        return code.length() >= 2 ? code.charAt(1) : 'f';
    }
}
