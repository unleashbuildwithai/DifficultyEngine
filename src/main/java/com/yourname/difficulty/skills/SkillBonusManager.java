package com.yourname.difficulty.skills;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * SkillBonusManager â€” Pure-static formulas for all skill-based stat bonuses.
 *
 * â”€â”€ MELEE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Bonus damage     = level Ã— 0.02           (Lv99 â‰ˆ +1.98 dmg / ~1 heart)
 *  Crit chance      = level Ã— 0.3%           (Lv99 â‰ˆ 29.7%)
 *  Crit multiplier  = 1.5Ã—
 *
 * â”€â”€ RANGED â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Bonus damage     = level Ã— 0.015          (Lv99 â‰ˆ +1.49 dmg)
 *  Arrow effect     = base duration Ã— (1 + level/99)  (Lv99 â‰ˆ 2Ã— duration)
 *
 * â”€â”€ DEFENCE â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Dmg reduction    = level Ã— 0.2%           (Lv99 â‰ˆ 19.8% reduction)
 *  Extra HP         = floor(level/10) half-hearts, max 10 HP (5 hearts)
 *                     Applied via AttributeModifier on GENERIC_MAX_HEALTH.
 *
 * â”€â”€ FARMING â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Double-drop chance = (level/99)^1.5 Ã— 50%
 *    Lv 1  â‰ˆ  0.05%   Lv25 â‰ˆ  6.3%   Lv50 â‰ˆ 17.8%   Lv99 = 50%
 *
 * â”€â”€ WOODCUTTING â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Double-drop chance = (level/99)^1.5 Ã— 33%
 *    Lv 1  â‰ˆ  0.03%   Lv25 â‰ˆ  4.2%   Lv50 â‰ˆ 11.8%   Lv99 = 33%
 */
public final class SkillBonusManager {

    private SkillBonusManager() {}

    // â”€â”€ UUID for the defence HP attribute modifier â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private static final UUID   DEFENCE_HP_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String DEFENCE_HP_KEY  = "difficultyengine_defence_hp";

    // â”€â”€ Melee â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Extra flat damage added to each melee hit. */
    public static double meleeDamageBonus(int level) {
        return level * 0.02;
    }

    /** Probability (0â€“1) of a critical strike on this hit. */
    public static double meleeCritChance(int level) {
        return level * 0.003; // 0 â†’ 29.7%
    }

    /** Damage multiplier applied when a crit lands. */
    public static final double CRIT_MULTIPLIER = 1.5;

    // â”€â”€ Ranged â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Extra flat damage added to each arrow hit. */
    public static double rangedDamageBonus(int level) {
        return level * 0.015;
    }

    /**
     * Scale factor for tipped arrow potion effect durations.
     * Level 1 â†’ ~1.01Ã— | Level 99 â†’ 2.0Ã—
     */
    public static double arrowEffectScale(int level) {
        return 1.0 + (level / 99.0);
    }

    // â”€â”€ Defence â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Fraction of incoming damage to reduce.
     * Level 1 â†’ 0.2%  |  Level 99 â†’ 19.8%
     */
    public static double defenceDamageReduction(int level) {
        return level * 0.002;
    }

    /**
     * Extra max HP (in HP units, not hearts) from Defence skill.
     * One heart = 2 HP.
     * Every 10 levels = +1 HP (Â½ heart).  Max = 10 HP (5 hearts).
     */
    public static double defenceExtraHp(int level) {
        return Math.min(10.0, Math.floor(level / 10.0));
    }

    // â”€â”€ Farming â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Probability (0â€“1) of a double crop drop.
     * Quadratic-ish curve: (level/99)^1.5 Ã— 0.5
     * Level 1 â‰ˆ 0.05%   Level 50 â‰ˆ 17.8%   Level 99 = 50%
     */
    public static double farmingDoubleDropChance(int level) {
        if (level <= 0) return 0;
        double ratio = level / 99.0;
        return Math.pow(ratio, 1.5) * 0.50;
    }

    // â”€â”€ Woodcutting â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Probability (0â€“1) of a double log drop.
     * Level 1 â‰ˆ 0.03%   Level 50 â‰ˆ 11.8%   Level 99 = 33%
     */
    public static double woodcuttingDoubleDropChance(int level) {
        if (level <= 0) return 0;
        double ratio = level / 99.0;
        return Math.pow(ratio, 1.5) * 0.33;
    }

    // â”€â”€ Defence HP AttributeModifier â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Applies (or updates) the Defence bonus max-HP AttributeModifier for
     * the given player. Safe to call on every level-up and on join.
     */
    public static void applyDefenceHpBonus(Player player, int defenceLevel) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        // Remove any existing modifier from this plugin
        attr.getModifiers().stream()
            .filter(m -> DEFENCE_HP_UUID.equals(m.getUniqueId()))
            .forEach(attr::removeModifier);

        double bonus = defenceExtraHp(defenceLevel);
        if (bonus <= 0) return;

        AttributeModifier mod = new AttributeModifier(
            DEFENCE_HP_UUID,
            DEFENCE_HP_KEY,
            bonus,
            AttributeModifier.Operation.ADD_NUMBER
        );
        attr.addModifier(mod);

        // Ensure current HP doesn't exceed new max
        if (player.getHealth() > attr.getValue()) {
            player.setHealth(attr.getValue());
        }
    }

    /** Removes the Defence HP modifier (e.g. on plugin disable). */
    public static void removeDefenceHpBonus(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;
        attr.getModifiers().stream()
            .filter(m -> DEFENCE_HP_UUID.equals(m.getUniqueId()))
            .forEach(attr::removeModifier);
    }

    // â”€â”€ Prayer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Probability (0â€“1) of prayer blocking an incoming hit entirely.
     * Same curve as farming: (level/99)^1.5 Ã— 0.30
     * Level 1 â‰ˆ 0.03%  |  Level 50 â‰ˆ 10.6%  |  Level 99 = 30%
     */
    public static double prayerProtectionChance(int level) {
        if (level <= 0) return 0;
        double ratio = level / 99.0;
        return Math.pow(ratio, 1.5) * 0.30;
    }

    // â”€â”€ Magic â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Extra damage (hearts, not HP) for magic staff spells.
     *
     * Scales SMOOTHLY and continuously from level 1 to 99 (not in level-33
     * steps) so every single level-up feels like real, incremental progress:
     *
     *   bonus(level) = (level / 99) Ã— MAGIC_DAMAGE_MAX_BONUS
     *
     * Level 1  â‰ˆ +0.03 hearts   Level 50 â‰ˆ +1.52 hearts   Level 99 = +3.0 hearts
     */
    private static final double MAGIC_DAMAGE_MAX_BONUS = 3.0;

    public static double magicDamageBonus(int level) {
        if (level <= 0) return 0.0;
        return (Math.min(level, 99) / 99.0) * MAGIC_DAMAGE_MAX_BONUS;
    }


    // â”€â”€ OSRS Combat Level â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Calculates a RuneScape-style combat level using ALL combat stats.
     *
     * MELEE represents both Attack AND Strength combined (attack+strength = MELEE).
     * All five combat skills contribute to the final level, not just the dominant one.
     *
     * Formula:
     *   base      = 0.25 Ã— (Defence + floor(Prayer / 2))
     *   melee     = 0.65   Ã— Melee   (0.325 Ã— attack + 0.325 Ã— strength = 0.65 Ã— MELEE)
     *   ranged    = 0.4875 Ã— Ranged
     *   magic     = 0.4875 Ã— Magic
     *   dominant  = max(melee, ranged, magic)
     *   secondary = (melee + ranged + magic âˆ’ dominant) Ã— 0.15  â† non-dominant styles add 15 %
     *   combat    = min(99, floor(base + dominant + secondary))
     *
     * At all 99: 37 + 64.35 + (48.26+48.26)Ã—0.15 â‰ˆ 115 â†’ capped at 99.
     * At melee99 only: ~65 â€” specialised fighters still show strong combat level.
     */
    public static int getCombatLevel(int melee, int ranged, int defence, int prayer, int magic) {
        double base      = 0.25 * (defence + Math.floor(prayer / 2.0));
        double meleeC    = 0.65   * melee;   // Attack + Strength combined
        double rangedC   = 0.4875 * ranged;
        double magicC    = 0.4875 * magic;
        double dominant  = Math.max(meleeC, Math.max(rangedC, magicC));
        double secondary = (meleeC + rangedC + magicC - dominant) * 0.15;
        return Math.min(99, (int) Math.floor(base + dominant + secondary));
    }
}
