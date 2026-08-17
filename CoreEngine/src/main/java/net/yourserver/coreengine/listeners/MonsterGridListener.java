package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.config.ConfigManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * MonsterGridListener — the "Market safe-zone anchor" (controls: /monstergrid).
 *
 * <p>Cancels hostile monster spawns inside a configurable radius anchored on
 * the configured Market NPC location so the market plaza is always safe. Only
 * natural-style spawns ({@code NATURAL}, {@code REINFORCEMENTS},
 * {@code DEFAULT}) that qualify as hostile {@link Monster}s are cancelled —
 * mob spawners, spawn eggs, dungeon bosses and any other scripted spawns pass
 * through untouched.</p>
 */
public class MonsterGridListener implements Listener {

    private final ConfigManager config;

    public MonsterGridListener(ConfigManager config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!config.isMonsterGridEnabled()) return;
        if (!(event.getEntity() instanceof Monster)) return;

        // Natural-style world spawns only — leave spawners/eggs/bosses alone.
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason != CreatureSpawnEvent.SpawnReason.NATURAL
                && reason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS
                && reason != CreatureSpawnEvent.SpawnReason.DEFAULT) {
            return;
        }

        Location anchor = config.getMarketNpcLocation();
        World   world   = anchor.getWorld();
        Location spawn  = event.getLocation();
        if (world == null || !world.equals(spawn.getWorld())) return;

        double radius = config.getMonsterGridRadius();
        if (spawn.distanceSquared(anchor) <= radius * radius) {
            event.setCancelled(true);
        }
    }
}