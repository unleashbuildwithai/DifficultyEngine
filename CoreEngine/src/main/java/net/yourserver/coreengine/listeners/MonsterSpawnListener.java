package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Cancels natural monster spawns within a radius of any player who has the
 * "Remove Monsters" toggle enabled in the Create-a-Ville hub.
 */
public class MonsterSpawnListener implements Listener {

    private static final double RADIUS_SQUARED = 64.0 * 64.0;

    private final CoreEngine plugin;

    public MonsterSpawnListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        if (event.getLocation().getWorld() == null) return;
        for (Player p : event.getLocation().getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(event.getLocation()) < RADIUS_SQUARED
                    && plugin.getPlayerSettingsManager().get(p.getUniqueId()).removeMonsters) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
