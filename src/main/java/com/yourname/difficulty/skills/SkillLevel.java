package com.yourname.difficulty.skills;

/**
 * SkillLevel — Exact RuneScape XP table for levels 1–99.
 *
 * XP_TABLE[i] = total XP required to reach level (i+1).
 * So XP_TABLE[0] = 0  (level 1 starts at 0 XP)
 *    XP_TABLE[1] = 83 (level 2 requires 83 total XP)
 *    ...
 *    XP_TABLE[98] = 13,034,431 (level 99)
 */
public final class SkillLevel {

    private SkillLevel() {}

    /** Total XP required to reach each level (index 0 = level 1). */
    public static final long[] XP_TABLE = {
            0L,         // level 1
            83L,        // level 2
            174L,       // level 3
            276L,       // level 4
            388L,       // level 5
            512L,       // level 6
            650L,       // level 7
            801L,       // level 8
            969L,       // level 9
            1_154L,     // level 10
            1_358L,     // level 11
            1_584L,     // level 12
            1_833L,     // level 13
            2_107L,     // level 14
            2_411L,     // level 15
            2_746L,     // level 16
            3_115L,     // level 17
            3_523L,     // level 18
            3_973L,     // level 19
            4_470L,     // level 20
            5_018L,     // level 21
            5_624L,     // level 22
            6_291L,     // level 23
            7_028L,     // level 24
            7_842L,     // level 25
            8_740L,     // level 26
            9_730L,     // level 27
            10_824L,    // level 28
            12_031L,    // level 29
            13_363L,    // level 30
            14_833L,    // level 31
            16_456L,    // level 32
            18_247L,    // level 33
            20_224L,    // level 34
            22_406L,    // level 35
            24_815L,    // level 36
            27_473L,    // level 37
            30_408L,    // level 38
            33_648L,    // level 39
            37_224L,    // level 40
            41_171L,    // level 41
            45_529L,    // level 42
            50_339L,    // level 43
            55_649L,    // level 44
            61_512L,    // level 45
            67_983L,    // level 46
            75_127L,    // level 47
            83_014L,    // level 48
            91_721L,    // level 49
            101_333L,   // level 50
            111_945L,   // level 51
            123_660L,   // level 52
            136_594L,   // level 53
            150_872L,   // level 54
            166_636L,   // level 55
            184_040L,   // level 56
            203_254L,   // level 57
            224_466L,   // level 58
            247_886L,   // level 59
            273_742L,   // level 60
            302_288L,   // level 61
            333_804L,   // level 62
            368_599L,   // level 63
            407_015L,   // level 64
            449_428L,   // level 65
            496_254L,   // level 66
            547_953L,   // level 67
            605_032L,   // level 68
            668_051L,   // level 69
            737_627L,   // level 70
            814_445L,   // level 71
            899_257L,   // level 72
            992_895L,   // level 73
            1_096_278L, // level 74
            1_210_421L, // level 75
            1_336_443L, // level 76
            1_475_581L, // level 77
            1_629_200L, // level 78
            1_798_808L, // level 79
            1_986_068L, // level 80
            2_192_818L, // level 81
            2_421_087L, // level 82
            2_673_114L, // level 83
            2_951_373L, // level 84
            3_258_594L, // level 85
            3_597_792L, // level 86
            3_972_294L, // level 87
            4_385_776L, // level 88
            4_842_295L, // level 89
            5_346_332L, // level 90
            5_902_831L, // level 91
            6_517_253L, // level 92
            7_195_629L, // level 93
            7_944_614L, // level 94
            8_771_558L, // level 95
            9_684_577L, // level 96
            10_692_629L,// level 97
            11_805_606L,// level 98
            13_034_431L // level 99
    };

    /** Maximum level (99). */
    public static final int MAX_LEVEL = 99;

    /**
     * Returns the level (1–99) for a given total XP amount.
     * Uses a simple scan of the XP_TABLE — fast enough since it's only 99 entries.
     */
    public static int getLevelForXp(long xp) {
        int level = 1;
        for (int i = 1; i < XP_TABLE.length; i++) {
            if (xp >= XP_TABLE[i]) {
                level = i + 1;
            } else {
                break;
            }
        }
        return Math.min(level, MAX_LEVEL);
    }

    /**
     * Returns the total XP required to reach the given level (1–99).
     */
    public static long getXpForLevel(int level) {
        if (level <= 1) return 0L;
        if (level >= MAX_LEVEL) return XP_TABLE[XP_TABLE.length - 1];
        return XP_TABLE[level - 1];
    }

    /**
     * Returns XP needed to go from current xp to next level.
     * Returns 0 if already at max level.
     */
    public static long getXpToNextLevel(long currentXp) {
        int level = getLevelForXp(currentXp);
        if (level >= MAX_LEVEL) return 0L;
        return XP_TABLE[level] - currentXp;
    }

    /**
     * Returns a progress bar string (10 chars) showing progress to next level.
     * Example: "§a█████░░░░░"
     */
    public static String getProgressBar(long currentXp) {
        int  level      = getLevelForXp(currentXp);
        if (level >= MAX_LEVEL) return "§6██████████";

        long levelStart = XP_TABLE[level - 1];
        long levelEnd   = XP_TABLE[level];
        long range      = levelEnd - levelStart;
        long progress   = currentXp - levelStart;

        double pct    = range > 0 ? (double) progress / range : 0.0;
        int    filled = (int) Math.round(pct * 10);
        int    empty  = 10 - filled;

        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(empty);
    }

    /**
     * Returns a title/rank string for the given level (just like RS capes).
     */
    public static String getRank(int level) {
        if (level >= 99) return "§6✦ Master";
        if (level >= 85) return "§dGrandmaster";
        if (level >= 70) return "§5Expert";
        if (level >= 50) return "§9Journeyman";
        if (level >= 30) return "§aApprentice";
        if (level >= 10) return "§7Novice";
        return "§8Beginner";
    }
}
