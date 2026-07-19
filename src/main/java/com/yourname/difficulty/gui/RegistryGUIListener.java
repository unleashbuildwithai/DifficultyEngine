package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * RegistryGUIListener — Handles all click interactions inside the 8-page RegistryGUI.
 *
 * Title detection uses {@link RegistryGUI#pageFromTitle(String)} — any page title
 * that starts with the registry base prefix is matched automatically.
 *
 * Slot behaviour:
 *   slot 45 (prev arrow) → open page N-1
 *   slot 49 (label)      → ignored
 *   slot 53 (next arrow) → open page N+1
 *   slots 0–44           → give item to player (with permission checks)
 */
public class RegistryGUIListener implements Listener {

    private final ItemFactory itemFactory;
    private final RegistryGUI registryGUI;

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
        int page = RegistryGUI.pageFromTitle(title);
        if (page < 1) return;

        // Always cancel — items must never leave the registry inventory
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only act on clicks inside the top inventory
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();

        // ── Navigation ────────────────────────────────────────────────────────

        if (slot == SLOT_PREV && page > 1) {
            player.closeInventory();
            registryGUI.openPage(player, page - 1);
            return;
        }

        if (slot == SLOT_NEXT && page < RegistryGUI.PAGE_COUNT) {
            player.closeInventory();
            registryGUI.openPage(player, page + 1);
            return;
        }

        if (slot == SLOT_LABEL) return;
        if (slot >= RegistryGUI.ITEMS_PER_PAGE) return; // bottom nav row

        // ── Item click ────────────────────────────────────────────────────────

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Navigation arrows / glass / book label should not be giveable
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

        if (itemFactory.isGunZSword(clicked)) {
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§8[§6DifficultyEngine§8] §c✗ The §cGunZ Sword §cis §4Admin Spawn Only§c.");
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
