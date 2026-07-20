lpackage com.yourname.difficulty.bag;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicBagDeathListener — Prevents the Magic Bag from dropping on death.
 *
 * ── Behaviour ─────────────────────────────────────────────────────────────
 *  • On death   : removes the Magic Bag from the drop list and holds it.
 *  • On respawn : restores the bag to hotbar slot 8 (top-right).
 *
 *  The bag is NOT locked to any slot — players can move it freely after
 *  it is restored.  Right-clicking it in hand always opens the GUI.
 */
public class MagicBagDeathListener implements Listener {

    private final MagicBagManager bagManager;
    private final JavaPlugin       plugin;

    /**
     * Temporarily holds each player's Magic Bag item between death and respawn.
     * Cleaned up immediately on respawn (or if the player never respawns in
     * the same session, the entry is harmless).
     */
    private final Map<UUID, ItemStack> heldBags = new HashMap<>();

    public MagicBagDeathListener(MagicBagManager bagManager, JavaPlugin plugin) {
        this.bagManager = bagManager;
        this.plugin     = plugin;
    }

    // ── Death — pull the bag out of the drop list ─────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID   uuid   = player.getUniqueId();

        // Remove bag from drops
        var iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            ItemStack drop = iter.next();
            if (bagManager.isMagicBag(drop)) {
                heldBags.put(uuid, drop.clone());
                iter.remove();
                break;
            }
        }

        // If bag wasn't in drops (keepInventory on, etc.) still track it
        // so we know to restore it on respawn.
        if (!heldBags.containsKey(uuid)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && bagManager.isMagicBag(item)) {
                    heldBags.put(uuid, item.clone());
                    break;
                }
            }
        }
    }

    // ── Respawn — put the bag back in slot 8 (top-right) ─────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        ItemStack savedBag = heldBags.remove(uuid);

        // Run 5 ticks later so inventory is fully loaded after respawn
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // If the player already has a bag (e.g. keepInventory), no need to re-add
            for (ItemStack it : player.getInventory().getContents()) {
                if (it != null && bagManager.isMagicBag(it)) return;
            }

            ItemStack bagToRestore = (savedBag != null) ? savedBag : bagManager.buildMagicBag();

            // Place in slot 8 (top-right hotbar), displacing anything there
            ItemStack displaced = player.getInventory().getItem(MagicBagChestInterceptListener.BAG_SLOT);
            player.getInventory().setItem(MagicBagChestInterceptListener.BAG_SLOT, bagToRestore);
            if (displaced != null && !displaced.getType().isAir()) {
                player.getInventory().addItem(displaced);
            }

            player.sendActionBar("§d✦ §7Your Magic Bag was kept safe from death!");
        }, 5L);
    }
}
