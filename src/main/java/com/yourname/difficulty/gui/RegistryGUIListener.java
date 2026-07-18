package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * RegistryGUIListener — Handles all click interactions inside the RegistryGUI.
 *
 * Matches both TITLE_P1 and TITLE_P2 to support 2-page navigation.
 *
 * Slot behaviour:
 *   slot 45 (prev arrow) → open page 1
 *   slot 49 (label)      → ignored
 *   slot 53 (next arrow) → open page 2
 *   slots 0-44           → give item to player (with permission checks)
 *
 * Permission tiers:
 *   Turbo Minecart  → difficultyengine.turbocart
 *   Skill Capes     → difficultyengine.cape.admin
 *   Max Cape        → difficultyengine.cape.admin
 */
public class RegistryGUIListener implements Listener {

    private final ItemFactory itemFactory;
    private final RegistryGUI registryGUI;

    /** Navigation slots in the bottom row. */
    private static final int SLOT_PREV  = 45;
    private static final int SLOT_LABEL = 49;
    private static final int SLOT_NEXT  = 53;

    public RegistryGUIListener(ItemFactory itemFactory, RegistryGUI registryGUI) {
        this.itemFactory = itemFactory;
        this.registryGUI = registryGUI;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        boolean isPage1 = RegistryGUI.TITLE_P1.equals(title);
        boolean isPage2 = RegistryGUI.TITLE_P2.equals(title);
        if (!isPage1 && !isPage2) return;

        // Always cancel — items must never leave the registry inventory
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only act on clicks inside the top inventory
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();

        // ── Navigation clicks ─────────────────────────────────────────────────

        if (slot == SLOT_NEXT && isPage1) {
            player.closeInventory();
            registryGUI.openPage(player, 2);
            return;
        }

        if (slot == SLOT_PREV && isPage2) {
            player.closeInventory();
            registryGUI.openPage(player, 1);
            return;
        }

        // Label click / glass filler — ignore
        if (slot == SLOT_LABEL) return;
        if (slot >= RegistryGUI.ITEMS_PER_PAGE) return; // bottom nav row

        // ── Item slot click ───────────────────────────────────────────────────

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Navigation arrows / glass should not be giveable
        if (clicked.getType() == Material.ARROW ||
            clicked.getType() == Material.GRAY_STAINED_GLASS_PANE ||
            clicked.getType() == Material.BOOK) return;

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

        if (itemFactory.isAncientKillTome(clicked)) {
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§8[§6DifficultyEngine§8] §c✗ The §cAncient Kill Tome §cis §4Admin/Boss-Event Only§c.");
                player.sendMessage("§8  Survive a Double Boss event to earn one.");
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
