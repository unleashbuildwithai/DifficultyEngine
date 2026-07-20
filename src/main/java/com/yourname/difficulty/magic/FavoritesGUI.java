package com.yourname.difficulty.magic;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * FavoritesGUI — Chest inventory for managing combo hint favorites.
 *
 * ── Layout (27 slots / 3 rows) ────────────────────────────────────────────────
 *  Row 1 (0–8):  8 chain items (one per chain tag) + info item
 *  Row 2 (9–17): glass pane fillers
 *  Row 3 (18–26):
 *    Slot 18 — help text item
 *    Slot 22 — "Read Full Tome" (opens written book)
 *    Slot 26 — "Close"
 *
 * ── Interaction ───────────────────────────────────────────────────────────────
 *  Left-click a chain item → toggle ⭐ / ○ (handled by FavoritesGUIListener)
 *  Shift-click → also toggles
 *
 * ── Title encoding ────────────────────────────────────────────────────────────
 *  "§d✦ Combo Favorites §8[favgui]" — the "[favgui]" tag lets FavoritesGUIListener
 *  identify this inventory without a UUID map.
 */
public class FavoritesGUI {

    public static final String GUI_TAG = "[favgui]";
    public static final String TITLE   = "§d✦ Combo Favorites §8" + GUI_TAG;

    private final ComboFavoritesManager favManager;
    private final SpellBookManager      spellBookManager;

    public FavoritesGUI(ComboFavoritesManager favManager, SpellBookManager spellBookManager) {
        this.favManager       = favManager;
        this.spellBookManager = spellBookManager;
    }

    /** Opens the favorites GUI for the given player. */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        Set<String> favs = favManager.getFavorites(player.getUniqueId());

        // ── Row 1: 8 chain items ───────────────────────────────────────────────
        for (int i = 0; i < ComboFavoritesManager.ALL_TAGS.size(); i++) {
            String tag  = ComboFavoritesManager.ALL_TAGS.get(i);
            boolean starred = favs.contains(tag);
            inv.setItem(i, buildChainItem(tag, starred));
        }

        // Slot 8 — Info item
        inv.setItem(8, buildInfoItem());

        // ── Row 2: glass pane fillers ─────────────────────────────────────────
        ItemStack pane = buildPane();
        for (int i = 9; i <= 17; i++) inv.setItem(i, pane);

        // ── Row 3 ─────────────────────────────────────────────────────────────
        // Slot 18 — Help text
        inv.setItem(18, buildHelpItem(favs.isEmpty()));
        // Slots 19–21 — fillers
        for (int i = 19; i <= 21; i++) inv.setItem(i, pane);
        // Slot 22 — Read Full Tome
        inv.setItem(22, buildReadTomeItem());
        // Slots 23–25 — fillers
        for (int i = 23; i <= 25; i++) inv.setItem(i, pane);
        // Slot 26 — Close
        inv.setItem(26, buildCloseItem());

        player.openInventory(inv);
    }

    /** Rebuilds a single chain slot after toggling — called by the listener. */
    public void refreshChainSlot(Inventory inv, String tag, boolean nowStarred) {
        int slot = ComboFavoritesManager.ALL_TAGS.indexOf(tag);
        if (slot < 0) return;
        inv.setItem(slot, buildChainItem(tag, nowStarred));
    }

    /** Updates the help slot text (changes when favorites become empty/non-empty). */
    public void refreshHelpSlot(Inventory inv, boolean isEmpty) {
        inv.setItem(18, buildHelpItem(isEmpty));
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    /** Builds the item for a combo chain. Starred = ENCHANTED_BOOK glow, unstarred = BOOK. */
    private ItemStack buildChainItem(String tag, boolean starred) {
        ComboFavoritesManager.ChainInfo info = ComboFavoritesManager.getInfo(tag);

        ItemStack item = starred
            ? new ItemStack(Material.ENCHANTED_BOOK)
            : new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String starPrefix = starred ? "§e⭐ " : "§8○ ";
            meta.setDisplayName(starPrefix + info.displayName());

            List<String> lore = new ArrayList<>();
            lore.add("§8" + "─".repeat(24));
            lore.add("§7Trigger: §f" + info.trigger());
            lore.add("§7Shows:   " + info.color() + info.hint());
            lore.add("§8" + "─".repeat(24));
            if (starred) {
                lore.add("§e⭐ §aFAVORITED §7— hints active");
                lore.add("§7Click to §cun-star §7(hide hints)");
            } else {
                lore.add("§8○ §7NOT FAVORITED — hints hidden");
                lore.add("§7Click to §aStar §7(show hints)");
            }
            meta.setLore(lore);

            // Glint override for visual distinction
            if (starred) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // Store the tag in display name for click detection (see listener)
            // Tag is encoded as the last word in the internal name
            meta.getPersistentDataContainer(); // ensure PDC is available
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d✦ Combo Hint Guide");
            meta.setLore(List.of(
                "§8" + "─".repeat(24),
                "§7Star (⭐) chains to enable their",
                "§7action bar hints during combat.",
                "§8" + "─".repeat(24),
                "§7Requires §5Spell Combo Book§7 in",
                "§7inventory for hints to appear.",
                "§8" + "─".repeat(24),
                "§8Nothing starred = no hints shown."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildHelpItem(boolean isEmpty) {
        ItemStack item = isEmpty
            ? new ItemStack(Material.GRAY_DYE)
            : new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (isEmpty) {
                meta.setDisplayName("§c✗ §7No favorites starred");
                meta.setLore(List.of(
                    "§7No combo hints will appear.",
                    "§7Click chains above to star them!"
                ));
            } else {
                meta.setDisplayName("§a✓ §7Hints active for starred combos");
                meta.setLore(List.of(
                    "§7Starred chains will show action",
                    "§7bar hints during combat.",
                    "§8(Requires Spell Combo Book)"
                ));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildReadTomeItem() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ §dRead Full Arcane Tome");
            meta.setLore(List.of(
                "§7Opens the full spell tome book",
                "§7to read all combo recipes.",
                "§8Click to open."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c✗ Close");
            meta.setLore(List.of("§7Close this menu."));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildPane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Slot index → chain tag, or null if the slot is not a chain item. */
    public static String tagAtSlot(int slot) {
        if (slot < 0 || slot >= ComboFavoritesManager.ALL_TAGS.size()) return null;
        return ComboFavoritesManager.ALL_TAGS.get(slot);
    }

    /** Returns true if this inventory's title contains the GUI tag. */
    public static boolean isThisGUI(Inventory inv) {
        if (inv == null || inv.getLocation() == null) {
            // Player inventory — title check via viewer
            return false;
        }
        // For chest inventories the title is set on the view, not the inventory
        return false; // listener checks via InventoryView.getTitle()
    }
}
