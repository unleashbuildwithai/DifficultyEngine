package com.yourname.difficulty.bag;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
 * ── Right-click magic bag item → open GUI
 * ── Inventory close → save contents
 * ── Click inside GUI → enforce section restrictions + sort button
 * ── Shift-click magic item from chest → auto-route into bag
 * ── Player join → load bag from disk
 * ── Player quit → save bag to disk
 */
public class MagicBagGUIListener implements Listener {

    private final MagicBagManager bagManager;
    private final MagicBagGUI     bagGUI;

    public MagicBagGUIListener(MagicBagManager bagManager, MagicBagGUI bagGUI) {
        this.bagManager = bagManager;
        this.bagGUI     = bagGUI;
    }

    // ── Auto-load / save on join / quit ───────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        bagManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        bagManager.saveAsync(event.getPlayer().getUniqueId());
    }

    // ── Right-click the bag item to open it ───────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onUse(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !bagManager.isMagicBag(item)) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true); // prevent chest placement
        bagGUI.open(event.getPlayer());
    }

    // ── Save when the bag GUI is closed ───────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;

        // Read items out of the GUI back into the bag array
        ItemStack[] bag = bagManager.getBag(player.getUniqueId());
        bagGUI.readItems(event.getInventory(), bag);
        bagManager.saveAsync(player.getUniqueId());
    }

    // ── Bag GUI click handling ────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBagClick(InventoryClickEvent event) {
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        event.setCancelled(true); // default: block everything

        boolean isTopInv = event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory());

        if (!isTopInv) {
            // ── Player's bottom inventory ─────────────────────────────────
            handleBottomClick(player, event);
            return;
        }

        // ── Top (bag) inventory ───────────────────────────────────────────
        int slot = event.getSlot();

        // Sort button
        if (slot == MagicBagGUI.SORT_SLOT) {
            sortBag(player, event.getView().getTopInventory());
            player.sendMessage("§e⟳ §7Magic Bag sorted by quantity!");
            return;
        }

        // Is this a valid item slot?
        int bagIndex = MagicBagGUI.guiSlotToBagIndex(slot);
        if (bagIndex < 0) return; // glass pane — ignore

        // Standard left/right click: swap cursor ↔ slot
        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();

            boolean cursorEmpty   = cursor == null || cursor.getType().isAir();
            boolean currentEmpty  = current == null || current.getType().isAir();

            if (cursorEmpty && currentEmpty) return;

            int section = MagicBagGUI.guiSlotToSection(slot);

            if (!cursorEmpty) {
                // Putting an item INTO the bag — enforce section classification
                int itemSection = bagManager.classifyItem(cursor);
                if (itemSection != section) {
                    player.sendMessage("§c✗ §7That item belongs in §"
                            + sectionColor(itemSection) + MagicBagManager.sectionLabel(itemSection)
                            + "§7, not §" + sectionColor(section)
                            + MagicBagManager.sectionLabel(section) + "§7.");
                    return;
                }
            }

            // Perform the swap
            event.setCancelled(false);
        }

        // Shift-click from top: move to player inventory
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType().isAir()) return;

            // Give item to player
            player.getInventory().addItem(current.clone());
            event.getView().getTopInventory().setItem(slot, new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBagDrag(InventoryDragEvent event) {
        if (!MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        // Cancel drags that touch glass slots
        for (int raw : event.getRawSlots()) {
            if (raw < MagicBagGUI.SIZE && MagicBagGUI.guiSlotToBagIndex(raw) < 0) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── Bottom-inventory click (shift-click to bag) ───────────────────────

    private void handleBottomClick(Player player, InventoryClickEvent event) {
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) {
            // Regular click inside player inv while bag is open — allow normally
            event.setCancelled(false);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Try to put it in the correct bag section
        if (bagManager.classifyItem(clicked) >= 0) {
            event.setCancelled(true);
            if (bagManager.addToBag(player.getUniqueId(), clicked)) {
                event.setCurrentItem(new ItemStack(Material.AIR));
                // Refresh GUI display
                bagGUI.populateItems(event.getView().getTopInventory(),
                        bagManager.getBag(player.getUniqueId()));
                int section = bagManager.classifyItem(clicked) < 0 ? 0
                        : bagManager.classifyItem(clicked.clone());
                player.sendMessage("§d✦ §7Item auto-sorted to §"
                        + sectionColor(bagManager.classifyItem(clicked.clone()))
                        + MagicBagManager.sectionLabel(
                            bagManager.classifyItem(clicked.clone())) + "§7.");
            } else {
                player.sendMessage("§c✗ §7That bag section is full!");
                event.setCancelled(false); // let it go to player inv normally
            }
        } else {
            // Not a magic item — shift-click goes normally to player inventory
            event.setCancelled(false);
        }
    }

    // ── Auto-collect from chests ──────────────────────────────────────────

    /**
     * When a player shift-clicks a magic item from a regular chest (or any
     * container), and they have a Magic Bag in their inventory, the item is
     * auto-routed into the bag instead of the player's main inventory.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChestShiftClick(InventoryClickEvent event) {
        // Only fire for non-bag GUIs (the bag GUI has its own handler above)
        if (MagicBagGUI.TITLE.equals(event.getView().getTitle())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only shift-clicks from the top (container) inventory
        if (event.getClickedInventory() == null) return;
        boolean fromTop = event.getClickedInventory().equals(event.getView().getTopInventory());
        if (!fromTop) return;
        if (event.getClick() != ClickType.SHIFT_LEFT
                && event.getClick() != ClickType.SHIFT_RIGHT) return;

        // Must be a container type (chest, barrel, hopper…)
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.CHEST
                && topType != InventoryType.BARREL
                && topType != InventoryType.HOPPER
                && topType != InventoryType.DISPENSER
                && topType != InventoryType.DROPPER) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Is it a magic item AND does the player have a magic bag?
        if (bagManager.classifyItem(clicked) < 0) return;
        if (!playerHasBag(player)) return;

        // Redirect to the bag
        event.setCancelled(true);
        ItemStack toAdd = clicked.clone();
        if (bagManager.addToBag(player.getUniqueId(), toAdd)) {
            event.setCurrentItem(new ItemStack(Material.AIR));
            player.sendMessage("§d✦ §7" + itemName(clicked) + " §7→ §dMagic Bag §8("
                    + MagicBagManager.sectionLabel(bagManager.classifyItem(clicked)) + "§8)");
        } else {
            event.setCancelled(false);
            player.sendMessage("§c✗ §7Magic Bag section full — item went to inventory.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

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
            case 0 -> '5';   // purple
            case 1 -> '9';   // blue
            case 2 -> 'b';   // cyan
            case 3 -> '2';   // green
            default -> '7';  // gray
        };
    }

    // ── Sort button action ────────────────────────────────────────────────

    /**
     * Sorts each section of the bag individually by stack amount (desc),
     * then updates the open GUI to reflect the new order.
     */
    private void sortBag(Player player, org.bukkit.inventory.Inventory topInv) {
        ItemStack[] bag = bagManager.getBag(player.getUniqueId());

        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            int start = s * MagicBagManager.SLOTS_PER_SECTION;
            ItemStack[] section = Arrays.copyOfRange(bag, start,
                    start + MagicBagManager.SLOTS_PER_SECTION);

            Arrays.sort(section, Comparator.comparingInt(
                    it -> (it == null || it.getType().isAir()) ? 0 : -it.getAmount()));

            for (int j = 0; j < MagicBagManager.SLOTS_PER_SECTION; j++) {
                bag[start + j] = section[j];
            }
        }

        bagGUI.populateItems(topInv, bag);
        bagManager.saveAsync(player.getUniqueId());
    }
}
