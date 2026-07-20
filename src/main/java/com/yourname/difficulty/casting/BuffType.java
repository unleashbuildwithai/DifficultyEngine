package com.yourname.difficulty.casting;

/**
 * BuffType — All combo-cast buff outcomes.
 *
 * Each BuffType maps to a specific gameplay effect triggered by the
 * CastingEngine when a player's element queue matches a known combo.
 *
 * Duration and potency are applied in BuffLogic.
 */
public enum BuffType {

    // ── Offensive combos ──────────────────────────────────────────────────────
    /** FIRE + FIRE — Inferno Burst: sets nearby enemies on fire */
    INFERNO_BURST,

    /** WATER + FIRE — Steam Blast: blinds nearby enemies for 3s */
    STEAM_BLAST,

    /** FIRE + EARTH — Magma Trap: slows + burns target */
    MAGMA_TRAP,

    /** AIR + FIRE — Tornado Flame: launches the caster + fire nova */
    TORNADO_FLAME,

    // ── Defensive combos ─────────────────────────────────────────────────────
    /** WATER + WATER — Tidal Surge: slows all nearby mobs */
    TIDAL_SURGE,

    /** EARTH + EARTH — Stone Skin: Resistance II for 10s */
    STONE_SKIN,

    /** EARTH + WATER — Quicksand: root nearby mobs (Slowness IV) */
    QUICKSAND,

    /** AIR + WATER — Mist Veil: invisibility + speed for 6s */
    MIST_VEIL,

    // ── Elemental reaction combos ─────────────────────────────────────────────
    /** EARTH + AIR — Sandstorm: triggers the SandstormManager */
    SANDSTORM,

    /** AIR + AIR — Gale Force: launches all nearby mobs into the air */
    GALE_FORCE,

    /** WATER + EARTH — Mud Wall: instant barrier (cobblestone wall placed) */
    MUD_WALL,

    /** FIRE + WATER + EARTH + AIR — Grand Harmony: all four elements — heals + strength */
    GRAND_HARMONY,

    // ── Support combos ────────────────────────────────────────────────────────
    /** WATER + AIR — Cleanse: removes all negative potion effects */
    CLEANSE,

    /** FIRE + AIR — Blaze Dash: Speed IV for 4s */
    BLAZE_DASH,

    /** EARTH + FIRE — Fortify: Absorption II for 30s */
    FORTIFY;

    /** Human-readable colour-coded name. */
    public String displayName() {
        return switch (this) {
            case INFERNO_BURST  -> "§c🔥 Inferno Burst";
            case STEAM_BLAST    -> "§b💨 Steam Blast";
            case MAGMA_TRAP     -> "§6🌋 Magma Trap";
            case TORNADO_FLAME  -> "§e🌪 Tornado Flame";
            case TIDAL_SURGE    -> "§b🌊 Tidal Surge";
            case STONE_SKIN     -> "§2🪨 Stone Skin";
            case QUICKSAND      -> "§a🌿 Quicksand";
            case MIST_VEIL      -> "§f🌫 Mist Veil";
            case SANDSTORM      -> "§e⚡ Sandstorm";
            case GALE_FORCE     -> "§f🌀 Gale Force";
            case MUD_WALL       -> "§2🧱 Mud Wall";
            case GRAND_HARMONY  -> "§d✦ Grand Harmony";
            case CLEANSE        -> "§a✨ Cleanse";
            case BLAZE_DASH     -> "§c🔥 Blaze Dash";
            case FORTIFY        -> "§6🛡 Fortify";
        };
    }
}
