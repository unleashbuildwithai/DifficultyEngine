package com.yourname.difficulty.items;

/**
 * RangedGearTier — The four tiers of Ranged armour/gear, gated by Ranged skill level.
 *
 * ── Tier Progression ─────────────────────────────────────────────────────────
 *  LEATHER   (Ranged Lv  1) : Leather armour — light, mobile, entry ranger kit.
 *  CHAIN     (Ranged Lv 40) : Chainmail armour — balanced protection/mobility.
 *  NETHERITE (Ranged Lv 70) : Netherite armour — high-tier with ranged bonuses.
 *  DRAGON    (Ranged Lv 99) : Dragon Ranged Armour — end-game enchanted netherite.
 *                             Glowing, vibrant, named with green/gold formatting.
 *
 * ── Dragon Ranged Armour note ─────────────────────────────────────────────────
 *  Same base as Dragon Melee Armour but with Projectile Protection IV instead
 *  of Protection IV, and a separate PDC key so the equip listener can verify
 *  the correct Ranged skill level (99) rather than Melee.
 *
 * ── Bonuses per piece ────────────────────────────────────────────────────────
 *  {@link #rangedBonus}   additive Ranged damage multiplier while wearing.
 *  {@link #drawSpeedBonus} ms shaved off bow draw-time per piece worn.
 */
public enum RangedGearTier {

    LEATHER(
        "leather_ranged_gear",
        /* Ranged level required */ 1,
        /* Ranged damage bonus   */ 1.0,
        /* Draw speed bonus (ms) */ 0L,
        /* Chat colour           */ "§e",
        /* Display prefix        */ "§e🏹 Leather",
        /* Craft hint            */ "Leather + String (standard crafting)"
    ),

    CHAIN(
        "chain_ranged_gear",
        40, 1.15, 50L,
        "§a",
        "§a🏹 Chainmail",
        "Chainmail + Feathers + Enchanting Table (lvl 30)"
    ),

    NETHERITE(
        "netherite_ranged_gear",
        70, 1.35, 100L,
        "§2",
        "§2🏹 Netherite Ranger",
        "Netherite Ingot + Chain Armour (Smithing Table)"
    ),

    DRAGON(
        "dragon_ranged_gear",
        99, 1.75, 200L,
        "§a",
        "§a§l✦ Dragon Ranged",
        "Dragon Scale + Netherite Armour (Magic Cauldron)"
    );

    // ── Public fields ─────────────────────────────────────────────────────────

    /** Persistent Data Container key for this tier. */
    public final String pdcKey;
    /** Ranged skill level required to equip this tier. */
    public final int    levelRequired;
    /** Additive Ranged damage multiplier while wearing (all 4 pieces). */
    public final double rangedBonus;
    /** Milliseconds subtracted from bow draw time per piece worn. */
    public final long   drawSpeedBonus;
    /** Minecraft colour code for chat messages. */
    public final String colorCode;
    /** Display-name prefix shown on the item. */
    public final String displayPrefix;
    /** Short crafting recipe hint shown in item lore. */
    public final String craftIngredients;

    RangedGearTier(String pdcKey, int levelRequired, double rangedBonus,
                   long drawSpeedBonus, String colorCode,
                   String displayPrefix, String craftIngredients) {
        this.pdcKey          = pdcKey;
        this.levelRequired   = levelRequired;
        this.rangedBonus     = rangedBonus;
        this.drawSpeedBonus  = drawSpeedBonus;
        this.colorCode       = colorCode;
        this.displayPrefix   = displayPrefix;
        this.craftIngredients = craftIngredients;
    }
}
