package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Handles region-selector stick clicks (left = corner 1, right = corner 2). */
public class RegionSelectionListener implements Listener {

    private final CoreEngine plugin;

    public RegionSelectionListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;
        if (!item.getItemMeta().getPersistentDataContainer()
                .has(PDCKeys.regionStick(), PersistentDataType.BYTE)) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getRegionManager().setPos1(player.getUniqueId(), block.getLocation());
            player.sendMessage("§aCorner 1 set at §e" + block.getX() + ", " + block.getY() + ", " + block.getZ());
        } else {
            plugin.getRegionManager().setPos2(player.getUniqueId(), block.getLocation());
            player.sendMessage("§aCorner 2 set at §e" + block.getX() + ", " + block.getY() + ", " + block.getZ());
        }
    }
}
