package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
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
        }
    }

    /**
     * When a player picks up a Dragon Armour Page, unlock all 4 Dragon Armour
     * piece recipes in their crafting book (they are NOT auto-discovered on join).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupDragonArmourPage(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item == null || !itemFactory.isDragonArmourPage(item)) return;

        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            player.discoverRecipe(new NamespacedKey(plugin, "dragon_armour_" + piece));
        }
        player.sendMessage("§6✦ §7The §e§lDragon Armour §7recipes have been unlocked in your crafting book!");
    }
}
