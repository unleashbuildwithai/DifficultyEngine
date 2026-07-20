package com.yourname.difficulty.gui;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    private final ItemFactory       itemFactory;
    private final RegistryGUI       registryGUI;
    private final JavaPlugin        plugin;
    /** Tracks which registry page each player is currently on (for in-place updates). */
    private final Map<UUID, Integer> pageTracker = new HashMap<>();

    private static final int SLOT_PREV  = 45;
    private static final int SLOT_LABEL = 49;
    private static final int SLOT_NEXT  = 53;

    public RegistryGUIListener(ItemFactory itemFactory, RegistryGUI registryGUI, JavaPlugin plugin) {
        this.itemFactory = itemFactory;
        this.registryGUI = registryGUI;
        this.plugin      = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        // Use tracked page first (in-place updates don't change title), fall back to title
        int page;
        if (event.getWhoClicked() instanceof Player whoClicked) {
            page = pageTracker.getOrDefault(whoClicked.getUniqueId(),
                    RegistryGUI.pageFromTitle(title));
        } else {
            page = RegistryGUI.pageFromTitle(title);
        }
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
            int newPage = page - 1;
            pageTracker.put(player.getUniqueId(), newPage);
            registryGUI.updateInventoryPage(event.getView().getTopInventory(), newPage);
            return;
        }

        if (slot == SLOT_NEXT && page < RegistryGUI.PAGE_COUNT) {
            int newPage = page + 1;
            pageTracker.put(player.getUniqueId(), newPage);
            registryGUI.updateInventoryPage(event.getView().getTopInventory(), newPage);
            return;
        }

        if (slot == SLOT_LABEL) return;
        if (slot >= RegistryGUI.ITEMS_PER_PAGE) return; // bottom nav row

        // ── Item click ────────────────────────────────────────────────────────

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Navigation arrows and glass panes should not be giveable.
        // BOOK is allowed — Earth Magic Pages and the page-label BOOK at slot 49
        // are already filtered above (slot 49 returns early). Do NOT block BOOK here.
        if (clicked.getType() == Material.ARROW ||
            clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

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

        // GunZ Sword — no longer admin-only; exclusive boss drop from the Infernal Blazefiend.
        // It can still appear in the registry as a reference/preview item for admins.

        // ── Give item ─────────────────────────────────────────────────────────
        ItemStack copy = clicked.clone();
        player.getInventory().addItem(copy);
        player.sendMessage("§8[§6DifficultyEngine§8] §7Received: §f" + formatName(clicked));

        // ── Auto-open WRITTEN_BOOK items so players can read them immediately ─
        // Exception: Ancient Kill Tome — just deliver to inventory for admin use;
        //            do NOT close the registry or auto-open the book.
        if (clicked.getType() == Material.WRITTEN_BOOK && !itemFactory.isAncientKillTome(clicked)) {
            // Schedule close first (1 tick), then open book (2 ticks) to avoid
            // inventory-state conflicts that silently prevent openBook from working.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.closeInventory();
                plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> { if (player.isOnline()) player.openBook(copy); }, 2L);
            }, 1L);
        }
    }

    /** Clean up page tracker when the registry is closed. */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (RegistryGUI.pageFromTitle(title) >= 1
                || (event.getPlayer() instanceof Player p
                    && pageTracker.containsKey(p.getUniqueId()))) {
            if (event.getPlayer() instanceof Player p) {
                pageTracker.remove(p.getUniqueId());
            }
        }
    }

    private String formatName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }
}
