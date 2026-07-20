package com.yourname.difficulty.magic;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

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
    private final JavaPlugin            plugin;

    public FavoritesGUIListener(FavoritesGUI gui,
                                ComboFavoritesManager favManager,
                                SpellBookManager spellBookManager,
                                JavaPlugin plugin) {
        this.gui              = gui;
        this.favManager       = favManager;
        this.spellBookManager = spellBookManager;
        this.plugin           = plugin;
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
            // Schedule close on next tick, then open book 2 ticks later to avoid
            // inventory-state conflicts that silently prevent openBook from firing.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.openBook(spellBookManager.buildBookForPlayer(player.getUniqueId()));
                        int count = spellBookManager.getUnlockedCount(player.getUniqueId());
                        player.sendActionBar("§5✦ §dArcane Tome §8— §7"
                                + count + "§8/§7" + SpellBookManager.TOTAL_PAGES
                                + " §dpages unlocked");
                    }
                }, 2L);
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
}
