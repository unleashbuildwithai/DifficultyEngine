package com.yourname.difficulty.boss.gilded;

import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared mutable state for the Gilded Enforcer boss encounter, extracted from
 * {@link GildedBossManager} so the tracking maps/sets can be passed to the
 * split-out helper classes ({@link GildedFuseFrenzy}, {@link GildedDisplaySync})
 * without duplicating bookkeeping.
 */
class GildedBossState {

    /** UUID of the Pillager -> UUID of its rider Creeper. */
    final Map<UUID, UUID> pillagerToCreeper = new HashMap<>();
    /** Set of all rider-Creeper UUIDs belonging to Gilded Enforcers (immortal + damage-immune). */
    final Set<UUID> gildedRiderCreepers = new HashSet<>();
    /** Set of all Gilded Enforcer Pillager UUIDs (explosion-immune). */
    final Set<UUID> gildedPillagers = new HashSet<>();

    /** Pillager UUID -> number of lightning-multiply rounds already used (0, 1, or 2). */
    final Map<UUID, Integer> multiplyRoundsUsed = new HashMap<>();
    /** Pillager UUIDs currently mid-frenzy (prevents overlapping multiply tasks). */
    final Set<UUID> currentlyMultiplying = new HashSet<>();
    /** Regular (mortal, non-boss) clone Creeper UUIDs spawned by the multiply frenzy. */
    final Set<UUID> multipliedClones = new HashSet<>();

    /** Pillager carrier UUID -> paired gilded_boss ItemDisplay UUID (position-synced every tick). */
    final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    /** Pillager carrier UUID -> current spin angle (radians), advanced each sync tick. */
    final Map<UUID, Double> spinAngles = new HashMap<>();

    final NamespacedKey displayTagKey;

    GildedBossState(NamespacedKey displayTagKey) {
        this.displayTagKey = displayTagKey;
    }

    void clear() {
        gildedPillagers.clear();
        gildedRiderCreepers.clear();
        pillagerToCreeper.clear();
        multiplyRoundsUsed.clear();
        currentlyMultiplying.clear();
        multipliedClones.clear();
    }
}
