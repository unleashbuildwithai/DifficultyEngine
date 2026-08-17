package net.yourserver.coreengine.listeners;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Market NPC egg: an admin can right-click the egg to spawn a "Market"
 * villager, and any player who right-clicks that villager opens the Market shop
 * GUI directly (no teleport prompt).
 */
public class MarketNpcListener implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final CoreEngine plugin;

    public MarketNpcListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onUseEgg(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() != Material.VILLAGER_SPAWN_EGG
                || held.getItemMeta() == null
                || !held.getItemMeta().getPersistentDataContainer().has(PDCKeys.marketNpcEgg())) {
            return;
        }
        event.setCancelled(true);
        Location spawn = player.getLocation().clone().add(
                player.getLocation().getDirection().multiply(1.2)).add(0, 0.5, 0);
        Villager npc = spawn.getWorld().spawn(spawn, Villager.class, v -> {
            v.customName(LEGACY.deserialize("§dMarket"));
            v.setCustomNameVisible(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.getPersistentDataContainer().set(PDCKeys.marketNpc(), PersistentDataType.BYTE, (byte) 1);
        });
        player.sendMessage("§aPlaced the Market NPC at your location.");
        ItemMetaShrink(player, held);
    }

    @EventHandler
    public void onInteractNpc(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(PDCKeys.marketNpc())) {
            event.setCancelled(true);
            if (plugin.getMarketGuiManager() != null) {
                plugin.getMarketGuiManager().openMain(event.getPlayer());
            }
        }
    }

    private void ItemMetaShrink(Player player, ItemStack held) {
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
