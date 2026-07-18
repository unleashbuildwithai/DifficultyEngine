package com.yourname.difficulty;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * Periodically spawns extra hostile mobs near Nightmare players to simulate
 * the ~50% increased spawn rate described in the design.
 *
 * Runs every 15 seconds (doubled frequency). Each Nightmare player gets 6 bonus
 * mobs spawned within 64–128 blocks (far distance), so they start approaching
 * from a greater distance and create sustained pressure from all directions.
 */
public class NightmareSpawnTask extends BukkitRunnable {

    /** Hostile mob types to randomly pick from when bonus-spawning. */
    private static final EntityType[] HOSTILE_POOL = {
        EntityType.ZOMBIE,
        EntityType.ZOMBIE,          // weighted — zombies are most common
        EntityType.SKELETON,
        EntityType.CREEPER,
        EntityType.SPIDER,
        EntityType.ZOMBIE_VILLAGER
    };

    private static final int  BONUS_MOBS     = 6;   // extra mobs per nightmare player per cycle
    private static final int  MIN_DIST       = 64;  // minimum distance — mobs come from far away
    private static final int  MAX_DIST       = 128; // maximum distance — wide encircling spawns
    private static final int  SPAWN_ATTEMPTS = 40;  // more attempts needed for larger area

    private final PlayerDifficultyManager manager;
    private final Random random = new Random();

    public NightmareSpawnTask(PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (Player player : manager.getPlugin().getServer().getOnlinePlayers()) {
            if (manager.getDifficulty(player.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            if (player.isDead()) continue;

            for (int i = 0; i < BONUS_MOBS; i++) {
                Location loc = findSpawnLocation(player);
                if (loc == null) continue;

                EntityType type = HOSTILE_POOL[random.nextInt(HOSTILE_POOL.length)];
                player.getWorld().spawnEntity(loc, type);
            }
        }
    }

    /**
     * Tries to find a surface-level air block near the player that is between
     * MIN_DIST and MAX_DIST blocks away horizontally. Returns null if no valid
     * location is found after SPAWN_ATTEMPTS tries.
     */
    private Location findSpawnLocation(Player player) {
        Location base = player.getLocation();

        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            // Random horizontal offset within [MIN_DIST, MAX_DIST]
            int sign = random.nextBoolean() ? 1 : -1;
            int dx = sign * (MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1));
            sign = random.nextBoolean() ? 1 : -1;
            int dz = sign * (MIN_DIST + random.nextInt(MAX_DIST - MIN_DIST + 1));

            // Place candidate on the highest solid surface
            Location candidate = base.clone().add(dx, 0, dz);
            int surfaceY = candidate.getWorld().getHighestBlockYAt(candidate);
            candidate.setY(surfaceY + 1); // one block above the surface

            // Validate: foot block must be air, block below must be solid
            if (!candidate.getBlock().getType().isAir()) continue;
            if (!candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;

            return candidate;
        }
        return null; // couldn't find a valid spot
    }
}
