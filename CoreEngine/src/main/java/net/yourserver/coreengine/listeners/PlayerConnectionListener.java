package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Removes the player's transient {@link net.yourserver.coreengine.gui.MarketSession}
 * on disconnect.
 * <p>
 * Mid-transaction disconnect protection is already guaranteed by the
 * {@link net.yourserver.coreengine.market.MarketManager} design: all DB
 * mutations complete inside a single synchronous call before returning, and
 * item delivery (the only player-dependent step) falls back to the claimable
 * escrow inbox via {@code deliverOrEscrow} when the recipient is not
 * confirmably online. Nothing is ever left half-committed.
 */
public class PlayerConnectionListener implements Listener {

    private final CoreEngine plugin;

    public PlayerConnectionListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var gui = plugin.getMarketGuiManager();
        if (gui != null) {
            gui.removeSession(event.getPlayer().getUniqueId());
        }
    }
}