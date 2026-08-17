package net.yourserver.coreengine.market;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Per-player {@link ReentrantLock} registry used to serialize all market
 * mutations touching a player's money or inventory.
 * <p>
 * Deadlock-safety: whenever a transaction involves two players (buyer and
 * seller), locks are always acquired through {@link #lockAll(UUID...)}
 * which sorts the UUIDs first and locks them in that deterministic order,
 * so two cross-transactions can never wait on each other's locks in a cycle.
 * <p>
 * All Module 1 operations currently run on the Bukkit main thread, so in
 * practice these locks are a defense-in-depth guarantee for any future
 * async refactor (and for any async DB helper threads that touch balances).
 */
public class MarketLockRegistry {

    private final ConcurrentHashMap<UUID, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ReentrantLock getLock(UUID uuid) {
        return locks.computeIfAbsent(uuid, k -> new ReentrantLock());
    }

    /** Locks a single player's lock (blocks). */
    public void lock(UUID uuid) {
        getLock(uuid).lock();
    }

    /** Unlocks a single player's lock. */
    public void unlock(UUID uuid) {
        getLock(uuid).unlock();
    }

    /**
     * Locks the given players' locks in deterministic (sorted-UUID) order,
     * guaranteeing deadlock-free acquisition for multi-party transactions.
     * Release with {@link #unlockAll(UUID...)} in reverse order.
     */
    public void lockAll(UUID... uuids) {
        List<UUID> sorted = new ArrayList<>(Arrays.asList(uuids));
        sorted.sort(Comparator.comparing(UUID::toString));
        for (UUID uuid : sorted) {
            lock(uuid);
        }
    }

    /** Releases the given players' locks in reverse acquisition order. */
    public void unlockAll(UUID... uuids) {
        List<UUID> sorted = new ArrayList<>(Arrays.asList(uuids));
        sorted.sort(Comparator.comparing(UUID::toString).reversed());
        for (UUID uuid : sorted) {
            unlock(uuid);
        }
    }

    /** Drops a player's lock entry entirely (called on disconnect cleanup). */
    public void remove(UUID uuid) {
        locks.remove(uuid);
    }
}
