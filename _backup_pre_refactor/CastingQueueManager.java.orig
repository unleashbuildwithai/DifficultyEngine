package com.yourname.difficulty.casting;

import com.yourname.difficulty.magic.MagicElement;

import java.util.*;

/**
 * CastingQueueManager — Tracks each player's recent element casts.
 *
 * Every time a player casts a spell with an elemental staff, the element
 * is pushed onto their personal queue (max 4 elements).
 *
 * When the queue is full or the player right-clicks with the SupportStaff,
 * the CastingEngine checks the queue against the combo recipe map.
 *
 * The queue auto-expires after 10 seconds of inactivity.
 */
public class CastingQueueManager {

    /** Maximum number of elements the queue holds before auto-triggering. */
    public static final int MAX_QUEUE_SIZE = 4;

    /** Time (ms) before a cast queue expires with no new additions. */
    private static final long QUEUE_EXPIRY_MS = 10_000L;

    /** Player UUID → ordered list of cast elements (most recent last). */
    private final Map<UUID, ArrayList<MagicElement>> queues = new HashMap<>();

    /** Player UUID → timestamp of last element cast (for expiry). */
    private final Map<UUID, Long> lastCastTime = new HashMap<>();

    // ── Queue modification ────────────────────────────────────────────────────

    /**
     * Adds an element to the player's casting queue.
     *
     * <p>If the queue already holds {@link #MAX_QUEUE_SIZE} elements,
     * the oldest element is dropped first (sliding window).
     *
     * @return the current state of the queue after addition
     */
    public List<MagicElement> addCast(UUID playerUuid, MagicElement element) {
        pruneExpired(playerUuid);

        ArrayList<MagicElement> queue =
                queues.computeIfAbsent(playerUuid, k -> new ArrayList<>());

        if (queue.size() >= MAX_QUEUE_SIZE) {
            queue.remove(0); // drop oldest
        }
        queue.add(element);
        lastCastTime.put(playerUuid, System.currentTimeMillis());
        return Collections.unmodifiableList(queue);
    }

    /**
     * Returns the current queue for a player (immutable view).
     * Returns an empty list if the queue has expired.
     */
    public List<MagicElement> getQueue(UUID playerUuid) {
        pruneExpired(playerUuid);
        ArrayList<MagicElement> queue = queues.get(playerUuid);
        return queue != null ? Collections.unmodifiableList(queue) : List.of();
    }

    /** Clears the queue for a player. */
    public void clearQueue(UUID playerUuid) {
        queues.remove(playerUuid);
        lastCastTime.remove(playerUuid);
    }

    /** Returns true if the player has at least one element in their queue. */
    public boolean hasQueue(UUID playerUuid) {
        pruneExpired(playerUuid);
        ArrayList<MagicElement> q = queues.get(playerUuid);
        return q != null && !q.isEmpty();
    }

    /**
     * Returns a string representation of the queue for use in combo matching.
     * Example: [FIRE, WATER] → "FIRE,WATER"
     */
    public String queueKey(UUID playerUuid) {
        List<MagicElement> q = getQueue(playerUuid);
        if (q.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < q.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(q.get(i).name());
        }
        return sb.toString();
    }

    // ── Expiry ────────────────────────────────────────────────────────────────

    private void pruneExpired(UUID playerUuid) {
        Long last = lastCastTime.get(playerUuid);
        if (last != null && System.currentTimeMillis() - last > QUEUE_EXPIRY_MS) {
            queues.remove(playerUuid);
            lastCastTime.remove(playerUuid);
        }
    }

    /** Call periodically (e.g. every 100 ticks) to clean up all expired queues. */
    public void cleanupAll() {
        long now = System.currentTimeMillis();
        lastCastTime.entrySet().removeIf(e -> now - e.getValue() > QUEUE_EXPIRY_MS);
        queues.keySet().removeIf(uuid -> !lastCastTime.containsKey(uuid));
    }
}
