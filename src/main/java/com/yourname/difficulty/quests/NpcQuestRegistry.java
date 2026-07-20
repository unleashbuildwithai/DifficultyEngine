package com.yourname.difficulty.quests;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.yourname.difficulty.quests.NpcQuestDef.*;
import static org.bukkit.Material.*;
import static org.bukkit.entity.EntityType.*;

/**
 * NpcQuestRegistry — all 300 NPC-gated quest definitions.
 *
 *  IDs  1–150  → Main quests   (required for Quest Skill Cape)
 *  IDs 151–300 → Secret quests (required for Boss Quest Cape / fire aura)
 *
 * Dimensions: "world" / "world_nether" / "world_the_end"
 *
 * All lists are unmodifiable after construction.
 */
public final class NpcQuestRegistry {

    private static final List<NpcQuestDef> ALL;
    private static final String OW = "world";
    private static final String NT = "world_nether";
    private static final String EN = "world_the_end";

    static {
        List<NpcQuestDef> q = new ArrayList<>(300);

        // ══════════════════════════════════════════════════════════════════════
        //  MAIN QUESTS — OVERWORLD (1 – 50)
        // ══════════════════════════════════════════════════════════════════════

        q.add(kill(1,  "Undead's End",     "Blacksmith Jim",    OW, ZOMBIE,   25, 500).hidden(ROTTEN_FLESH, 24, 250).build());
        q.add(kill(2,  "Bone Collector",   "Archer Ellis",      OW, SKELETON, 30, 500).build());
        q.add(kill(3,  "Powder Keg",       "Miner Thomas",      OW, CREEPER,  20, 600).build());
        q.add(kill(4,  "Web Cleaner",      "Farmer Rose",       OW, SPIDER,   40, 450).build());
        q.add(kill(5,  "Witch Hunt",       "Herbalist Mara",    OW, WITCH,    10, 700).build());
        q.add(kill(6,  "Slime Season",     "Miller Pete",       OW, SLIME,    50, 400).build());
        q.add(kill(7,  "Desert Storm",     "Desert Wanderer",   OW, HUSK,     30, 500).build());
        q.add(kill(8,  "Ice Cap",          "Tundra Hunter",     OW, STRAY,    25, 550).build());
        q.add(kill(9,  "Tide Breaker",     "Fisherman Lars",    OW, DROWNED,  30, 500).build());
        q.add(kill(10, "Night Terror",     "Sleepless Guard",   OW, PHANTOM,  20, 600).build());

        q.add(collect(11, "Harvest Moon",   "Farmer Rose",       OW, WHEAT,          64, 400).build());
        q.add(collect(12, "Root Digger",    "Cook Elena",        OW, CARROT,         64, 400).build());
        q.add(collect(13, "Metal Smith",    "Smith Harold",      OW, IRON_INGOT,     32, 450).build());
        q.add(collect(14, "Black Gold",     "Miner Thomas",      OW, COAL,           64, 300).build());
        q.add(collect(15, "Gem Seeker",     "Jeweler Hans",      OW, DIAMOND,         5, 800).build());
        q.add(collect(16, "Flock Manager",  "Shepherd Ben",      OW, WHITE_WOOL,     32, 350).build());
        q.add(collect(17, "Tannery Run",    "Tanner Anna",       OW, LEATHER,        16, 400).build());
        q.add(collect(18, "Forest Work",    "Lumberjack Roy",    OW, OAK_LOG,        64, 300).build());
        q.add(collect(19, "Gravel Pit",     "Digger Sam",        OW, GRAVEL,         64, 250).build());
        q.add(collect(20, "Green Dreams",   "Merchant Yara",     OW, EMERALD,         3, 600).build());

        q.add(collect(21, "First Catch",    "Fisherman Lars",    OW, Material.COD,            20, 350).build());
        q.add(collect(22, "Deep Pull",      "Fisher Bea",        OW, Material.SALMON,          5, 400).build());
        q.add(collect(23, "Strange Catch",  "River Fisher",      OW, Material.PUFFERFISH,      1, 300).build());
        q.add(collect(24, "Daily Bread",    "Cook Elena",        OW, BREAD,          16, 350).build());
        q.add(collect(25, "Morning Gather", "Farmer Rose",       OW, Material.EGG,            16, 300).build());
        q.add(collect(26, "Dairy Run",      "Dairy Maid",        OW, MILK_BUCKET,     3, 350).build());
        q.add(collect(27, "Bee Good",       "Beekeeper Hilda",   OW, HONEY_BOTTLE,    3, 400).build());
        q.add(collect(28, "Apple Season",   "Orchard Keeper",    OW, APPLE,          16, 300).build());
        q.add(collect(29, "Ink Well",       "Scribe Roland",     OW, INK_SAC,        10, 300).build());
        q.add(collect(30, "Pluck Run",      "Fletcher Cass",     OW, FEATHER,        32, 300).build());

        q.add(collect(31, "Iron Smith",     "Blacksmith Jim",    OW, IRON_SWORD,      1, 400).build());
        q.add(collect(32, "Bowyer's Work",  "Archer Ellis",      OW, BOW,             1, 400).build());
        q.add(collect(33, "Guard Duty",     "Guard Captain",     OW, SHIELD,          1, 450).build());
        q.add(collect(34, "Plate Work",     "Armorer Vik",       OW, IRON_CHESTPLATE, 1, 500).build());
        q.add(collect(35, "Diamond Edge",   "Mining Captain",    OW, DIAMOND_PICKAXE, 1, 700).build());
        q.add(collect(36, "Flint Knapper",  "Quarry Boss",       OW, FLINT,          16, 250).build());
        q.add(collect(37, "Book Smart",     "Librarian Clara",   OW, BOOK,            3, 350).build());
        q.add(collect(38, "Chart Maker",    "Cartographer Ned",  OW, MAP,             1, 400).build());
        q.add(collect(39, "Timepiece",      "Clockmaker Finn",   OW, CLOCK,           1, 450).build());
        q.add(collect(40, "True North",     "Traveler Gale",     OW, COMPASS,         1, 400).build());

        q.add(collect(41, "Green Trade",    "Village Elder",     OW, EMERALD,         5, 600).build());
        q.add(collect(42, "Restful Night",  "Innkeeper Sue",     OW, WHITE_BED,       1, 350).build());
        q.add(collect(43, "Trade Road",     "Merchant Yara",     OW, EMERALD,         8, 700).build());
        q.add(collect(44, "Herd Leader",    "Rancher Jo",        OW, LEAD,            1, 400).build());
        q.add(collect(45, "Gold Seeker",    "Alchemist Rex",     OW, GOLD_INGOT,      1, 450).build());
        q.add(collect(46, "Spark Test",     "Engineer Volt",     OW, REDSTONE,       16, 350).build());
        q.add(collect(47, "Lapis Lore",     "Scholar Tessa",     OW, LAPIS_LAZULI,    8, 350).build());
        q.add(collect(48, "Web Weaver",     "Weaver Linn",       OW, STRING,         32, 300).build());
        q.add(collect(49, "Bone Stock",     "Butcher Hank",      OW, BONE,           16, 300).build());
        q.add(collect(50, "Portal Prep",    "Portal Watcher",    OW, Material.ENDER_PEARL,     5, 500).build());

        // ══════════════════════════════════════════════════════════════════════
        //  MAIN QUESTS — NETHER (51 – 100)
        // ══════════════════════════════════════════════════════════════════════

        q.add(kill(51, "Blaze Breaker",    "Flame Warden",      NT, BLAZE,         25, 600).build());
        q.add(kill(52, "Gold Hunt",        "Gold Guard",        NT, PIGLIN,        30, 550).build());
        q.add(kill(53, "Hog Wild",         "Nether Butcher",    NT, HOGLIN,        20, 650).build());
        q.add(kill(54, "Ghost Buster",     "Nether Hunter",     NT, GHAST,         10, 700).build());
        q.add(kill(55, "Skull March",      "Bone Collector",    NT, WITHER_SKELETON,15,750).build());
        q.add(kill(56, "Lava Jumper",      "Lava Walker",       NT, MAGMA_CUBE,    30, 500).build());
        q.add(kill(57, "Walker Stop",      "Lava Fisher",       NT, STRIDER,       15, 600).build());
        q.add(kill(58, "Zoglin Zap",       "Scout Axe",         NT, ZOGLIN,        10, 650).build());
        q.add(kill(59, "Brute Force",      "Fortress Guard",    NT, PIGLIN_BRUTE,  10, 800).build());
        q.add(kill(60, "Bone Yard",        "Bone Warden",       NT, SKELETON,      20, 500).build());

        q.add(collect(61, "Crystal Dig",   "Crystal Miner",     NT, QUARTZ,        32, 400).build());
        q.add(collect(62, "Rock Heap",     "Nether Mason",      NT, NETHERRACK,    64, 250).build());
        q.add(collect(63, "Gold Dust",     "Nether Prospector", NT, GOLD_NUGGET,   16, 350).build());
        q.add(collect(64, "Ancient Find",  "Relic Hunter",      NT, ANCIENT_DEBRIS,  1,1000).build());
        q.add(collect(65, "Basalt Base",   "Builder Brik",      NT, BASALT,        32, 300).build());
        q.add(collect(66, "Dark Wall",     "Dark Mason",        NT, BLACKSTONE,    32, 300).build());
        q.add(collect(67, "Soul Path",     "Spirit Walker",     NT, SOUL_SAND,     16, 350).build());
        q.add(collect(68, "Blue Wood",     "Nether Carpenter",  NT, WARPED_PLANKS, 16, 350).build());
        q.add(collect(69, "Red Wood",      "Red Carpenter",     NT, CRIMSON_PLANKS,16, 350).build());
        q.add(collect(70, "Fortress Stone","Fortress Builder",  NT, NETHER_BRICK,  32, 400).build());

        q.add(collect(71, "Rod Cache",     "Fire Keeper",       NT, BLAZE_ROD,     10, 500).build());
        q.add(collect(72, "Wart Farm",     "Brew Master",       NT, NETHER_WART,   16, 400).build());
        q.add(collect(73, "Cream Pool",    "Potion Seller",     NT, MAGMA_CREAM,    8, 450).build());
        q.add(collect(74, "Tear Drop",     "Tear Collector",    NT, GHAST_TEAR,     3, 700).build());
        q.add(collect(75, "Gold Knight",   "Gilded Knight",     NT, GOLDEN_HELMET,  1, 500).build());
        q.add(collect(76, "Fungus Farm",   "Nether Gardener",   NT, CRIMSON_FUNGUS,16, 350).build());
        q.add(collect(77, "Blue Shroom",   "Blue Gardener",     NT, WARPED_FUNGUS, 16, 350).build());
        q.add(collect(78, "Hog Skin",      "Nether Tanner",     NT, LEATHER,        8, 400).build());
        q.add(collect(79, "Nether Pork",   "Nether Cook",       NT, PORKCHOP,       8, 350).build());
        q.add(collect(80, "Cannon Load",   "Cannon Master",     NT, FIRE_CHARGE,    8, 450).build());

        q.add(collect(81, "Soul Soil",     "Soul Collector",    NT, SOUL_SOIL,     16, 350).build());
        q.add(collect(82, "Glow Find",     "Glow Hunter",       NT, SHROOMLIGHT,    8, 450).build());
        q.add(collect(83, "Chain Craft",   "Chain Smith",       NT, CHAINMAIL_CHESTPLATE,1,550).build());
        q.add(collect(84, "Snout Badge",   "Banner Keeper",     NT, PIGLIN_BANNER_PATTERN,1,600).build());
        q.add(collect(85, "Magnet Stone",  "Magnet Sage",       NT, LODESTONE,      1, 700).build());
        q.add(collect(86, "Death Anchor",  "Anchor Keeper",     NT, RESPAWN_ANCHOR, 1, 750).build());
        q.add(collect(87, "Glow Dust",     "Light Keeper",      NT, GLOWSTONE_DUST,16, 350).build());
        q.add(collect(88, "Quartz Block",  "Architect Var",     NT, QUARTZ_BLOCK,  16, 400).build());
        q.add(collect(89, "Gold Vault",    "Treasury Guard",    NT, GOLD_BLOCK,     1, 800).build());
        q.add(collect(90, "Portal Block",  "Portal Smith",      NT, OBSIDIAN,       4, 400).build());

        q.add(collect(91, "Barter Trade",  "Trade Piglin",      NT, GOLD_INGOT,    10, 500).build());
        q.add(collect(92, "Lava Tap",      "Lava Tap",          NT, LAVA_BUCKET,    2, 400).build());
        q.add(collect(93, "Saddle Up",     "Mount Keeper",      NT, SADDLE,         1, 500).build());
        q.add(collect(94, "Brew Setup",    "Alchemist Neth",    NT, BREWING_STAND,  1, 600).build());
        q.add(collect(95, "Bone Ash",      "Bone Ash Trader",   NT, BONE_MEAL,     32, 300).build());
        q.add(collect(96, "Nether Gold",   "Ore Keeper",        NT, RAW_GOLD,       8, 450).build());
        q.add(collect(97, "Double Find",   "Master Relic",      NT, ANCIENT_DEBRIS, 2,1500).build());
        q.add(collect(98, "Ghast Tears",   "Ghast Warden",      NT, GHAST_TEAR,     5, 900).build());
        q.add(collect(99, "Full Gold",     "Gilded Warden",     NT, GOLDEN_CHESTPLATE,1,600).build());
        q.add(collect(100,"Nether Master", "Nether Sage",       NT, BLAZE_ROD,     20,1200).hidden(ANCIENT_DEBRIS,1,500).build());

        // ══════════════════════════════════════════════════════════════════════
        //  MAIN QUESTS — END (101 – 150)
        // ══════════════════════════════════════════════════════════════════════

        q.add(kill(101,"Void Stalker",     "End Warden",        EN, ENDERMAN,      25, 700).build());
        q.add(kill(102,"Shell Shock",      "Shell Hunter",      EN, SHULKER,       10, 750).build());
        q.add(kill(103,"Tiny Terror",      "Bug Squasher",      EN, ENDERMITE,     20, 500).build());
        q.add(kill(104,"Dragon Bane",      "Dragon Scholar",    EN, ENDER_DRAGON,   1,3000).build());
        q.add(collect(105,"Chorus Harvest","End Farmer",        EN, CHORUS_FRUIT,  16, 600).build());
        q.add(collect(106,"Purpur Palace", "End Builder",       EN, PURPUR_BLOCK,  32, 600).build());
        q.add(collect(107,"End Stone Pile","End Mason",         EN, END_STONE,     32, 500).build());
        q.add(collect(108,"Pearl Pool",    "Pearl Trader",      EN, Material.ENDER_PEARL,   16, 700).build());
        q.add(collect(109,"Wing Find",     "Wing Seeker",       EN, ELYTRA,         1,3000).build());
        q.add(collect(110,"Shell Craft",   "Shell Smith",       EN, SHULKER_SHELL,  4, 800).build());

        q.add(collect(111,"Pop Culture",   "End Cook",          EN, POPPED_CHORUS_FRUIT,8,550).build());
        q.add(collect(112,"Rod Collector", "Rod Trader",        EN, END_ROD,        8, 600).build());
        q.add(collect(113,"Trophy Find",   "Trophy Hunter",     EN, DRAGON_HEAD,    1,5000).build());
        q.add(collect(114,"Void Chest",    "Void Keeper",       EN, ENDER_CHEST,    1, 800).build());
        q.add(collect(115,"Eye Seeker",    "Seer",              EN, ENDER_EYE,      8, 600).build());
        q.add(collect(116,"Outer Reach",   "Explorer Vex",      EN, CHORUS_FRUIT,  32, 800).build());
        q.add(collect(117,"City Loot",     "City Raider",       EN, SHULKER_SHELL,  8,1000).build());
        q.add(kill(118,  "Enderman Elite", "Void Master",       EN, ENDERMAN,      50,1000).build());
        q.add(kill(119,  "Shulker Siege",  "Shell Siege Master",EN, SHULKER,       20,1000).build());
        q.add(collect(120,"Dragon Vial",   "Alchemy End",       EN, Material.DRAGON_BREATH, 4, 900).build());

        q.add(collect(121,"Firework Prep", "Elytra Flier",      EN, Material.FIREWORK_ROCKET, 4, 600).build());
        q.add(collect(122,"End Bricks",    "End Bricklayer",    EN, END_STONE_BRICKS,16,550).build());
        q.add(collect(123,"Chorus Bloom",  "End Botanist",      EN, CHORUS_FLOWER,  4, 700).build());
        q.add(collect(124,"Phantom Cloak", "End Crafter",       EN, PHANTOM_MEMBRANE,4,700).build());
        q.add(collect(125,"Spectral Stock","Spectral Archer",   EN, Material.SPECTRAL_ARROW, 4, 650).build());
        q.add(collect(126,"Pearl Storm",   "Pearl Storm Master",EN, Material.ENDER_PEARL,   32,1000).build());
        q.add(kill(127,  "Mite Massacre",  "Mite Warden",       EN, ENDERMITE,     50, 700).build());
        q.add(collect(128,"Shell Box",     "Container Master",  EN, SHULKER_SHELL, 16,1500).build());
        q.add(collect(129,"Crystal Art",   "Crystal Ritualist", EN, Material.END_CRYSTAL,    4,1200).build());
        q.add(collect(130,"End Complete",  "End Sage",          EN, ELYTRA,         1,2000).hidden(Material.DRAGON_BREATH,4,1000).build());

        q.add(kill(131,  "Dark Spire",     "Dark Warden",       EN, ENDERMAN,      75,1500).build());
        q.add(collect(132,"Shell Master",  "Shell Master",      EN, SHULKER_SHELL, 32,2000).build());
        q.add(kill(133,  "Void Clear",     "Void Cleaner",      EN, SHULKER,       30,1500).build());
        q.add(collect(134,"Dragon Blood",  "Alchemy End II",    EN, Material.DRAGON_BREATH, 8,1200).build());
        q.add(kill(135,  "Final Dragon",   "Dragon Chronicler", EN, ENDER_DRAGON,   1,5000).build());
        q.add(collect(136,"Purpur Palace2","Master Builder",    EN, PURPUR_BLOCK,  64,1200).build());
        q.add(collect(137,"Pearl Master",  "Pearl Master",      EN, Material.ENDER_PEARL,   64,1500).build());
        q.add(kill(138,  "Ender Hundreds", "Void Centurion",    EN, ENDERMAN,     100,2000).build());
        q.add(collect(139,"Dragon Trophy", "Trophy Hall",       EN, DRAGON_HEAD,    1,4000).hidden(Material.DRAGON_BREATH,8,2000).build());
        q.add(collect(140,"End Stone Vault","End Vault Keeper", EN, END_STONE,     64, 700).build());

        q.add(kill(141,  "Shulker Hundred","Shell Century",     EN, SHULKER,       50,2000).build());
        q.add(collect(142,"Chorus Garden", "Garden Master",     EN, CHORUS_FLOWER, 16,1000).build());
        q.add(collect(143,"Dragon Hoard",  "Dragon Hoarder",    EN, Material.DRAGON_BREATH,16,2000).build());
        q.add(kill(144,  "Mite King",      "Mite King",         EN, ENDERMITE,    100,1000).build());
        q.add(collect(145,"End Collection","Collection Master", EN, ELYTRA,         1,3000).hidden(SHULKER_SHELL,16,1500).build());
        q.add(collect(146,"Purpur Throne", "Purpur King",       EN, PURPUR_BLOCK, 128,2000).build());
        q.add(kill(147,  "Void Legend",    "Void Legend",       EN, ENDERMAN,     200,5000).build());
        q.add(kill(148,  "Shell Legend",   "Shell Legend",      EN, SHULKER,       75,3000).build());
        q.add(kill(149,  "Dragon Legend",  "Dragon Legend",     EN, ENDER_DRAGON,   1,8000).hidden(Material.DRAGON_BREATH,16,4000).build());
        q.add(collect(150,"Quest Pinnacle","Grand Quest Master", EN, NETHER_STAR,   1,10000).hidden(Material.DRAGON_BREATH,16,5000).build());

        // ══════════════════════════════════════════════════════════════════════
        //  SECRET QUESTS — OVERWORLD (151 – 200)
        // ══════════════════════════════════════════════════════════════════════

        q.add(collect(151,"Rotten Secret",  "Hermit Jake",       OW, ROTTEN_FLESH, 64, 800).secret().hidden(BONE,32,400).build());
        q.add(kill(152,  "Web Whisperer",   "Spider Lady",       OW, CAVE_SPIDER,  20, 700).secret().build());
        q.add(collect(153,"Midnight Wool",  "Night Shepherd",    OW, BLACK_WOOL,   16, 600).secret().sneak().build());
        q.add(collect(154,"Golden Meal",    "Hermit Cook",       OW, GOLDEN_APPLE,  3,1000).secret().build());
        q.add(kill(155,  "Vindicator Fall", "Exile Scout",       OW, VINDICATOR,   10, 900).secret().build());
        q.add(kill(156,  "Evoker's End",    "Witch Breaker",     OW, EVOKER,        3,1500).secret().build());
        q.add(kill(157,  "Vex Punisher",    "Ghost Cleaner",     OW, VEX,          20, 700).secret().build());
        q.add(kill(158,  "Pillager Raid",   "Raid Veteran",      OW, PILLAGER,     30, 800).secret().build());
        q.add(kill(159,  "Ravager Run",     "Beast Tamer",       OW, RAVAGER,       5,1500).secret().build());
        q.add(kill(160,  "Lost Cure",       "Village Doctor",    OW, ZOMBIE_VILLAGER,5,800).secret().build());

        q.add(collect(161,"Silver Touch",   "Alch Hermit",       OW, IRON_INGOT,   64, 600).secret().hidden(GOLD_INGOT,16,500).build());
        q.add(collect(162,"Enchanted Book", "Sage Librarian",    OW, ENCHANTED_BOOK, 1, 800).secret().build());
        q.add(collect(163,"Name Tag Bearer","Pet Trainer",       OW, NAME_TAG,      3, 700).secret().sneak().build());
        q.add(collect(164,"Saddle Rider",   "Horse Whisperer",   OW, SADDLE,        3, 700).secret().build());
        q.add(collect(165,"Sugar Rush",     "Baker Hermit",      OW, SUGAR,        32, 500).secret().hidden(CAKE,1,400).build());
        q.add(collect(166,"Rare Fruit",     "Jungle Guide",      OW, MELON_SLICE,  32, 500).secret().build());
        q.add(collect(167,"Cactus Cache",   "Desert Hermit",     OW, CACTUS,       64, 500).secret().sneak().build());
        q.add(collect(168,"Snowball Fight", "Snow Hermit",       OW, Material.SNOWBALL,     64, 400).secret().build());
        q.add(collect(169,"Ice Cold",       "Glacier Walker",    OW, PACKED_ICE,   16, 600).secret().build());
        q.add(collect(170,"Sponge Seeker",  "Deep Diver",        OW, SPONGE,        1,1000).secret().build());

        q.add(kill(171,  "Silverfish Nest", "Stone Mason Secret",OW, SILVERFISH,   50, 600).secret().build());
        q.add(collect(172,"Amethyst Stash", "Crystal Hermit",    OW, AMETHYST_SHARD,32,700).secret().hidden(AMETHYST_BLOCK,4,400).build());
        q.add(collect(173,"Copper Hoard",   "Oxidized Sage",     OW, COPPER_INGOT, 32, 600).secret().build());
        q.add(collect(174,"Raw Iron Pile",  "Smelter Secret",    OW, RAW_IRON,     32, 550).secret().build());
        q.add(collect(175,"Dripstone Cave", "Cave Sage",         OW, POINTED_DRIPSTONE,16,600).secret().build());
        q.add(collect(176,"Glow Lichen",    "Biolum Sage",       OW, GLOW_LICHEN,  16, 500).secret().sneak().build());
        q.add(collect(177,"Ancient Sculk",  "Ancient Guardian",  OW, SCULK,        16, 800).secret().sneak().build());
        q.add(collect(178,"Echo Shard",     "Echo Walker",       OW, ECHO_SHARD,    1,1500).secret().sneak().build());
        q.add(collect(179,"Recovery Compass","Lost One",         OW, RECOVERY_COMPASS,1,1200).secret().build());
        q.add(collect(180,"Disc Collector", "Music Box Hermit",  OW, MUSIC_DISC_13, 1,1000).secret().build());

        q.add(collect(181,"Lush Moss",      "Moss Hermit",       OW, MOSS_BLOCK,   32, 500).secret().build());
        q.add(collect(182,"Spore Blossom",  "Nature Spirit",     OW, SPORE_BLOSSOM, 4, 600).secret().sneak().build());
        q.add(collect(183,"Tropical Gift",  "Axolotl Keeper",    OW, Material.TROPICAL_FISH, 5, 600).secret().build());
        q.add(collect(184,"Frog Spawn",     "Swamp Sage",        OW, FROGSPAWN,     1, 700).secret().sneak().build());
        q.add(kill(185,  "Guardian Secret", "Deep Warden",       OW, GUARDIAN,     30, 900).secret().build());
        q.add(kill(186,  "Elder Secret",    "Elder Warden",      OW, ELDER_GUARDIAN, 1,2000).secret().build());
        q.add(collect(187,"Sea Lantern",    "Deep Light Keeper", OW, SEA_LANTERN,   8, 600).secret().build());
        q.add(collect(188,"Conduit Shell",  "Sea Master",        OW, NAUTILUS_SHELL, 4, 900).secret().build());
        q.add(collect(189,"Totem of Life",  "Life Keeper",       OW, TOTEM_OF_UNDYING,1,3000).secret().build());
        q.add(collect(190,"Forbidden Apple","Forbidden Sage",    OW, ENCHANTED_GOLDEN_APPLE,1,5000).secret().hidden(GOLD_BLOCK,9,2000).build());

        q.add(kill(191,  "Warden Terror",   "Ancient Listener",  OW, WARDEN,        1,5000).secret().sneak().build());
        q.add(collect(192,"Sculk Sensor",   "Echo Sensitive",    OW, SCULK_SENSOR,  4, 800).secret().sneak().build());
        q.add(collect(193,"Sculk Catalyst", "Ancient Catalyst",  OW, SCULK_CATALYST, 1,1200).secret().build());
        q.add(collect(194,"Sculk Shrieker", "Shrieker Keeper",   OW, SCULK_SHRIEKER, 1,2000).secret().sneak().build());
        q.add(kill(195,  "Witch Coven",     "Coven Breaker",     OW, WITCH,        20,1000).secret().hidden(SUGAR,8,500).build());
        q.add(kill(196,  "Iron Golem Fall", "Rebel Sage",        OW, IRON_GOLEM,    1, 800).secret().sneak().build());
        q.add(collect(197,"Prism Cache",    "Prismarine Sage",   OW, PRISMARINE_CRYSTALS,32,700).secret().build());
        q.add(collect(198,"Name Tag Stash", "Librarian Exile",   OW, NAME_TAG,      5, 800).secret().build());
        q.add(kill(199,  "Zombie Horde",    "Horde Watcher",     OW, ZOMBIE,        75, 700).secret().hidden(ROTTEN_FLESH,64,400).build());
        q.add(collect(200,"Disc Pigstep",   "Bastion Music",     OW, MUSIC_DISC_PIGSTEP,1,2000).secret().build());

        // ══════════════════════════════════════════════════════════════════════
        //  SECRET QUESTS — NETHER (201 – 250)
        // ══════════════════════════════════════════════════════════════════════

        q.add(collect(201,"Bastion Gold",   "Hidden Brute",      NT, GOLD_INGOT,   32, 900).secret().sneak().build());
        q.add(kill(202,  "Lava Baiter",     "Lava Bait Sage",    NT, PIGLIN,        5,1000).secret().hidden(LAVA_BUCKET,2,500).build());
        q.add(collect(203,"Nether Bone",    "Fossil Hunter",     NT, BONE_BLOCK,    8, 700).secret().build());
        q.add(collect(204,"Gold Diplomat",  "Piglin Diplomat",   NT, GOLDEN_BOOTS,  1, 600).secret().hidden(GOLDEN_HELMET,1,300).build());
        q.add(kill(205,  "Brute Gauntlet",  "Brute Baron",       NT, PIGLIN_BRUTE, 20,2000).secret().build());
        q.add(collect(206,"Crying Portal",  "Void Weeper",       NT, CRYING_OBSIDIAN, 8, 800).secret().build());
        q.add(collect(207,"Pigstep Disc",   "Bastion DJ",        NT, MUSIC_DISC_PIGSTEP,1,2000).secret().sneak().build());
        q.add(collect(208,"Warped Sign",    "Blue Forest Sign",  NT, WARPED_SIGN,   2, 500).secret().build());
        q.add(collect(209,"Crimson Sign",   "Red Forest Sign",   NT, CRIMSON_SIGN,  2, 500).secret().build());
        q.add(kill(210,  "Hoglin Rancher",  "Piglin Rancher",    NT, HOGLIN,       40,1200).secret().build());

        q.add(collect(211,"Fossil Sage",    "Bone Fossil Sage",  NT, BONE_BLOCK,   16, 800).secret().build());
        q.add(collect(212,"Quartz Pillar",  "Quartz Architect",  NT, QUARTZ_PILLAR, 8, 600).secret().build());
        q.add(kill(213,  "Ghast Rider",     "Cloud Buster",      NT, GHAST,        20,1500).secret().build());
        q.add(collect(214,"Nether Gold Ore","Raw Nether Miner",  NT, NETHER_GOLD_ORE, 8, 600).secret().build());
        q.add(collect(215,"Weeping Vine",   "Crying Vine Sage",  NT, WEEPING_VINES,16, 500).secret().sneak().build());
        q.add(collect(216,"Twisting Vine",  "Twisted Sage",      NT, TWISTING_VINES,16,500).secret().sneak().build());
        q.add(collect(217,"Warped Nylium",  "Blue Ground Sage",  NT, WARPED_NYLIUM, 8, 550).secret().build());
        q.add(collect(218,"Crimson Nylium", "Red Ground Sage",   NT, CRIMSON_NYLIUM, 8, 550).secret().build());
        q.add(kill(219,  "Skeleton Sniper", "Nether Archer",     NT, SKELETON,     40, 900).secret().build());
        q.add(kill(220,  "Skull Hunter",    "Skull Hunter Secret",NT,WITHER_SKELETON,30,1500).secret().hidden(WITHER_SKELETON_SKULL,1,1000).build());

        q.add(collect(221,"Debris Master",  "Debris Baron",      NT, ANCIENT_DEBRIS, 3,3000).secret().build());
        q.add(collect(222,"Strider Stick",  "Lava Sailor",       NT, WARPED_FUNGUS_ON_A_STICK,1,600).secret().build());
        q.add(collect(223,"Crying Hoard",   "Portal Memory",     NT, CRYING_OBSIDIAN,16,1200).secret().build());
        q.add(kill(224,  "Zoglin Pack",     "Hog Caller",        NT, ZOGLIN,       25,1200).secret().build());
        q.add(collect(225,"Soul Campfire",  "Soul Fire Sage",    NT, SOUL_CAMPFIRE, 2, 600).secret().sneak().build());
        q.add(collect(226,"Soul Torch",     "Soul Lighter",      NT, SOUL_TORCH,    8, 500).secret().build());
        q.add(kill(227,  "Magma Titan",     "Magma Baron",       NT, MAGMA_CUBE,   50,1200).secret().build());
        q.add(collect(228,"Snout Armor",    "Piglin Warlord",    NT, NETHERITE_INGOT,1,2000).secret().sneak().build());
        q.add(collect(229,"Red Nether Brick","Red Mason",        NT, RED_NETHER_BRICKS,32,700).secret().build());
        q.add(collect(230,"Chain Lord",     "Chain Lord",        NT, CHAIN,        16, 600).secret().build());

        q.add(kill(231,  "Enderman Nether", "End Stalker Nether",NT, ENDERMAN,     20, 900).secret().build());
        q.add(collect(232,"Nether Wart Blk","Wart Block Sage",   NT, NETHER_WART_BLOCK,8,500).secret().sneak().build());
        q.add(collect(233,"Warped Wart Blk","Warped Sage",       NT, WARPED_WART_BLOCK,8,500).secret().sneak().build());
        q.add(collect(234,"Blaze Core",     "Fire Master Secret",NT, BLAZE_ROD,    20,1200).secret().hidden(MAGMA_CREAM,8,600).build());
        q.add(collect(235,"Skull Trophy",   "Skull Collector",   NT, WITHER_SKELETON_SKULL,3,5000).secret().build());
        q.add(kill(236,  "Wither Army",     "Skull Baron",       NT, WITHER_SKELETON,50,2000).secret().hidden(WITHER_SKELETON_SKULL,1,2000).build());
        q.add(collect(237,"Magma Block",    "Fire Core Sage",    NT, MAGMA_BLOCK,   8, 600).secret().build());
        q.add(kill(238,  "Ghast Purge",     "Ghast Purger",      NT, GHAST,        25,2000).secret().hidden(GHAST_TEAR,5,1000).build());
        q.add(collect(239,"Lodestone Pair", "Magnetic Baron",    NT, LODESTONE,     1, 700).secret().hidden(COMPASS,1,500).build());
        q.add(collect(240,"Netherite Set",  "Netherite Baron",   NT, NETHERITE_INGOT,4,5000).secret().hidden(ANCIENT_DEBRIS,4,2000).build());

        q.add(kill(241,  "Strider Lord",    "Strider Lord",      NT, STRIDER,      30,1000).secret().build());
        q.add(collect(242,"Master Barterer","Master Barterer",   NT, GOLD_INGOT,   32,1200).secret().sneak().build());
        q.add(kill(243,  "Nether Champion", "Nether Champion",   NT, PIGLIN_BRUTE, 40,3000).secret().hidden(ANCIENT_DEBRIS,2,1500).build());
        q.add(kill(244,  "Ghast Cleaner",   "Ghast Purger II",   NT, GHAST,        30,2500).secret().build());
        q.add(collect(245,"Nether Sprouts", "Nether Naturalist", NT, NETHER_SPROUTS,16, 500).secret().sneak().build());
        q.add(collect(246,"Warped Hyphae",  "Fungal Sage",       NT, WARPED_HYPHAE,16, 550).secret().build());
        q.add(collect(247,"Crimson Hyphae", "Crimson Fungal",    NT, CRIMSON_HYPHAE,16,550).secret().build());
        q.add(kill(248,  "Brute Siege",     "Fortress Siege",    NT, PIGLIN_BRUTE, 30,2500).secret().build());
        q.add(collect(249,"Hoglin Leather", "Leather Baron",     NT, LEATHER,      32, 700).secret().build());
        q.add(kill(250,  "Nether Legend",   "Nether Legend",     NT, BLAZE,        50,5000).secret().hidden(BLAZE_ROD,20,2500).build());

        // ══════════════════════════════════════════════════════════════════════
        //  SECRET QUESTS — END (251 – 300)
        // ══════════════════════════════════════════════════════════════════════

        q.add(collect(251,"Dragon Breath",  "Dragon Sage Secret",EN, Material.DRAGON_BREATH, 8,2000).secret().build());
        q.add(kill(252,  "End Elite",       "End Elite Warden",  EN, ENDERMAN,     150,3000).secret().build());
        q.add(kill(253,  "Shell Master",    "Shell Grand Master",EN, SHULKER,       40,2000).secret().build());
        q.add(collect(254,"Dragon Head",    "Trophy Master",     EN, DRAGON_HEAD,    1,6000).secret().sneak().build());
        q.add(kill(255,  "Mite Queen",      "Mite Queen",        EN, ENDERMITE,    200,2000).secret().build());
        q.add(collect(256,"End Cake Party", "Party End Sage",    EN, CAKE,           1,1000).secret().sneak().build());
        q.add(collect(257,"Respawn Anchor", "Reckless Sage",     EN, RESPAWN_ANCHOR, 1,1500).secret().sneak().build());
        q.add(kill(258,  "Dragon Slayer",   "Dragon Legendary",  EN, ENDER_DRAGON,   1,10000).secret().hidden(Material.DRAGON_BREATH,16,5000).build());
        q.add(collect(259,"Lead Dragon",    "MyPet Tamer End",   EN, LEAD,           5,2000).secret().sneak().build());
        q.add(collect(260,"End Tower",      "Obsidian Placer",   EN, OBSIDIAN,      64,1200).secret().build());

        q.add(collect(261,"Gateway Key",    "Void Gateway Sage", EN, Material.ENDER_PEARL,   64,2000).secret().build());
        q.add(kill(262,  "Shulker Legend",  "Shell Legend Scrt", EN, SHULKER,      100,5000).secret().hidden(SHULKER_SHELL,16,2500).build());
        q.add(collect(263,"Ship Loot",      "Ship Raider Secret",EN, ELYTRA,         1,4000).secret().hidden(DRAGON_HEAD,1,3000).build());
        q.add(collect(264,"Void Walk Full", "Void Walker Master",EN, Material.ENDER_PEARL,  128,3000).secret().build());
        q.add(kill(265,  "Enderman God",    "End God Warden",    EN, ENDERMAN,     300,8000).secret().build());
        q.add(collect(266,"Purpur Throne",  "End King",          EN, PURPUR_BLOCK, 256,3000).secret().build());
        q.add(collect(267,"Full End Armor", "End Armor Master",  EN, ELYTRA,         1,5000).secret().hidden(SHULKER_SHELL,32,3000).build());
        q.add(collect(268,"Beacon of End",  "End Beacon Sage",   EN, NETHER_STAR,    1,5000).secret().sneak().build());
        q.add(kill(269,  "End Dungeon",     "Dungeon End Master",EN, SHULKER,       50,3000).secret().hidden(ELYTRA,1,3000).build());
        q.add(collect(270,"Crystal Garden", "Crystal Garden",    EN, Material.END_CRYSTAL,    8,3000).secret().build());

        q.add(collect(271,"Void Moss",      "End Botanist Scrt", EN, CHORUS_FLOWER, 32,2000).secret().sneak().build());
        q.add(collect(272,"Dragon Alchemy", "End Alchemist",     EN, Material.DRAGON_BREATH,32,4000).secret().hidden(GHAST_TEAR,5,2000).build());
        q.add(kill(273,  "Mite Legend",     "Mite Legend",       EN, ENDERMITE,    500,5000).secret().build());
        q.add(collect(274,"Elytra Mastery", "Elytra Grand Master",EN,Material.FIREWORK_ROCKET,64,2000).secret().sneak().build());
        q.add(collect(275,"Obsidian Spire", "Spire Builder",     EN, OBSIDIAN,     128,2000).secret().build());
        q.add(collect(276,"Shell City",     "Shulker City",      EN, SHULKER_SHELL, 64,5000).secret().build());
        q.add(kill(277,  "Dragon Massacre", "Dragon Baron",      EN, ENDER_DRAGON,   1,12000).secret().hidden(Material.DRAGON_BREATH,32,8000).build());
        q.add(collect(278,"End Complete Set","End Completionist", EN, ELYTRA,         1,8000).secret().hidden(Material.DRAGON_BREATH,16,4000).build());
        q.add(collect(279,"Void Citadel",   "Citadel Builder",   EN, PURPUR_BLOCK, 512,5000).secret().build());
        q.add(collect(280,"End God Set",    "End God",           EN, NETHERITE_INGOT,4,10000).secret().sneak().build());

        q.add(kill(281,  "End Warlord",     "End Warlord",       EN, ENDERMAN,     500,15000).secret().build());
        q.add(collect(282,"Pearl Ocean",    "Pearl Ocean Sage",  EN, Material.ENDER_PEARL,  256,5000).secret().hidden(SHULKER_SHELL,32,3000).build());
        q.add(collect(283,"Dragon Scale",   "Cape Dragon Sage",  EN, Material.DRAGON_BREATH,64,10000).secret().hidden(ELYTRA,1,5000).build());
        q.add(kill(284,  "End Titan",       "End Titan",         EN, SHULKER,      200,10000).secret().hidden(SHULKER_SHELL,64,5000).build());
        q.add(collect(285,"End Stone Vault","Vault End Keeper",  EN, END_STONE,    256,3000).secret().build());
        q.add(collect(286,"MyPet Dragon",   "MyPet Sage End",    EN, LEAD,          10,5000).secret().sneak().build());
        q.add(kill(287,  "Void Champion",   "Void Champion",     EN, ENDERMITE,   1000,8000).secret().build());
        q.add(collect(288,"Crystal Fort",   "Crystal Fort",      EN, Material.END_CRYSTAL,   16,5000).secret().build());
        q.add(collect(289,"Dragon Blood",   "Blood Sage End",    EN, Material.DRAGON_BREATH,128,15000).secret().build());
        q.add(collect(290,"End Emperor",    "End Emperor",       EN, PURPUR_BLOCK,1024,10000).secret().build());

        q.add(kill(291,  "Dragon God",      "Dragon God",        EN, ENDER_DRAGON,   1,20000).secret().hidden(Material.DRAGON_BREATH,64,10000).build());
        q.add(collect(292,"Shell God",      "Shell God",         EN, SHULKER_SHELL,128,8000).secret().build());
        q.add(collect(293,"End Pearl God",  "Pearl God",         EN, Material.ENDER_PEARL,  512,10000).secret().build());
        q.add(kill(294,  "Void God",        "Void God",          EN, ENDERMAN,    1000,20000).secret().build());
        q.add(collect(295,"End Full Legend","Full Legend",       EN, ELYTRA,         1,15000).secret().hidden(Material.DRAGON_BREATH,128,8000).build());
        q.add(kill(296,  "Dragon Legend",   "Dragon Legend Scrt",EN, ENDER_DRAGON,   1,25000).secret().sneak().build());
        q.add(collect(297,"End Crystal God","End Crystal God",   EN, Material.END_CRYSTAL,   32,10000).secret().build());
        q.add(collect(298,"The Last Shell", "Shell Omega",       EN, SHULKER_SHELL,256,15000).secret().hidden(ELYTRA,1,8000).build());
        q.add(collect(299,"Void Ascension", "Ascension Sage",    EN, Material.DRAGON_BREATH,256,20000).secret().hidden(NETHER_STAR,1,10000).build());
        q.add(kill(300,  "The Omega",       "The Omega",         EN, ENDER_DRAGON,   1,50000).secret().hidden(Material.DRAGON_BREATH,256,25000).build());

        // ══════════════════════════════════════════════════════════════════════
        //  BOSS ROOM QUESTS (301 – 306) — placed FAR from the boss room coords
        //  Abyssal Chamber  (-21, -39, -69)   → NPC far north at (-21, 70, -270)
        //  Crimson Pit      (-108, -26, -14)  → NPC far east  at ( 92, 70,  -14)
        //  Verdant Shrine   ( 61, -43, 100)   → NPC far west  at (-140,70,  100)
        //  Tempest Sanctum  (115, -38, -47)   → NPC far south at (115, 70,  153)
        //  Void Sanctum     (-16, -57,  99)   → NPC far north at (-16, 70, -101)
        //  Gilded Sanctum   (-14, -42, 267)   → NPC far east  at (186, 70,  267)
        // ══════════════════════════════════════════════════════════════════════

        q.add(kill(301, "Depths of the Abyss", "Deep Sea Oracle",
                OW, GUARDIAN, 50, 5000)
                .secret().hidden(PRISMARINE_CRYSTALS, 16, 2000).build());

        q.add(kill(302, "Embers of the Pit",   "Ember Sage",
                NT, WITHER_SKELETON, 30, 5000)
                .secret().hidden(Material.BLAZE_ROD, 16, 2000).build());

        q.add(kill(303, "Verdant Curse",        "Forest Keeper",
                OW, CAVE_SPIDER, 60, 5000)
                .secret().hidden(STRING, 64, 2000).build());

        q.add(kill(304, "Storm Wrath",          "Storm Caller",
                OW, PHANTOM, 40, 5000)
                .secret().hidden(Material.PHANTOM_MEMBRANE, 16, 2000).build());

        q.add(kill(305, "The Void Stirs",       "Void Scholar",
                EN, ENDERMAN, 100, 6000)
                .secret().hidden(Material.ENDER_PEARL, 32, 3000).build());

        q.add(collect(306, "Gold of the Sanctum", "Gold Baron",
                NT, GOLD_BLOCK, 5, 6000)
                .secret().hidden(NETHERITE_INGOT, 1, 3000).build());

        ALL = Collections.unmodifiableList(q);
    }

    private NpcQuestRegistry() {}

    /** All 300 quest definitions. */
    public static List<NpcQuestDef> all() { return ALL; }

    /** All 150 main quests (id 1–150). */
    public static List<NpcQuestDef> mainQuests() {
        return ALL.stream().filter(q -> !q.secret).toList();
    }

    /** All 150 secret quests (id 151–300). */
    public static List<NpcQuestDef> secretQuests() {
        return ALL.stream().filter(q -> q.secret).toList();
    }

    /** Look up a single quest by id, or null if not found. */
    public static NpcQuestDef byId(int id) {
        for (NpcQuestDef q : ALL) if (q.id == id) return q;
        return null;
    }
}
