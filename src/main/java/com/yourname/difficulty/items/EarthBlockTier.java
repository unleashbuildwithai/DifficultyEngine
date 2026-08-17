package com.yourname.difficulty.items;

import org.bukkit.Material;
import org.bukkit.World;

/**
 * EarthBlockTier — Tiered block-throwing system for the Earth staff.
 *
 * At Magic Level 10+, players can throw blocks instead of dirt bolts.
 * Each tier requires:
 *  • A minimum Magic level
 *  • The matching block in the player's inventory (consumed on throw)
 *  • The corresponding page unlocked in the player's Earth Book
 *
 * Damage is in Minecraft HP units (2 HP = 1 full heart).
 * Example: trapDamage = 4.0 → 2 full hearts damage.
 *
 * Pages are found as mob drops, gated by dimension:
 *  • Overworld → Dirt / Cobblestone / Stone / Iron Block
 *  • Nether    → Gold Block / Obsidian / Nether Bricks / Ancient Debris
 *  • End       → End Stone / End Stone Bricks
 */
public enum EarthBlockTier {
    //                            lvl  material                trapDmg suffDmg  displayName               pageKey                  dimension
    DIRT           ( 10, Material.DIRT,              4.0,   8.0, "§2Dirt",             "de_earth_page_dirt",      World.Environment.NORMAL ),
    COBBLESTONE    ( 15, Material.COBBLESTONE,        6.0,  12.0, "§7Cobblestone",      "de_earth_page_cobble",    World.Environment.NORMAL ),
    STONE          ( 25, Material.STONE,             10.0,  16.0, "§8Stone",            "de_earth_page_stone",     World.Environment.NORMAL ),
    IRON_BLOCK     ( 30, Material.IRON_BLOCK,        14.0,  22.0, "§7Iron Block",       "de_earth_page_iron",      World.Environment.NORMAL ),
    GOLD_BLOCK     ( 50, Material.GOLD_BLOCK,        18.0,  28.0, "§6Gold Block",       "de_earth_page_gold",      World.Environment.NETHER ),
    OBSIDIAN       ( 60, Material.OBSIDIAN,          24.0,  36.0, "§8Obsidian",         "de_earth_page_obsidian",  World.Environment.NETHER ),
    NETHER_BRICKS  ( 75, Material.NETHER_BRICKS,     30.0,  44.0, "§cNether Bricks",   "de_earth_page_nether",    World.Environment.NETHER ),
    ANCIENT_DEBRIS ( 90, Material.ANCIENT_DEBRIS,    36.0,  54.0, "§4Ancient Debris",  "de_earth_page_debris",    World.Environment.NETHER ),
    END_STONE      ( 95, Material.END_STONE,         42.0,  64.0, "§eEnd Stone",       "de_earth_page_endstone",  World.Environment.THE_END ),
    END_STONE_BRICKS(99, Material.END_STONE_BRICKS,  48.0,  74.0, "§fEnd Stone Bricks","de_earth_page_endbricks", World.Environment.THE_END );

    /** Minimum Magic level required to throw this block. */
    public final int      levelRequired;
    /** The Minecraft block material that must be in the player's inventory. */
    public final Material material;
    /** Damage on the initial hit (HP units; 2 HP = 1 full heart). */
    public final double   trapDamage;
    /** Damage on the heavier follow-up hit (HP units). */
    public final double   suffocateDamage;
    /** Coloured name shown in action-bar messages. */
    public final String   displayName;
    /** PDC key string used on the corresponding Earth Magic Page item. */
    public final String   pageKey;
    /** The dimension whose mobs drop this tier's page. */
    public final World.Environment dimension;

    EarthBlockTier(int level, Material mat, double trap, double suffocate,
                   String name, String key, World.Environment dimension) {
        this.levelRequired   = level;
        this.material        = mat;
        this.trapDamage      = trap;
        this.suffocateDamage = suffocate;
        this.displayName     = name;
        this.pageKey         = key;
        this.dimension       = dimension;
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
