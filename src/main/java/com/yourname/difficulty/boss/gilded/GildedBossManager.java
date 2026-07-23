package com.yourname.difficulty.boss.gilded;

import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.CrimsonBossManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * GildedBossManager — manages §6The Gilded Enforcer§r boss.
 *
 * The boss is a Pillager (immune to explosive damage, super speed) with a
 * Creeper permanently riding on its head. The Creeper has effectively
 * unlimited health (constantly healed) and repeatedly primes/explodes near
 * players — but never actually dies and never damages the Pillager.
 *
 * Tied to the Gilded Sanctum arena (boss_rooms/gilded_sanctum.schem),
 * triggered via a Gilded Spawner Block (GOLD_BLOCK).
 *
 * ── "Gilded Fuse" lightning event (NEW) ───────────────────────────────────
 * If genuine natural weather lightning strikes near the rider Creeper, it
 * gets knocked clean off the Pillager's head and enters a limited "multiply"
 * frenzy: it clones itself repeatedly (regular, mortal, killable Creepers —
 * NOT the immortal boss fuse) up to a hard cap. This can only happen twice
 * per boss life: the first trigger caps at 25 total clones, the second
 * (and final) trigger caps at 35. After both rounds are used, lightning no
 * longer affects the fuse creeper — it simply stays on the Pillager's head
 * as normal. This keeps the fun "chaos" idea while strictly bounding the
 * total number of extra mobs so it can never runaway-spawn.
 */
public class GildedBossManager implements Listener {

    private static final double PILLAGER_MAX_HP = 400.0;
    /** Radius within which the rider Creeper primes to explode. */
    private static final double CREEPER_TRIGGER_RADIUS = 4.0;
    /** Explosion radius/power of the rider Creeper's blasts. */
    private static final float  CREEPER_EXPLOSION_POWER = 2.0f;
    /** Cooldown between explosions (ticks). */
    private static final long   EXPLOSION_COOLDOWN_TICKS = 60L; // 3s

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

    /** Visual scale of the Enforcer's custom gilded_boss model display. */
    private static final float GILDED_DISPLAY_SCALE = 4.0f;
    /** How fast the gilded_boss model bobs/rotates in place (radians per tick). */
    private static final double GILDED_SPIN_SPEED = 0.015;

    private final JavaPlugin plugin;
    private final BossEffectListener bossEffectListener;
    private final CrimsonBossManager crimsonBossManager;
    private final Random random = new Random();

    /** UUID of the Pillager -> UUID of its rider Creeper. */
    private final Map<UUID, UUID> pillagerToCreeper = new HashMap<>();
    /** Set of all rider-Creeper UUIDs belonging to Gilded Enforcers (immortal + damage-immune). */
    private final Set<UUID> gildedRiderCreepers = new HashSet<>();
    /** Set of all Gilded Enforcer Pillager UUIDs (explosion-immune). */
    private final Set<UUID> gildedPillagers = new HashSet<>();

    /** Pillager UUID → number of lightning-multiply rounds already used (0, 1, or 2). */
    private final Map<UUID, Integer> multiplyRoundsUsed = new HashMap<>();
    /** Pillager UUIDs currently mid-frenzy (prevents overlapping multiply tasks). */
    private final Set<UUID> currentlyMultiplying = new HashSet<>();
    /** Regular (mortal, non-boss) clone Creeper UUIDs spawned by the multiply frenzy. */
    private final Set<UUID> multipliedClones = new HashSet<>();

    /** Pillager carrier UUID → paired gilded_boss ItemDisplay UUID (position-synced every tick). */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    /** Pillager carrier UUID → current spin angle (radians), advanced each sync tick. */
    private final Map<UUID, Double> spinAngles = new HashMap<>();
    private final NamespacedKey displayTagKey;

    public GildedBossManager(JavaPlugin plugin, BossEffectListener bossEffectListener, CrimsonBossManager crimsonBossManager) {
        this.plugin = plugin;
        this.bossEffectListener = bossEffectListener;
        this.crimsonBossManager = crimsonBossManager;
        this.displayTagKey = new NamespacedKey(plugin, "de_gilded_display");

        // Position-sync task: streams each carrier's live location to its
        // paired gilded_boss ItemDisplay and applies a gentle bob/spin
        // animation so the gold-block golem feels alive.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (carrierToDisplay.isEmpty()) return;
            Iterator<Map.Entry<UUID, UUID>> it = carrierToDisplay.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                Entity carrier = plugin.getServer().getEntity(entry.getKey());
                Entity display = plugin.getServer().getEntity(entry.getValue());

                if (!(carrier instanceof Pillager p) || p.isDead() || !p.isValid()) {
                    if (display != null && !display.isDead()) display.remove();
                    it.remove();
                    spinAngles.remove(entry.getKey());
                    continue;
                }
                if (!(display instanceof ItemDisplay id) || display.isDead() || !display.isValid()) {
                    it.remove();
                    spinAngles.remove(entry.getKey());
                    continue;
                }

                double angle = spinAngles.getOrDefault(entry.getKey(), 0.0) + GILDED_SPIN_SPEED;
                spinAngles.put(entry.getKey(), angle);
                double bob = Math.sin(angle * 2.0) * 0.15;
                Location target = carrier.getLocation().clone().add(0, bob, 0);
                if (!display.getLocation().getWorld().equals(target.getWorld())
                        || display.getLocation().distanceSquared(target) > 0.0004) {
                    display.teleport(target);
                }
                com.yourname.difficulty.boss.BossDisplayUtil.setYawRotation(id, GILDED_DISPLAY_SCALE, (float) angle);
            }
        }, 1L, 1L);
    }


    public boolean isGildedEnforcerAlive() {
        for (UUID uuid : new ArrayList<>(gildedPillagers)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e instanceof LivingEntity le && !le.isDead()) return true;
            gildedPillagers.remove(uuid);
        }
        return false;
    }

    public Pillager spawnGildedEnforcer(Location loc) {
        if (loc == null) {
            World w = plugin.getServer().getWorld("ancient_realm");
            if (w == null) w = plugin.getServer().getWorlds().get(0);
            loc = new Location(w, 0, 70, 0);
        }

        // Rebuild the Gilded Sanctum arena before spawning
        crimsonBossManager.rebuildArena(null, loc);

        Pillager pillager = (Pillager) loc.getWorld().spawnEntity(loc, EntityType.PILLAGER);
        pillager.setCustomName("§6§lThe Gilded Enforcer");
        pillager.setCustomNameVisible(false); // name lives on the gilded_boss display instead
        pillager.setRemoveWhenFarAway(false);
        // Hide the vanilla Pillager silhouette — only the custom gilded_boss
        // ItemDisplay model should be visible. The Pillager remains as an
        // invisible physics/AI/hitbox carrier (still rides the fuse Creeper).
        pillager.setInvisible(true);
        pillager.setSilent(true);


        var hpAttr = pillager.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(PILLAGER_MAX_HP);
            pillager.setHealth(Math.min(PILLAGER_MAX_HP, hpAttr.getValue()));
        } else {
            pillager.setHealth(Math.min(PILLAGER_MAX_HP, 1024.0));
        }

        // Super speed
        var spdAttr = pillager.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (spdAttr != null) spdAttr.setBaseValue(spdAttr.getBaseValue() * 3.0);
        pillager.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 3, false, false, false));

        pillager.setCanJoinRaid(false);

        // Spawn the rider Creeper
        Creeper creeper = (Creeper) loc.getWorld().spawnEntity(
                loc.clone().add(0, 0, 0), EntityType.CREEPER);
        creeper.setCustomName("§a☠ §6Gilded Fuse");
        creeper.setCustomNameVisible(true);
        creeper.setRemoveWhenFarAway(false);
        creeper.setPowered(false);
        creeper.setExplosionRadius((int) CREEPER_EXPLOSION_POWER);
        var creeperHp = creeper.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (creeperHp != null) {
            creeperHp.setBaseValue(2000.0);
            creeper.setHealth(Math.min(2000.0, creeperHp.getValue()));
        }

        pillager.addPassenger(creeper);

        gildedPillagers.add(pillager.getUniqueId());
        gildedRiderCreepers.add(creeper.getUniqueId());
        pillagerToCreeper.put(pillager.getUniqueId(), creeper.getUniqueId());

        pillager.setMetadata("de_gilded_pillager", new FixedMetadataValue(plugin, true));
        creeper.setMetadata("de_gilded_creeper", new FixedMetadataValue(plugin, true));

        bossEffectListener.registerBoss(pillager);
        bossEffectListener.spawnShriek(pillager);

        // Spawn the custom gilded_boss ItemDisplay visual paired to this carrier
        ItemDisplay display = spawnGildedDisplay(pillager);
        carrierToDisplay.put(pillager.getUniqueId(), display.getUniqueId());
        spinAngles.put(pillager.getUniqueId(), 0.0);

        startAI(pillager, creeper);
        announceSpawn(loc);

        return pillager;
    }

    /**
     * Builds the Gilded Enforcer's custom Blockbench gold-golem visual — a
     * fixed-orientation ItemDisplay showing the gilded_boss model, entirely
     * independent from the now-invisible vanilla Pillager carrier underneath.
     */
    private ItemDisplay spawnGildedDisplay(Pillager carrier) {
        return com.yourname.difficulty.boss.BossDisplayUtil.spawnDisplay(
                carrier, 0.0, Material.RAW_GOLD, 3004, GILDED_DISPLAY_SCALE,
                15, 15,
                "§6§lThe Gilded Enforcer",
                displayTagKey,
                Display.Billboard.FIXED);
    }

    private void startAI(Pillager pillager, Creeper creeper) {
        new BukkitRunnable() {
            int tick = 0;
            long lastExplosion = -EXPLOSION_COOLDOWN_TICKS;

            @Override
            public void run() {
                if (pillager.isDead() || !pillager.isValid()) {
                    if (creeper.isValid()) creeper.remove();
                    gildedPillagers.remove(pillager.getUniqueId());
                    gildedRiderCreepers.remove(creeper.getUniqueId());
                    pillagerToCreeper.remove(pillager.getUniqueId());
                    UUID displayUuid = carrierToDisplay.remove(pillager.getUniqueId());
                    if (displayUuid != null) {
                        Entity d = plugin.getServer().getEntity(displayUuid);
                        if (d != null && !d.isDead()) d.remove();
                    }
                    spinAngles.remove(pillager.getUniqueId());
                    cancel();
                    return;
                }
                tick++;


                // Keep the creeper alive & riding no matter what
                if (creeper.isDead() || !creeper.isValid()) {
                    // Respawn a fresh immortal creeper on the pillager's head
                    Creeper fresh = (Creeper) pillager.getWorld().spawnEntity(pillager.getLocation(), EntityType.CREEPER);
                    fresh.setCustomName("§a☠ §6Gilded Fuse");
                    fresh.setCustomNameVisible(true);
                    fresh.setRemoveWhenFarAway(false);
                    fresh.setExplosionRadius((int) CREEPER_EXPLOSION_POWER);
                    var freshHp = fresh.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (freshHp != null) { freshHp.setBaseValue(2000.0); fresh.setHealth(2000.0); }
                    fresh.setMetadata("de_gilded_creeper", new FixedMetadataValue(plugin, true));
                    pillager.addPassenger(fresh);
                    gildedRiderCreepers.add(fresh.getUniqueId());
                    pillagerToCreeper.put(pillager.getUniqueId(), fresh.getUniqueId());
                    return;
                } else {
                    // Full-heal every tick — truly unlimited health
                    var hp = creeper.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    if (hp != null) creeper.setHealth(hp.getValue());
                }

                // Find nearest player to trigger the fuse
                Player nearest = null;
                double closest = CREEPER_TRIGGER_RADIUS;
                for (Entity e : pillager.getNearbyEntities(CREEPER_TRIGGER_RADIUS, CREEPER_TRIGGER_RADIUS, CREEPER_TRIGGER_RADIUS)) {
                    if (e instanceof Player p && !p.isDead()) {
                        double d = p.getLocation().distance(pillager.getLocation());
                        if (d < closest) { closest = d; nearest = p; }
                    }
                }

                if (nearest != null && (tick - lastExplosion) >= EXPLOSION_COOLDOWN_TICKS) {
                    lastExplosion = tick;
                    detonateFuse(pillager, creeper);
                }

                // Pillager targets nearest player always
                if (pillager.getTarget() == null && nearest != null) {
                    pillager.setTarget(nearest);
                }
            }
        }.runTaskTimer(plugin, 20L, 5L);
    }

    /** Triggers a real (but harmless-to-boss) explosion at the creeper's location. */
    private void detonateFuse(Pillager pillager, Creeper creeper) {
        Location loc = creeper.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 1.0f, 0.8f);

        // 1s delay, then real explosion (damages nearby players/blocks, not the boss)
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!creeper.isValid()) return;
                world.playSound(creeper.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                world.createExplosion(creeper.getLocation(), CREEPER_EXPLOSION_POWER, false, true, creeper);
                world.spawnParticle(Particle.EXPLOSION_EMITTER, creeper.getLocation(), 2, 0.3, 0.3, 0.3, 0);
                // Ensure creeper survives its own blast
                var hp = creeper.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) creeper.setHealth(hp.getValue());
                creeper.setFireTicks(0);
                pillager.setFireTicks(0);
                pillager.setHealth(Math.min(pillager.getHealth() + 1, pillager.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()));
            }
        }.runTaskLater(plugin, 20L);
    }

    private void announceSpawn(Location loc) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) > 120) continue;
            p.sendMessage("");
            p.sendMessage("§6☠ §e§lTHE GILDED ENFORCER HAS AWAKENED! §6☠");
            p.sendMessage("§7A pillager king marches, crowned by an undying fuse of death.");
            p.sendMessage("§c⚠ §7The rider §2Creeper §7cannot be killed and explodes relentlessly!");
            p.sendMessage("§c⚠ §7The Enforcer itself is §c§limmune to explosive damage§7.");
            p.sendMessage("");
            p.sendTitle("§6§l☠ BOSS AWAKENS ☠", "§7§oThe Gilded Enforcer marches...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PILLAGER_CELEBRATE, 1.0f, 0.6f);
        }
    }

    // ── Damage immunity: Pillager immune to explosive damage ─────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGildedPillagerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager)) return;
        if (!gildedPillagers.contains(pillager.getUniqueId())) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGildedPillagerGenericDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Pillager pillager)) return;
        if (!gildedPillagers.contains(pillager.getUniqueId())) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
            event.setCancelled(true);
        }
    }

    // ── Immortality: rider Creeper never actually dies ────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGildedCreeperDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Creeper creeper)) return;
        if (!gildedRiderCreepers.contains(creeper.getUniqueId())) return;

        double resultingHealth = creeper.getHealth() - event.getFinalDamage();
        if (resultingHealth <= 0.5) {
            // Never let it actually die — cap the damage
            event.setDamage(Math.max(0, creeper.getHealth() - 1.0));
        }
    }

    // ── Explosion protection for the arena / gilded spawner block ────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onGildedExplode(EntityExplodeEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof Creeper creeper && gildedRiderCreepers.contains(creeper.getUniqueId())) {
            // Don't let the fuse creeper destroy the arena or the spawner block
            event.blockList().removeIf(b -> b.getType() == Material.GOLD_BLOCK);
        }
    }

    // ── "Gilded Fuse" lightning event: knock the fuse creeper off + multiply ───

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

        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(pillagerToCreeper.entrySet())) {
            UUID pillagerUuid = entry.getKey();
            UUID creeperUuid  = entry.getValue();

            if (currentlyMultiplying.contains(pillagerUuid)) continue; // already mid-frenzy

            int roundsUsed = multiplyRoundsUsed.getOrDefault(pillagerUuid, 0);
            if (roundsUsed >= 2) continue; // both rounds already consumed for this boss life

            Entity creeperEnt = plugin.getServer().getEntity(creeperUuid);
            if (!(creeperEnt instanceof Creeper fuseCreeper) || fuseCreeper.isDead() || !fuseCreeper.isValid()) continue;

            if (fuseCreeper.getLocation().distanceSquared(strikeLoc) > LIGHTNING_TRIGGER_RADIUS * LIGHTNING_TRIGGER_RADIUS) continue;

            Entity pillagerEnt = plugin.getServer().getEntity(pillagerUuid);
            if (!(pillagerEnt instanceof Pillager pillager)) continue;

            // ── Knock the fuse creeper off the pillager's head ────────────────
            fuseCreeper.eject();
            Vector knock = new Vector(
                    (random.nextDouble() - 0.5) * 0.6,
                    0.6,
                    (random.nextDouble() - 0.5) * 0.6);
            fuseCreeper.setVelocity(knock);

            int roundCap = (roundsUsed == 0) ? MULTIPLY_CAP_ROUND_1 : MULTIPLY_CAP_ROUND_2;
            multiplyRoundsUsed.put(pillagerUuid, roundsUsed + 1);

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
        currentlyMultiplying.add(pillagerUuid);

        new BukkitRunnable() {
            int spawnedThisRound = 0;

            @Override
            public void run() {
                // Stop conditions: boss died, or round cap reached
                if (pillager.isDead() || !pillager.isValid() || spawnedThisRound >= totalCap) {
                    currentlyMultiplying.remove(pillagerUuid);
                    cancel();
                    return;
                }

                // Pace concurrent population — skip this tick if already at the concurrent cap
                int aliveClones = 0;
                for (UUID cloneUuid : multipliedClones) {
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
                multipliedClones.add(clone.getUniqueId());

                base.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, spawnLoc, 1, 0.1, 0.1, 0.1, 0);
                spawnedThisRound++;
            }
        }.runTaskTimer(plugin, 0L, MULTIPLY_SPAWN_INTERVAL_TICKS);
    }

    public void cleanup() {
        for (UUID uuid : new ArrayList<>(gildedPillagers)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null) e.remove();
        }
        for (UUID uuid : new ArrayList<>(gildedRiderCreepers)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null) e.remove();
        }
        for (UUID uuid : new ArrayList<>(multipliedClones)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null) e.remove();
        }
        gildedPillagers.clear();
        gildedRiderCreepers.clear();
        pillagerToCreeper.clear();
        multiplyRoundsUsed.clear();
        currentlyMultiplying.clear();
        multipliedClones.clear();
    }
}
