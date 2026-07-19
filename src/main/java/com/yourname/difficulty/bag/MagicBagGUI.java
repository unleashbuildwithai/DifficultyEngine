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
 * MagicBagGUI — 54-slot inventory UI for the Magic Bag.
 *
 * Layout (6 rows × 9 columns):
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ Row 0 │ Title bar — purple glass panes + "✦ Magic Bag ✦"            │
 * │ Row 1 │ [🔮 Runes §5] [item][item][item][item][item][item][item][item]│
 * │ Row 2 │ [⚗ Staffs §9] [item][item][item][item][item][item][item][item]│
 * │ Row 3 │ [📜 Spells §b] [item][item][item][item][item][item][item][item]│
 * │ Row 4 │ [🌿 Ingred §2] [item][item][item][item][item][item][item][item]│
 * │ Row 5 │ Bottom bar — sort button + info panes                        │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Glass panes: slots 0-8 (top bar), 9, 18, 27, 36 (section labels), 45-53 (bottom bar)
 * Item slots:  10-17 (sec 0), 19-26 (sec 1), 28-35 (sec 2), 37-44 (sec 3)
 */
public class MagicBagGUI {

    public static final String TITLE    = "§5✦ Magic Bag ✦";
    public static final int    SIZE     = 54;

    // ── GUI slot indices ──────────────────────────────────────────────────
    /** Section header (glass pane) slots — one per section. */
    public static final int[] SECTION_HEADER_SLOTS = {9, 18, 27, 36};

    /** Item storage slots per section, in order. */
    public static final int[][] SECTION_ITEM_SLOTS = {
        {10, 11, 12, 13, 14, 15, 16, 17},  // Section 0: Runes
        {19, 20, 21, 22, 23, 24, 25, 26},  // Section 1: Staffs
        {28, 29, 30, 31, 32, 33, 34, 35},  // Section 2: Spells
        {37, 38, 39, 40, 41, 42, 43, 44},  // Section 3: Ingredients
    };

    /** Sort / utility button slot. */
    public static final int SORT_SLOT = 49;

    // ── Section header colours ────────────────────────────────────────────
    private static final Material[] SECTION_GLASS = {
        Material.PURPLE_STAINED_GLASS_PANE,  // Sec 0: Runes
        Material.BLUE_STAINED_GLASS_PANE,    // Sec 1: Staffs
        Material.CYAN_STAINED_GLASS_PANE,    // Sec 2: Spells
        Material.GREEN_STAINED_GLASS_PANE,   // Sec 3: Ingredients
    };

    private final MagicBagManager bagManager;

    public MagicBagGUI(MagicBagManager bagManager) {
        this.bagManager = bagManager;
    }

    // ── Open ──────────────────────────────────────────────────────────────

    /** Builds and opens the Magic Bag inventory for the player. */
    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        // Load from disk if not already in memory
        if (!bagManager.hasBag(uuid)) bagManager.loadPlayer(uuid);

        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        populateFrame(inv);
        populateItems(inv, bagManager.getBag(uuid));
        player.openInventory(inv);
    }

    // ── Frame (glass panes) ───────────────────────────────────────────────

    private void populateFrame(Inventory inv) {
        ItemStack topPane = pane(Material.PURPLE_STAINED_GLASS_PANE, "§5✦ Magic Bag ✦",
                List.of("§7Your personal magic storage."));

        // Top bar (row 0)
        for (int i = 0; i < 9; i++) inv.setItem(i, topPane);

        // Section headers
        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            String label  = MagicBagManager.sectionLabel(s);
            int    header = SECTION_HEADER_SLOTS[s];
            inv.setItem(header, pane(SECTION_GLASS[s], label,
                    List.of("§8Slots: §7" + MagicBagManager.SLOTS_PER_SECTION,
                            "§8Auto-sorted when shift-clicking magic items from chests.")));
        }

        // Bottom bar (row 5)
        ItemStack bottomPane = pane(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 45; i < 54; i++) inv.setItem(i, bottomPane);

        // Sort button
        inv.setItem(SORT_SLOT, buildSortButton());
    }

    /** Fills the item slots from the player's bag array. */
    public void populateItems(Inventory inv, ItemStack[] bag) {
        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            for (int j = 0; j < MagicBagManager.SLOTS_PER_SECTION; j++) {
                int guiSlot  = SECTION_ITEM_SLOTS[s][j];
                int bagIndex = s * MagicBagManager.SLOTS_PER_SECTION + j;
                ItemStack item = (bag[bagIndex] != null) ? bag[bagIndex] : null;
                inv.setItem(guiSlot, item);
            }
        }
    }

    /** Reads items back from the GUI into the bag array. */
    public void readItems(Inventory inv, ItemStack[] bag) {
        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            for (int j = 0; j < MagicBagManager.SLOTS_PER_SECTION; j++) {
                int guiSlot  = SECTION_ITEM_SLOTS[s][j];
                int bagIndex = s * MagicBagManager.SLOTS_PER_SECTION + j;
                ItemStack item = inv.getItem(guiSlot);
                bag[bagIndex] = (item != null && !item.getType().isAir()) ? item.clone() : null;
            }
        }
    }

    // ── Sort button ───────────────────────────────────────────────────────

    private ItemStack buildSortButton() {
        ItemStack it = new ItemStack(Material.HOPPER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§e⟳ Auto-Sort Bag");
            m.setLore(List.of(
                "§7Click to sort items in each section",
                "§7by quantity (highest first).",
                "§8Items stay in their correct section."
            ));
            it.setItemMeta(m);
        }
        return it;
    }

    // ── Helper: coloured glass pane ───────────────────────────────────────

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

    // ── Slot classifier ───────────────────────────────────────────────────

    /**
     * Returns the bag-storage index for a GUI slot, or -1 if the slot is
     * a glass pane / button and should not be interactive.
     */
    public static int guiSlotToBagIndex(int slot) {
        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            for (int j = 0; j < MagicBagManager.SLOTS_PER_SECTION; j++) {
                if (SECTION_ITEM_SLOTS[s][j] == slot)
                    return s * MagicBagManager.SLOTS_PER_SECTION + j;
            }
        }
        return -1;
    }

    /** Returns which GUI section (0-3) a given GUI slot belongs to, or -1. */
    public static int guiSlotToSection(int slot) {
        for (int s = 0; s < MagicBagManager.SECTION_COUNT; s++) {
            for (int j = 0; j < MagicBagManager.SLOTS_PER_SECTION; j++) {
                if (SECTION_ITEM_SLOTS[s][j] == slot) return s;
            }
        }
        return -1;
    }
}
