package com.yourname.difficulty;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.Random;

/**
 * DifficultyEngine — The Brain
 *
 * Two responsibilities:
 *  1. onCreatureSpawn  — Scale mob stats based on the highest difficulty
 *                        player within 64 blocks.
 *  2. onEntityTarget   — Redirect mob aggro toward Nightmare players (35%
 *                        chance) and protect Peaceful players from targeting.
 */
public class DifficultyEngine implements Listener {

    /** Radius to scan for players when a mob spawns. */
    private static final double SPAWN_CHECK_RADIUS = 64.0;
    /** Radius to scan when redirecting mob aggro. */
    private static final double AGGRO_CHECK_RADIUS = 32.0;
    /**
     * % chance a mob re-targets the nearest Nightmare player instead of its
     * current target (only fires when the current target is NOT Nightmare).
     */
    private static final int NIGHTMARE_AGGRO_CHANCE = 35;

    private final PlayerDifficultyManager manager;
    private final Random random = new Random();

    public DifficultyEngine(Main plugin, PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    // -------------------------------------------------------------------------
    // Spawn scaling
    // -------------------------------------------------------------------------

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;

        // Find the toughest nearby player difficulty
        DifficultyLevel level = getHighestNearbyDifficulty(mob, SPAWN_CHECK_RADIUS);

        // EASY is vanilla — no changes. PEACEFUL doesn't buff mobs either.
        if (level.getTier() <= DifficultyLevel.EASY.getTier()) return;

        applyStats(mob, level);
    }

    /** Applies all attribute buffs from the given difficulty tier to the mob. */
    private void applyStats(LivingEntity mob, DifficultyLevel level) {

        // Max health
        AttributeInstance maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            double scaled = maxHp.getBaseValue() * level.getHealthMult();
            maxHp.setBaseValue(scaled);
            mob.setHealth(scaled);
        }

        // Attack damage
        AttributeInstance atk = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * level.getDamageMult());
        }

        // Movement speed
        AttributeInstance spd = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(spd.getBaseValue() * level.getSpeedMult());
        }

        // Follow range (how far the mob tracks before giving up)
        AttributeInstance follow = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(level.getFollowRange());
        }
    }

    // -------------------------------------------------------------------------
    // Aggro management
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        DifficultyLevel targetDiff    = manager.getDifficulty(target.getUniqueId());
        DifficultyLevel highestNearby = getHighestNearbyDifficulty(mob, AGGRO_CHECK_RADIUS);

        // ── Peaceful protection ──────────────────────────────────────────────
        // A peaceful player won't be targeted UNLESS there is a Nightmare player
        // nearby (Nightmare players absorb all aggro in mixed groups).
        if (targetDiff == DifficultyLevel.PEACEFUL
                && highestNearby != DifficultyLevel.NIGHTMARE) {
            event.setCancelled(true);
            return;
        }

        // ── Nightmare aggro preference ────────────────────────────────────────
        // If the current target is NOT already a Nightmare player, there is a
        // 35% chance the mob will re-lock onto the nearest Nightmare player.
        if (targetDiff != DifficultyLevel.NIGHTMARE
                && random.nextInt(100) < NIGHTMARE_AGGRO_CHANCE) {
            Player nightmareTarget = getNearestNightmarePlayer(mob, AGGRO_CHECK_RADIUS);
            if (nightmareTarget != null && !nightmareTarget.equals(target)) {
                event.setTarget(nightmareTarget);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the highest DifficultyLevel among all players within
     * {@code radius} blocks of the given mob. Defaults to EASY.
     */
    DifficultyLevel getHighestNearbyDifficulty(LivingEntity mob, double radius) {
        DifficultyLevel highest = DifficultyLevel.EASY;
        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            DifficultyLevel d = manager.getDifficulty(p.getUniqueId());
            if (d.getTier() > highest.getTier()) {
                highest = d;
            }
        }
        return highest;
    }

    /**
     * Returns the closest online Nightmare player within {@code radius} blocks
     * of the mob, or {@code null} if none exists.
     */
    private Player getNearestNightmarePlayer(LivingEntity mob, double radius) {
        Player nearest = null;
        double closestSq = Double.MAX_VALUE;
        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            if (manager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            double distSq = p.getLocation().distanceSquared(mob.getLocation());
            if (distSq < closestSq) {
                closestSq = distSq;
                nearest   = p;
            }
        }
        return nearest;
    }
}
