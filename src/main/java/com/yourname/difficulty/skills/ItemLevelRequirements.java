package com.yourname.difficulty.skills;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * ItemLevelRequirements — Defines minimum skill levels required to use items.
 *
 * ── Melee weapons (MELEE skill) ────────────────────────────────────────────
 *   Wooden  sword/axe  →  1
 *   Stone   sword/axe  →  5
 *   Golden  sword/axe  → 10
 *   Iron    sword/axe  → 20
 *   Diamond sword/axe  → 40
 *   Netherite sword/axe→ 70
 *
 * ── Ranged weapons (RANGED skill) ──────────────────────────────────────────
 *   Bow      →  5
 *   Crossbow → 25
 *
 * ── Seeds / plantable items (FARMING skill) ────────────────────────────────
 *   Wheat Seeds       →  1
 *   Potato / Carrot   →  5
 *   Pumpkin/Melon Seeds → 10
 *   Beetroot Seeds    → 15
 *   Cocoa Beans       → 20
 *   Nether Wart       → 30
 *   Sweet Berries     → 35
 *   Bamboo            → 40
 *   Chorus Flower     → 45
 *   Glow Berries      → 50
 *
 * ── Fishing rods (FISHING skill) ───────────────────────────────────────────
 *   Required level = min(99, totalEnchantmentScore × 5)
 *   where totalEnchantmentScore = sum of all enchantment levels on the rod.
 *   Example: Lure III + Luck III + Unbreaking III = 9 → Level 45 required.
 */
public final class ItemLevelRequirements {

    private ItemLevelRequirements() {}

    // ── Melee ─────────────────────────────────────────────────────────────────

    public static int getMeleeRequirement(Material mat) {
        return switch (mat) {
            case WOODEN_SWORD,    WOODEN_AXE    ->  1;
            case STONE_SWORD,     STONE_AXE     ->  5;
            case GOLDEN_SWORD,    GOLDEN_AXE    -> 10;
            case IRON_SWORD,      IRON_AXE      -> 20;
            case DIAMOND_SWORD,   DIAMOND_AXE   -> 40;
            case NETHERITE_SWORD, NETHERITE_AXE -> 70;
            default -> 0; // no requirement
        };
    }

    // ── Ranged ────────────────────────────────────────────────────────────────

    public static int getRangedRequirement(Material mat) {
        return switch (mat) {
            case BOW      ->  5;
            case CROSSBOW -> 25;
            default -> 0;
        };
    }

    // ── Seeds / farming ───────────────────────────────────────────────────────

    public static int getSeedRequirement(Material mat) {
        return switch (mat) {
            case WHEAT_SEEDS                      ->  1;
            case POTATO, CARROT                   ->  5;
            case PUMPKIN_SEEDS, MELON_SEEDS        -> 10;
            case BEETROOT_SEEDS                   -> 15;
            case COCOA_BEANS                      -> 20;
            case NETHER_WART                      -> 30;
            case SWEET_BERRIES                    -> 35;
            case BAMBOO                           -> 40;
            case CHORUS_FLOWER                    -> 45;
            case GLOW_BERRIES                     -> 50;
            default -> 0;
        };
    }

    // ── Fishing rods ──────────────────────────────────────────────────────────

    /**
     * Calculates the required FISHING level to use a fishing rod.
     * An unenchanted rod requires Level 1.
     * Each enchantment level point on the rod adds 5 to the required level.
     *
     * @param rod the fishing rod ItemStack (may be null)
     * @return required fishing level (1–99)
     */
    public static int getFishingRodRequirement(ItemStack rod) {
        if (rod == null || rod.getType() != Material.FISHING_ROD) return 0;
        if (!rod.hasItemMeta()) return 1;

        int totalScore = 0;
        for (Map.Entry<Enchantment, Integer> entry : rod.getItemMeta().getEnchants().entrySet()) {
            totalScore += entry.getValue();
        }
        if (totalScore == 0) return 1;
        return Math.min(99, totalScore * 5);
    }

    // ── Unified item check ────────────────────────────────────────────────────

    /**
     * Returns a {@link LevelRequirement} (skill type + level) for the given item,
     * or {@code null} if the item has no skill requirement.
     */
    public static LevelRequirement getRequirement(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        Material mat = item.getType();

        int meleeReq = getMeleeRequirement(mat);
        if (meleeReq > 0) return new LevelRequirement(SkillType.MELEE, meleeReq);

        int rangedReq = getRangedRequirement(mat);
        if (rangedReq > 0) return new LevelRequirement(SkillType.RANGED, rangedReq);

        int seedReq = getSeedRequirement(mat);
        if (seedReq > 0) return new LevelRequirement(SkillType.FARMING, seedReq);

        if (mat == Material.FISHING_ROD) {
            int rodReq = getFishingRodRequirement(item);
            if (rodReq > 0) return new LevelRequirement(SkillType.FISHING, rodReq);
        }

        return null;
    }

    // ── Data record ───────────────────────────────────────────────────────────

    /**
     * Simple container: the skill that must reach {@code requiredLevel}.
     */
    public record LevelRequirement(SkillType skill, int requiredLevel) {

        /** Returns a formatted message like "§c⚔ Melee Combat §7Level §a20" */
        public String formatRequirement() {
            return skill.getColorCode() + skill.getDisplayName()
                    + " §7Level §a" + requiredLevel;
        }
    }
}
