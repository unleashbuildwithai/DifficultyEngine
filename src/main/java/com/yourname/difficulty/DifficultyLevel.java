package com.yourname.difficulty;

/**
 * The five difficulty tiers a player can choose from.
 * Stats are applied to mobs that spawn near that player.
 */
public enum DifficultyLevel {

    //              tier  health  damage  speed   followRange  bonusSpawns
    PEACEFUL  (0,   0.75,  0.75,  1.00,   16.0,   false),
    EASY      (1,   1.00,  1.00,  1.00,   20.0,   false),
    MEDIUM    (2,   1.10,  1.08,  1.02,   26.0,   false),
    HARD      (3,   1.25,  1.15,  1.05,   32.0,   false),
    NIGHTMARE (4,   1.50,  1.25,  1.15,  128.0,   true);

    private final int    tier;
    private final double healthMult;
    private final double damageMult;
    private final double speedMult;
    private final double followRange;
    private final boolean bonusSpawns;

    DifficultyLevel(int tier, double health, double damage,
                    double speed, double followRange, boolean bonusSpawns) {
        this.tier        = tier;
        this.healthMult  = health;
        this.damageMult  = damage;
        this.speedMult   = speed;
        this.followRange = followRange;
        this.bonusSpawns = bonusSpawns;
    }

    public int     getTier()        { return tier; }
    public double  getHealthMult()  { return healthMult; }
    public double  getDamageMult()  { return damageMult; }
    public double  getSpeedMult()   { return speedMult; }
    public double  getFollowRange() { return followRange; }
    public boolean hasBonusSpawns() { return bonusSpawns; }

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
