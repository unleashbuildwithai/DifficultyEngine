package com.yourname.difficulty.skills;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * SkillBonusManager — Pure-static formulas for all skill-based stat bonuses.
 *
 * ── MELEE ──────────────────────────────────────────────────────────────────
 *  Bonus damage     = level × 0.02           (Lv99 ≈ +1.98 dmg / ~1 heart)
 *  Crit chance      = level × 0.3%           (Lv99 ≈ 29.7%)
 *  Crit multiplier  = 1.5×
 *
 * ── RANGED ─────────────────────────────────────────────────────────────────
 *  Bonus damage     = level × 0.015          (Lv99 ≈ +1.49 dmg)
 *  Arrow effect     = base duration × (1 + level/99)  (Lv99 ≈ 2× duration)
 *
 * ── DEFENCE ────────────────────────────────────────────────────────────────
 *  Dmg reduction    = level × 0.2%           (Lv99 ≈ 19.8% reduction)
 *  Extra HP         = floor(level/10) half-hearts, max 10 HP (5 hearts)
 *                     Applied via AttributeModifier on GENERIC_MAX_HEALTH.
 *
 * ── FARMING ────────────────────────────────────────────────────────────────
 *  Double-drop chance = (level/99)^1.5 × 50%
 *    Lv 1  ≈  0.05%   Lv25 ≈  6.3%   Lv50 ≈ 17.8%   Lv99 = 50%
 *
 * ── WOODCUTTING ────────────────────────────────────────────────────────────
 *  Double-drop chance = (level/99)^1.5 × 33%
 *    Lv 1  ≈  0.03%   Lv25 ≈  4.2%   Lv50 ≈ 11.8%   Lv99 = 33%
 */
public final class SkillBonusManager {

    private SkillBonusManager() {}

    // ── UUID for the defence HP attribute modifier ────────────────────────────
    private static final UUID   DEFENCE_HP_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String DEFENCE_HP_KEY  = "difficultyengine_defence_hp";

    // ── Melee ─────────────────────────────────────────────────────────────────

    /** Extra flat damage added to each melee hit. */
    public static double meleeDamageBonus(int level) {
        return level * 0.02;
    }

    /** Probability (0–1) of a critical strike on this hit. */
    public static double meleeCritChance(int level) {
        return level * 0.003; // 0 → 29.7%
    }

    /** Damage multiplier applied when a crit lands. */
    public static final double CRIT_MULTIPLIER = 1.5;

    // ── Ranged ────────────────────────────────────────────────────────────────

    /** Extra flat damage added to each arrow hit. */
    public static double rangedDamageBonus(int level) {
        return level * 0.015;
    }

    /**
     * Scale factor for tipped arrow potion effect durations.
     * Level 1 → ~1.01× | Level 99 → 2.0×
     */
    public static double arrowEffectScale(int level) {
        return 1.0 + (level / 99.0);
    }

    // ── Defence ───────────────────────────────────────────────────────────────

    /**
     * Fraction of incoming damage to reduce.
     * Level 1 → 0.2%  |  Level 99 → 19.8%
     */
    public static double defenceDamageReduction(int level) {
        return level * 0.002;
    }

    /**
     * Extra max HP (in HP units, not hearts) from Defence skill.
     * One heart = 2 HP.
     * Every 10 levels = +1 HP (½ heart).  Max = 10 HP (5 hearts).
     */
    public static double defenceExtraHp(int level) {
        return Math.min(10.0, Math.floor(level / 10.0));
    }

    // ── Farming ───────────────────────────────────────────────────────────────

    /**
     * Probability (0–1) of a double crop drop.
     * Quadratic-ish curve: (level/99)^1.5 × 0.5
     * Level 1 ≈ 0.05%   Level 50 ≈ 17.8%   Level 99 = 50%
     */
    public static double farmingDoubleDropChance(int level) {
        if (level <= 0) return 0;
        double ratio = level / 99.0;
        return Math.pow(ratio, 1.5) * 0.50;
    }

    // ── Woodcutting ───────────────────────────────────────────────────────────

    /**
     * Probability (0–1) of a double log drop.
     * Level 1 ≈ 0.03%   Level 50 ≈ 11.8%   Level 99 = 33%
     */
    public static double woodcuttingDoubleDropChance(int level) {
        if (level <= 0) return 0;
        double ratio = level / 99.0;
        return Math.pow(ratio, 1.5) * 0.33;
    }

    // ── Defence HP AttributeModifier ─────────────────────────────────────────

    /**
     * Applies (or updates) the Defence bonus max-HP AttributeModifier for
     * the given player. Safe to call on every level-up and on join.
     */
    public static void applyDefenceHpBonus(Player player, int defenceLevel) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
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
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        attr.getModifiers().stream()
            .filter(m -> DEFENCE_HP_UUID.equals(m.getUniqueId()))
            .forEach(attr::removeModifier);
    }
}
