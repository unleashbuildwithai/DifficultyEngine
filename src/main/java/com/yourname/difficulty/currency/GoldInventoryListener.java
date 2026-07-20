package com.yourname.difficulty.currency;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * GoldInventoryListener — Makes the Gold Vault GUI read-only.
 *
 * Cancels all click and drag events inside the Gold Vault inventory so
 * players cannot take items out of or place items into it.
 */
public class GoldInventoryListener implements Listener {

    private final GoldManager goldManager;

    public GoldInventoryListener(GoldManager goldManager) {
        this.goldManager = goldManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGoldVaultClick(InventoryClickEvent event) {
        if (!GoldInventoryGUI.isGoldVaultTitle(event.getView().getTitle())) return;
        // Block ALL clicks inside the Gold Vault — it's display-only
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGoldVaultDrag(InventoryDragEvent event) {
        if (!GoldInventoryGUI.isGoldVaultTitle(event.getView().getTitle())) return;
        event.setCancelled(true);
    }

    /**
     * Opens the Gold Vault for a player (called from /inventory command in Main).
     */
    public void openVault(Player player) {
        long balance = goldManager.getBalance(player.getUniqueId());
        GoldInventoryGUI.open(player, balance);
    }
}
