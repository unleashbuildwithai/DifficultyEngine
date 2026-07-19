package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.items.MeleeGearTier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * MeleeGearCraftListener — Intercepts crafting for all Melee Gear tiers.
 *
 * When a player places the correct ingredients for any melee gear recipe, this
 * listener replaces the vanilla armour result with the properly PDC-tagged,
 * tier-aware Melee Gear piece.
 *
 * ── Recipes ───────────────────────────────────────────────────────────────────
 *  IRON      (Lv  1): Iron piece + Iron Ingot
 *  DIAMOND   (Lv 40): Diamond piece + Diamond + Lapis Lazuli
 *  NETHERITE (Lv 70): Netherite piece + Netherite Ingot + Diamond
 *  DRAGON    (Lv 99): Netherite piece + Nether Star + Dragon Breath
 *
 * All recipes are registered in Main.java as ShapelessRecipes.
 * The listener identifies the tier by matching recipe key suffixes.
 */
public class MeleeGearCraftListener implements Listener {

    private final ItemFactory itemFactory;

    public MeleeGearCraftListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;

        NamespacedKey key  = recipe.getKey();
        String        name = key.getKey(); // e.g. "melee_iron_helmet"

        ItemStack custom = getCustomResult(name);
        if (custom != null) {
            event.getInventory().setResult(custom);
        }
    }

    private ItemStack getCustomResult(String recipeKey) {
        // ── IRON ──────────────────────────────────────────────────────────────
        if (recipeKey.equals("melee_iron_helmet"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.IRON, Material.IRON_HELMET,     "Helmet");
        if (recipeKey.equals("melee_iron_chestplate"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.IRON, Material.IRON_CHESTPLATE, "Chestplate");
        if (recipeKey.equals("melee_iron_leggings"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.IRON, Material.IRON_LEGGINGS,   "Leggings");
        if (recipeKey.equals("melee_iron_boots"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.IRON, Material.IRON_BOOTS,      "Boots");

        // ── DIAMOND ───────────────────────────────────────────────────────────
        if (recipeKey.equals("melee_diamond_helmet"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DIAMOND, Material.DIAMOND_HELMET,     "Helmet");
        if (recipeKey.equals("melee_diamond_chestplate"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DIAMOND, Material.DIAMOND_CHESTPLATE, "Chestplate");
        if (recipeKey.equals("melee_diamond_leggings"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DIAMOND, Material.DIAMOND_LEGGINGS,   "Leggings");
        if (recipeKey.equals("melee_diamond_boots"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DIAMOND, Material.DIAMOND_BOOTS,      "Boots");

        // ── NETHERITE ─────────────────────────────────────────────────────────
        if (recipeKey.equals("melee_netherite_helmet"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.NETHERITE, Material.NETHERITE_HELMET,     "Helmet");
        if (recipeKey.equals("melee_netherite_chestplate"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.NETHERITE, Material.NETHERITE_CHESTPLATE, "Chestplate");
        if (recipeKey.equals("melee_netherite_leggings"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.NETHERITE, Material.NETHERITE_LEGGINGS,   "Leggings");
        if (recipeKey.equals("melee_netherite_boots"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.NETHERITE, Material.NETHERITE_BOOTS,      "Boots");

        // ── DRAGON ────────────────────────────────────────────────────────────
        if (recipeKey.equals("melee_dragon_helmet"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DRAGON, Material.NETHERITE_HELMET,     "Helmet");
        if (recipeKey.equals("melee_dragon_chestplate"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DRAGON, Material.NETHERITE_CHESTPLATE, "Chestplate");
        if (recipeKey.equals("melee_dragon_leggings"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DRAGON, Material.NETHERITE_LEGGINGS,   "Leggings");
        if (recipeKey.equals("melee_dragon_boots"))
            return itemFactory.buildMeleeGearPiece(MeleeGearTier.DRAGON, Material.NETHERITE_BOOTS,      "Boots");

        return null;
    }
}
