package net.yourserver.coreengine.teleport;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Centralizes teleport permission logic: who may teleport to whom, based on
 * the target's {@link PlayerSettingsManager.TpPrivacy} level, plus a soft
 * party check against DifficultyEngine's PartyManager (via reflection, so
 * CoreEngine does not hard-depend on it).
 */
public class TeleportManager {

    private final CoreEngine plugin;
    private final PlayerSettingsManager settings;

    public TeleportManager(CoreEngine plugin, PlayerSettingsManager settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    /**
     * Whether {@code requester} may teleport to {@code target}, per the
     * target's TP privacy level.
     */
    public boolean canTeleportTo(UUID target, UUID requester) {
        PlayerSettingsManager.TpPrivacy privacy = settings.get(target).tpPrivacy;
        return switch (privacy) {
            case EVERYONE -> true;
            case NOBODY -> false;
            case PARTY -> inSameParty(target, requester);
        };
    }

    /** True if both players are in the same DifficultyEngine party. */
    public boolean inSameParty(UUID a, UUID b) {
        Plugin de = Bukkit.getPluginManager().getPlugin("DifficultyEngine");
        if (de == null) {
            return false;
        }
        try {
            Object main = de.getClass().getMethod("getInstance").invoke(null);
            Object partyManager = main.getClass().getMethod("getPartyManager").invoke(main);
            if (partyManager == null) {
                return false;
            }
            // getPartyMembers(UUID) -> Set<UUID>
            Object members = partyManager.getClass()
                    .getMethod("getPartyMembers", UUID.class).invoke(partyManager, a);
            if (members instanceof java.util.Set<?> set) {
                return set.contains(b);
            }
        } catch (Exception ignored) {
            // DifficultyEngine API changed or unavailable - fall through.
        }
        return false;
    }
}
