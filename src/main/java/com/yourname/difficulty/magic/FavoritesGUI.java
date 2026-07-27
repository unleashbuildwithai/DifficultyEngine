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
 * FavoritesGUI — Chest inventory for managing passive elemental proc favorites.
 *
 * ── REWORK NOTE ────────────────────────────────────────────────────────────
 * The old combo-chain row (8 chain items) has been REMOVED ENTIRELY along
 * with the combo-chain system itself. This GUI now only manages the 4
 * passive elemental procs (Fire/Water/Earth/Air).
 *
 * ── Layout (27 slots / 3 rows) ────────────────────────────────────────────────
 *  Row 1 (0–3):  4 proc items (Fire/Water/Earth/Air) + info item at slot 8
 *  Row 2 (9–17): glass pane fillers
 *  Row 3 (18–26):
 *    Slot 18 — help text item
 *    Slot 22 — "Read Full Tome" (opens written book)
 *    Slot 26 — "Close"
 *
 * ── Interaction ───────────────────────────────────────────────────────────────
 *  Left-click a proc item → toggle ⭐ / ○ (handled by FavoritesGUIListener)
 *  Shift-click → also toggles
 *
 * ── Title encoding ────────────────────────────────────────────────────────────
 *  "§d✦ Combo Favorites §8[favgui]" — the "[favgui]" tag lets FavoritesGUIListener
 *  identify this inventory without a UUID map.
 */
public class FavoritesGUI {

    public static final String GUI_TAG = "[favgui]";
    public static final String TITLE   = "§d✦ Proc Favorites §8" + GUI_TAG;

    private final ComboFavoritesManager favManager;
    private final SpellBookManager      spellBookManager;

    public FavoritesGUI(ComboFavoritesManager favManager, SpellBookManager spellBookManager) {
        this.favManager       = favManager;
        this.spellBookManager = spellBookManager;
    }

    /** Opens the favorites GUI for the given player. */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        Set<String> favs         = favManager.getFavorites(player.getUniqueId());
        Set<Integer> unlocked    = spellBookManager.getUnlockedPages(player.getUniqueId());

        // ── Row 1: 4 passive elemental procs — gated behind their Arcane Tome page ──
        for (int i = 0; i < ComboFavoritesManager.PROC_TAGS.size(); i++) {
            String  procTag       = ComboFavoritesManager.PROC_TAGS.get(i);
            Integer requiredPage  = ComboFavoritesManager.PROC_REQUIRED_PAGE.get(procTag);
            boolean pageUnlocked  = (requiredPage == null) || unlocked.contains(requiredPage);

            if (pageUnlocked) {
                boolean starred = favs.contains(procTag);
                inv.setItem(i, buildProcItem(procTag, starred));
            } else {
                inv.setItem(i, buildLockedChainItem(procTag, requiredPage));
            }
        }

        // Slot 8 — Info item
        inv.setItem(8, buildInfoItem());

        ItemStack pane = buildPane();
        for (int i = ComboFavoritesManager.PROC_TAGS.size(); i <= 17; i++) {
            if (i == 8) continue;
            inv.setItem(i, pane);
        }

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

    /** Rebuilds a single proc slot after toggling — called by the listener. */
    public void refreshChainSlot(Inventory inv, String tag, boolean nowStarred) {
        int procIdx = ComboFavoritesManager.PROC_TAGS.indexOf(tag);
        if (procIdx >= 0) {
            inv.setItem(procIdx, buildProcItem(tag, nowStarred));
        }
    }


    /** Updates the help slot text (changes when favorites become empty/non-empty). */
    public void refreshHelpSlot(Inventory inv, boolean isEmpty) {
        inv.setItem(18, buildHelpItem(isEmpty));
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    /**
     * Builds the item for a passive elemental proc (FIRE_PROC, WATER_PROC,
     * EARTH_PROC, AIR_PROC), with lore explaining the real dice-roll mechanic
     * and any element-specific escape/interaction mechanics.
     */
    private ItemStack buildProcItem(String procTag, boolean starred) {
        String name;
        String colorCode;
        String desc;
        String extra;
        int    levelReq;
        switch (procTag) {
            case ComboFavoritesManager.FIRE_PROC  -> {
                name = "Fire Proc: Burn"; colorCode = "§c";
                desc = "Fire DoT + brief slow";
                extra = "§7Target can channel §bDownpour §7to extinguish early!";
                levelReq = ElementalProcManager.FIRE_LEVEL_REQ;
            }
            case ComboFavoritesManager.WATER_PROC -> {
                name = "Water Proc: Wet"; colorCode = "§b";
                desc = "Slow debuff";
                extra = "§7Boosted chance while §bDownpour §7is active!";
                levelReq = ElementalProcManager.WATER_LEVEL_REQ;
            }
            case ComboFavoritesManager.EARTH_PROC -> {
                name = "Earth Proc: Muddy"; colorCode = "§2";
                desc = "Heavy slow debuff";
                extra = "§7Mine a block with a pickaxe to break free!";
                levelReq = ElementalProcManager.EARTH_LEVEL_REQ;
            }
            case ComboFavoritesManager.AIR_PROC   -> {
                name = "Air Proc: Chilled/Frozen"; colorCode = "§f";
                desc = "Stun scaling with your Magic level (0.5s-5s)";
                extra = "§7Holding a §cFire Staff §7melts it away faster!";
                levelReq = ElementalProcManager.AIR_LEVEL_REQ;
            }
            default -> { name = "Unknown Proc"; colorCode = "§7"; desc = "?"; extra = ""; levelReq = 0; }
        }

        ItemStack item = starred
            ? new ItemStack(Material.ENCHANTED_BOOK)
            : new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String starPrefix = starred ? "§e⭐ " : "§8○ ";
            meta.setDisplayName(starPrefix + colorCode + name);

            List<String> lore = new ArrayList<>();
            lore.add("§8" + "─".repeat(24));
            lore.add("§7Passive proc — §fANY basic hit");
            lore.add("§7Effect: " + colorCode + desc);
            lore.add("§7Requires: §eMagic Lv " + levelReq + "+");
            if (!extra.isEmpty()) {
                lore.add("§8" + "─".repeat(24));
                lore.add(extra);
            }
            lore.add("§8" + "─".repeat(24));
            lore.add("§7Base proc chance: §f15%");
            lore.add(starred
                ? "§e⭐ Favorited bonus: §a+15% §7(§f30% total§7)"
                : "§8○ Favorite this for §a+15% §7chance!");
            lore.add("§8" + "─".repeat(24));
            if (starred) {
                lore.add("§e⭐ §aFAVORITED §7— boosted rate");
                lore.add("§7Click to §cun-star");
            } else {
                lore.add("§8○ §7NOT FAVORITED — base rate only");
                lore.add("§7Click to §aStar §7(boost chance§7)");
            }
            meta.setLore(lore);

            if (starred) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Builds a locked placeholder item for a proc whose Arcane Tome page
     * has not yet been unlocked by the player.
     */
    private ItemStack buildLockedChainItem(String tag, int requiredPageIndex) {

        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8🔒 §7???");
            meta.setLore(List.of(
                "§8" + "─".repeat(24),
                "§7This proc is hidden.",
                "§8" + "─".repeat(24),
                "§7Unlock §dArcane Tome §7page §d" + (requiredPageIndex + 1),
                "§7to reveal this proc.",
                "§8" + "─".repeat(24),
                "§8Find §dSpell Pages §8(4% mob drop)",
                "§8and right-click to absorb them!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Returns true if the item in the given slot is a locked proc placeholder.
     * Used by FavoritesGUIListener to skip toggle on locked slots.
     */
    public static boolean isLockedSlot(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_DYE) return false;
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return false;
        return item.getItemMeta().getDisplayName().startsWith("§8🔒");
    }

    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d✦ Elemental Proc Guide");
            meta.setLore(List.of(
                "§8" + "─".repeat(24),
                "§7Star (⭐) a proc to boost its",
                "§7chance from §f15% §7to §a30%§7.",
                "§8" + "─".repeat(24),
                "§7Every proc rolls independently",
                "§7on ANY matching basic hit —",
                "§7no combos, no chains needed.",
                "",
                "§7🔥 Fire → §cBurn",
                "§7💧 Water → §bWet",
                "§7🌿 Earth → §2Muddy",
                "§7💨 Air → §fChilled/Frozen",
                "§8" + "─".repeat(24),
                "§8Nothing starred = base 15% only."
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
                    "§7All procs still trigger at their",
                    "§7base §f15% §7chance — but nothing",
                    "§7is boosted.",
                    "§7Click procs above to star them!"
                ));
            } else {
                meta.setDisplayName("§a✓ §7Boosted chance for starred procs");
                meta.setLore(List.of(
                    "§7Starred procs get a §a+15% §7chance",
                    "§7boost §8(30% total)§7.",
                    "§8(No book required)"
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
                "§7to read all elemental knowledge.",
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

    /** Slot index → proc tag, or null if the slot is not a togglable item. */
    public static String tagAtSlot(int slot) {
        if (slot >= 0 && slot < ComboFavoritesManager.PROC_TAGS.size()) {
            return ComboFavoritesManager.PROC_TAGS.get(slot);
        }
        return null;
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
