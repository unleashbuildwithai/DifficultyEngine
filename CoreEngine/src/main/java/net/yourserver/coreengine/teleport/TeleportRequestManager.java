package net.yourserver.coreengine.teleport;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending teleport request + combat tagging for the /tp, /tphere, /tpaccept
 * flow. A request is stored against the TARGET; accepting pops it and resolves
 * the direction ({@link RequestType#TP} pulls the requester to the target,
 * {@link RequestType#TPHERE} pulls the target to the requester).
 */
public class TeleportRequestManager {

    public enum RequestType {
        TP,
        TPHERE
    }

    public record Pending(UUID requester, String requesterName, RequestType type) {
    }

    /** Requests keyed by target UUID. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    /** Combat tag: UUID -> epoch millis when combat ends. */
    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private static final long COMBAT_SECONDS = 10L;

    public void request(Player requester, Player target, RequestType type) {
        pending.put(target.getUniqueId(),
                new Pending(requester.getUniqueId(), requester.getName(), type));
    }

    public boolean hasPending(UUID target) {
        return pending.containsKey(target);
    }

    public Pending poll(UUID target) {
        return pending.remove(target);
    }

    /** Called when the player takes or deals damage. */
    public void markCombat(UUID uuid) {
        combatUntil.put(uuid, System.currentTimeMillis() + COMBAT_SECONDS * 1000L);
    }

    public boolean isInCombat(UUID uuid) {
        Long until = combatUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    /** Drops an expired combat tag without any further action. */
    public void cleanup() {
        long now = System.currentTimeMillis();
        combatUntil.values().removeIf(until -> until <= now);
    }
}
