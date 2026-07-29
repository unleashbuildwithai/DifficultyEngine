package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Particle;

/**
 * Static per-skill symbol shape/colour/ambient-particle lookup tables used by
 * {@link CapeVisualTask} to render each cape's attribute symbol. Extracted
 * purely to keep {@code CapeVisualTask} under the 400-line limit — holds no
 * state/logic of its own.
 */
final class CapeSymbolShapes {

    private CapeSymbolShapes() {}

    /**
     * Returns an array of {rightOffset, upOffset} pairs (in blocks) that
     * together trace the outline of each skill's attribute symbol.
     * Centre (0,0) is at the cape surface; scale is roughly ±0.5 blocks.
     */
    static double[][] getSymbolShape(SkillType skill) {
        return switch (skill) {

            // ─── MELEE — crossed sword ────────────────────────────────────
            case MELEE -> new double[][] {
                { 0.00,  0.52},   // blade tip
                { 0.00,  0.37},   // blade upper
                { 0.00,  0.22},   // blade mid
                {-0.22,  0.04},   // crossguard left
                {-0.11,  0.04},   // crossguard inner-left
                { 0.00,  0.04},   // crossguard centre
                { 0.11,  0.04},   // crossguard inner-right
                { 0.22,  0.04},   // crossguard right
                { 0.00, -0.12},   // grip upper
                { 0.00, -0.28},   // grip lower
                { 0.00, -0.44},   // pommel
            };

            // ─── DEFENCE — kite shield ────────────────────────────────────
            case DEFENCE -> new double[][] {
                {-0.10,  0.48},   // top-left
                { 0.00,  0.52},   // top-centre
                { 0.10,  0.48},   // top-right
                {-0.27,  0.28},   // left upper
                { 0.27,  0.28},   // right upper
                {-0.30,  0.06},   // left mid
                { 0.30,  0.06},   // right mid
                {-0.27, -0.14},   // left lower
                { 0.27, -0.14},   // right lower
                {-0.14, -0.34},   // bottom-left
                { 0.14, -0.34},   // bottom-right
                { 0.00, -0.52},   // bottom point
            };

            // ─── RANGED — bow + arrow ─────────────────────────────────────
            case RANGED -> new double[][] {
                // Bow limbs (left arc)
                {-0.30,  0.42},   // bow top
                {-0.36,  0.22},   // bow upper curve
                {-0.38,  0.00},   // bow centre (limb)
                {-0.36, -0.22},   // bow lower curve
                {-0.30, -0.42},   // bow bottom
                // Bowstring (diagonal segments)
                {-0.24,  0.32},   // string top
                {-0.12,  0.12},   // string upper
                { 0.00,  0.00},   // nock
                {-0.12, -0.12},   // string lower
                {-0.24, -0.32},   // string bottom
                // Arrow shaft (rightward)
                { 0.12,  0.00},
                { 0.24,  0.00},
                { 0.36,  0.00},
                // Arrowhead
                { 0.46,  0.10},
                { 0.46, -0.10},
                { 0.54,  0.00},   // tip
            };

            // ─── FARMING — minecart ───────────────────────────────────────
            case FARMING -> new double[][] {
                // Cart body (flat-bed rectangle)
                {-0.24,  0.26},   // top-left
                {-0.08,  0.26},   // top mid-left
                { 0.08,  0.26},   // top mid-right
                { 0.24,  0.26},   // top-right
                {-0.24,  0.08},   // body bottom-left
                { 0.24,  0.08},   // body bottom-right
                {-0.24,  0.17},   // left side
                { 0.24,  0.17},   // right side
                // Axle bar
                {-0.12, -0.02},
                { 0.00, -0.02},
                { 0.12, -0.02},
                // Left wheel
                {-0.20, -0.10},   // wheel top
                {-0.26, -0.20},   // wheel outer
                {-0.18, -0.28},   // wheel bottom-outer
                {-0.10, -0.22},   // wheel inner
                // Right wheel
                { 0.20, -0.10},
                { 0.26, -0.20},
                { 0.18, -0.28},
                { 0.10, -0.22},
            };

            // ─── PRAYER — latin cross ─────────────────────────────────────
            case PRAYER -> new double[][] {
                { 0.00,  0.52},   // top
                { 0.00,  0.36},   // upper shaft
                { 0.00,  0.22},   // crossbar row
                {-0.28,  0.22},   // left arm
                {-0.14,  0.22},   // inner-left
                { 0.14,  0.22},   // inner-right
                { 0.28,  0.22},   // right arm
                { 0.00,  0.07},   // lower shaft upper
                { 0.00, -0.10},   // lower shaft mid
                { 0.00, -0.26},   // lower shaft lower
                { 0.00, -0.42},   // base
            };

            // ─── MAGIC — six-pointed star (Star of David) ─────────────────
            case MAGIC -> new double[][] {
                // Outer points
                { 0.00,  0.52},   // top
                { 0.26,  0.14},   // upper-right
                { 0.38, -0.20},   // lower-right
                { 0.00, -0.38},   // bottom
                {-0.38, -0.20},   // lower-left
                {-0.26,  0.14},   // upper-left
                // Inner hexagon ring
                { 0.00,  0.24},   // inner-top
                { 0.20,  0.06},   // inner-upper-right
                { 0.20, -0.16},   // inner-lower-right
                { 0.00, -0.22},   // inner-bottom
                {-0.20, -0.16},   // inner-lower-left
                {-0.20,  0.06},   // inner-upper-left
            };

            // ─── WOODCUTTING — axe ────────────────────────────────────────
            case WOODCUTTING -> new double[][] {
                // Handle (vertical)
                { 0.04,  0.48},   // top
                { 0.04,  0.32},
                { 0.04,  0.16},
                { 0.04,  0.00},
                { 0.04, -0.18},   // handle base
                // Axe head (upper-right)
                { 0.22,  0.48},   // blade top-left
                { 0.36,  0.38},   // blade top arc
                { 0.42,  0.22},   // blade outer upper
                { 0.42,  0.06},   // blade outer lower
                { 0.34, -0.06},   // blade lower arc
                { 0.18,  0.02},   // blade inner lower
                // Blade cutting edge (rightmost vertical)
                { 0.44,  0.34},
                { 0.44,  0.14},
            };

            default -> new double[][]{};
        };
    }

    /** Primary DUST colour for each skill cape's symbol. */
    static Color getSkillColor(SkillType skill) {
        return switch (skill) {
            case MELEE       -> Color.fromRGB(220,  40,  40);   // crimson
            case RANGED      -> Color.fromRGB( 40, 200,  80);   // lime-green
            case DEFENCE     -> Color.fromRGB( 60, 120, 255);   // royal-blue
            case PRAYER      -> Color.fromRGB(240, 220, 180);   // warm white/gold
            case WOODCUTTING -> Color.fromRGB( 80, 160,  50);   // forest-green
            case FARMING     -> Color.fromRGB(180, 110,  40);   // harvest-gold
            default          -> Color.fromRGB(200, 200, 200);
        };
    }

    /** Secondary ambient particle that fills the space around the symbol. */
    static Particle getAmbientParticle(SkillType skill) {
        return switch (skill) {
            case MELEE       -> Particle.CRIT;
            case RANGED      -> Particle.ENCHANTED_HIT;
            case DEFENCE     -> Particle.END_ROD;
            case PRAYER      -> Particle.ENCHANT;
            case FARMING     -> Particle.COMPOSTER;
            case WOODCUTTING -> Particle.HAPPY_VILLAGER;
            default          -> null;  // MAGIC uses pure rainbow dust
        };
    }
}
