package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CustomItemCraftListener — Intercepts registered shapeless crafting recipes
 * and replaces the vanilla placeholder result with the proper PDC-tagged
 * custom item from ItemFactory.
 *
 * ── Handled recipes ──────────────────────────────────────────────────────────
 *  soulfur_potion_recipe      → PDC Soulfur Potion     (POTION placeholder)
 *  turbo_minecart_recipe      → PDC Turbo Minecart      (MINECART placeholder)
 *  magic_bag_recipe           → PDC Magic Bag           (CHEST placeholder)
 *  de_earth_page_recipe_<X>   → PDC Earth Magic Page   (BOOK placeholder, 8 tiers)
 *
 * ── Earth Page Discovery ──────────────────────────────────────────────────────
 *  When a player picks up an Earth Magic Page (any tier) for the first time,
 *  the corresponding crafting recipe is unlocked in their recipe book.
 *  This means Earth Magic Page recipes are NOT auto-discovered on join —
 *  players must find one first before they can craft more.
 */
public class CustomItemCraftListener implements Listener {

    private final ItemFactory itemFactory;
    private final JavaPlugin  plugin;

    public CustomItemCraftListener(ItemFactory itemFactory, JavaPlugin plugin) {
        this.itemFactory = itemFactory;
        this.plugin      = plugin;
    }

    // ── PrepareItemCraftEvent: swap placeholder for real PDC item ─────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapelessRecipe recipe)) return;
        String key = recipe.getKey().getKey();

        switch (key) {
            case "soulfur_potion_recipe" ->
                event.getInventory().setResult(itemFactory.buildSoulfurPotion());

            case "turbo_minecart_recipe" ->
                event.getInventory().setResult(itemFactory.buildTurboMinecart());

            case "magic_bag_recipe" ->
                event.getInventory().setResult(itemFactory.buildMagicBag());

            case "empty_magic_bottle_recipe" ->
                event.getInventory().setResult(itemFactory.buildEmptyMagicBottle());

            default -> {
                // Check Earth Magic Page recipes (one per EarthBlockTier)
                for (EarthBlockTier tier : EarthBlockTier.values()) {
                    if (key.equals("de_earth_page_recipe_" + tier.name().toLowerCase())) {
                        event.getInventory().setResult(itemFactory.buildEarthMagicPage(tier));
                        return;
                    }
                }
            }
        }
    }

    // ── Earth Page discovery: unlock recipe on first pickup ───────────────────

    /**
     * When a player picks up an Earth Magic Page item, discover the crafting
     * recipe for that page tier in their recipe book.
     *
     * Earth Magic Page recipes are intentionally NOT auto-discovered on join —
     * players must first find/receive a page before they can see how to craft more.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupEarthPage(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item == null || !item.hasItemMeta()) return;

        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            NamespacedKey pageKey = itemFactory.getEarthPageKey(tier);
            if (pageKey != null && pdc.has(pageKey, PersistentDataType.BYTE)) {
                // Unlock the crafting recipe for this tier
                NamespacedKey recipeKey = new NamespacedKey(plugin,
                        "de_earth_page_recipe_" + tier.name().toLowerCase());
                player.discoverRecipe(recipeKey);
                break;
            }
        }
    }

    /**
     * Also discover the recipe when a player crafts an Earth Magic Page
     * (so they can immediately see the recipe for next time).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftEarthPage(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) return;

        var pdc = result.getItemMeta().getPersistentDataContainer();
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            NamespacedKey pageKey = itemFactory.getEarthPageKey(tier);
            if (pageKey != null && pdc.has(pageKey, PersistentDataType.BYTE)) {
                NamespacedKey recipeKey = new NamespacedKey(plugin,
                        "de_earth_page_recipe_" + tier.name().toLowerCase());
                player.discoverRecipe(recipeKey);
                break;
            }
        }
    }
}
