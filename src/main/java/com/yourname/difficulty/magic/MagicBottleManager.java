package com.yourname.difficulty.magic;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * MagicBottleManager — Tracks placed Catching Blocks and their stored Empty Magic Bottles.
 *
 * ── Workflow ──────────────────────────────────────────────────────────────────
 *  1. Player places a Catching Block item (LODESTONE + PDC tag).
 *     → {@link #register(Location)} called from CatchingBlockListener.
 *
 *  2. Player right-clicks the block holding an Empty Magic Bottle.
 *     → {@link #depositBottle(Location)} stores one bottle (up to MAX_BOTTLES).
 *
 *  3. Lightning rod within 5 blocks is struck while it is raining.
 *     → {@link #consumeBottleForCharge(Location)} removes one empty bottle.
 *     → Caller gives the player a Charged Magic Bottle (4 casts).
 *
 *  4. Player breaks the Catching Block.
 *     → {@link #getBottleCount(Location)} used to drop stored bottles.
 *     → {@link #unregister(Location)} cleans up the entry.
 */
public class MagicBottleManager {

    public static class CatchingBlockState {
        public int emptyBottles = 0;
        public int fullBottles = 0;
    }

    /** Maximum empty bottles that can be stored per catching block. */
    public static final int MAX_BOTTLES = 4; // Slots 0-3 for empty, slots 5-8 for full

    /**
     * Normalised Location (block coords) → CatchingBlockState.
     */
    private final Map<Location, CatchingBlockState> blocks = new HashMap<>();

    // ── Block registration ────────────────────────────────────────────────────

    /** Registers a placed Catching Block location. */
    public void register(Location loc) {
        blocks.putIfAbsent(norm(loc), new CatchingBlockState());
    }

    /** Removes the catching block entry. */
    public void unregister(Location loc) {
        blocks.remove(norm(loc));
    }

    /** Returns {@code true} if this block location is a tracked Catching Block. */
    public boolean isTracked(Location loc) {
        return blocks.containsKey(norm(loc));
    }

    public CatchingBlockState getState(Location loc) {
        return blocks.get(norm(loc));
    }

    public Set<Location> getTrackedLocations() {
        return Collections.unmodifiableSet(blocks.keySet());
    }

    // ── Bottle operations ─────────────────────────────────────────────────────

    public int getBottleCount(Location loc) {
        CatchingBlockState state = getState(loc);
        return state == null ? 0 : state.emptyBottles;
    }
    
    public int getFullBottleCount(Location loc) {
        CatchingBlockState state = getState(loc);
        return state == null ? 0 : state.fullBottles;
    }

    public boolean depositBottle(Location loc) {
        CatchingBlockState state = getState(loc);
        if (state == null) return false;
        if (state.emptyBottles >= MAX_BOTTLES) return false;
        state.emptyBottles++;
        return true;
    }

    public boolean consumeBottleForCharge(Location loc) {
        CatchingBlockState state = getState(loc);
        if (state == null || state.emptyBottles <= 0) return false;
        if (state.fullBottles >= MAX_BOTTLES) return false; // Full bottles output full!
        state.emptyBottles--;
        state.fullBottles++;
        return true;
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Normalises a Location to integer block coordinates so that comparisons
     * from BlockPlaceEvent, PlayerInteractEvent and LightningStrikeEvent all agree.
     */
    private static Location norm(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
