package com.yourname.difficulty.boss.gilded;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Handles the "Gilded Fuse" lightning event: natural weather lightning
 * striking near the Gilded Enforcer's rider Creeper knocks it off the
 * Pillager's head and triggers a limited multiply frenzy (regular, mortal,
 * killable Creepers — NOT the immortal boss fuse). Extracted from
 * {@link GildedBossManager} to keep files under the 400-line limit.
 */
class GildedFuseFrenzy implements Listener {

    /** Radius within which a lightning strike can knock the fuse creeper off. */
    private static final double LIGHTNING_TRIGGER_RADIUS = 6.0;
    /** Max total clones spawned on the FIRST lightning-triggered multiply round. */
    private static final int MULTIPLY_CAP_ROUND_1 = 25;
    /** Max total clones spawned on the SECOND (final) lightning-triggered multiply round. */
    private static final int MULTIPLY_CAP_ROUND_2 = 35;
    /** Max clones alive SIMULTANEOUSLY during a multiply frenzy (paces the spawn burst). */
    private static final int MULTIPLY_CONCURRENT_CAP = 10;
    /** Ticks between each clone spawn during a multiply frenzy. */
    private static final long MULTIPLY_SPAWN_INTERVAL_TICKS = 6L;
    /** Explosion radius/power of the rider Creeper's blasts (used for clone visuals). */
    private static final float CREEPER_EXPLOSION_POWER = 2.0f;

    private final JavaPlugin plugin;
    private final GildedBossState state;
    private final Random random = new Random();

    GildedFuseFrenzy(JavaPlugin plugin, GildedBossState state) {
        this.plugin = plugin;
        this.state = state;
    }

    /**
     * Natural weather lightning striking near the fuse creeper knocks it off
     * the Pillager's head and triggers a limited multiply frenzy. This can
     * only happen twice per boss life (round 1 = cap 25, round 2 = cap 35).
     * After both rounds are consumed, lightning no longer affects the fuse.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLightningNearFuse(LightningStrikeEvent event) {
        // Only genuine natural weather lightning triggers this — never
        // plugin/admin-triggered strikes.
        if (event.getCause() != LightningStrikeEvent.Cause.WEATHER) return;

        Location strikeLoc = event.getLightning().getLocation();

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(state.pillagerToCreeper.entrySet())) {
            UUID pillagerUuid = entry.getKey();
            UUID creeperUuid  = entry.getValue();

            if (state.currentlyMultiplying.contains(pillagerUuid)) continue; // already mid-frenzy

            int roundsUsed = state.multiplyRoundsUsed.getOrDefault(pillagerUuid, 0);
            if (roundsUsed >= 2) continue; // both rounds already consumed for this boss life

            Entity creeperEnt = plugin.getServer().getEntity(creeperUuid);
            if (!(creeperEnt instanceof Creeper fuseCreeper) || fuseCreeper.isDead() || !fuseCreeper.isValid()) continue;

            if (fuseCreeper.getLocation().distanceSquared(strikeLoc) > LIGHTNING_TRIGGER_RADIUS * LIGHTNING_TRIGGER_RADIUS) continue;

            Entity pillagerEnt = plugin.getServer().getEntity(pillagerUuid);
            if (!(pillagerEnt instanceof Pillager pillager)) continue;

            // ── Knock the fuse creeper off the pillager's head ─────────────────
            fuseCreeper.eject();
            Vector knock = new Vector(
                    (random.nextDouble() - 0.5) * 0.6,
                    0.6,
                    (random.nextDouble() - 0.5) * 0.6);
            fuseCreeper.setVelocity(knock);

            int roundCap = (roundsUsed == 0) ? MULTIPLY_CAP_ROUND_1 : MULTIPLY_CAP_ROUND_2;
            state.multiplyRoundsUsed.put(pillagerUuid, roundsUsed + 1);

            fuseCreeper.getWorld().playSound(fuseCreeper.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
            for (Player p : fuseCreeper.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(fuseCreeper.getLocation()) <= 14400.0) {
                    p.sendMessage("§e⚡ §6The Gilded Fuse was struck by lightning and knocked loose! §7It's multiplying!");
                    p.sendTitle("§6§lFUSE OVERLOAD!", "§7Creepers multiplying — cap: " + roundCap, 5, 40, 10);
                }
            }

            startMultiplyFrenzy(pillager, fuseCreeper, roundCap);
        }
    }

    /**
     * Spawns regular mortal Creeper clones near the knocked-off fuse creeper,
     * one every {@link #MULTIPLY_SPAWN_INTERVAL_TICKS}, up to {@code totalCap}
     * total clones for this round, never allowing more than
     * {@link #MULTIPLY_CONCURRENT_CAP} to be alive at once.
     */
    private void startMultiplyFrenzy(Pillager pillager, Creeper originCreeper, int totalCap) {
        UUID pillagerUuid = pillager.getUniqueId();
        state.currentlyMultiplying.add(pillagerUuid);

        new BukkitRunnable() {
            int spawnedThisRound = 0;

            @Override
            public void run() {
                // Stop conditions: boss died, or round cap reached
                if (pillager.isDead() || !pillager.isValid() || spawnedThisRound >= totalCap) {
                    state.currentlyMultiplying.remove(pillagerUuid);
                    cancel();
                    return;
                }

                // Pace concurrent population — skip this tick if already at the concurrent cap
                int aliveClones = 0;
                for (UUID cloneUuid : state.multipliedClones) {
                    Entity e = plugin.getServer().getEntity(cloneUuid);
                    if (e instanceof LivingEntity le && !le.isDead() && le.isValid()) aliveClones++;
                }
                if (aliveClones >= MULTIPLY_CONCURRENT_CAP) return;

                Location base = (originCreeper.isValid() ? originCreeper.getLocation() : pillager.getLocation());
                double angle = random.nextDouble() * Math.PI * 2.0;
                double dist  = 1.0 + random.nextDouble() * 3.0;
                Location spawnLoc = base.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

                Creeper clone = (Creeper) base.getWorld().spawnEntity(spawnLoc, EntityType.CREEPER);
                clone.setCustomName("§a☠ §7Gilded Fuse Spawn");
                clone.setCustomNameVisible(true);
                // Regular, mortal creeper — NOT tagged as a boss rider, so it can be
                // killed normally and does not get infinite-health treatment.
                state.multipliedClones.add(clone.getUniqueId());

                base.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, spawnLoc, 1, 0.1, 0.1, 0.1, 0);
                spawnedThisRound++;
            }
        }.runTaskTimer(plugin, 0L, MULTIPLY_SPAWN_INTERVAL_TICKS);
    }
}
