package com.yourname.difficulty.boss;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EffectRegistry — Central store for all active buffs and debuffs.
 *
 * Buffs and debuffs are stored independently of the entities that applied them.
 * This means a "Leached" effect persists even if:
 *   • The boss moves away from the player
 *   • The player changes worlds
 *   • The boss dies mid-effect
 *
 * Storage: UUID (player) → Map<EffectType, Long (expiry ms)>
 *
 * Usage:
 *   registry.apply(playerUuid, EffectType.LEACHED, durationTicks);
 *   registry.has(playerUuid, EffectType.LEACHED);
 *   registry.remove(playerUuid, EffectType.LEACHED);
 *   registry.tickCleanup(); // call once per tick to remove expired effects
 *
 * Boss vulnerability uses entity UUID (not player), stored in a separate map.
 */
public class EffectRegistry {

    /** Player UUID → (EffectType → expiry timestamp in milliseconds) */
    private final Map<UUID, Map<EffectType, Long>> playerEffects = new ConcurrentHashMap<>();

    /** Boss/entity UUID → vulnerable until timestamp */
    private final Map<UUID, Long> vulnerableEntities = new ConcurrentHashMap<>();

    /** Boss/entity UUID → Shriek ArmorStand UUID */
    private final Map<UUID, UUID> shriekStands = new ConcurrentHashMap<>();

    // ── Player effect API ─────────────────────────────────────────────────────

    /**
     * Applies an effect to a player for the given duration.
     *
     * @param playerUuid  the target player
     * @param type        the effect to apply
     * @param durationMs  duration in milliseconds (use {@link #ticks(int)} helper)
     */
    public void apply(UUID playerUuid, EffectType type, long durationMs) {
        long expiry = System.currentTimeMillis() + durationMs;
        playerEffects
                .computeIfAbsent(playerUuid, k -> new EnumMap<>(EffectType.class))
                .put(type, expiry);
    }

    /**
     * Applies an effect using tick-based duration (20 ticks = 1 second).
     */
    public void applyTicks(UUID playerUuid, EffectType type, int ticks) {
        apply(playerUuid, type, (long) ticks * 50L); // 50ms per tick
    }

    /** Returns true if the player has the given effect and it hasn't expired. */
    public boolean has(UUID playerUuid, EffectType type) {
        Map<EffectType, Long> effects = playerEffects.get(playerUuid);
        if (effects == null) return false;
        Long expiry = effects.get(type);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            effects.remove(type);
            return false;
        }
        return true;
    }

    /** Removes an effect from a player immediately. */
    public void remove(UUID playerUuid, EffectType type) {
        Map<EffectType, Long> effects = playerEffects.get(playerUuid);
        if (effects != null) effects.remove(type);
    }

    /** Removes ALL effects from a player. */
    public void clearAll(UUID playerUuid) {
        playerEffects.remove(playerUuid);
    }

    /** Returns how many milliseconds remain on an effect (0 if not active). */
    public long getRemainingMs(UUID playerUuid, EffectType type) {
        Map<EffectType, Long> effects = playerEffects.get(playerUuid);
        if (effects == null) return 0;
        Long expiry = effects.get(type);
        if (expiry == null) return 0;
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    // ── Boss vulnerability API ────────────────────────────────────────────────

    /** Marks a boss entity as vulnerable for the given duration. */
    public void setVulnerable(UUID bossUuid, long durationMs) {
        vulnerableEntities.put(bossUuid, System.currentTimeMillis() + durationMs);
    }

    /** Returns true if the boss entity is currently vulnerable. */
    public boolean isVulnerable(UUID bossUuid) {
        Long expiry = vulnerableEntities.get(bossUuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            vulnerableEntities.remove(bossUuid);
            return false;
        }
        return true;
    }

    /** Removes vulnerability from a boss. */
    public void clearVulnerable(UUID bossUuid) {
        vulnerableEntities.remove(bossUuid);
    }

    // ── Shriek ArmorStand tracking ────────────────────────────────────────────

    /** Associates a Shriek distortion ArmorStand with a boss entity. */
    public void registerShriek(UUID bossUuid, UUID armorStandUuid) {
        shriekStands.put(bossUuid, armorStandUuid);
    }

    /** Returns the Shriek ArmorStand UUID for a boss, or null if none. */
    public UUID getShriekStand(UUID bossUuid) {
        return shriekStands.get(bossUuid);
    }

    /** Removes the Shriek ArmorStand registration for a boss. */
    public void clearShriek(UUID bossUuid) {
        shriekStands.remove(bossUuid);
    }

    /** Returns true if the boss has an active Shriek distortion. */
    public boolean hasShriek(UUID bossUuid) {
        return shriekStands.containsKey(bossUuid);
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    /**
     * Prunes all expired entries. Call this periodically (e.g. every 20 ticks)
     * to prevent memory leaks from players who logged out with effects active.
     */
    public void tickCleanup() {
        long now = System.currentTimeMillis();

        playerEffects.values().forEach(effects ->
                effects.entrySet().removeIf(e -> now > e.getValue()));
        playerEffects.entrySet().removeIf(e -> e.getValue().isEmpty());

        vulnerableEntities.entrySet().removeIf(e -> now > e.getValue());
    }

    // ── Utility helper ────────────────────────────────────────────────────────

    /** Converts seconds to milliseconds. */
    public static long seconds(int s) { return (long) s * 1000L; }

    /** Converts ticks to milliseconds. */
    public static long ticks(int t) { return (long) t * 50L; }
}
