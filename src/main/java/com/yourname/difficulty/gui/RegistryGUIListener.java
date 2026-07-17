package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * RegistryGUIListener — Handles all click interactions inside the RegistryGUI.
 *
 * When a player clicks a non-empty slot in the GUI:
 *   • The click is always cancelled (items stay in the GUI).
 *   • Permission-restricted items require the appropriate permission node.
 *   • Permitted items are cloned and added to the player's inventory.
 *
 * Permission tiers:
 *   Turbo Minecart  → difficultyengine.turbocart
 *   Skill Capes     → difficultyengine.cape.admin
 *   Max Cape        → difficultyengine.cape.admin
 */
public class RegistryGUIListener implements Listener {

    private final ItemFactory itemFactory;

    public RegistryGUIListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!RegistryGUI.TITLE.equals(event.getView().getTitle())) return;

        // Always cancel — items must never leave the registry inventory
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only act on clicks inside the top inventory
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // ── Per-item permission checks ─────────────────────────────────────────

        if (itemFactory.isTurboMinecart(clicked)) {
            if (!player.hasPermission("difficultyengine.turbocart")) {
                player.sendMessage("§8[§6DifficultyEngine§8] §c✗ The §6Turbo Minecart §crequires VIP or Admin access.");
                player.sendMessage("§8  Permission: §fdifficultyengine.turbocart");
                return;
            }
        }

        if (itemFactory.isAnyCape(clicked)) {
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§8[§6DifficultyEngine§8] §c✗ Skill Capes are §4Admin Only§c.");
                player.sendMessage("§8  Permission: §fdifficultyengine.cape.admin");
                player.sendMessage("§8  Earn capes by reaching §aLevel 99 §8in a skill!");
                return;
            }
        }

        // ── Give item ─────────────────────────────────────────────────────────
        player.getInventory().addItem(clicked.clone());
        player.sendMessage("§8[§6DifficultyEngine§8] §7Received: §f" + formatName(clicked));
    }

    private String formatName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }
}
