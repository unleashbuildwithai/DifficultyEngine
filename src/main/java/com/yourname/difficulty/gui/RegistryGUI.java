package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * RegistryGUI — 54-slot chest GUI with 10 categorised pages.
 *
 * Pages:
 *   1  — Core Items        (Soulfur Potion, Turbo Minecart, Shard, Magic Bag)
 *   2  — Staffs & Runes   (4 staffs, 4 runes, 4 rune dusts, combo book, tome)
 *   3  — Mage Gear        (4 tiers × 4 pieces + Ancient Kill Tome)
 *   4  — Melee Gear       (4 tiers × 4 pieces + GunZ Sword)
 *   5  — Ranged Gear      (4 tiers × 4 pieces + Dark Bow + Dragon Arrows)
 *   6  — Earth Magic      (8 Earth Magic Pages, one per tier)
 *   7  — Magic Books      (Novice → Master lore books)
 *   8  — Capes & Cosmetics (skill capes, Unicorn Slippers, Rainbow Axolotl)
 *   9  — Support Staff    (Support Rune + 7 Support Pages)
 *   10 — Lightning Magic  (Catching Block, Empty/Charged Magic Bottles, Quest Book)
 *
 * Title format:
 *   Page 1 → "§8✦ §6Item Registry §8✦"
 *   Page N → "§8✦ §6Item Registry §8✦ §8[N]"
 *
 * Nav row (slot 45–53):
 *   45 = Prev arrow (hidden on page 1)
 *   49 = Page label
 *   53 = Next arrow (hidden on last page)
 */
public class RegistryGUI {

    public static final int    PAGE_COUNT     = 11;
    public static final int    ITEMS_PER_PAGE = 45;
    private static final int   SIZE           = 54;
    private static final int   SLOT_PREV      = 45;
    private static final int   SLOT_LABEL     = 49;
    private static final int   SLOT_NEXT      = 53;

    private static final String TITLE_BASE = "§8✦ §6Item Registry §8✦";

    private static final String[] PAGE_NAMES = {
        "",                      // index 0 unused
        "§7Core Items",          // 1
        "§bStaffs & Runes",      // 2
        "§5Mage Gear",           // 3
        "§7Melee Gear",          // 4
        "§eRanged Gear",         // 5
        "§2Earth Magic",         // 6
        "§6Magic Books",         // 7
        "§dCapes & Cosmetics",   // 8
        "§5Support Staff",       // 9
        "§b⚡ Lightning Magic",  // 10
        "§4Boss Spawners"        // 11
    };

    private final ItemFactory itemFactory;

    public RegistryGUI(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    // ── Title helpers ─────────────────────────────────────────────────────────

    public static String titleForPage(int page) {
        return page == 1 ? TITLE_BASE : TITLE_BASE + " §8[" + page + "]";
    }

    public static int pageFromTitle(String title) {
        if (title == null) return -1;

        // Strip colors to make it 100% robust on all Paper/Purpur/Spigot versions
        String cleanTitle = title.replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();
        String cleanBase = TITLE_BASE.replaceAll("(?i)§[0-9A-FK-ORX]", "").trim();

        if (cleanBase.equals(cleanTitle)) return 1;

        String cleanPrefix = cleanBase + " [";
        if (cleanTitle.startsWith(cleanPrefix) && cleanTitle.endsWith("]")) {
            try {
                return Integer.parseInt(cleanTitle.substring(cleanPrefix.length(), cleanTitle.length() - 1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    // ── Open ─────────────────────────────────────────────────────────────────

    public void open(Player player) { openPage(player, 1); }

    public void openPage(Player player, int page) {
        if (page < 1 || page > PAGE_COUNT) page = 1;
        String title = titleForPage(page);
        List<ItemStack> items = itemFactory.getPageItems(page);

        Inventory inv = Bukkit.createInventory(null, SIZE, title);

        for (int i = 0; i < items.size() && i < ITEMS_PER_PAGE; i++) {
            inv.setItem(i, items.get(i));
        }

        // ── Navigation row ────────────────────────────────────────────────────
        ItemStack glass = filler();
        for (int s = 45; s < SIZE; s++) inv.setItem(s, glass);

        if (page > 1) {
            inv.setItem(SLOT_PREV, navButton("§a« Previous",
                "§7Go to §6Page " + (page - 1) + "§7:",
                "§8  " + PAGE_NAMES[page - 1]));
        }

        inv.setItem(SLOT_LABEL, pageLabel(page));

        if (page < PAGE_COUNT) {
            inv.setItem(SLOT_NEXT, navButton("§aNext Page »",
                "§7Go to §6Page " + (page + 1) + "§7:",
                "§8  " + PAGE_NAMES[page + 1]));
        }

        player.openInventory(inv);
    }

    /**
     * Updates an ALREADY-OPEN registry inventory in-place for the given page.
     * Because the inventory object stays the same (just its contents change),
     * Minecraft never sends a "close inventory" packet, so the player's cursor
     * position is preserved — no jarring teleport to the centre.
     *
     * The inventory title cannot be changed without a reopen in most versions,
     * so we update a title tracker via the page-label item instead.
     *
     * @param inv  the top inventory currently open for the player
     * @param page the page to navigate to (1–PAGE_COUNT)
     */
    public void updateInventoryPage(Inventory inv, int page) {
        if (page < 1 || page > PAGE_COUNT) return;
        List<ItemStack> items = itemFactory.getPageItems(page);

        inv.clear();

        for (int i = 0; i < items.size() && i < ITEMS_PER_PAGE; i++) {
            inv.setItem(i, items.get(i));
        }

        // Navigation row
        ItemStack glass = filler();
        for (int s = 45; s < 54; s++) inv.setItem(s, glass);

        if (page > 1) {
            inv.setItem(SLOT_PREV, navButton("§a« Previous",
                "§7Go to §6Page " + (page - 1) + "§7:",
                "§8  " + PAGE_NAMES[page - 1]));
        }
        inv.setItem(SLOT_LABEL, pageLabel(page));
        if (page < PAGE_COUNT) {
            inv.setItem(SLOT_NEXT, navButton("§aNext Page »",
                "§7Go to §6Page " + (page + 1) + "§7:",
                "§8  " + PAGE_NAMES[page + 1]));
        }
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
            m.setDisplayName("§6Item Registry §8— §ePage " + page + " §8/ §e" + PAGE_COUNT + " §8pages");
            m.setLore(List.of(
                "§8" + "─".repeat(24),
                "§7Page §e1§8:  §7Core Items",
                "§7Page §e2§8:  §bStaffs & Runes",
                "§7Page §e3§8:  §5Mage Gear",
                "§7Page §e4§8:  §7Melee Gear",
                "§7Page §e5§8:  §eRanged Gear",
                "§7Page §e6§8:  §2Earth Magic Pages",
                "§7Page §e7§8:  §6Magic Books",
                "§7Page §e8§8:  §dCapes & Cosmetics",
                "§7Page §e9§8:  §5Support Staff",
                "§7Page §e10§8: §b⚡ Lightning Magic",
                "§8" + "─".repeat(24),
                "§8Clicking any item gives you a copy.",
                "§8[DifficultyEngine — Item Registry]"
            ));
            it.setItemMeta(m);
        }
        return it;
    }
}
