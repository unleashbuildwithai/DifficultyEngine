package com.yourname.difficulty.items;

import org.bukkit.Color;

/**
 * MageGearTier — The four tiers of elemental mage armour, gated by Magic level.
 *
 * ── Tier Progression ─────────────────────────────────────────────────────────
 *  APPRENTICE (Magic Lv  1) : Easy entry gear.  String + Purple Dye.
 *  MAGE       (Magic Lv 30) : Blaze-imbued, core mage set.
 *  ALCH       (Magic Lv 60) : Ender-touched, significant power spike.
 *  MASTER     (Magic Lv 90) : Dragon-blessed, maximum elemental power.
 *
 * ── Bonuses per piece worn ───────────────────────────────────────────────────
 *  {@link #cooldownBonus} ms off the spell cast cooldown (stacks across 4 pieces).
 *  {@link #airPower}      contributes to the Air Gust velocity multiplier.
 *
 * ── Air Gust formula ─────────────────────────────────────────────────────────
 *  Total power  = sum of airPower across all mage gear pieces worn (0 – 8.0).
 *  Multiplier   = 0.50 + (totalPower / 8.0) × 1.50   →  range 0.50 → 2.00
 *
 *  No gear at all: 0.50× velocity (50 % nerf, anyone can cast but weak)
 *  Full Apprentice set (4× 0.5 = 2.0 power): ≈ 0.875×
 *  Full Mage set      (4× 1.0 = 4.0 power): ≈ 1.250×
 *  Full Alch set      (4× 1.5 = 6.0 power): ≈ 1.625×
 *  Full Master set    (4× 2.0 = 8.0 power): ≈ 2.000× (double knockback)
 */
public enum MageGearTier {

    APPRENTICE(
        "apprentice_mage_gear",
        /* Magic level required */ 1,
        /* Cooldown bonus (ms) */ 100L,
        /* Air power value    */ 0.5,
        /* Leather colour     */ Color.fromRGB(100, 0, 180),
        /* Chat colour code   */ "§9",
        /* Display prefix     */ "§9✧ Apprentice",
        /* Craft ingredients  */ "Leather piece + Purple Dye + String"
    ),

    MAGE(
        "mage_gear",
        30, 250L, 1.0,
        Color.fromRGB(45, 0, 110),
        "§5",
        "§5✦ Mage",
        "Leather piece + Purple Dye + Blaze Powder"
    ),

    ALCH(
        "alch_mage_gear",
        60, 350L, 1.5,
        Color.fromRGB(0, 30, 140),
        "§b",
        "§b✦✦ Alch Mage",
        "Leather piece + Blue Dye + Blaze Powder + Eye of Ender"
    ),

    MASTER(
        "master_mage_gear",
        90, 500L, 2.0,
        Color.fromRGB(10, 0, 30),
        "§4",
        "§4★ Master Mage",
        "Leather + Black Dye + Blaze Powder + Enchanted Shard + Dragon Breath"
    );

    // ── Public fields ─────────────────────────────────────────────────────────
    /** Persistent Data Container key for this tier. */
    public final String pdcKey;
    /** Magic skill level required to equip this tier. */
    public final int    levelRequired;
    /** Milliseconds subtracted from the spell cooldown per piece worn. */
    public final long   cooldownBonus;
    /** Contribution to the air gust power calculation per piece worn (0.5–2.0). */
    public final double airPower;
    /** Leather colour applied to this tier's armour. */
    public final Color  color;
    /** Minecraft colour code prefix (e.g. "§5" for mage purple). */
    public final String colorCode;
    /** Display name prefix shown on the item. */
    public final String displayPrefix;
    /** Short crafting recipe hint shown in item lore. */
    public final String craftIngredients;

    MageGearTier(String pdcKey, int levelRequired, long cooldownBonus,
                 double airPower, Color color, String colorCode,
                 String displayPrefix, String craftIngredients) {
        this.pdcKey          = pdcKey;
        this.levelRequired   = levelRequired;
        this.cooldownBonus   = cooldownBonus;
        this.airPower        = airPower;
        this.color           = color;
        this.colorCode       = colorCode;
        this.displayPrefix   = displayPrefix;
        this.craftIngredients = craftIngredients;
    }
}
