package com.yourname.difficulty.bag;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;

/**
 * MagicBagGUI — 4-page 54-slot inventory UI for the Magic Bag.
 *
 * ── Page layout (6 rows × 9 columns) ─────────────────────────────────────
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ Row 0 │ Coloured header panes + element name (the "top" bar)             │
 * │ Row 1 │ 9 item slots                                                     │
 * │ Row 2 │ 9 item slots                                                     │
 * │ Row 3 │ 9 item slots                                                     │
 * │ Row 4 │ 9 item slots   (36 item slots total, indices 9–44)               │
 * │ Row 5 │ [◀ Prev]  [gray] [gray] [gray] [⟳ Sort] [gray] [gray] [gray] [▶ Next] │
 * └──────────────────────────────────────────────────────────────────────────┘
 *
 * ── Pages ─────────────────────────────────────────────────────────────────
 *   Page 0 — 🔥 Fire   (universal inbox — accepts any item)
 *   Page 1 — 💧 Water
 *   Page 2 — 🌍 Earth
 *   Page 3 — 💨 Air
 *
 * ── Navigation ────────────────────────────────────────────────────────────
 *   PREV_SLOT (45) — Previous page button
 *   SORT_SLOT (49) — Auto-Sort button
 *   NEXT_SLOT (53) — Next page button
 */
public class MagicBagGUI {

    /** Inventory title for each page. Used to detect magic-bag inventories. */
    public static final String[] PAGE_TITLES = {
        "§c🔥 Fire",
        "§b💧 Water",
        "§2🌍 Earth",
        "§7💨 Air",
    };

    /** GUI inventory size. */
    public static final int SIZE = 54;

    /** Slots that hold player items (rows 1–4, gui slots 9–44). */
    public static final int ITEM_SLOT_FIRST = 9;
    public static final int ITEM_SLOT_LAST  = 44;

    /** Navigation / utility slots in the bottom bar (row 5). */
    public static final int PREV_SLOT = 45;
    public static final int SORT_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    // ── Page colours ──────────────────────────────────────────────────────────

    private static final Material[] PAGE_GLASS = {
        Material.RED_STAINED_GLASS_PANE,    // 🔥 Fire
        Material.CYAN_STAINED_GLASS_PANE,   // 💧 Water
        Material.LIME_STAINED_GLASS_PANE,   // 🌍 Earth
        Material.WHITE_STAINED_GLASS_PANE,  // 💨 Air
    };

    private final MagicBagManager bagManager;

    public MagicBagGUI(MagicBagManager bagManager) {
        this.bagManager = bagManager;
    }

    // ── Title helpers ─────────────────────────────────────────────────────────

    /** Returns {@code true} if the given title belongs to a magic-bag page. */
    public static boolean isMagicBagTitle(String title) {
        for (String t : PAGE_TITLES) {
            if (t.equals(title)) return true;
        }
        return false;
    }

    /** Returns the page index (0-3) for a title, or 0 if not matched. */
    public static int titleToPage(String title) {
        for (int i = 0; i < PAGE_TITLES.length; i++) {
            if (PAGE_TITLES[i].equals(title)) return i;
        }
        return 0;
    }

    // ── Open ──────────────────────────────────────────────────────────────────

    /**
     * Builds and opens the Magic Bag on the given {@code page} for the player.
     * Load from disk first if the bag hasn't been loaded yet.
     */
    public void open(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (!bagManager.hasBag(uuid)) bagManager.loadPlayer(uuid);

        Inventory inv = Bukkit.createInventory(null, SIZE, PAGE_TITLES[page]);
        populateFrame(inv, page);
        populateItems(inv, bagManager.getBag(uuid), page);
        player.openInventory(inv);
    }

    // ── Frame ─────────────────────────────────────────────────────────────────

    /**
     * Fills the header row and bottom navigation bar with glass panes and
     * navigation buttons.  Item slots (9–44) are left empty for content.
     */
    public void populateFrame(Inventory inv, int page) {
        Material glass    = PAGE_GLASS[page];
        String   pageName = MagicBagManager.pageLabel(page);

        // Row 0: full header bar in this page's colour
        ItemStack header = pane(glass, pageName,
                List.of("§7Any item can be placed here.",
                        "§8Click §7⟳ Auto-Sort §8to organise by element."));
        for (int i = 0; i < 9; i++) inv.setItem(i, header);

        // Row 5: navigation bar (gray fill + buttons)
        ItemStack gray = pane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, gray);

        // ◀ Prev page
        int prevPage = (page - 1 + MagicBagManager.PAGES) % MagicBagManager.PAGES;
        inv.setItem(PREV_SLOT, pane(PAGE_GLASS[prevPage],
                "§e◀ " + MagicBagManager.pageLabel(prevPage),
                List.of("§7Go to the previous page.")));

        // ⟳ Sort
        inv.setItem(SORT_SLOT, buildSortButton());

        // ▶ Next page
        int nextPage = (page + 1) % MagicBagManager.PAGES;
        inv.setItem(NEXT_SLOT, pane(PAGE_GLASS[nextPage],
                MagicBagManager.pageLabel(nextPage) + " §e▶",
                List.of("§7Go to the next page.")));
    }

    // ── Item population ───────────────────────────────────────────────────────

    /**
     * Writes items from the bag array (for the given {@code page}) into the
     * corresponding GUI item slots (9–44).
     */
    public void populateItems(Inventory inv, ItemStack[] bag, int page) {
        int bagStart = page * MagicBagManager.SLOTS_PER_PAGE;
        for (int j = 0; j < MagicBagManager.SLOTS_PER_PAGE; j++) {
            inv.setItem(ITEM_SLOT_FIRST + j, bag[bagStart + j]);
        }
    }

    /**
     * Reads items back from GUI slots (9–44) into the bag array for
     * the given {@code page}.
     */
    public void readItems(Inventory inv, ItemStack[] bag, int page) {
        int bagStart = page * MagicBagManager.SLOTS_PER_PAGE;
        for (int j = 0; j < MagicBagManager.SLOTS_PER_PAGE; j++) {
            ItemStack item = inv.getItem(ITEM_SLOT_FIRST + j);
            bag[bagStart + j] = (item != null && !item.getType().isAir())
                    ? item.clone() : null;
        }
    }

    // ── Slot classification ───────────────────────────────────────────────────

    /** Returns {@code true} if {@code slot} is an item storage slot (9–44). */
    public static boolean isItemSlot(int slot) {
        return slot >= ITEM_SLOT_FIRST && slot <= ITEM_SLOT_LAST;
    }

    /**
     * Returns the bag-array index for a GUI slot on the given page,
     * or {@code -1} if the slot is not an item slot.
     */
    public static int guiSlotToBagIndex(int slot, int page) {
        if (!isItemSlot(slot)) return -1;
        return page * MagicBagManager.SLOTS_PER_PAGE + (slot - ITEM_SLOT_FIRST);
    }

    // ── Sort button ───────────────────────────────────────────────────────────

    private ItemStack buildSortButton() {
        ItemStack it = new ItemStack(Material.HOPPER);
        ItemMeta m   = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§e⟳ Auto-Sort Bag");
            m.setLore(List.of(
                "§7Redistributes ALL items across all pages",
                "§7based on their magic element.",
                "§8Items land in random slots for that element."
            ));
            it.setItemMeta(m);
        }
        return it;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static ItemStack pane(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m   = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(lore);
            it.setItemMeta(m);
        }
        return it;
    }
}
