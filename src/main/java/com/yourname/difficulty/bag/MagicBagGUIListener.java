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

import java.util.Arrays;
import java.util.Comparator;

/**
 * MagicBagGUIListener — handles all interaction with the Magic Bag.
 *
 * ── Right-click Magic Bag item              → open GUI
 * ── Inventory close                         → save contents
 * ── Click inside bag GUI                    → enforce section order + sort
 * ── Shift-click magic item from any chest   → auto-route into bag section
 * ── Player join                             → load bag from disk
 * ── Player quit                             → save bag to disk
 */
public class MagicBagGUIListener implements Listener {

    private final MagicBagManager bagManager;
    private final MagicBagGUI     bagGUI;

    public MagicBagGUIListener(MagicBagManager bagManager, MagicBagGUI bagGUI) {
        this.bagManager = bagManager;
        this.bagGUI     = bagGUI;
    }

    // ── Auto-load / save on join / quit ───────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        bagManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        bagManager.saveAsync(event.getPlayer().getUniqueId());
    }

    // ── Prevent placing the CHEST block when holding the Magic Bag ───────────

    /**
     * Paper 1.21 sometimes fires BlockPlaceEvent even when PlayerInteractEvent
     * was cancelled.  Cancel chest placement whenever the item in hand is the
     * Magic Bag so the player never accidentally places a chest instead of
     * opening the bag.
     */
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

        event.setCancelled(true); // prevent chest placement
        bagGUI.open(event.getPlayer());
    }

    // ── Save when the bag GUI is closed ───────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;

        ItemStack[] bag = bagManager.getBag(player.getUniqueId());
        bagGUI.readItems(event.getInventory(), bag);
        bagManager.saveAsync(player.getUniqueId());
    }

    // ── Bag GUI click handling ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBagClick(InventoryClickEvent event) {
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true); // default: block everything

        boolean isTopInv = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!isTopInv) {
            handleBottomClick(player, event);
            return;
        }

        // ── Top (bag) inventory ───────────────────────────────────────────────
        int slot = event.getSlot();

        // Sort button
        if (slot == MagicBagGUI.SORT_SLOT) {
            sortBag(player, event.getView().getTopInventory());
            player.sendMessage("§e⟳ §7Magic Bag sorted!");
            return;
        }

        // Glass pane / non-item slot?
        int bagIndex = MagicBagGUI.guiSlotToBagIndex(slot);
        if (bagIndex < 0) return;

        // Left / right click — allow swap only if cursor item fits this section
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            ItemStack cursor  = event.getCursor();
            ItemStack current = event.getCurrentItem();

            boolean cursorEmpty  = cursor == null || cursor.getType().isAir();
            boolean currentEmpty = current == null || current.getType().isAir();

            if (cursorEmpty && currentEmpty) return;

            if (!cursorEmpty) {
                // Enforce section restriction
                int itemSection = bagManager.classifyItem(cursor);
                int guiSection  = MagicBagGUI.guiSlotToSection(slot);

                if (itemSection < 0) {
                    player.sendMessage("§c✗ §7That item doesn't belong in the Magic Bag.");
                    return;
                }
                if (itemSection != guiSection) {
                    player.sendMessage("§c✗ §7That belongs in §"
                            + sectionColor(itemSection)
                            + MagicBagManager.sectionLabel(itemSection)
                            + "§7, not §" + sectionColor(guiSection)
                            + MagicBagManager.sectionLabel(guiSection) + "§7.");
                    return;
                }
            }
            event.setCancelled(false); // allow the vanilla swap
        }

        // Shift-click from top: move item back to player inventory
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) return;
            player.getInventory().addItem(current.clone());
            event.getView().getTopInventory().setItem(slot, new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBagDrag(InventoryDragEvent event) {
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        for (int raw : event.getRawSlots()) {
            if (raw < MagicBagGUI.SIZE && MagicBagGUI.guiSlotToBagIndex(raw) < 0) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Bottom-inventory click (shift-click into bag while bag GUI open) ───────

    private void handleBottomClick(Player player, InventoryClickEvent event) {
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) {
            event.setCancelled(false); // allow normal clicks in player inv
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int section = bagManager.classifyItem(clicked);
        if (section >= 0) {
            // It's a magic item — route to bag
            event.setCancelled(true);
            if (bagManager.addToBag(player.getUniqueId(), clicked)) {
                event.setCurrentItem(new ItemStack(Material.AIR));
                bagGUI.populateItems(event.getView().getTopInventory(),
                        bagManager.getBag(player.getUniqueId()));
                player.sendMessage("§d✦ §7Item sorted to §"
                        + sectionColor(section)
                        + MagicBagManager.sectionLabel(section) + "§7.");
            } else {
                player.sendMessage("§c✗ §7That bag section is full!");
                event.setCancelled(false);
            }
        } else {
            event.setCancelled(false); // not a magic item, allow normal shift
        }
    }

    // ── Auto-collect / auto-bag for any chest interaction ─────────────────────

    /**
     * Intercepts shift-clicks that involve a magic item and a container, in
     * BOTH directions — so players can't accidentally lose magic items in chests.
     *
     * Direction A (chest → player inventory):
     *   Shift-clicking a magic item FROM a chest redirects it into the bag.
     *
     * Direction B (player inventory → chest):
     *   Shift-clicking a magic item FROM the player inventory while a chest is
     *   open redirects it into the bag instead of the chest.
     *
     * In both cases the bag GUI does not need to be open.  The player must be
     * carrying a Magic Bag somewhere in their inventory.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChestShiftClick(InventoryClickEvent event) {
        // Skip if the bag GUI itself is open (handled by onBagClick above)
        if (MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) return;

        // Must involve a container as the top inventory
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CHEST
                && topType != InventoryType.BARREL
                && topType != InventoryType.HOPPER
                && topType != InventoryType.DISPENSER
                && topType != InventoryType.DROPPER) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        int section = bagManager.classifyItem(clicked);
        if (section < 0) return;           // not a magic item — let vanilla handle it
        if (!playerHasBag(player)) return; // player not carrying a bag

        // Both directions — from chest or from player inventory
        event.setCancelled(true);
        ItemStack toAdd = clicked.clone();
        if (bagManager.addToBag(player.getUniqueId(), toAdd)) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            player.sendMessage("§d✦ §7" + itemName(clicked) + " §8→ §dMagic Bag §8("
                    + MagicBagManager.sectionLabel(section) + "§8)");
        } else {
            event.setCancelled(false);
            player.sendMessage("§c✗ §7Magic Bag section full — item went normally.");
        }
    }

    // ── Sort button action ────────────────────────────────────────────────────

    private void sortBag(Player player, org.bukkit.inventory.Inventory topInv) {
        ItemStack[] bag = bagManager.getBag(player.getUniqueId());

        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            int start = s * MagicBagManager.SLOTS_PER_SECTION;
            ItemStack[] section = Arrays.copyOfRange(bag, start,
                    start + MagicBagManager.SLOTS_PER_SECTION);

            // Sort: non-empty first (desc amount), nulls at back
            Arrays.sort(section, Comparator.comparingInt(
                    it -> (it == null || it.getType().isAir()) ? 0 : -it.getAmount()));

            System.arraycopy(section, 0, bag, start, MagicBagManager.SLOTS_PER_SECTION);
        }

        bagGUI.populateItems(topInv, bag);
        bagManager.saveAsync(player.getUniqueId());
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

    private static char sectionColor(int section) {
        return switch (section) {
            case 0  -> '5';   // purple
            case 1  -> '9';   // blue
            case 2  -> 'b';   // cyan
            case 3  -> '2';   // green
            default -> '7';   // gray
        };
    }
}
