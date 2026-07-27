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
 * LightningMonsterSummonListener — "Monster Storms".
 *
 * Whenever §fgenuine natural weather lightning§r strikes anywhere in the
 * world, there is a §e20% chance§r that strike becomes a "Monster Storm" and
 * summons 1-10 extra hostile mobs around the strike location.
 *
 * ── IMPORTANT: ONLY weather lightning can trigger this ──────────────────────
 * Any lightning triggered BY the plugin itself — admin/dev commands
 * (/adminlight, /lightningadmin), the Support Staff's on-hit lightning proc,
 * boss lightning barrages (Tempest Overlord, Crimson boss, etc.) — must NOT
 * spawn bonus mobs. Those are identified via {@link LightningStrikeEvent#getCause()}
 * being anything other than {@code Cause.WEATHER}, and are ignored entirely.
 *
 * This was the actual cause of the server-crashing mob overload: admin/player
 * -triggered lightning was ALSO spawning 1-10 mobs per strike with no gate,
 * so spell/boss lightning barrages (which can fire many strikes rapidly)
 * were compounding into hundreds of extra mobs. Restricting bonus spawns to
 * real weather strikes only — plus the 20% chance gate — fixes this while
 * keeping the original 1-10 mob "Monster Storm" flavour intact for natural
 * thunderstorms.
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

    /** Minimum and maximum number of bonus mobs summoned per triggered Monster Storm. */
    private static final int MIN_MOBS = 1;
    private static final int MAX_MOBS = 10;

    /** Radius around the strike within which mobs are scattered. */
    private static final double SCATTER_RADIUS = 6.0;

    /** Chance that a genuine weather lightning strike becomes a "Monster Storm". */
    private static final double MONSTER_STORM_CHANCE = 0.20; // 20%

    private final JavaPlugin plugin;

    public LightningMonsterSummonListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningStrike(LightningStrikeEvent event) {
        // ── ONLY genuine natural weather lightning can trigger a Monster Storm ──
        // Admin lightning, spell procs, and boss lightning barrages are all
        // plugin-triggered (Cause != WEATHER) and must never spawn bonus mobs.
        if (event.getCause() != LightningStrikeEvent.Cause.WEATHER) return;

        // ── 20% chance gate — most weather strikes are just weather ─────────────
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        if (rand.nextDouble() >= MONSTER_STORM_CHANCE) return;

        Location loc = event.getLightning().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

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
                world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4, 0.02);
            }
        }
    }
}
