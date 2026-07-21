package com.yourname.difficulty.boss.crimson;

import com.yourname.difficulty.items.ItemFactory;
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
 */
public class CrimsonBossSpawner implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory;
    private final CrimsonBossManager crimsonBossManager;
    private final TempestOverlordManager tempestOverlordManager;
    private final VoidWitherManager voidWitherManager;
    private final GildedBossManager gildedBossManager;

    public CrimsonBossSpawner(JavaPlugin plugin, ItemFactory itemFactory,
                               CrimsonBossManager crimsonBossManager,
                               TempestOverlordManager tempestOverlordManager,
                               VoidWitherManager voidWitherManager,
                               GildedBossManager gildedBossManager) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.crimsonBossManager = crimsonBossManager;
        this.tempestOverlordManager = tempestOverlordManager;
        this.voidWitherManager = voidWitherManager;
        this.gildedBossManager = gildedBossManager;
    }

    private boolean isSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.GILDED_BLACKSTONE;
    }

    private boolean isTempestSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.CRYING_OBSIDIAN;
    }

    private boolean isVoidSpawnerBlock(Block block) {
        if (block == null) return false;
        return block.getType() == Material.BLACK_CONCRETE;
    }

    private boolean isGildedSpawnerBlock(Block block) {
        if (block == null) return false;
        if (block.getType() != Material.GOLD_BLOCK) return false;
        return block.hasMetadata("de_gilded_spawner");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();

        if (itemFactory.isBlazefiendSpawner(item)) {
            block.setMetadata("de_blazefiend_spawner", new FixedMetadataValue(plugin, true));
            crimsonBossManager.registerCrimsonCube(block.getLocation());
            event.getPlayer().sendMessage("§a✓ §7Placed Blazefiend Spawner block!");
        } else if (itemFactory.isTempestSpawner(item)) {
            block.setMetadata("de_tempest_spawner", new FixedMetadataValue(plugin, true));
            event.getPlayer().sendMessage("§a✓ §7Placed Tempest Spawner block!");
        } else if (itemFactory.isVoidSpawner(item)) {
            block.setMetadata("de_void_spawner", new FixedMetadataValue(plugin, true));
            event.getPlayer().sendMessage("§a✓ §7Placed Void Spawner block!");
        } else if (itemFactory.isGildedSpawner(item)) {
            block.setMetadata("de_gilded_spawner", new FixedMetadataValue(plugin, true));
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
