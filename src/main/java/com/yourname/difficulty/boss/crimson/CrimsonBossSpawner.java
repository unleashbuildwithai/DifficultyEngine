package com.yourname.difficulty.boss.crimson;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.boss.BossSpawnerRegistry;
import com.yourname.difficulty.boss.CrimsonBossManager;
import com.yourname.difficulty.boss.gilded.GildedBossManager;
import com.yourname.difficulty.boss.tempest.TempestOverlordManager;
import com.yourname.difficulty.boss.voidwither.VoidWitherManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CrimsonBossSpawner — handles custom placable boss spawners for Crimson Pit, Tempest Sanctum, and Void Sanctum.
 *
 * ── Spawner identity ──────────────────────────────────────────────────────
 * A block only counts as a "real" spawner if BOTH:
 *   1. Its material matches the expected type (e.g. GILDED_BLACKSTONE), AND
 *   2. Its exact Location is present in the persisted {@link BossSpawnerRegistry}
 *      for that type.
 *
 * This replaces the previous metadata-only check (Block.hasMetadata(...)),
 * which was a loose in-memory/coordinate-keyed tag that could be
 * accidentally inherited by ANY vanilla block of the same material placed
 * at a previously-flagged coordinate (creative mode, /give, WorldEdit,
 * natural generation, etc.) — this was the exact bug where a
 * creative-placed Gilded Blackstone block (never obtained from the
 * Registry) worked as a real Blazefiend Spawner. Block metadata is still
 * set alongside the registry entry purely for fast/legacy compatibility,
 * but the registry check is now authoritative.
 */
public class CrimsonBossSpawner implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory;
    private final CrimsonBossManager crimsonBossManager;
    private final TempestOverlordManager tempestOverlordManager;
    private final VoidWitherManager voidWitherManager;
    private final GildedBossManager gildedBossManager;
    private final BossSpawnerRegistry spawnerRegistry;

    /**
     * Race-condition guard: tracks block locations currently mid-activation
     * (between the eligibility checks and the actual spawnBoss/arena-rebuild
     * call). Without this, two players hitting/right-clicking the same
     * spawner block in the same tick could both pass the "isBossAlive()"
     * check before either boss registers as alive, resulting in two bosses
     * spawning (or the arena being rebuilt twice, wasting resources / causing
     * visual glitches). Guarded with a synchronized block since Bukkit events
     * for the same block can theoretically fire from different call paths
     * (BlockDamageEvent + PlayerInteractEvent) within the same tick.
     */
    private final java.util.Set<org.bukkit.Location> activatingLocations =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());


    public CrimsonBossSpawner(JavaPlugin plugin, ItemFactory itemFactory,
                               CrimsonBossManager crimsonBossManager,
                               TempestOverlordManager tempestOverlordManager,
                               VoidWitherManager voidWitherManager,
                               GildedBossManager gildedBossManager,
                               BossSpawnerRegistry spawnerRegistry) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.crimsonBossManager = crimsonBossManager;
        this.tempestOverlordManager = tempestOverlordManager;
        this.voidWitherManager = voidWitherManager;
        this.gildedBossManager = gildedBossManager;
        this.spawnerRegistry = spawnerRegistry;
    }

    /**
     * Requires BOTH the correct material AND registration in the persisted
     * BossSpawnerRegistry for this exact block Location. This prevents ANY
     * naturally-placed/found/survival-crafted/creative-given block of the
     * same material (a gold block, crying obsidian decoration, black
     * concrete, gilded blackstone from a bastion, etc.) from ever triggering
     * spawner logic — only blocks placed via the Registry's spawner ITEMS
     * (which register their location, see onSpawnerPlace below) — or
     * relocated by rebuildArena() — are recognised as real spawners.
     */
    private boolean isSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.GILDED_BLACKSTONE
                && spawnerRegistry.isRegistered(block.getLocation(), BossSpawnerRegistry.TYPE_BLAZEFIEND);
    }

    private boolean isTempestSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.CRYING_OBSIDIAN
                && spawnerRegistry.isRegistered(block.getLocation(), BossSpawnerRegistry.TYPE_TEMPEST);
    }

    private boolean isVoidSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.BLACK_CONCRETE
                && spawnerRegistry.isRegistered(block.getLocation(), BossSpawnerRegistry.TYPE_VOID);
    }

    private boolean isGildedSpawnerBlock(Block block) {
        if (block == null) return false;
        if (block.getType() != Material.GOLD_BLOCK) return false;
        return spawnerRegistry.isRegistered(block.getLocation(), BossSpawnerRegistry.TYPE_GILDED);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();

        if (itemFactory.isBlazefiendSpawner(item)) {
            block.setMetadata("de_blazefiend_spawner", new FixedMetadataValue(plugin, true));
            spawnerRegistry.register(block.getLocation(), BossSpawnerRegistry.TYPE_BLAZEFIEND);
            crimsonBossManager.registerCrimsonCube(block.getLocation());
            event.getPlayer().sendMessage("§a✓ §7Placed Blazefiend Spawner block!");
        } else if (itemFactory.isTempestSpawner(item)) {
            block.setMetadata("de_tempest_spawner", new FixedMetadataValue(plugin, true));
            spawnerRegistry.register(block.getLocation(), BossSpawnerRegistry.TYPE_TEMPEST);
            event.getPlayer().sendMessage("§a✓ §7Placed Tempest Spawner block!");
        } else if (itemFactory.isVoidSpawner(item)) {
            block.setMetadata("de_void_spawner", new FixedMetadataValue(plugin, true));
            spawnerRegistry.register(block.getLocation(), BossSpawnerRegistry.TYPE_VOID);
            event.getPlayer().sendMessage("§a✓ §7Placed Void Spawner block!");
        } else if (itemFactory.isGildedSpawner(item)) {
            block.setMetadata("de_gilded_spawner", new FixedMetadataValue(plugin, true));
            spawnerRegistry.register(block.getLocation(), BossSpawnerRegistry.TYPE_GILDED);
            event.getPlayer().sendMessage("§6✓ §7Placed Gilded Spawner block!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block) || isGildedSpawnerBlock(block)) {
            Player player = event.getPlayer();
            if (!player.hasPermission("difficultyengine.cape.admin") && !player.isOp()) {
                event.setCancelled(true);
                player.sendMessage("§c✗ §7This spawner block is protected like bedrock! Only admins can remove it.");
            } else {
                block.removeMetadata("de_blazefiend_spawner", plugin);
                block.removeMetadata("de_tempest_spawner", plugin);
                block.removeMetadata("de_void_spawner", plugin);
                block.removeMetadata("de_gilded_spawner", plugin);
                spawnerRegistry.unregister(block.getLocation(), BossSpawnerRegistry.TYPE_BLAZEFIEND);
                spawnerRegistry.unregister(block.getLocation(), BossSpawnerRegistry.TYPE_TEMPEST);
                spawnerRegistry.unregister(block.getLocation(), BossSpawnerRegistry.TYPE_VOID);
                spawnerRegistry.unregister(block.getLocation(), BossSpawnerRegistry.TYPE_GILDED);
                player.sendMessage("§a✓ §7Removed protected spawner block.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerStrike(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block) || isGildedSpawnerBlock(block)) {
            handleSpawnerActivation(event.getPlayer(), block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block) || isGildedSpawnerBlock(block)) {
            event.setCancelled(true);
            handleSpawnerActivation(event.getPlayer(), block);
        }
    }

    public void handleSpawnerActivation(Player player, Block block) {
        if (block == null) return;

        // ── Race-condition guard ───────────────────────────────────────────
        // Atomically claim this block location for the duration of the
        // activation attempt. If another player's click/hit is already mid-
        // activation on the SAME block this tick, bail out silently instead
        // of letting both requests pass the isBossAlive()/isAlive() checks
        // before either boss actually registers as spawned.
        org.bukkit.Location key = block.getLocation().toBlockLocation();
        if (!activatingLocations.add(key)) {
            player.sendActionBar("§c✗ §7Spawner is already being activated!");
            return;
        }
        try {
            handleSpawnerActivationInternal(player, block);
        } finally {
            activatingLocations.remove(key);
        }
    }

    private void handleSpawnerActivationInternal(Player player, Block block) {
        if (isSpawnerBlock(block)) {

            if (crimsonBossManager.isBossAlive()) {
                player.sendActionBar("§c🔥 §7The Blazefiend already roams these caves...");
                return;
            }
            if (!block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Blazefiend Spawner only works inside the §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§c☠ §4The Spawner has been activated, awakening the Infernal Blazefiend!");
            crimsonBossManager.rebuildArena(player, block.getLocation());
            crimsonBossManager.spawnBoss(block.getLocation());

        } else if (isTempestSpawnerBlock(block)) {
            boolean isTempestAlive = false;
            for (Entity ent : block.getWorld().getEntitiesByClass(Phantom.class)) {
                String cName = ent.getCustomName();
                if (cName != null && cName.contains("Tempest Overlord") && !ent.isDead()) {
                    isTempestAlive = true;
                    break;
                }
            }
            if (isTempestAlive) {
                player.sendActionBar("§c⚡ §7The Tempest Overlord already roams these skies...");
                return;
            }
            if (!block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Tempest Spawner only works inside the §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§c☠ §4The Spawner has been activated, awakening the Tempest Overlord!");
            crimsonBossManager.rebuildArena(player, block.getLocation());
            tempestOverlordManager.spawnTempestOverlord(block.getLocation());

        } else if (isVoidSpawnerBlock(block)) {
            boolean isWitherAlive = false;
            for (Entity ent : block.getWorld().getEntitiesByClass(Wither.class)) {
                String cName = ent.getCustomName();
                if (cName != null && (cName.contains("Void Wither") || cName.contains("Void Zurion")) && !ent.isDead()) {
                    isWitherAlive = true;
                    break;
                }
            }
            if (isWitherAlive) {
                player.sendActionBar("§0☠ §7The Void Wither already roams this realm...");
                return;
            }
            if (!block.getWorld().getName().equals("void_realm") && !block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Void Spawner only works inside the §5Void Realm§7 or §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§0☠ §4The Spawner has been activated, awakening the Void Wither!");
            crimsonBossManager.rebuildArena(player, block.getLocation());
            voidWitherManager.spawnVoidWither(block.getLocation());

        } else if (isGildedSpawnerBlock(block)) {
            if (gildedBossManager != null && gildedBossManager.isGildedEnforcerAlive()) {
                player.sendActionBar("§6☠ §7The Gilded Enforcer already marches these halls...");
                return;
            }
            if (!block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Gilded Spawner only works inside the §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§6☠ §4The Spawner has been activated, awakening The Gilded Enforcer!");
            crimsonBossManager.rebuildArena(player, block.getLocation());
            if (gildedBossManager != null) {
                gildedBossManager.spawnGildedEnforcer(block.getLocation());
            }
        }
    }
}
