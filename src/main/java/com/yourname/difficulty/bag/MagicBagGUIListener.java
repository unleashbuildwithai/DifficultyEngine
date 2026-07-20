package com.yourname.difficulty.bag;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicBagGUIListener — handles all interaction with the 4-page Magic Bag.
 *
 * ── Interactions handled ──────────────────────────────────────────────────
 *  Right-click Magic Bag item          → open GUI on page 0 (Fire/inbox)
 *  Click ◀ / ▶ buttons                → navigate pages (saves first)
 *  Click ⟳ Auto-Sort                  → redistribute items by element
 *  Click item slot (left/right)        → vanilla swap (ANY item allowed)
 *  Shift-click item slot (top inv)     → move item to player inventory
 *  Shift-click from player inventory   → move item into current bag page (random slot)
 *  Shift-click magic item from chest   → auto-route into correct element page
 *  Close GUI                           → save current page
 *  Join / Quit                         → load / save bag data
 *
 * ── Key fix from previous version ────────────────────────────────────────
 *  • Items NO LONGER vanish on auto-sort: the current page is read back into
 *    the bag array BEFORE autoSort() is called.
 *  • ANY item can be placed in any slot — no element restriction on placement.
 *    Auto-sort is the only thing that moves items between pages.
 */
public class MagicBagGUIListener implements Listener {

    private final MagicBagManager bagManager;
    private final MagicBagGUI     bagGUI;
    private final JavaPlugin       plugin;

    /**
     * Tracks which page each player is currently viewing.
     * Removed on close or quit.
     */
    private final Map<UUID, Integer> playerPage = new HashMap<>();

    /**
     * While a player is navigating to another page (i.e. closing one inventory
     * to open the next), we suppress the normal close-save to avoid a double
     * save that could interleave with the open.  The navigation click already
     * performs a readItems() before scheduling the page switch.
     */
    private final java.util.Set<UUID> navigating = new java.util.HashSet<>();

    public MagicBagGUIListener(MagicBagManager bagManager,
                                MagicBagGUI     bagGUI,
                                JavaPlugin       plugin) {
        this.bagManager = bagManager;
        this.bagGUI     = bagGUI;
        this.plugin     = plugin;
    }

    // ── Public helper ─────────────────────────────────────────────────────────

    /** Opens the bag on page 0 (the Fire/inbox page). */
    public void openForPlayer(Player player) {
        openPage(player, 0);
    }

    // ── Auto-load / save on join / quit ───────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        bagManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerPage.remove(uuid);
        navigating.remove(uuid);
        bagManager.saveAsync(uuid);
    }

    // ── Prevent placing the CHEST block when holding the Magic Bag ────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (bagManager.isMagicBag(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    // ── Right-click the bag item to open the GUI ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !bagManager.isMagicBag(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);
        openPage(event.getPlayer(), 0);
    }

    // ── Save when the bag GUI is closed ───────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        if (!MagicBagGUI.isMagicBagTitle(event.getView().getTitle())) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();

        if (navigating.contains(uuid)) {
            // Navigation already read + will reopen — don't double-process
            navigating.remove(uuid);
            return;
        }

        int page = playerPage.getOrDefault(uuid, 0);
        bagGUI.readItems(event.getInventory(), bagManager.getBag(uuid), page);
        bagManager.saveAsync(uuid);
        playerPage.remove(uuid);
    }

    // ── Drag: cancel drags over non-item slots ────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBagDrag(InventoryDragEvent event) {
        if (!MagicBagGUI.isMagicBagTitle(event.getView().getTitle())) return;

        // Cancel if the drag touches the header row or nav row
        for (int raw : event.getRawSlots()) {
            if (raw < MagicBagGUI.SIZE && !MagicBagGUI.isItemSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Main click handler ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBagClick(InventoryClickEvent event) {
        if (!MagicBagGUI.isMagicBagTitle(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true); // block by default; un-cancel selectively

        boolean isTopInv = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!isTopInv) {
            // Click in PLAYER inventory (bottom half)
            handleBottomClick(player, event);
            return;
        }

        // ── Top (bag) inventory ───────────────────────────────────────────────
        int slot = event.getSlot();
        int page = playerPage.getOrDefault(player.getUniqueId(), 0);

        // ── Navigation buttons ────────────────────────────────────────────────
        if (slot == MagicBagGUI.PREV_SLOT) {
            navigateTo(player, event.getView().getTopInventory(), page,
                    (page - 1 + MagicBagManager.PAGES) % MagicBagManager.PAGES);
            return;
        }
        if (slot == MagicBagGUI.NEXT_SLOT) {
            navigateTo(player, event.getView().getTopInventory(), page,
                    (page + 1) % MagicBagManager.PAGES);
            return;
        }

        // ── Auto-sort button ──────────────────────────────────────────────────
        if (slot == MagicBagGUI.SORT_SLOT) {
            // CRITICAL FIX: read current page items into bag BEFORE sorting,
            // otherwise items placed in this GUI session get wiped by autoSort.
            ItemStack[] bag = bagManager.getBag(player.getUniqueId());
            bagGUI.readItems(event.getView().getTopInventory(), bag, page);

            bagManager.autoSort(player.getUniqueId());

            // Refresh the current page view
            bagGUI.populateItems(event.getView().getTopInventory(),
                    bagManager.getBag(player.getUniqueId()), page);

            player.sendMessage("§d✦ §7Magic Bag sorted by element across all pages!");
            return;
        }

        // ── Item storage slots ────────────────────────────────────────────────
        if (!MagicBagGUI.isItemSlot(slot)) return; // header/nav pane clicked — ignore

        // Left / right click — vanilla item swap (magic items only)
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            ItemStack cursor  = event.getCursor();
            ItemStack current = event.getCurrentItem();
            boolean cursorEmpty  = cursor  == null || cursor.getType().isAir();
            boolean currentEmpty = current == null || current.getType().isAir();
            if (cursorEmpty && currentEmpty) return;
            // Block non-magic items from being placed into the bag
            if (!cursorEmpty && !isMagicItem(cursor)) {
                player.sendMessage("§c✗ §7Only §dMagic Items §7can be stored in the Magic Bag!");
                return; // event stays cancelled
            }
            event.setCancelled(false); // allow vanilla item exchange
            return;
        }

        // Shift-click from top → push item back to player inventory
        if (event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) return;
            player.getInventory().addItem(current.clone());
            event.getView().getTopInventory().setItem(slot, new ItemStack(Material.AIR));
        }
    }

    // ── Player-inventory click while bag is open ──────────────────────────────

    private void handleBottomClick(Player player, InventoryClickEvent event) {
        // Allow normal non-shift clicks in player inventory
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) {
            event.setCancelled(false);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int page = playerPage.getOrDefault(player.getUniqueId(), 0);

        // Try to add to current page first; fall back to any page
        boolean added = bagManager.addToBag(player.getUniqueId(), clicked.clone(), page);
        if (!added) {
            // Classify and try the correct page
            added = bagManager.addToBag(player.getUniqueId(), clicked.clone());
        }

        if (added) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            bagGUI.populateItems(event.getView().getTopInventory(),
                    bagManager.getBag(player.getUniqueId()), page);
            player.sendMessage("§d✦ §7Item added to bag.");
        } else {
            event.setCancelled(false);
            player.sendMessage("§c✗ §7Bag page is full! Try auto-sorting or using another page.");
        }
    }

    // ── Chest shift-click → auto-route into bag ───────────────────────────────

    /**
     * Intercepts shift-clicks on items inside chests/barrels/hoppers when the
     * player carries a Magic Bag.  Routes magic items straight into the correct
     * element page instead of the player's inventory.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChestShiftClick(InventoryClickEvent event) {
        // Skip if bag GUI is open (handled above)
        if (MagicBagGUI.isMagicBagTitle(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) return;

        // Only trigger from containers
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CHEST
                && topType != InventoryType.BARREL
                && topType != InventoryType.HOPPER
                && topType != InventoryType.DISPENSER
                && topType != InventoryType.DROPPER) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        if (!playerHasBag(player)) return;

        // Only intercept items that have an element classification (non-zero)
        // so that vanilla items shift-click normally into inventory
        int targetPage = bagManager.classifyItemToPage(clicked);
        // If it's a pure magic-element item (rune/staff), auto-bag it
        // For generic items, let vanilla handle it (they can manually bag them)
        if (targetPage == 0 && bagManager.classifyItemToPage(clicked) == 0) {
            // Check if it's actually a magic item (staff/rune/gear)
            boolean isMagic = false;
            org.bukkit.inventory.meta.ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String dn = meta.getDisplayName();
                if (dn.contains("Rune") || dn.contains("Staff") || dn.contains("Mage")
                        || dn.contains("Spell") || dn.contains("Dragon") || dn.contains("Dark Bow")) {
                    isMagic = true;
                }
            }
            if (!isMagic) return; // let vanilla shift-click handle it
        }

        event.setCancelled(true);
        if (bagManager.addToBag(player.getUniqueId(), clicked.clone())) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            player.sendMessage("§d✦ §7" + itemName(clicked)
                    + " §8→ §dMagic Bag §8(" + MagicBagManager.pageLabel(targetPage) + "§8)");
        } else {
            event.setCancelled(false);
            player.sendMessage("§c✗ §7Magic Bag full — item went normally.");
        }
    }

    // ── Navigation helper ─────────────────────────────────────────────────────

    /**
     * Saves the current page, marks the player as "navigating" (so the close
     * event doesn't double-save), then opens the target page after 1 tick.
     */
    private void navigateTo(Player player, org.bukkit.inventory.Inventory topInv,
                             int currentPage, int targetPage) {
        UUID uuid = player.getUniqueId();

        // Save current page items into the bag array
        bagGUI.readItems(topInv, bagManager.getBag(uuid), currentPage);
        bagManager.saveAsync(uuid);

        // Mark navigating so onClose doesn't interfere
        navigating.add(uuid);
        playerPage.put(uuid, targetPage);

        // Open new page after 1 tick (inventory must be fully closed first)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) openPage(player, targetPage);
        }, 1L);
    }

    /** Opens a specific page, recording it in playerPage. */
    private void openPage(Player player, int page) {
        playerPage.put(player.getUniqueId(), page);
        bagGUI.open(player, page);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean playerHasBag(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (bagManager.isMagicBag(it)) return true;
        }
        return false;
    }

    private static String itemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName())
            return item.getItemMeta().getDisplayName();
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * Returns true if the given item is considered a "magic item" that may be
     * stored in the Magic Bag.  An item qualifies if its display name contains
     * any of the recognised magic keywords.
     */
    private boolean isMagicItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String dn = org.bukkit.ChatColor.stripColor(meta.getDisplayName()).toLowerCase();
            if (dn.contains("rune")   || dn.contains("staff")  || dn.contains("mage")
             || dn.contains("spell")  || dn.contains("dragon") || dn.contains("dark bow")
             || dn.contains("magic")  || dn.contains("tome")   || dn.contains("wand")
             || dn.contains("bottle") || dn.contains("dust")   || dn.contains("crystal")
             || dn.contains("orb")    || dn.contains("scroll") || dn.contains("sigil")) {
                return true;
            }
        }
        // Also accept items that have a PDC magic tag (runes / staves detected by manager)
        // classifyItemToPage returns > 0 only for element-specific runes/staves,
        // but both are magic; page 0 can also hold generic magic items via display name check above.
        return bagManager.classifyItemToPage(item) > 0;
    }
}
