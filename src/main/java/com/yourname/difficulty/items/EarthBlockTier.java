package com.yourname.difficulty.items;

import org.bukkit.Material;

/**
 * EarthBlockTier — Tiered block-throwing system for the Earth staff.
 *
 * At Magic Level 10+, players can throw blocks instead of dirt bolts.
 * Each tier requires:
 *  • A minimum Magic level
 *  • The matching block in the player's inventory (consumed on throw)
 *  • The corresponding Earth Magic Page item carried in inventory
 *
 * Damage is in Minecraft HP units (2 HP = 1 full heart).
 * Example: trapDamage = 4.0 → 2 full hearts damage.
 *
 * Mechanic:
 *  1st earth hit on a player → TRAP (block placed under feet + trap damage + slowness)
 *  2nd earth hit while trapped → SUFFOCATE (blocks over head + suffocate damage)
 */
public enum EarthBlockTier {
    //                            lvl  material                trapDmg suffDmg  displayName              pageKey
    DIRT          ( 10, Material.DIRT,              4.0,   8.0, "§2Dirt",            "de_earth_page_dirt"     ),
    COBBLESTONE   ( 15, Material.COBBLESTONE,        6.0,  12.0, "§7Cobblestone",     "de_earth_page_cobble"   ),
    STONE         ( 25, Material.STONE,             10.0,  16.0, "§8Stone",           "de_earth_page_stone"    ),
    IRON_BLOCK    ( 30, Material.IRON_BLOCK,        14.0,  22.0, "§7Iron Block",      "de_earth_page_iron"     ),
    GOLD_BLOCK    ( 50, Material.GOLD_BLOCK,        18.0,  28.0, "§6Gold Block",      "de_earth_page_gold"     ),
    OBSIDIAN      ( 60, Material.OBSIDIAN,          24.0,  36.0, "§8Obsidian",        "de_earth_page_obsidian" ),
    NETHER_BRICKS ( 75, Material.NETHER_BRICKS,     30.0,  44.0, "§cNether Bricks",  "de_earth_page_nether"   ),
    ANCIENT_DEBRIS( 90, Material.ANCIENT_DEBRIS,    36.0,  54.0, "§4Ancient Debris", "de_earth_page_debris"   );

    /** Minimum Magic level required to throw this block. */
    public final int      levelRequired;
    /** The Minecraft block material that must be in the player's inventory. */
    public final Material material;
    /** Damage on the initial TRAP hit (HP units; 2 HP = 1 full heart). */
    public final double   trapDamage;
    /** Damage on the SUFFOCATE follow-up hit (HP units). */
    public final double   suffocateDamage;
    /** Coloured name shown in action-bar messages. */
    public final String   displayName;
    /** PDC key string used on the corresponding Earth Magic Page item. */
    public final String   pageKey;

    EarthBlockTier(int level, Material mat, double trap, double suffocate,
                   String name, String key) {
        this.levelRequired   = level;
        this.material        = mat;
        this.trapDamage      = trap;
        this.suffocateDamage = suffocate;
        this.displayName     = name;
        this.pageKey         = key;
    }

    /**
     * Returns the EarthBlockTier matching the given material, or {@code null}
     * if the material is not a throwable block.
     */
    public static EarthBlockTier fromMaterial(Material mat) {
        for (EarthBlockTier t : values()) {
            if (t.material == mat) return t;
        }
        return null;
    }
}
