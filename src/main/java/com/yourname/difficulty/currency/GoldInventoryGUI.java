package com.yourname.difficulty.currency;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GoldInventoryGUI — A 54-slot virtual chest that displays the player's gold
 * balance as visual gold blocks, keeping coins OUT of the main inventory.
 *
 * Opened with /inventory.
 *
 * Layout:
 *   Row 1: ─────── Title bar (gold block icon with balance) ───────
 *   Row 2-5: Gold coin display stacks (for visual representation)
 *   Row 6: Navigation / info bar
 *
 * The GUI is READ-ONLY — no items can be taken or placed.
 * All gold management is done through GoldManager (earn/spend).
 */
public class GoldInventoryGUI {

    /** Chest title — used to identify the GUI in inventory click events. */
    public static final String TITLE = "§6✦ Gold Vault";

    public static boolean isGoldVaultTitle(String title) {
        return title != null && title.startsWith("§6✦ Gold Vault");
    }

    /**
     * Opens the Gold Vault GUI for the player.
     *
     * @param player  the player to open it for
     * @param balance the player's current gold balance
     */
    public static void open(Player player, long balance) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        populate(inv, balance);
        player.openInventory(inv);
    }

    /** Populates the inventory with visual gold display. */
    public static void populate(Inventory inv, long balance) {
        // ── Header row (row 1, slots 0-8) ────────────────────────────────
        ItemStack header = makeItem(Material.GOLD_BLOCK,
                "§6§l✦ Gold Vault",
                "§e Balance: §6" + GoldManager.formatGold(balance) + " gp",
                "",
                "§7Your gold is kept safely here,",
                "§7separate from your main inventory.",
                "",
                "§8Earn gold by defeating mobs,",
                "§8completing quests, and trading.");

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, i == 4 ? header : makeGlass(Material.YELLOW_STAINED_GLASS_PANE, "§6───"));
        }

        // ── Gold coin stack visualization (rows 2-5, slots 9-44) ─────────
        // Split balance into stacks of: 1M, 100K, 10K, 1K, 100, 10, 1
        long remaining = balance;
        int slot = 9;
        long[] denominations = {1_000_000, 100_000, 10_000, 1_000, 100, 10, 1};
        String[] denomNames  = {"§61,000,000 gp", "§6100,000 gp", "§610,000 gp",
                                "§61,000 gp",    "§6100 gp",    "§610 gp",   "§61 gp"};
        Material[] denomMats = {Material.GOLD_BLOCK, Material.GOLD_INGOT, Material.GOLD_INGOT,
                                Material.GOLD_INGOT, Material.GOLD_NUGGET, Material.GOLD_NUGGET,
                                Material.GOLD_NUGGET};

        for (int d = 0; d < denominations.length && slot < 45; d++) {
            long count = remaining / denominations[d];
            remaining  = remaining % denominations[d];
            if (count <= 0) continue;

            // Stack in groups of 64
            while (count > 0 && slot < 45) {
                int stackAmt = (int) Math.min(count, 64);
                count -= stackAmt;
                ItemStack coin = new ItemStack(denomMats[d], stackAmt);
                ItemMeta m = coin.getItemMeta();
                if (m != null) {
                    m.setDisplayName(denomNames[d]);
                    m.setLore(List.of("§8×" + stackAmt + " stack(s)"));
                    coin.setItemMeta(m);
                }
                inv.setItem(slot++, coin);
            }
        }

        // Fill remaining slots with grey glass panes
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, makeGlass(Material.GRAY_STAINED_GLASS_PANE, "§8Empty"));
            }
        }

        // ── Footer row (row 6, slots 45-53) ──────────────────────────────
        ItemStack balanceBtn = makeItem(Material.EMERALD,
                "§a§l✦ Balance",
                "§e" + GoldManager.formatGold(balance) + " gp",
                "",
                "§7Earn more gold by:",
                "§8• Killing mobs (20% drop chance)",
                "§8• Defeating bosses",
                "§8• Completing quests",
                "§8• Nightmare difficulty for 100% gold");

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, i == 49 ? balanceBtn
                    : makeGlass(Material.YELLOW_STAINED_GLASS_PANE, "§6───"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ItemStack makeItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            List<String> loreList = new ArrayList<>();
            for (String l : lore) loreList.add(l);
            m.setLore(loreList);
            item.setItemMeta(m);
        }
        return item;
    }

    private static ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) { m.setDisplayName(name); item.setItemMeta(m); }
        return item;
    }
}
