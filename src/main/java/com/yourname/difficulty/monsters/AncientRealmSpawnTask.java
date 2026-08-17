package com.yourname.difficulty.monsters;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * AncientRealmSpawnTask — ambient spawner that keeps the dungeon realms
 * (ancient_realm, void_realm) populated with custom monsters.
 *
 * <p>Every tick period, each online player inside a realm world gets its nearby
 * custom-monster population topped up (up to {@link #MAX_PER_PLAYER} within
 * {@link #RADIUS} blocks), picking a fitting monster from {@link #MOB_IDS}.
 * Combined with {@code AncientRealmMonsterLock} this guarantees the realms are
 * filled exclusively by custom entity types. The spawns use the same
 * {@link CustomMonsterManager#spawn} path (SpawnReason#CUSTOM), so they are not
 * affected by the natural-spawn cap listener and are marked persistent.</p>
 */
public class AncientRealmSpawnTask extends BukkitRunnable {

    private static final Set<String>  LOCKED_WORLDS = Set.of("ancient_realm", "void_realm");

    /** Monster IDs that fit the realms, chosen at random. */
    private static final List<String> MOB_IDS = List.of(
            "giant_zombie", "lava_titan", "wind_wraith", "ghost_boss");

    /** Max custom monsters kept near a single player. */
    private static final int    MAX_PER_PLAYER = 6;
    /** How close monsters count as "nearby" for the population check. */
    private static final double RADIUS         = 40.0;
    /** Max monsters spawned in one pass for a player (paces the population). */
    private static final int    MAX_BURST      = 3;

    private final JavaPlugin            plugin;
    private final CustomMonsterManager  customMonsterManager;
    private final Random                random = new Random();

    public AncientRealmSpawnTask(JavaPlugin plugin, CustomMonsterManager customMonsterManager) {
        this.plugin               = plugin;
        this.customMonsterManager = customMonsterManager;
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!LOCKED_WORLDS.contains(player.getWorld().getName())) continue;
            if (!player.isValid() || player.isDead()) continue;

            int existing = 0;
            for (Entity e : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
                if (e instanceof LivingEntity && customMonsterManager.isCustomMonster(e)) {
                    existing++;
                }
            }
            if (existing >= MAX_PER_PLAYER) continue;

            int toSpawn = Math.min(MAX_BURST, MAX_PER_PLAYER - existing);
            for (int i = 0; i < toSpawn; i++) {
                String id = MOB_IDS.get(random.nextInt(MOB_IDS.size()));
                LivingEntity mob = customMonsterManager.spawn(id, randomSpot(player.getLocation()));
                if (mob != null) mob.setPersistent(true);
            }
        }
    }

    private Location randomSpot(Location origin) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist  = 14.0 + random.nextDouble() * 20.0;
        return origin.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
    }
}