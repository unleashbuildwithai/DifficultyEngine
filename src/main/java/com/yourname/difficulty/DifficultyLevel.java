package com.yourname.difficulty;

/**
 * The five difficulty tiers a player can choose from.
 * Stats are applied to mobs that spawn near that player.
 */
public enum DifficultyLevel {

    //              tier  health  damage  speed   followRange  bonusSpawns  monsterCap
    PEACEFUL  (0,   0.75,  0.75,  1.00,   16.0,   false,        10),
    EASY      (1,   1.00,  1.00,  1.00,   20.0,   false,        20),
    MEDIUM    (2,   1.10,  1.08,  1.02,   26.0,   false,        30),
    HARD      (3,   1.25,  1.15,  1.05,   32.0,   false,        40),
    NIGHTMARE (4,   1.50,  1.25,  1.15,  128.0,   true,         80);

    private final int    tier;
    private final double healthMult;
    private final double damageMult;
    private final double speedMult;
    private final double followRange;
    private final boolean bonusSpawns;
    /**
     * Maximum number of hostile mobs allowed to be alive near a single player
     * of this difficulty at once (used by MonsterCapListener). Starts at 10 for
     * Peaceful and climbs by 10 per tier, then doubles for Nightmare (40 -> 80).
     * When players are grouped in a party, each member's cap stacks additively
     * (see MonsterCapListener) so a full Nightmare party can be swarmed by far
     * more monsters than a solo player.
     */
    private final int monsterCap;

    DifficultyLevel(int tier, double health, double damage,
                    double speed, double followRange, boolean bonusSpawns, int monsterCap) {
        this.tier        = tier;
        this.healthMult  = health;
        this.damageMult  = damage;
        this.speedMult   = speed;
        this.followRange = followRange;
        this.bonusSpawns = bonusSpawns;
        this.monsterCap  = monsterCap;
    }

    public int     getTier()        { return tier; }
    public double  getHealthMult()  { return healthMult; }
    public double  getDamageMult()  { return damageMult; }
    public double  getSpeedMult()   { return speedMult; }
    public double  getFollowRange() { return followRange; }
    public boolean hasBonusSpawns() { return bonusSpawns; }
    /** Max concurrent hostile mobs allowed near a single player of this difficulty. */
    public int     getMonsterCap()  { return monsterCap; }


    /** Coloured display name shown in chat. */
    public String getDisplayName() {
        return switch (this) {
            case PEACEFUL  -> "§a☮ Peaceful";
            case EASY      -> "§2✦ Easy";
            case MEDIUM    -> "§e⚡ Medium";
            case HARD      -> "§c⚔ Hard";
            case NIGHTMARE -> "§4☠ Nightmare";
        };
    }

    /** Parse from a string (case-insensitive). Returns null if invalid. */
    public static DifficultyLevel fromString(String s) {
        return switch (s.toLowerCase()) {
            case "peaceful"            -> PEACEFUL;
            case "easy"                -> EASY;
            case "medium", "med", "normal" -> MEDIUM;
            case "hard"                -> HARD;
            case "nightmare", "nm"     -> NIGHTMARE;
            default                    -> null;
        };
    }
}
