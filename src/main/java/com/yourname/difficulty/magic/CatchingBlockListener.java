package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * CatchingBlockListener — Full lifecycle of the Catching Block + Magic Bottle system.
 *
 * ── Silk-Touch Ancient Debris Drop ───────────────────────────────────────────
 *  Mining ANCIENT_DEBRIS with a Silk Touch tool has a {@value #CATCH_BLOCK_CHANCE}%
 *  chance to also drop a Catching Block item alongside the debris.
 *
 * ── Catching Block Placement ──────────────────────────────────────────────────
 *  When the Catching Block item (LODESTONE + PDC tag) is placed, its location is
 *  registered with MagicBottleManager.
 *
 * ── Catching Block Removal ────────────────────────────────────────────────────
 *  Breaking a registered Catching Block drops all stored Empty Magic Bottles on
 *  the ground before unregistering the location.
 *
 * ── Right-click Deposit ───────────────────────────────────────────────────────
 *  Right-clicking a registered Catching Block while holding an Empty Magic Bottle
 *  deposits the bottle.  Right-clicking with an empty hand shows the current count.
 *
 * ── Lightning Rod Charge ─────────────────────────────────────────────────────
 *  When a lightning rod within {@value #ROD_SEARCH_RADIUS} blocks of a tracked
 *  catching block is struck by lightning WHILE IT IS RAINING, one stored Empty
 *  Magic Bottle is converted to a Charged Magic Bottle (4 casts) and dropped at
 *  the catching block.
 *
 *  Detection: EntityChangeBlockEvent fires when lightning activates a lightning rod
 *  (the rod changes to its "powered" blockdata variant).  This is more reliable
 *  than LightningStrikeEvent because it specifically identifies lightning-rod hits.
 */
public class CatchingBlockListener implements Listener {

    /** % chance to drop a Catching Block when silk-touching Ancient Debris. */
    private static final int CATCH_BLOCK_CHANCE = 25; // 25 %

    /** How far from a catching block a lightning rod may be and still count. */
    private static final int ROD_SEARCH_RADIUS = 5;

    private static final Random RAND = new Random();

    private final ItemFactory       itemFactory;
    private final MagicBottleManager bottleManager;

    public CatchingBlockListener(ItemFactory itemFactory, MagicBottleManager bottleManager) {
        this.itemFactory   = itemFactory;
        this.bottleManager = bottleManager;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SILK-TOUCH ANCIENT DEBRIS → CATCHING BLOCK DROP
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDebrisMined(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.ANCIENT_DEBRIS) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool == null || tool.getType().isAir()) return;
        if (!tool.hasItemMeta()) return;
        if (!tool.getItemMeta().hasEnchant(Enchantment.SILK_TOUCH)) return;

        // Roll 25 % chance
        if (RAND.nextInt(100) >= CATCH_BLOCK_CHANCE) return;

        // Drop a Catching Block at the block's location
        block.getWorld().dropItemNaturally(
            block.getLocation().add(0.5, 0.5, 0.5),
            itemFactory.buildCatchingBlock()
        );
        player.sendActionBar(
            "§b⚡ §7The Ancient Debris released a §bCatching Block§7! "
            + "§8(Place it near a §eLightning Rod §8in the rain)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CATCHING BLOCK PLACED
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCatchingBlockPlaced(BlockPlaceEvent event) {
        ItemStack placed = event.getItemInHand();
        if (!itemFactory.isCatchingBlock(placed)) return;

        Location loc = event.getBlock().getLocation();
        bottleManager.register(loc);

        event.getPlayer().sendActionBar(
            "§b⚡ §7Catching Block placed! "
            + "§8Right-click with an §bEmpty Magic Bottle §8to load it. "
            + "§8Must be raining with a §eLightning Rod §8within §e5 blocks§8.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CATCHING BLOCK BROKEN — drop stored bottles
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCatchingBlockBroken(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        if (!bottleManager.isTracked(loc)) return;

        // Drop all stored bottles
        int emptyCount = bottleManager.getBottleCount(loc);
        if (emptyCount > 0) {
            ItemStack empties = itemFactory.buildEmptyMagicBottle();
            empties.setAmount(emptyCount);
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), empties);
        }
        
        int fullCount = bottleManager.getFullBottleCount(loc);
        if (fullCount > 0) {
            ItemStack fulls = itemFactory.buildChargedMagicBottle(4);
            fulls.setAmount(fullCount);
            loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), fulls);
        }

        bottleManager.unregister(loc);
        event.getPlayer().sendMessage(
            "§b⚡ §7Catching Block removed. §8(" + emptyCount + " empty, " + fullCount + " full dropped)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RIGHT-CLICK CATCHING BLOCK — deposit / inspect
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // Main hand only to avoid double-fire
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.LODESTONE) return;
        if (!bottleManager.isTracked(clicked.getLocation())) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        Location loc   = clicked.getLocation();
        
        openCatchingBlockGUI(player, loc);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LIGHTNING ROD STRUCK → charge a bottle
    //
    //  EntityChangeBlockEvent fires when a lightning bolt activates a lightning
    //  rod — the rod's block-data changes to the powered state.  This is the
    //  cleanest Bukkit hook for "lightning rod was struck".
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningRodPowered(EntityChangeBlockEvent event) {
        // Powered lightning rods change to LIGHTNING_ROD with BlockData powered=true.
        // The entity driving the change is the lightning bolt.
        if (!(event.getEntity() instanceof org.bukkit.entity.LightningStrike)) return;
        if (event.getBlock().getType() != Material.LIGHTNING_ROD) return;

        Location rodLoc = event.getBlock().getLocation();

        // Must be raining
        if (!rodLoc.getWorld().hasStorm()) return;

        // Find a tracked catching block within ROD_SEARCH_RADIUS of the rod
        Location catchLoc = findNearbyCatchingBlock(rodLoc, ROD_SEARCH_RADIUS);
        if (catchLoc == null) return;

        // Attempt to consume one bottle
        if (!bottleManager.consumeBottleForCharge(catchLoc)) {
            // No bottles — show a "missed" spark to indicate the block was close but empty
            catchLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                catchLoc.clone().add(0.5, 1.0, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
            catchLoc.getWorld().playSound(catchLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 0.5f);
            // Notify nearby players
            for (Player p : catchLoc.getWorld().getNearbyPlayers(catchLoc, 16)) {
                p.sendActionBar("§c✗ §7Catching Block absorbed the strike but has §cno bottles§7 loaded!");
            }
            return;
        }

        // ── Visual feedback ───────────────────────────────────────────────────
        // Channel particles: rod → catching block
        Location dropLoc = catchLoc.clone().add(0.5, 1.0, 0.5);
        spawnChannel(rodLoc, catchLoc);

        catchLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
            dropLoc, 60, 0.3, 0.5, 0.3, 0.15);
        catchLoc.getWorld().spawnParticle(Particle.ENCHANT,
            dropLoc, 40, 0.3, 0.5, 0.3, 0.10);
        catchLoc.getWorld().spawnParticle(Particle.END_ROD,
            dropLoc, 20, 0.2, 0.4, 0.2, 0.05);

        catchLoc.getWorld().playSound(catchLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,  0.35f, 1.8f);
        catchLoc.getWorld().playSound(catchLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME,     1.0f,  1.2f);
        catchLoc.getWorld().playSound(catchLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP,   0.8f,  1.5f);

        MagicBottleManager.CatchingBlockState state = bottleManager.getState(catchLoc);
        int remaining = state != null ? state.emptyBottles : 0;
        for (Player p : catchLoc.getWorld().getNearbyPlayers(catchLoc, 24)) {
            p.sendMessage("§b⚡ §7Lightning charged a §bMagic Bottle§7! "
                + "§8(" + remaining + " empty bottle(s) remain)");
            p.sendActionBar("§b⚡ §6Charged Magic Bottle §7ready in block! §8(4 casts)");
        }
        
        // Update any open GUIs
        for (java.util.Map.Entry<java.util.UUID, Location> entry : playerGuiMap.entrySet()) {
            if (entry.getValue().equals(catchLoc)) {
                Player p = org.bukkit.Bukkit.getPlayer(entry.getKey());
                if (p != null && p.isOnline()) {
                    openCatchingBlockGUI(p, catchLoc);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GUI LOGIC
    // ══════════════════════════════════════════════════════════════════════════

    private void openCatchingBlockGUI(Player player, Location loc) {
        org.bukkit.inventory.Inventory inv = org.bukkit.Bukkit.createInventory(null, 9, "Catching Block");
        
        MagicBottleManager.CatchingBlockState state = bottleManager.getState(loc);
        if (state == null) return;
        
        // Slots 0-3: Empty Bottles
        for (int i = 0; i < 4; i++) {
            if (i < state.emptyBottles) {
                inv.setItem(i, itemFactory.buildEmptyMagicBottle());
            } else {
                inv.setItem(i, new ItemStack(Material.AIR));
            }
        }
        
        // Slot 4: Divider / Info
        ItemStack info = new ItemStack(Material.LODESTONE);
        org.bukkit.inventory.meta.ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b⚡ Catching Block");
            boolean raining = loc.getWorld().hasStorm();
            boolean hasRod = findNearbyLightningRod(loc, ROD_SEARCH_RADIUS) != null;
            meta.setLore(java.util.Arrays.asList(
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
        
        // Slots 5-8: Full Bottles
        for (int i = 5; i < 9; i++) {
            if ((i - 5) < state.fullBottles) {
                inv.setItem(i, itemFactory.buildChargedMagicBottle(4));
            } else {
                inv.setItem(i, new ItemStack(Material.AIR));
            }
        }
        
        playerGuiMap.put(player.getUniqueId(), loc);
        player.openInventory(inv);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.8f);
    }

    private final java.util.Map<java.util.UUID, Location> playerGuiMap = new java.util.HashMap<>();

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!playerGuiMap.containsKey(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals("Catching Block")) return;
        
        event.setCancelled(true);
        Location loc = playerGuiMap.get(player.getUniqueId());
        MagicBottleManager.CatchingBlockState state = bottleManager.getState(loc);
        if (state == null) {
            player.closeInventory();
            return;
        }
        
        org.bukkit.inventory.Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        
        if (clickedInv.equals(event.getView().getTopInventory())) {
            // Clicked top inventory
            int slot = event.getSlot();
            if (slot >= 0 && slot <= 3) {
                // Try to take empty bottle
                if (state.emptyBottles > 0) {
                    state.emptyBottles--;
                    player.getInventory().addItem(itemFactory.buildEmptyMagicBottle()).values().forEach(
                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    openCatchingBlockGUI(player, loc); // Refresh
                }
            } else if (slot >= 5 && slot <= 8) {
                // Try to take full bottle
                if (state.fullBottles > 0) {
                    state.fullBottles--;
                    player.getInventory().addItem(itemFactory.buildChargedMagicBottle(4)).values().forEach(
                        item -> player.getWorld().dropItemNaturally(player.getLocation(), item)
                    );
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    openCatchingBlockGUI(player, loc); // Refresh
                }
            }
        } else {
            // Clicked bottom inventory (player's inventory)
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && itemFactory.isEmptyMagicBottle(clickedItem)) {
                if (state.emptyBottles < MagicBottleManager.MAX_BOTTLES) {
                    state.emptyBottles++;
                    clickedItem.setAmount(clickedItem.getAmount() - 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.5f);
                    openCatchingBlockGUI(player, loc); // Refresh
                } else {
                    player.sendMessage("§cCatching block is full of empty bottles!");
                }
            } else if (clickedItem != null && itemFactory.isChargedMagicBottle(clickedItem)) {
                if (state.fullBottles < MagicBottleManager.MAX_BOTTLES) {
                    state.fullBottles++;
                    clickedItem.setAmount(clickedItem.getAmount() - 1);
                    player.playSound(player.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, 1.5f);
                    openCatchingBlockGUI(player, loc); // Refresh
                } else {
                    player.sendMessage("§cCatching block is full of charged bottles!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        playerGuiMap.remove(player.getUniqueId());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Searches in a cube of half-size {@code radius} around {@code origin} for
     * a LIGHTNING_ROD block.  Returns the first found location, or null.
     */
    private Location findNearbyLightningRod(Location origin, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block b = origin.clone().add(dx, dy, dz).getBlock();
                    if (b.getType() == Material.LIGHTNING_ROD) return b.getLocation();
                }
            }
        }
        return null;
    }

    /**
     * Searches in a cube of half-size {@code radius} around {@code rodLoc} for
     * a tracked Catching Block.  Returns the first found normalised location, or null.
     */
    private Location findNearbyCatchingBlock(Location rodLoc, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Location check = rodLoc.clone().add(dx, dy, dz);
                    if (bottleManager.isTracked(check)) return check;
                }
            }
        }
        return null;
    }

    /**
     * Spawns a particle channel from {@code from} (rod) to {@code to} (catching block)
     * to make the energy transfer visible.
     */
    private void spawnChannel(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int)(dist * 3));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location pt = from.clone().add(
                dx * t + 0.5,
                dy * t + 1.0,
                dz * t + 0.5
            );
            from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.05, 0.05, 0.05, 0.02);
        }
    }
}
