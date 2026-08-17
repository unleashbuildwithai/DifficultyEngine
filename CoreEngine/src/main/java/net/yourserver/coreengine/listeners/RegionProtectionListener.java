package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.protection.RegionManager.Region;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Protects blocks in regions and blocks monster spawns. Secure regions are unbreakable even for OP. */
public class RegionProtectionListener implements Listener {

    private final CoreEngine plugin;

    public RegionProtectionListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getRegionAt(
                event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (region == null) return;
        if (region.secure()) {
            event.setCancelled(true);
            player.sendMessage("§cThis block is secured and cannot be broken by anyone.");
        } else if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cThis block is protected (§e" + region.name() + "§c).");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Region region = plugin.getRegionManager().getRegionAt(
                event.getBlock().getWorld().getName(),
                event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        if (region == null) return;
        if (region.secure()) {
            event.setCancelled(true);
            player.sendMessage("§cThis area is secured and cannot be modified by anyone.");
        } else if (!player.isOp()) {
            event.setCancelled(true);
            player.sendMessage("§cThis area is protected (§e" + region.name() + "§c).");
        }
    }

    @EventHandler
    public void onMonsterSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        if (event.getLocation().getWorld() == null) return;
        Region region = plugin.getRegionManager().getRegionAt(
                event.getLocation().getWorld().getName(),
                event.getLocation().getBlockX(), event.getLocation().getBlockY(), event.getLocation().getBlockZ());
        if (region != null) {
            event.setCancelled(true);
        }
    }
}