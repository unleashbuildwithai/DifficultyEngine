package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * NoSpawnZoneListener — handles placement/removal of the admin-only
 * No-Spawn Zone Block (Registry page 11). Registers/unregisters the block's
 * location with {@link MonsterCapListener} so it takes effect immediately,
 * and persists across restarts is handled by MonsterCapListener re-scanning
 * on world load is NOT implemented — this is a manually-placed admin tool
 * intended for a single hub/starter world, re-placed if the server resets.
 */
public class NoSpawnZoneListener implements Listener {

    private final ItemFactory itemFactory;
    private final MonsterCapListener monsterCapListener;

    public NoSpawnZoneListener(ItemFactory itemFactory, MonsterCapListener monsterCapListener) {
        this.itemFactory = itemFactory;
        this.monsterCapListener = monsterCapListener;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!itemFactory.isNoSpawnZoneBlock(event.getItemInHand())) return;
        Player player = event.getPlayer();
        String worldName = event.getBlockPlaced().getWorld().getName().toLowerCase();
        if (!worldName.equals("starter") && !worldName.equals("starter_mv")) {
            player.sendMessage("§c✗ §7This block only functions in the §bstarter §7world — placed here, but it will have §cno effect§7.");
        } else {
            player.sendMessage("§b✓ §7No-Spawn Zone active! Monsters will no longer spawn within §e500 blocks §7of this block.");
        }
        monsterCapListener.registerNoSpawnZone(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // Always attempt to unregister this location — safe no-op if it
        // was never a No-Spawn Zone block (Sea Lantern is otherwise a
        // normal breakable vanilla block, so there's no reliable way to
        // "detect" the item after the block is gone; unregistering an
        // untracked location is harmless).
        monsterCapListener.unregisterNoSpawnZone(event.getBlock().getLocation());
    }

}
