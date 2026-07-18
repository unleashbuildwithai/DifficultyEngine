package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * RegistryGUI — A 54-slot chest inventory that mirrors every registered item.
 *
 * Layout (54 slots, 6 rows):
 *   Rows 0–4 (slots 0–44): items for the current page (max 45 per page)
 *   Row 5   (slots 45–53): navigation bar
 *     slot 45 = Previous Page button  (hidden on page 1)
 *     slot 49 = Page indicator (book)
 *     slot 53 = Next Page button      (hidden on last page)
 *
 * Clicking any item in rows 0–4 gives the player a copy of that item.
 * Page navigation is handled by {@link RegistryGUIListener}.
 */
public class RegistryGUI {

    /** Items per content page (rows 0-4). */
    public static final int ITEMS_PER_PAGE = 45;

    public static final String TITLE_P1 = "§8✦ §6Item Registry §8✦";
    public static final String TITLE_P2 = "§8✦ §6Item Registry §8§f‹2›";

    private static final int SIZE          = 54;
    private static final int SLOT_PREV     = 45;
    private static final int SLOT_LABEL    = 49;
    private static final int SLOT_NEXT     = 53;

    private final ItemFactory itemFactory;

    public RegistryGUI(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    // ── Open page 1 ───────────────────────────────────────────────────────────

    public void open(Player player) {
        openPage(player, 1);
    }

    // ── Open a specific page ──────────────────────────────────────────────────

    public void openPage(Player player, int page) {
        String title  = page == 1 ? TITLE_P1 : TITLE_P2;
        List<ItemStack> items = page == 1
            ? itemFactory.getPage1()
            : itemFactory.getPage2();

        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        // Fill content rows (0–44)
        for (int i = 0; i < items.size() && i < ITEMS_PER_PAGE; i++) {
            inv.setItem(i, items.get(i));
        }

        // ── Navigation row ────────────────────────────────────────────────────

        // Filler glass for the rest of row 5
        ItemStack glass = filler();
        for (int s = 45; s < SIZE; s++) inv.setItem(s, glass);

        // Previous page button (page 2 only)
        if (page == 2) {
            inv.setItem(SLOT_PREV, navButton(
                "§a« Previous Page",
                "§7Click to view §6Page 1§7:",
                "§8  Core items, staffs, runes,",
                "§8  mage gear & combo books."
            ));
        }

        // Page indicator
        inv.setItem(SLOT_LABEL, pageLabel(page));

        // Next page button (page 1 only)
        if (page == 1) {
            inv.setItem(SLOT_NEXT, navButton(
                "§aNext Page »",
                "§7Click to view §6Page 2§7:",
                "§8  Lore books (novice → master)",
                "§8  and all skill capes."
            ));
        }

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta  m = g.getItemMeta();
        if (m != null) { m.setDisplayName("§8"); g.setItemMeta(m); }
        return g;
    }

    private ItemStack navButton(String name, String... loreLines) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta  m  = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            m.setLore(List.of(loreLines));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack pageLabel(int page) {
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta  m  = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§6Item Registry §8— §ePage " + page + "§8/§e2");
            m.setLore(List.of(
                "§8" + "─".repeat(24),
                "§7Page §e1§8: §7Core magic items",
                "§7Page §e2§8: §7Lore books & capes",
                "§8" + "─".repeat(24),
                "§8Clicking any item gives you a copy.",
                "§8[DifficultyEngine — Item Registry]"
            ));
            it.setItemMeta(m);
        }
        return it;
    }
}
