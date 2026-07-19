package com.yourname.difficulty.items;

/**
 * MeleeGearTier — The four tiers of Melee armour, gated by Melee skill level.
 *
 * ── Tier Progression ─────────────────────────────────────────────────────────
 *  IRON      (Melee Lv  1) : Iron armour — entry-level melee protection.
 *  DIAMOND   (Melee Lv 40) : Diamond armour — mid-tier, significant defence.
 *  NETHERITE (Melee Lv 70) : Netherite armour — high-tier, knockback resistance.
 *  DRAGON    (Melee Lv 99) : Dragon Armour — end-game enchanted netherite set.
 *                            Glowing, vibrant, named with gold/red formatting.
 *
 * ── Dragon Armour note ───────────────────────────────────────────────────────
 *  Dragon Armour uses the Netherite base material with a full enchant suite
 *  (Protection IV, Unbreaking III, Mending, Thorns III, etc.) so it has the
 *  enchantment glint visual.  Without a resource pack the icon is the vanilla
 *  netherite sprite — but the colour-coded name, lore, and glow make it
 *  visually distinct.
 *
 * ── Bonuses per piece ────────────────────────────────────────────────────────
 *  {@link #defenceBonus}  flat Defence XP multiplier per hit while wearing.
 *  {@link #damageBonus}   additive Melee damage multiplier while wearing.
 */
public enum MeleeGearTier {

    IRON(
        "iron_melee_gear",
        /* Melee level required */ 1,
        /* Defence bonus        */ 1.0,
        /* Damage bonus         */ 1.0,
        /* Chat colour          */ "§7",
        /* Display prefix       */ "§7⚔ Iron",
        /* Craft hint           */ "Iron Ingots (standard smithing)"
    ),

    DIAMOND(
        "diamond_melee_gear",
        40, 1.25, 1.10,
        "§b",
        "§b⚔ Diamond",
        "Diamond + Enchantment Table (lvl 30)"
    ),

    NETHERITE(
        "netherite_melee_gear",
        70, 1.60, 1.25,
        "§8",
        "§8⚔ Netherite",
        "Netherite Ingot + Diamond Armour (Smithing Table)"
    ),

    DRAGON(
        "dragon_melee_gear",
        99, 2.50, 1.60,
        "§6",
        "§6§l✦ Dragon",
        "Dragon Scale + Netherite Armour (Magic Cauldron)"
    );

    // ── Public fields ─────────────────────────────────────────────────────────

    /** Persistent Data Container key for this tier. */
    public final String pdcKey;
    /** Melee skill level required to equip this tier. */
    public final int    levelRequired;
    /** Defence XP multiplier per hit while wearing (stacks across pieces). */
    public final double defenceBonus;
    /** Melee damage multiplier while wearing the full set. */
    public final double damageBonus;
    /** Minecraft colour code for chat messages. */
    public final String colorCode;
    /** Display-name prefix shown on the item. */
    public final String displayPrefix;
    /** Short crafting recipe hint shown in item lore. */
    public final String craftIngredients;

    MeleeGearTier(String pdcKey, int levelRequired, double defenceBonus,
                  double damageBonus, String colorCode,
                  String displayPrefix, String craftIngredients) {
        this.pdcKey           = pdcKey;
        this.levelRequired    = levelRequired;
        this.defenceBonus     = defenceBonus;
        this.damageBonus      = damageBonus;
        this.colorCode        = colorCode;
        this.displayPrefix    = displayPrefix;
        this.craftIngredients = craftIngredients;
    }
}
