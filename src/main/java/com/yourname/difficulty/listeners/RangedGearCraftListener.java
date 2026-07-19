package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.items.RangedGearTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * RangedGearCraftListener — Intercepts crafting for all Ranged Gear tiers.
 *
 * When a player places the correct ingredients for any ranged gear recipe, this
 * listener replaces the vanilla armour result with the properly PDC-tagged,
 * tier-aware Ranged Gear piece.
 *
 * ── Recipes ───────────────────────────────────────────────────────────────────
 *  LEATHER   (Lv  1): Leather piece + String
 *  CHAIN     (Lv 40): Chainmail piece + Feather + Lapis Lazuli
 *  NETHERITE (Lv 70): Netherite piece + Netherite Ingot + Feather
 *  DRAGON    (Lv 99): Netherite piece + Nether Star + Arrow
 *
 * All recipes are registered in Main.java as ShapelessRecipes.
 * The listener identifies the tier by matching recipe key suffixes.
 */
public class RangedGearCraftListener implements Listener {

    private final ItemFactory itemFactory;

    public RangedGearCraftListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;

        NamespacedKey key  = recipe.getKey();
        String        name = key.getKey(); // e.g. "ranged_leather_helmet"

        ItemStack custom = getCustomResult(name);
        if (custom != null) {
            event.getInventory().setResult(custom);
        }
    }

    private ItemStack getCustomResult(String recipeKey) {
        // ── LEATHER ───────────────────────────────────────────────────────────
        if (recipeKey.equals("ranged_leather_helmet"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.LEATHER, Material.LEATHER_HELMET,     "Cap");
        if (recipeKey.equals("ranged_leather_chestplate"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.LEATHER, Material.LEATHER_CHESTPLATE, "Tunic");
        if (recipeKey.equals("ranged_leather_leggings"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.LEATHER, Material.LEATHER_LEGGINGS,   "Chaps");
        if (recipeKey.equals("ranged_leather_boots"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.LEATHER, Material.LEATHER_BOOTS,      "Boots");

        // ── CHAIN ─────────────────────────────────────────────────────────────
        if (recipeKey.equals("ranged_chain_helmet"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.CHAIN, Material.CHAINMAIL_HELMET,     "Coif");
        if (recipeKey.equals("ranged_chain_chestplate"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.CHAIN, Material.CHAINMAIL_CHESTPLATE, "Body");
        if (recipeKey.equals("ranged_chain_leggings"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.CHAIN, Material.CHAINMAIL_LEGGINGS,   "Chaps");
        if (recipeKey.equals("ranged_chain_boots"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.CHAIN, Material.CHAINMAIL_BOOTS,      "Boots");

        // ── NETHERITE ─────────────────────────────────────────────────────────
        if (recipeKey.equals("ranged_netherite_helmet"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.NETHERITE, Material.NETHERITE_HELMET,     "Helm");
        if (recipeKey.equals("ranged_netherite_chestplate"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.NETHERITE, Material.NETHERITE_CHESTPLATE, "Platebody");
        if (recipeKey.equals("ranged_netherite_leggings"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.NETHERITE, Material.NETHERITE_LEGGINGS,   "Platelegs");
        if (recipeKey.equals("ranged_netherite_boots"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.NETHERITE, Material.NETHERITE_BOOTS,      "Boots");

        // ── DRAGON ────────────────────────────────────────────────────────────
        if (recipeKey.equals("ranged_dragon_helmet"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.DRAGON, Material.NETHERITE_HELMET,     "Helm");
        if (recipeKey.equals("ranged_dragon_chestplate"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.DRAGON, Material.NETHERITE_CHESTPLATE, "Platebody");
        if (recipeKey.equals("ranged_dragon_leggings"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.DRAGON, Material.NETHERITE_LEGGINGS,   "Platelegs");
        if (recipeKey.equals("ranged_dragon_boots"))
            return itemFactory.buildRangedGearPiece(RangedGearTier.DRAGON, Material.NETHERITE_BOOTS,      "Boots");

        return null;
    }
}
