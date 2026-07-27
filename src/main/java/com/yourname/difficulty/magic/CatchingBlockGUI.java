package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CatchingBlockGUI — the 9-slot bottle-management GUI for a Catching Block,
 * extracted out of {@link CatchingBlockListener} during the 400-line-file
 * cleanup pass. Behaviour (slot layout, click handling, refresh-on-change)
 * is unchanged from the original implementation.
 */
public class CatchingBlockGUI implements Listener {

    private static final int ROD_SEARCH_RADIUS = 5;
    private static final String TITLE = "Catching Block";

    private final ItemFactory itemFactory;
    private final MagicBottleManager bottleManager;
    private final Map<UUID, Location> playerGuiMap = new HashMap<>();

    public CatchingBlockGUI(ItemFactory itemFactory, MagicBottleManager bottleManager) {
        this.itemFactory = itemFactory;
        this.bottleManager = bottleManager;
    }

    /** Refreshes any currently-open GUI that is looking at the given location. */
    public void refreshViewersOf(Location catchLoc) {
        for (Map.Entry<UUID, Location> entry : playerGuiMap.entrySet()) {
            if (entry.getValue().equals(catchLoc)) {
                Player p = Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    open(p, catchLoc);
                }
            }
        }
    }

    public void open(Player player, Location loc) {
        Inventory inv = Bukkit.createInventory(null, 9, TITLE);

        MagicBottleManager.CatchingBlockState state = bottleManager.getState(loc);
        if (state == null) return;

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) { glassMeta.setDisplayName("§8"); glass.setItemMeta(glassMeta); }
        for (int i = 0; i < 9; i++) inv.setItem(i, glass);

        // Slot 2: Empty Bottles (stacked)
        if (state.emptyBottles > 0) {
            ItemStack emptyItem = itemFactory.buildEmptyMagicBottle();
            emptyItem.setAmount(state.emptyBottles);
            inv.setItem(2, emptyItem);
        } else {
            inv.setItem(2, new ItemStack(Material.AIR));
        }

        // Slot 3 & 5: Arrows
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta arrowMeta = arrow.getItemMeta();
        if (arrowMeta != null) { arrowMeta.setDisplayName("§8➔"); arrow.setItemMeta(arrowMeta); }
        inv.setItem(3, arrow);
        inv.setItem(5, arrow);

        // Slot 4: Divider / Info
        ItemStack info = new ItemStack(Material.LODESTONE);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b⚡ Catching Block");
            boolean raining = loc.getWorld().hasStorm();
            boolean hasRod = findNearbyLightningRod(loc, ROD_SEARCH_RADIUS) != null;
            meta.setLore(Arrays.asList(
                "§7Click empty bottles in your",
                "§7inventory to add them.",
                "",
                "§7Status:",
                raining ? "§aRaining ✔" : "§cNo rain ✗",
                hasRod ? "§aRod nearby ✔" : "§cNo rod within 5 blocks ✗",
                "",
                "§eLocation: §7" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
            ));
            info.setItemMeta(meta);
        }
        inv.setItem(4, info);

        // Slot 6: Full Bottles (stacked)
        if (state.fullBottles > 0) {
            ItemStack fullItem = itemFactory.buildChargedMagicBottle(4);
            fullItem.setAmount(state.fullBottles);
            inv.setItem(6, fullItem);
        } else {
            inv.setItem(6, new ItemStack(Material.AIR));
        }

        playerGuiMap.put(player.getUniqueId(), loc);
        player.openInventory(inv);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.8f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!playerGuiMap.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(TITLE)) return;

        event.setCancelled(true);
        Location loc = playerGuiMap.get(player.getUniqueId());
        MagicBottleManager.CatchingBlockState state = bottleManager.getState(loc);
        if (state == null) {
            player.closeInventory();
            return;
        }

        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;

        if (clickedInv.equals(event.getView().getTopInventory())) {
            int slot = event.getSlot();
            if (slot == 2) {
                if (state.emptyBottles > 0) {
                    int amountToTake = event.isShiftClick() ? state.emptyBottles : 1;
                    state.emptyBottles -= amountToTake;
                    ItemStack drop = itemFactory.buildEmptyMagicBottle();
                    drop.setAmount(amountToTake);
                    player.getInventory().addItem(drop).values().forEach(
                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    open(player, loc); // Refresh
                }
            } else if (slot == 6) {
                if (state.fullBottles > 0) {
                    int amountToTake = event.isShiftClick() ? state.fullBottles : 1;
                    state.fullBottles -= amountToTake;
                    ItemStack drop = itemFactory.buildChargedMagicBottle(4);
                    drop.setAmount(amountToTake);
                    player.getInventory().addItem(drop).values().forEach(
                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    open(player, loc); // Refresh
                }
            }
        } else {
            // Clicked bottom inventory (player's inventory)
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType().isAir()) return;

            boolean isEmpty  = itemFactory.isEmptyMagicBottle(clickedItem);
            boolean isCharged = !isEmpty && itemFactory.isChargedMagicBottle(clickedItem);
            if (!isEmpty && !isCharged) return;

            if (event.isShiftClick()) {
                // ── Shift-click: dump ALL matching bottles from the WHOLE inventory ──
                int deposited = 0;
                int capacity  = MagicBottleManager.MAX_BOTTLES - (isEmpty ? state.emptyBottles : state.fullBottles);
                if (capacity <= 0) {
                    player.sendMessage(isEmpty
                        ? "§cCatching block is full of empty bottles!"
                        : "§cCatching block is full of charged bottles!");
                    return;
                }
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length && deposited < capacity; i++) {
                    ItemStack it = contents[i];
                    if (it == null || it.getType().isAir()) continue;
                    boolean matches = isEmpty ? itemFactory.isEmptyMagicBottle(it) : itemFactory.isChargedMagicBottle(it);
                    if (!matches) continue;
                    int take = Math.min(it.getAmount(), capacity - deposited);
                    it.setAmount(it.getAmount() - take);
                    if (it.getAmount() <= 0) contents[i] = null;
                    deposited += take;
                }
                player.getInventory().setContents(contents);
                if (isEmpty) state.emptyBottles += deposited; else state.fullBottles += deposited;

                if (deposited > 0) {
                    player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.5f);
                    player.sendMessage("§b⚡ §7Deposited §b" + deposited + " §7bottle(s) into the Catching Block.");
                    open(player, loc); // Refresh
                }
            } else {
                // ── Normal click: deposit the WHOLE clicked stack at once ──
                int amount   = clickedItem.getAmount();
                int capacity = MagicBottleManager.MAX_BOTTLES - (isEmpty ? state.emptyBottles : state.fullBottles);
                if (capacity <= 0) {
                    player.sendMessage(isEmpty
                        ? "§cCatching block is full of empty bottles!"
                        : "§cCatching block is full of charged bottles!");
                    return;
                }
                int take = Math.min(amount, capacity);
                clickedItem.setAmount(amount - take);
                if (isEmpty) state.emptyBottles += take; else state.fullBottles += take;

                player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.5f);
                if (take < amount) {
                    player.sendMessage("§e⚠ §7Catching Block could only accept §e" + take + "§7/" + amount + " bottles (now full).");
                }
                open(player, loc); // Refresh
            }
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        playerGuiMap.remove(player.getUniqueId());
    }

    private Location findNearbyLightningRod(Location origin, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    org.bukkit.block.Block b = origin.clone().add(dx, dy, dz).getBlock();
                    if (b.getType() == Material.LIGHTNING_ROD) return b.getLocation();
                }
            }
        }
        return null;
    }
}
