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

    /** Maximum empty bottles that can be stored per catching block. */
    public static final int MAX_BOTTLES = 8;

    /**
     * Normalised Location (block coords) → count of empty magic bottles stored.
     * Normalisation strips sub-block precision, yaw and pitch so that block
     * comparisons from different event contexts always match.
     */
    private final Map<Location, Integer> storedBottles = new HashMap<>();

    // ── Block registration ────────────────────────────────────────────────────

    /** Registers a placed Catching Block location (starts with 0 bottles). */
    public void register(Location loc) {
        storedBottles.putIfAbsent(norm(loc), 0);
    }

    /** Removes the catching block entry (call after the block is broken). */
    public void unregister(Location loc) {
        storedBottles.remove(norm(loc));
    }

    /** Returns {@code true} if this block location is a tracked Catching Block. */
    public boolean isTracked(Location loc) {
        return storedBottles.containsKey(norm(loc));
    }

    /** An unmodifiable view of all currently tracked catching block locations. */
    public Set<Location> getTrackedLocations() {
        return Collections.unmodifiableSet(storedBottles.keySet());
    }

    // ── Bottle operations ─────────────────────────────────────────────────────

    /** Returns how many empty bottles are stored at this location (0 if not tracked). */
    public int getBottleCount(Location loc) {
        return storedBottles.getOrDefault(norm(loc), 0);
    }

    /**
     * Deposits one Empty Magic Bottle into the catching block.
     *
     * @return {@code true} if accepted; {@code false} if full ({@value #MAX_BOTTLES} bottles)
     *         or if the location is not a tracked Catching Block.
     */
    public boolean depositBottle(Location loc) {
        Location key = norm(loc);
        if (!storedBottles.containsKey(key)) return false;
        int current = storedBottles.get(key);
        if (current >= MAX_BOTTLES) return false;
        storedBottles.put(key, current + 1);
        return true;
    }

    /**
     * Consumes one empty bottle from the catching block (lightning charge event).
     *
     * @return {@code true} if a bottle was consumed and a Charged Bottle should
     *         be produced; {@code false} if no bottles are stored.
     */
    public boolean consumeBottleForCharge(Location loc) {
        Location key = norm(loc);
        Integer count = storedBottles.get(key);
        if (count == null || count <= 0) return false;
        storedBottles.put(key, count - 1);
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
