package com.yourname.difficulty.listeners;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * LightningMonsterSummonListener — Whenever lightning strikes anywhere in the
 * world (natural thunderstorm strikes OR any plugin-triggered
 * {@code world.strikeLightning(...)} call, e.g. the Support Staff's chance-
 * on-hit lightning), 1-10 extra hostile mobs are summoned around the strike
 * location.
 *
 * This does NOT fire for purely-visual {@code strikeLightningEffect(...)}
 * calls (those don't create a real Lightning entity / event), only for real
 * lightning strikes.
 */
public class LightningMonsterSummonListener implements Listener {

    private static final EntityType[] POOL = {
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.WITCH,
        EntityType.HUSK
    };

    /** Minimum and maximum number of bonus mobs summoned per lightning strike. */
    private static final int MIN_MOBS = 1;
    private static final int MAX_MOBS = 10;

    /** Radius around the strike within which mobs are scattered. */
    private static final double SCATTER_RADIUS = 6.0;

    private final JavaPlugin plugin;

    public LightningMonsterSummonListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        Location loc = event.getLightning().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int count = MIN_MOBS + rand.nextInt(MAX_MOBS - MIN_MOBS + 1); // 1-10 inclusive

        for (int i = 0; i < count; i++) {
            double angle = rand.nextDouble() * Math.PI * 2.0;
            double dist  = rand.nextDouble() * SCATTER_RADIUS;
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            Location spawnLoc = loc.clone().add(dx, 0, dz);
            spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1.0);

            if (!spawnLoc.getBlock().getType().isAir()) continue;

            EntityType type = POOL[rand.nextInt(POOL.length)];
            var spawned = world.spawnEntity(spawnLoc, type);
            if (spawned instanceof Monster mob) {
                world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }
}
