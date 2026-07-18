package com.yourname.difficulty.currency;

import org.bukkit.Material;
import java.util.EnumMap;
import java.util.Map;

/**
 * GoldValueRegistry — maps every Material to a gold value for tooltip display.
 * Higher tier / rarer items = more gold.
 */
public final class GoldValueRegistry {

    private static final Map<Material, Long> VALUES = new EnumMap<>(Material.class);

    static {
        // ── Tools & Weapons ───────────────────────────────────────────────────
        put(Material.WOODEN_SWORD,      5);   put(Material.STONE_SWORD,      15);
        put(Material.IRON_SWORD,       85);   put(Material.GOLDEN_SWORD,     40);
        put(Material.DIAMOND_SWORD,   500);   put(Material.NETHERITE_SWORD, 2000);
        put(Material.WOODEN_AXE,        5);   put(Material.STONE_AXE,        12);
        put(Material.IRON_AXE,         70);   put(Material.GOLDEN_AXE,       35);
        put(Material.DIAMOND_AXE,     450);   put(Material.NETHERITE_AXE,  1800);
        put(Material.BOW,              80);   put(Material.CROSSBOW,        200);
        put(Material.TRIDENT,         800);   put(Material.MACE,           1500);

        // ── Armour ────────────────────────────────────────────────────────────
        put(Material.LEATHER_HELMET,    5);   put(Material.LEATHER_CHESTPLATE,  8);
        put(Material.LEATHER_LEGGINGS,  6);   put(Material.LEATHER_BOOTS,       4);
        put(Material.IRON_HELMET,      60);   put(Material.IRON_CHESTPLATE,    90);
        put(Material.IRON_LEGGINGS,    80);   put(Material.IRON_BOOTS,         50);
        put(Material.GOLDEN_HELMET,    30);   put(Material.GOLDEN_CHESTPLATE,  45);
        put(Material.GOLDEN_LEGGINGS,  35);   put(Material.GOLDEN_BOOTS,       25);
        put(Material.DIAMOND_HELMET,  400);   put(Material.DIAMOND_CHESTPLATE,600);
        put(Material.DIAMOND_LEGGINGS,500);   put(Material.DIAMOND_BOOTS,     350);
        put(Material.NETHERITE_HELMET,1500); put(Material.NETHERITE_CHESTPLATE,2200);
        put(Material.NETHERITE_LEGGINGS,1800); put(Material.NETHERITE_BOOTS,  1200);

        // ── Resources ─────────────────────────────────────────────────────────
        put(Material.COAL,              1);   put(Material.IRON_INGOT,         5);
        put(Material.GOLD_INGOT,        8);   put(Material.DIAMOND,           60);
        put(Material.EMERALD,          70);   put(Material.NETHERITE_INGOT,  300);
        put(Material.NETHERITE_SCRAP, 100);   put(Material.ANCIENT_DEBRIS,   200);
        put(Material.LAPIS_LAZULI,      2);   put(Material.REDSTONE,           1);

        // ── Magic items ───────────────────────────────────────────────────────
        put(Material.BLAZE_ROD,        15);   put(Material.BLAZE_POWDER,       8);
        put(Material.PHANTOM_MEMBRANE, 12);   put(Material.NETHER_STAR,      500);
        put(Material.AMETHYST_SHARD,   20);   put(Material.ECHO_SHARD,       150);
        put(Material.PRISMARINE_SHARD,  5);   put(Material.PRISMARINE_CRYSTALS,8);

        // ── Food & Farming ────────────────────────────────────────────────────
        put(Material.WHEAT,             1);   put(Material.CARROT,             1);
        put(Material.POTATO,            1);   put(Material.BEETROOT,           2);
        put(Material.MELON_SLICE,       2);   put(Material.PUMPKIN,            3);
        put(Material.NETHER_WART,       4);   put(Material.COCOA_BEANS,        4);

        // ── Fishing ───────────────────────────────────────────────────────────
        put(Material.COD,               1);   put(Material.SALMON,             2);
        put(Material.PUFFERFISH,        5);   put(Material.TROPICAL_FISH,      8);
        put(Material.NAUTILUS_SHELL,   25);   put(Material.HEART_OF_THE_SEA, 300);

        // ── Potions & Misc ────────────────────────────────────────────────────
        put(Material.TOTEM_OF_UNDYING,250);  put(Material.ELYTRA,           500);
        put(Material.ENDER_PEARL,      10);  put(Material.ENDER_EYE,         30);
        put(Material.ENCHANTED_BOOK,   80);  put(Material.EXPERIENCE_BOTTLE,  3);
        put(Material.GOLDEN_APPLE,     50);  put(Material.ENCHANTED_GOLDEN_APPLE, 400);
        put(Material.SHULKER_BOX,      60);  put(Material.SHULKER_SHELL,     40);
    }

    private static void put(Material m, long v) { VALUES.put(m, v); }

    /** Returns the gold value for a material, or 1 as fallback. */
    public static long getValue(Material material) {
        return VALUES.getOrDefault(material, 1L);
    }

    /** Returns true if the material has a defined value. */
    public static boolean hasValue(Material material) {
        return VALUES.containsKey(material);
    }

    private GoldValueRegistry() {}
}
