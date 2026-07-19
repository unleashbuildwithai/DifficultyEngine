package com.yourname.difficulty.party;

import org.bukkit.entity.Player;

import java.util.*;

/**
 * PartyManager — tracks parties and pending invites.
 *
 * Party structure: leader UUID → set of all member UUIDs (includes leader).
 * memberIndex: member UUID → leader UUID for O(1) party lookup.
 *
 * Rolling DPS tracker:
 *   dpsLog: player UUID → deque of [timestampMs, damageX100] entries.
 *   Every entry older than 25 seconds is pruned before summing.
 */
public class PartyManager {

    /** leader UUID → all member UUIDs (includes leader) */
    private final Map<UUID, Set<UUID>>  parties     = new HashMap<>();
    /** member UUID → leader UUID */
    private final Map<UUID, UUID>       memberIndex = new HashMap<>();
    /** invited UUID → inviter UUID */
    private final Map<UUID, UUID>       invites     = new HashMap<>();

    /** Rolling damage log: player UUID → deque of [timestampMs, damage*100] */
    private final Map<UUID, ArrayDeque<long[]>> dpsLog = new HashMap<>();

    /**
     * UUIDs of members who are currently offline but still in a party.
     * They remain in the party data and will be welcomed back on rejoin.
     */
    private final Set<UUID> offlineMembers = new HashSet<>();

    private static final long WINDOW_MS = 25_000L; // 25-second rolling window

    // ── Party creation / joining ──────────────────────────────────────────────

    /** Creates a new 1-player party led by the given player. */
    public void createParty(UUID leader) {
        Set<UUID> members = new HashSet<>();
        members.add(leader);
        parties.put(leader, members);
        memberIndex.put(leader, leader);
    }

    /** Returns the leader UUID for the party this player belongs to, or null. */
    public UUID getLeader(UUID uuid) {
        return memberIndex.get(uuid);
    }

    public boolean isInParty(UUID uuid) {
        return memberIndex.containsKey(uuid);
    }

    public Set<UUID> getPartyMembers(UUID uuid) {
        UUID leader = getLeader(uuid);
        if (leader == null) return Set.of();
        return Collections.unmodifiableSet(parties.getOrDefault(leader, Set.of()));
    }

    public boolean isLeader(UUID uuid) {
        return memberIndex.getOrDefault(uuid, null) != null
            && memberIndex.get(uuid).equals(uuid);
    }

    // ── Invite system ─────────────────────────────────────────────────────────

    public void sendInvite(UUID inviter, UUID invited) {
        invites.put(invited, inviter);
    }

    public boolean hasPendingInvite(UUID uuid) {
        return invites.containsKey(uuid);
    }

    public UUID getInviter(UUID uuid) {
        return invites.get(uuid);
    }

    /**
     * Accepts the pending invite for the given player, joining the inviter's party.
     * Creates a party for the inviter if they aren't already in one.
     */
    public void acceptInvite(UUID invited) {
        UUID inviter = invites.remove(invited);
        if (inviter == null) return;

        if (!isInParty(inviter)) createParty(inviter);

        UUID leader = getLeader(inviter);
        parties.get(leader).add(invited);
        memberIndex.put(invited, leader);
    }

    public void declineInvite(UUID invited) {
        invites.remove(invited);
    }

    // ── Party leaving / disbanding ────────────────────────────────────────────

    /**
     * Removes a player from their party. If they were the leader,
     * a new leader is chosen from remaining members (or the party disbands).
     */
    // ── Offline member tracking ───────────────────────────────────────────────

    /**
     * Marks a member as offline.  They remain in the party — their name shows
     * grey in /party list and they are skipped by the HUD task.
     */
    public void markOffline(UUID uuid) { offlineMembers.add(uuid); }

    /**
     * Marks a member as back online.  Call from PlayerJoinEvent.
     */
    public void markOnline(UUID uuid) { offlineMembers.remove(uuid); }

    /** Returns {@code true} if the member has been marked offline. */
    public boolean isOffline(UUID uuid) { return offlineMembers.contains(uuid); }

    // ── Party leaving / disbanding ────────────────────────────────────────────

    public List<UUID> leaveParty(UUID uuid) {
        UUID leader = memberIndex.remove(uuid);
        if (leader == null) return List.of();
        dpsLog.remove(uuid);

        Set<UUID> members = parties.get(leader);
        if (members == null) return List.of();
        members.remove(uuid);

        if (members.isEmpty()) {
            parties.remove(leader);
            return List.of();
        }

        // If the leader left, pick a new one
        if (leader.equals(uuid)) {
            UUID newLeader = members.iterator().next();
            Set<UUID> newSet = new HashSet<>(members);
            parties.remove(leader);
            parties.put(newLeader, newSet);
            for (UUID m : newSet) memberIndex.put(m, newLeader);
        }
        return new ArrayList<>(members);
    }

    // ── DPS tracker ───────────────────────────────────────────────────────────

    /** Records a damage event for the player. Call from EntityDamageByEntityEvent. */
    public void recordDamage(UUID uuid, double damage) {
        dpsLog.computeIfAbsent(uuid, k -> new ArrayDeque<>())
              .addLast(new long[]{System.currentTimeMillis(), (long)(damage * 100)});
    }

    /**
     * Returns the rolling 25-second total damage for the player.
     * Prunes stale entries on every call.
     */
    public double getRollingDamage(UUID uuid) {
        ArrayDeque<long[]> deque = dpsLog.get(uuid);
        if (deque == null || deque.isEmpty()) return 0.0;
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        deque.removeIf(e -> e[0] < cutoff);
        return deque.stream().mapToLong(e -> e[1]).sum() / 100.0;
    }

    /** Resets the DPS log for a player (e.g. after 3s of no damage). */
    public void clearDps(UUID uuid) {
        dpsLog.remove(uuid);
    }
}
