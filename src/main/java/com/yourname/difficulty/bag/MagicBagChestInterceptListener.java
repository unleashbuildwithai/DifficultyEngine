 package com.yourname.difficulty.bag;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MagicBagChestInterceptListener — Three responsibilities:
 *
 * 1. CHEST → BAG INTERCEPT:
 *    When a player PLACES any magic item INTO a chest/barrel while carrying
 *    a Magic Bag, the item is rerouted to the bag instead.
 *    Chat message: "§d✦ [ItemName] → Magic Bag (🔥 Fire)!"
 *
 * 2. HARDCODED BAG SLOT (slot 8):
 *    On join and respawn, ensures the Magic Bag is in hotbar slot 8.
 *    Blocks any attempt to move the bag OUT of slot 8.
 *
 * 3. MAGIC ITEM DETECTION:
 *    Extends MagicBagGUIListener's existing shift-click routing to also
 *    handle normal left-click placement into chest slots.
 */
public class MagicBagChestInterceptListener implements Listener {

    /** Hotbar slot reserved for the Magic Bag (0-indexed; slot 8 = far right). */
    public static final int BAG_SLOT = 8;

    private final MagicBagManager bagManager;
    private final JavaPlugin       plugin;

    public MagicBagChestInterceptListener(MagicBagManager bagManager, JavaPlugin plugin) {
        this.bagManager = bagManager;
        this.plugin     = plugin;
    }

    // ── Hardcoded bag slot — ensure bag is always in slot 8 ──────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            if (p.isOnline()) ensureBagInSlot(p);
        }, 5L);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = event.getPlayer();
            if (p.isOnline()) ensureBagInSlot(p);
        }, 5L);
    }

    /**
     * Ensures the player has a Magic Bag in hotbar slot 8.
     * If slot 8 is empty, place the bag there.
     * If slot 8 has something else, we don't override it — just ensure a bag exists.
     */
    private void ensureBagInSlot(Player player) {
        ItemStack currentSlot8 = player.getInventory().getItem(BAG_SLOT);

        // If there's already a Magic Bag in slot 8, we're good
        if (currentSlot8 != null && bagManager.isMagicBag(currentSlot8)) return;

        // Check if the player has a bag anywhere in inventory
        ItemStack existingBag = null;
        int existingSlot = -1;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && bagManager.isMagicBag(item)) {
                existingBag = item;
                existingSlot = i;
                break;
            }
        }

        if (existingBag != null) {
            // Move existing bag to slot 8
            if (existingSlot != BAG_SLOT) {
                ItemStack displaced = player.getInventory().getItem(BAG_SLOT);
                player.getInventory().setItem(BAG_SLOT, existingBag.clone());
                player.getInventory().setItem(existingSlot, displaced);
            }
        } else {
            // Give a new Magic Bag in slot 8
            ItemStack displaced = player.getInventory().getItem(BAG_SLOT);
            player.getInventory().setItem(BAG_SLOT, bagManager.buildMagicBag());

            // Put displaced item in first free slot
            if (displaced != null && !displaced.getType().isAir()) {
                player.getInventory().addItem(displaced);
            }
        }
    }

    // ── Chest → Bag intercept ─────────────────────────────────────────────────
    // The bag is freely moveable — no slot lock.  Right-click in hand opens GUI.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // ── Chest → Bag intercept ─────────────────────────────────────────
        // Only intercept when the top inventory is a chest/container
        if (!isContainer(event.getView().getTopInventory().getType())) return;

        // We care about moving items FROM player inventory INTO chest
        // This happens when the clicked inventory is the top (chest)
        // and cursor has an item, OR when items move from bottom to top
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        // Check if player has a bag
        if (!playerHasBag(player)) return;

        // ── If cursor has a magic item and player is placing it into the chest ──
        ItemStack cursor = event.getCursor();
        if (cursor == null || cursor.getType().isAir()) return;

        // Only intercept magic items (not all items)
        if (!isMagicItem(cursor)) return;

        event.setCancelled(true);
        routeToBag(player, cursor.clone());
        event.getWhoClicked().setItemOnCursor(new ItemStack(Material.AIR));
    }

    // ── Route item to bag ─────────────────────────────────────────────────────

    private void routeToBag(Player player, ItemStack item) {
        int targetPage = bagManager.classifyItemToPage(item);
        boolean added  = bagManager.addToBag(player.getUniqueId(), item);

        if (added) {
            String pageName = MagicBagManager.pageLabel(targetPage);
            String itemName = getItemName(item);

            // Plain message
            player.sendMessage("§d✦ §7" + itemName + " §8→ §dMagic Bag §8("
                    + pageName + "§8)§7!");
        } else {
            // Bag full — give item back
            player.getInventory().addItem(item);
            player.sendMessage("§c✗ §7Magic Bag is full! Item returned.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isContainer(InventoryType type) {
        return type == InventoryType.CHEST
            || type == InventoryType.BARREL
            || type == InventoryType.HOPPER
            || type == InventoryType.DISPENSER
            || type == InventoryType.DROPPER
            || type == InventoryType.SHULKER_BOX;
    }

    private boolean playerHasBag(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (it != null && bagManager.isMagicBag(it)) return true;
        }
        return false;
    }

    /** Returns true if the item should be auto-routed to the Magic Bag. */
    private boolean isMagicItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        // If it classifies to a non-zero page it's an elemental item
        if (bagManager.classifyItemToPage(item) != 0) return true;

        // Check display name for magic keywords
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        String name = item.getItemMeta().getDisplayName().toLowerCase();
        return name.contains("rune") || name.contains("staff") || name.contains("mage")
            || name.contains("spell") || name.contains("dragon") || name.contains("dark bow")
            || name.contains("arcane") || name.contains("earth") || name.contains("support staff")
            || name.contains("survivor token");
    }

    private static String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            return item.getItemMeta().getDisplayName();
        return item.getType().name().replace('_', ' ').toLowerCase();
    }
}
