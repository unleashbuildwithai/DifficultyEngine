package net.yourserver.coreengine.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Marker holder used to identify which custom GUI a clicked inventory
 * belongs to. Phase 6 extension: it now also carries the per-player
 * {@link MarketSession} that was active when the inventory was built, so the
 * click listener can route actions (page nav, sub-views, buy/fulfill,
 * confirmations) based on the current session state.
 * <p>
 * Module 1 uses {@code guiType = "MARKET_MAIN"} for every market inventory
 * (matching the existing {@code GUIListener} switch case); sub-view
 * distinction is handled entirely through {@link MarketSession#getView()}.
 */
public class CustomGUIHolder implements InventoryHolder {

    private final String guiType;
    private MarketSession session;

    public CustomGUIHolder(String guiType) {
        this.guiType = guiType;
    }

    public CustomGUIHolder(String guiType, MarketSession session) {
        this.guiType = guiType;
        this.session = session;
    }

    public String getGuiType() {
        return guiType;
    }

    public MarketSession getSession() {
        return session;
    }

    public void setSession(MarketSession session) {
        this.session = session;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
