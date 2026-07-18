package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.items.MageGearTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * MageGearCraftListener — Intercepts crafting for all Mage Gear tiers.
 *
 * When a player places the correct ingredients for any mage gear recipe, this
 * listener replaces the vanilla leather result with the properly PDC-tagged,
 * colour-tinted, tier-aware Mage Gear piece.
 *
 * ── Recipes ───────────────────────────────────────────────────────────────────
 *  APPRENTICE (Lv 1):  Leather + Purple Dye + String
 *  MAGE       (Lv 30): Leather + Purple Dye + Blaze Powder
 *  ALCH       (Lv 60): Leather + Blue Dye + Blaze Powder + Eye of Ender
 *  MASTER     (Lv 90): Leather + Black Dye + Blaze Powder + Enchanted Shard + Dragon Breath
 *
 * All recipes are registered in Main.java as ShapelessRecipes.
 * The listener identifies the tier by matching recipe key suffixes.
 */
public class MageGearCraftListener implements Listener {

    private final ItemFactory itemFactory;

    public MageGearCraftListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;

        NamespacedKey key  = recipe.getKey();
        String        name = key.getKey(); // e.g. "apprentice_hood_recipe"

        ItemStack custom = getCustomResult(name);
        if (custom != null) {
            event.getInventory().setResult(custom);
        }
    }

    private ItemStack getCustomResult(String recipeKey) {
        // ── APPRENTICE ────────────────────────────────────────────────────────
        if (recipeKey.equals("apprentice_hood_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.APPRENTICE, Material.LEATHER_HELMET,     "Hood");
        if (recipeKey.equals("apprentice_top_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.APPRENTICE, Material.LEATHER_CHESTPLATE, "Robe Top");
        if (recipeKey.equals("apprentice_bottom_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.APPRENTICE, Material.LEATHER_LEGGINGS,   "Robe Bottom");
        if (recipeKey.equals("apprentice_boots_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.APPRENTICE, Material.LEATHER_BOOTS,      "Boots");

        // ── MAGE (original) ───────────────────────────────────────────────────
        if (recipeKey.equals("mage_hood_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MAGE, Material.LEATHER_HELMET,     "Hood");
        if (recipeKey.equals("mage_robe_top_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MAGE, Material.LEATHER_CHESTPLATE, "Robe Top");
        if (recipeKey.equals("mage_robe_bottom_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MAGE, Material.LEATHER_LEGGINGS,   "Robe Bottom");
        if (recipeKey.equals("mage_boots_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MAGE, Material.LEATHER_BOOTS,      "Boots");

        // ── ALCH ──────────────────────────────────────────────────────────────
        if (recipeKey.equals("alch_hood_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.ALCH, Material.LEATHER_HELMET,     "Hood");
        if (recipeKey.equals("alch_top_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.ALCH, Material.LEATHER_CHESTPLATE, "Robe Top");
        if (recipeKey.equals("alch_bottom_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.ALCH, Material.LEATHER_LEGGINGS,   "Robe Bottom");
        if (recipeKey.equals("alch_boots_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.ALCH, Material.LEATHER_BOOTS,      "Boots");

        // ── MASTER ────────────────────────────────────────────────────────────
        if (recipeKey.equals("master_hood_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MASTER, Material.LEATHER_HELMET,     "Hood");
        if (recipeKey.equals("master_top_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MASTER, Material.LEATHER_CHESTPLATE, "Robe Top");
        if (recipeKey.equals("master_bottom_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MASTER, Material.LEATHER_LEGGINGS,   "Robe Bottom");
        if (recipeKey.equals("master_boots_recipe"))
            return itemFactory.buildMageGearPiece(MageGearTier.MASTER, Material.LEATHER_BOOTS,      "Boots");

        return null;
    }
}
