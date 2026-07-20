package com.yourname.difficulty.quests;

import org.bukkit.entity.EntityType;
import org.bukkit.Material;

/**
 * QuestType — defines every quest in the game.
 *
 * Permanent quests can only be completed once.
 * Repeatable quests reset after each completion (grind for runes/gold).
 */
public enum QuestType {

    // ── Repeatable kill quests ────────────────────────────────────────────────

    BLAZE_HUNTER(
        "Blaze Hunter",
        "§7Kill §c50 Blazes§7 in the Nether.",
        Material.BLAZE_ROD,
        EntityType.BLAZE, 50,
        "FIRE_RUNE:64,GOLD:500",
        true
    ),
    DEPTHS_DIVER(
        "Depths Diver",
        "§7Kill §b30 Drowned§7 in the ocean.",
        Material.PRISMARINE_CRYSTALS,
        EntityType.DROWNED, 30,
        "WATER_RUNE:64,GOLD:500",
        true
    ),
    UNDEAD_SLAYER(
        "Undead Slayer",
        "§7Kill §260 Zombies§7 anywhere.",
        Material.ROTTEN_FLESH,
        EntityType.ZOMBIE, 60,
        "EARTH_RUNE:64,GOLD:500",
        true
    ),
    SKY_TERROR(
        "Sky Terror",
        "§7Kill §740 Phantoms§7 at night.",
        Material.PHANTOM_MEMBRANE,
        EntityType.PHANTOM, 40,
        "AIR_RUNE:64,GOLD:500",
        true
    ),
    MONSTER_HUNTER(
        "Monster Hunter",
        "§7Kill §f100 monsters§7 of any type.",
        Material.IRON_SWORD,
        null, 100,                          // null = any mob
        "GOLD:250,XP:500",
        true
    ),
    GUARDIAN_SLAYER(
        "Guardian Slayer",
        "§7Kill §b20 Guardians§7 at an ocean monument.",
        Material.GUARDIAN_SPAWN_EGG,
        EntityType.GUARDIAN, 20,
        "WATER_RUNE:32,GOLD:400",
        true
    ),
    GHAST_HUNTER(
        "Ghast Hunter",
        "§7Kill §715 Ghasts§7 in the Nether.",
        Material.GHAST_TEAR,
        EntityType.GHAST, 15,
        "AIR_RUNE:32,GOLD:400",
        true
    ),
    SPIDER_SLAYER(
        "Spider Slayer",
        "§7Kill §240 Spiders§7 or Cave Spiders.",
        Material.SPIDER_EYE,
        EntityType.SPIDER, 40,
        "EARTH_RUNE:32,GOLD:300",
        true
    ),

    // ── Permanent quests (one-time) ───────────────────────────────────────────

    FIRST_BOSS(
        "First Blood",
        "§7Defeat your §cfirst boss§7 (Wither/Dragon/Guardian).",
        Material.NETHER_STAR,
        EntityType.WITHER, 1,               // any boss — checked in QuestKillListener
        "GOLD:500,XP:1000",
        false
    ),
    WITHER_SLAYER(
        "Wither Slayer",
        "§7Defeat the §cWither§7.",
        Material.WITHER_SKELETON_SKULL,
        EntityType.WITHER, 1,
        "GOLD:1000,XP:2000",
        false
    ),
    DRAGON_SLAYER(
        "Dragon Slayer",
        "§7Defeat the §5Ender Dragon§7.",
        Material.DRAGON_EGG,
        EntityType.ENDER_DRAGON, 1,
        "GOLD:2000,XP:3000",
        false
    ),
    TEMPEST_SLAYER(
        "Storm Wrath",
        "§7Defeat the §5⚡ Tempest Overlord§7.",
        Material.FEATHER,
        EntityType.PHANTOM, 1,
        "GOLD:1500,XP:2500",
        false
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    public final String     displayName;
    public final String     description;
    public final Material   icon;
    /** Target entity type. null = any mob. */
    public final EntityType targetType;
    public final int        targetCount;
    /**
     * Reward string: comma-separated tokens like
     * "GOLD:500", "XP:1000", "FIRE_RUNE:64", "AIR_RUNE:32"
     */
    public final String     rewardSpec;
    /** true = can be completed again after reward is claimed */
    public final boolean    repeatable;

    QuestType(String displayName, String description, Material icon,
              EntityType targetType, int targetCount,
              String rewardSpec, boolean repeatable) {
        this.displayName = displayName;
        this.description = description;
        this.icon        = icon;
        this.targetType  = targetType;
        this.targetCount = targetCount;
        this.rewardSpec  = rewardSpec;
        this.repeatable  = repeatable;
    }
}
