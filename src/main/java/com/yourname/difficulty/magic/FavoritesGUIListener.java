package com.yourname.difficulty.magic;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

/**
 * FavoritesGUIListener — Handles clicks in the Combo Favorites chest GUI.
 *
 * ── Click behaviour ────────────────────────────────────────────────────────────
 *  Slots 0–7   (chain items)   → toggle ⭐ favourite on/off
 *  Slot 22     (Read Tome)     → close GUI, open written-book spell tome
 *  Slot 26     (Close)         → close GUI
 *  All other slots             → cancel (no item move)
 *
 * ── GUI identification ─────────────────────────────────────────────────────────
 *  Detected by checking {@link org.bukkit.inventory.InventoryView#getTitle()}
 *  contains {@link FavoritesGUI#GUI_TAG}.
 */
public class FavoritesGUIListener implements Listener {

    private final FavoritesGUI          gui;
    private final ComboFavoritesManager favManager;
    private final SpellBookManager      spellBookManager;

    public FavoritesGUIListener(FavoritesGUI gui,
                                ComboFavoritesManager favManager,
                                SpellBookManager spellBookManager) {
        this.gui              = gui;
        this.favManager       = favManager;
        this.spellBookManager = spellBookManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ── Identify our GUI by title ─────────────────────────────────────────
        String title = event.getView().getTitle();
        if (!title.contains(FavoritesGUI.GUI_TAG)) return;

        // Always cancel to prevent item pickup
        event.setCancelled(true);

        Inventory inv  = event.getInventory();
        int       slot = event.getRawSlot();

        // ── Slot 0–7: chain items → toggle favourite ──────────────────────────
        String tag = FavoritesGUI.tagAtSlot(slot);
        if (tag != null) {
            boolean nowStarred = favManager.toggle(player.getUniqueId(), tag);
            gui.refreshChainSlot(inv, tag, nowStarred);
            gui.refreshHelpSlot(inv, !favManager.hasAnyFavorite(player.getUniqueId()));

            if (nowStarred) {
                ComboFavoritesManager.ChainInfo info = ComboFavoritesManager.getInfo(tag);
                player.sendActionBar("§e⭐ §7Starred: " + info.displayName()
                        + " §8— hints now active!");
                player.playSound(player.getLocation(),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
            } else {
                ComboFavoritesManager.ChainInfo info = ComboFavoritesManager.getInfo(tag);
                player.sendActionBar("§8○ §7Un-starred: " + info.displayName()
                        + " §8— hints hidden.");
                player.playSound(player.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
            }
            return;
        }

        // ── Slot 22: Read Full Tome ───────────────────────────────────────────
        if (slot == 22) {
            player.closeInventory();
            // Open the written-book view one tick later (avoids NullPointerException
            // from trying to open a book while closing an inventory)
            player.getServer().getScheduler().runTaskLater(
                    getPlugin(player), () -> {
                        if (player.isOnline()) {
                            player.openBook(spellBookManager.buildBookForPlayer(player.getUniqueId()));
                            int count = spellBookManager.getUnlockedCount(player.getUniqueId());
                            player.sendActionBar("§5✦ §dArcane Tome §8— §7"
                                    + count + "§8/§7" + SpellBookManager.TOTAL_PAGES
                                    + " §dpages unlocked");
                        }
                    }, 1L);
            return;
        }

        // ── Slot 26: Close ────────────────────────────────────────────────────
        if (slot == 26) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.2f);
        }

        // All other slots are fillers / info items — click is already cancelled
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (!title.contains(FavoritesGUI.GUI_TAG)) return;
        // Auto-save on close (already saved after each toggle, but belt-and-suspenders)
        favManager.save();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Returns the server's plugin instance via the scheduler's owner.
     * We use this because FavoritesGUIListener doesn't hold a plugin reference
     * — we obtain it lazily from the player's server.
     */
    private org.bukkit.plugin.Plugin getPlugin(Player player) {
        // Get the first plugin registered (DifficultyEngine will be first because
        // it registered this listener). Fallback: just use the player's server scheduler.
        return player.getServer().getPluginManager().getPlugins()[0];
    }
}
