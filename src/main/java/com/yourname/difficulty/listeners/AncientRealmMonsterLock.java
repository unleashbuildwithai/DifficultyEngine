package com.yourname.difficulty.listeners;

import com.yourname.difficulty.monsters.CustomMonsterManager;
import org.bukkit.World;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Set;

/**
 * AncientRealmMonsterLock — keeps the dungeon realms (ancient_realm, void_realm)
 * populated by CUSTOM monsters only.
 *
 * <p>Any vanilla hostile-mob spawn that originates from natural world generation
 * or reinforcement/weather is cancelled, while scripted and player-driven
 * content — our own custom monsters, dungeon boss spawns, player-built spawners,
 * spawn eggs and {@code /spawnmob} — is left completely untouched.</p>
 */
public class AncientRealmMonsterLock implements Listener {

    /** Realm worlds that must only ever hold custom monsters. */
    private static final Set<String> LOCKED_WORLDS = Set.of("ancient_realm", "void_realm");

    private final CustomMonsterManager customMonsterManager;

    public AncientRealmMonsterLock(CustomMonsterManager customMonsterManager) {
        this.customMonsterManager = customMonsterManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getEntity().getWorld();
        if (world == null || !LOCKED_WORLDS.contains(world.getName())) return;

        // Only hostile monsters are restricted — passive / neutral mobs are fine.
        if (!(event.getEntity() instanceof Monster)) return;

        // Our own custom monsters (tagged carriers) are always allowed.
        if (customMonsterManager.isCustomMonster(event.getEntity())) return;

        // Allow scripted + player-driven spawns (bosses, spawners, eggs, /spawnmob).
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        switch (reason) {
            case SPAWNER, SPAWNER_EGG, EGG, DISPENSE_EGG, CUSTOM -> { return; }
            default -> { /* fall through and cancel below */ }
        }

        // Anything else (natural ambient spawns, reinforcements, lightning-crossed
        // mobs, mounted mobs, etc.) is a vanilla monster we do not want here.
        event.setCancelled(true);
    }
}