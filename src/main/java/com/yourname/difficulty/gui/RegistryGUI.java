package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * RegistryGUI — A 54-slot chest inventory that mirrors every item registered
 * in {@link ItemFactory}.
 *
 * Clicking any item in this GUI gives the player a copy of that item.
 * All click logic is handled by {@link RegistryGUIListener}.
 *
 * Usage:
 *   registryGUI.open(player);   // opens the GUI for the player
 */
public class RegistryGUI {

    /**
     * The exact title used when creating the inventory.
     * {@link RegistryGUIListener} matches against this string to identify
     * GUI inventories without keeping a reference to the Inventory object.
     */
    public static final String TITLE = "§8✦ §6Item Registry §8✦";
    private static final int   SIZE  = 54;

    private final ItemFactory itemFactory;

    public RegistryGUI(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /**
     * Creates a fresh 54-slot inventory populated with all registered items
     * and opens it for the given player.
     *
     * Each call creates a new {@link Inventory} instance so multiple players
     * can have the GUI open simultaneously without sharing state.
     */
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        List<ItemStack> items = itemFactory.getAll();
        for (int slot = 0; slot < items.size() && slot < SIZE; slot++) {
            inv.setItem(slot, items.get(slot));
        }

        player.openInventory(inv);
    }
}
