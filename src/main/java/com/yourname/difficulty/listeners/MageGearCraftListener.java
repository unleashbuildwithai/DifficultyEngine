package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * MageGearCraftListener — Intercepts crafting for Mage Gear pieces.
 *
 * When a player opens a crafting table and places the correct ingredients for
 * a mage gear recipe, this listener replaces the vanilla leather result with
 * the PDC-tagged, coloured Mage Gear item (Mage Hood, Mage Robe Top, etc.).
 *
 * Recipes (all shapeless, registered in Main.java):
 *   Mage Hood         — LEATHER_HELMET     + PURPLE_DYE + BLAZE_POWDER
 *   Mage Robe Top     — LEATHER_CHESTPLATE + PURPLE_DYE + BLAZE_POWDER
 *   Mage Robe Bottom  — LEATHER_LEGGINGS   + PURPLE_DYE + BLAZE_POWDER
 *   Mage Boots        — LEATHER_BOOTS      + PURPLE_DYE + BLAZE_POWDER
 *
 * The PDC-tagged result item has the arcane purple colour and the cooldown
 * reduction lore that the normal crafting table result would not have.
 */
public class MageGearCraftListener implements Listener {

    // Recipe key suffixes (must match keys registered in Main.java)
    private static final String KEY_HOOD     = "mage_hood_recipe";
    private static final String KEY_TOP      = "mage_robe_top_recipe";
    private static final String KEY_BOTTOM   = "mage_robe_bottom_recipe";
    private static final String KEY_BOOTS    = "mage_boots_recipe";

    private final ItemFactory itemFactory;

    public MageGearCraftListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /**
     * PrepareItemCraftEvent fires whenever the player's crafting matrix changes.
     * We replace the displayed result with the custom PDC item so the player sees
     * and receives the correctly-tagged Mage Gear, not a vanilla leather piece.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;

        NamespacedKey key  = recipe.getKey();
        String        name = key.getKey();

        ItemStack custom = switch (name) {
            case KEY_HOOD   -> itemFactory.buildMageGear(
                                    Material.LEATHER_HELMET,
                                    "§5✦ Mage Hood",
                                    "-250ms cooldown");

            case KEY_TOP    -> itemFactory.buildMageGear(
                                    Material.LEATHER_CHESTPLATE,
                                    "§5✦ Mage Robe Top",
                                    "-250ms cooldown");

            case KEY_BOTTOM -> itemFactory.buildMageGear(
                                    Material.LEATHER_LEGGINGS,
                                    "§5✦ Mage Robe Bottom",
                                    "-250ms cooldown");

            case KEY_BOOTS  -> itemFactory.buildMageGear(
                                    Material.LEATHER_BOOTS,
                                    "§5✦ Mage Boots",
                                    "-250ms cooldown");

            default -> null;
        };

        if (custom != null) {
            event.getInventory().setResult(custom);
        }
    }
}
