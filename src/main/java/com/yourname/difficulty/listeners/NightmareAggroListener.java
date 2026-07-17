package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * NightmareAggroListener — Threat-aggregation for NIGHTMARE players.
 *
 * When a mob targets a player who carries the NIGHTMARE PDC status tag:
 *   1. Nearby players within 15 blocks of the NIGHTMARE player are identified
 *      as "party members".
 *   2. The mob's target is redirected to a party member selected by a
 *      weighted-random draw based on each member's difficulty tier.
 *
 * Aggro-weight table (out of 1 000):
 *   PEACEFUL  →  125  (12.5%)
 *   EASY      →  125  (12.5%)
 *   MEDIUM    →  125  (12.5%)
 *   HARD      →  200  (20.0%)
 *   NIGHTMARE →  425  (42.5%)  ← "the rest"
 *
 * Performance guards — both must pass before any expensive scan runs:
 *   Guard 1: target player must carry the "nightmare_status" PDC tag.
 *   Guard 2: mob must be within 15 blocks of the target (distanceSquared check).
 *
 * The NIGHTMARE_status PDC tag is set/cleared by
 * {@link PlayerDifficultyManager#setDifficulty} whenever a player's tier changes.
 */
public class NightmareAggroListener implements Listener {

    /** Block radius used to collect party members around the NIGHTMARE player. */
    private static final double PARTY_RADIUS    = 15.0;
    private static final double PARTY_RADIUS_SQ = PARTY_RADIUS * PARTY_RADIUS;

    private final PlayerDifficultyManager manager;
    private final Random random = new Random();

    public NightmareAggroListener(PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    // ── Event handler ─────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {

        // ── Performance guard 1: monsters only, targeting players only ─────────
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        // ── Performance guard 2: target must be NIGHTMARE-tagged (PDC check) ───
        if (!manager.isNightmareTagged(target)) return;

        // ── Performance guard 3: mob must be within 15 blocks of the target ────
        if (mob.getLocation().distanceSquared(target.getLocation()) > PARTY_RADIUS_SQ) return;

        // ── Collect party members (other players within 15 blocks of target) ───
        List<Player> partyMembers = new ArrayList<>();
        for (Entity nearby : target.getNearbyEntities(PARTY_RADIUS, PARTY_RADIUS, PARTY_RADIUS)) {
            if (nearby instanceof Player partyMember && !partyMember.equals(target)) {
                partyMembers.add(partyMember);
            }
        }

        if (partyMembers.isEmpty()) return;

        // ── Weighted-random pick: higher tier = higher aggro share ─────────────
        Player chosen = weightedRandomPick(partyMembers);
        if (chosen != null) {
            event.setTarget(chosen);
        }
    }

    // ── Weighted selection ────────────────────────────────────────────────────

    /**
     * Picks a player from the candidate list using a weighted random draw.
     * Each candidate's weight is determined by their current difficulty tier.
     * If total weight somehow resolves to zero, falls back to uniform random.
     */
    private Player weightedRandomPick(List<Player> candidates) {
        int totalWeight = 0;
        int[] weights   = new int[candidates.size()];

        for (int i = 0; i < candidates.size(); i++) {
            int w       = aggroWeight(manager.getDifficulty(candidates.get(i).getUniqueId()));
            weights[i]  = w;
            totalWeight += w;
        }

        if (totalWeight == 0) {
            // Fallback: uniform random
            return candidates.get(random.nextInt(candidates.size()));
        }

        int roll       = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) return candidates.get(i);
        }

        // Should never reach here, but be safe
        return candidates.get(candidates.size() - 1);
    }

    /**
     * Maps a {@link DifficultyLevel} to its aggro weight (out of 1 000).
     *
     * PEACEFUL  →  125  (12.5%)
     * EASY      →  125  (12.5%)
     * MEDIUM    →  125  (12.5%)
     * HARD      →  200  (20.0%)
     * NIGHTMARE →  425  (42.5%)
     */
    private int aggroWeight(DifficultyLevel level) {
        return switch (level) {
            case PEACEFUL  -> 125;
            case EASY      -> 125;
            case MEDIUM    -> 125;
            case HARD      -> 200;
            case NIGHTMARE -> 425;
        };
    }
}
