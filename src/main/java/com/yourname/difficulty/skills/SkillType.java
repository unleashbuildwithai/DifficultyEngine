package com.yourname.difficulty.skills;

import org.bukkit.Material;

/**
 * All trainable skills in the DifficultyEngine skill system.
 * Each skill maps to an icon (item used in the /mystats GUI),
 * a display name, and a chat colour code.
 */
public enum SkillType {

    MELEE      ("Melee Combat",  "§c", Material.IRON_SWORD),
    RANGED     ("Ranged",        "§a", Material.BOW),
    DEFENCE    ("Defence",       "§9", Material.SHIELD),
    PRAYER     ("Prayer",        "§f", Material.BONE),
    MAGIC      ("Magic",         "§d", Material.BLAZE_POWDER),
    WOODCUTTING("Woodcutting",   "§2", Material.IRON_AXE),
    FISHING    ("Fishing",       "§b", Material.FISHING_ROD),
    FARMING    ("Farming",       "§e", Material.DIAMOND_HOE);

    private final String   displayName;
    private final String   colorCode;
    private final Material icon;

    SkillType(String displayName, String colorCode, Material icon) {
        this.displayName = displayName;
        this.colorCode   = colorCode;
        this.icon        = icon;
    }

    public String   getDisplayName() { return displayName; }
    public String   getColorCode()   { return colorCode;   }
    public Material getIcon()        { return icon;        }
    public String   colored()        { return colorCode + displayName; }
}
