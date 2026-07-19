package com.yourname.difficulty.magic;

import org.bukkit.Material;

/**
 * MagicElement — The four elemental staff types.
 *
 * Each element defines:
 *  • PDC key strings for staff identity and rune identity
 *  • Display materials for staff and rune items (no resource pack required)
 *  • Display names and colour codes
 *  • Custom Model Data IDs for resource pack support
 *
 * Staff CMDs: FIRE=2001  WATER=2002  EARTH=2003  AIR=2004
 * Rune  CMDs: FIRE=3001  WATER=3002  EARTH=3003  AIR=3004
 *
 * Crafting recipes (all shapeless):
 *   Fire  Staff: Enchanted Shard + BLAZE_ROD         + STICK
 *   Water Staff: Enchanted Shard + PRISMARINE_SHARD  + STICK
 *   Earth Staff: Enchanted Shard + EMERALD           + STICK
 *   Air   Staff: Enchanted Shard + PHANTOM_MEMBRANE  + STICK
 *
 * Rune crafting (shapeless, 4 base material → 8 runes):
 *   Fire  Rune: 4× NETHER_BRICK       → 8× Fire Rune
 *   Water Rune: 4× ICE                → 8× Water Rune
 *   Earth Rune: 4× CLAY_BALL          → 8× Earth Rune
 *   Air   Rune: 4× FEATHER            → 8× Air Rune
 */
public enum MagicElement {

    FIRE (
        "fire_staff",  "fire_rune",
        Material.BLAZE_ROD,         Material.NETHER_BRICK,
        Material.BLAZE_ROD,         Material.NETHER_BRICK,
        "§c🔥 Fire Staff",           "§c🔥 Fire Rune",
        "§c", 2001, 3001
    ),
    WATER(
        "water_staff", "water_rune",
        Material.PRISMARINE_CRYSTALS, Material.PRISMARINE_SHARD,
        Material.PRISMARINE_SHARD,  Material.ICE,
        "§b💧 Water Staff",          "§b💧 Water Rune",
        "§b", 2002, 3002
    ),
    EARTH(
        "earth_staff", "earth_rune",
        Material.EMERALD,           Material.CLAY_BALL,
        Material.EMERALD,           Material.CLAY_BALL,
        "§2🌿 Earth Staff",          "§2🌿 Earth Rune",
        "§2", 2003, 3003
    ),
    AIR  (
        "air_staff",   "air_rune",
        Material.FEATHER,           Material.PAPER,
        Material.PHANTOM_MEMBRANE,  Material.FEATHER,
        "§f⚡ Air Staff",            "§f⚡ Air Rune",
        "§f", 2004, 3004
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    /** PDC key name for the staff item. */
    public final String staffKey;
    /** PDC key name for the rune item. */
    public final String runeKey;
    /** In-game material used as the staff base (what it looks like). */
    public final Material staffMaterial;
    /** In-game material used as the rune base (what it looks like). */
    public final Material runeMaterial;
    /** Crafting ingredient unique to this element (besides Enchanted Shard + Stick). */
    public final Material staffCraftIngredient;
    /** Base material used to craft runes (4× → 8 runes). */
    public final Material runeCraftIngredient;
    /** Chat/display name for the staff. */
    public final String staffName;
    /** Chat/display name for the rune. */
    public final String runeName;
    /** Colour code for this element. */
    public final String color;
    /** Custom Model Data for the staff. */
    public final int staffCMD;
    /** Custom Model Data for the rune. */
    public final int runeCMD;

    MagicElement(String staffKey, String runeKey,
                 Material staffMat, Material runeMat,
                 Material staffCraftIngredient, Material runeCraftIngredient,
                 String staffName, String runeName,
                 String color, int staffCMD, int runeCMD) {
        this.staffKey             = staffKey;
        this.runeKey              = runeKey;
        this.staffMaterial        = staffMat;
        this.runeMaterial         = runeMat;
        this.staffCraftIngredient = staffCraftIngredient;
        this.runeCraftIngredient  = runeCraftIngredient;
        this.staffName            = staffName;
        this.runeName             = runeName;
        this.color                = color;
        this.staffCMD             = staffCMD;
        this.runeCMD              = runeCMD;
    }
}
