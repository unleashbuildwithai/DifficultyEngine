package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ShapelessRecipe;

/**
 * MagicCauldronCraftListener — Intercepts registered Magic Cauldron recipes and
 * replaces the vanilla placeholder output with a proper PDC-tagged Rune Dust item.
 *
 * ── How Magic Cauldron recipes work ──────────────────────────────────────────
 *  1. Shapeless recipes are registered in Main.java with a GLOWSTONE_DUST
 *     placeholder as the result (vanilla won't allow PDC items in recipes).
 *  2. This listener fires on PrepareItemCraftEvent and swaps the placeholder for
 *     the real PDC-tagged Rune Dust item.
 *  3. The player takes the item normally — CraftItemEvent consumes all ingredients.
 *
 * ── Recipe yields ─────────────────────────────────────────────────────────────
 *  BASIC (no Diamond):
 *    CAULDRON + LAVA_BUCKET  + 4× NETHERRACK        → 16 Fire Rune Dust
 *    CAULDRON + 2× WATER_BUCKET + 4× PRISMARINE     → 16 Water Rune Dust
 *    CAULDRON + WATER_BUCKET + 4× DIRT              → 16 Earth Rune Dust
 *    CAULDRON + PUFFERFISH   + WATER_BUCKET         → 16 Air Rune Dust
 *
 *  PREMIUM (add 1 Diamond — 5× more dust!):
 *    Same ingredients + 1× DIAMOND                  → 80 Rune Dust of that element
 *
 *  Converting dust to runes (separate crafting recipe):
 *    4× Rune Dust → 8 Runes  (i.e., 1 dust = 2 runes)
 *    16 basic dust  → 32 Runes
 *    80 premium dust → 160 Runes
 */
public class MagicCauldronCraftListener implements Listener {

    private static final int YIELD_BASIC   = 16;
    private static final int YIELD_PREMIUM = 80;

    private final ItemFactory itemFactory;

    public MagicCauldronCraftListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;

        String key = recipe.getKey().getKey();

        switch (key) {
            case "cauldron_fire_basic"    -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.FIRE,  YIELD_BASIC));
            case "cauldron_fire_premium"  -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.FIRE,  YIELD_PREMIUM));
            case "cauldron_water_basic"   -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.WATER, YIELD_BASIC));
            case "cauldron_water_premium" -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.WATER, YIELD_PREMIUM));
            case "cauldron_earth_basic"   -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.EARTH, YIELD_BASIC));
            case "cauldron_earth_premium" -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.EARTH, YIELD_PREMIUM));
            case "cauldron_air_basic"     -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.AIR,   YIELD_BASIC));
            case "cauldron_air_premium"   -> event.getInventory().setResult(
                    itemFactory.buildRuneDust(MagicElement.AIR,   YIELD_PREMIUM));
            default -> { /* not our recipe */ }
        }
    }
}
