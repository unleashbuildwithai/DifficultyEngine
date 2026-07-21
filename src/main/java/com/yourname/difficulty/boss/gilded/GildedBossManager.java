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
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

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
 */
public class GildedBossManager implements Listener {

    private static final double PILLAGER_MAX_HP = 400.0;
    /** Radius within which the rider Creeper primes to explode. */
    private static final double CREEPER_TRIGGER_RADIUS = 4.0;
    /** Explosion radius/power of the rider Creeper's blasts. */
    private static final float  CREEPER_EXPLOSION_POWER = 2.0f;
    /** Cooldown between explosions (ticks). */
    private static final long   EXPLOSION_COOLDOWN_TICKS = 60L; // 3s

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

    public GildedBossManager(JavaPlugin plugin, BossEffectListener bossEffectListener, CrimsonBossManager crimsonBossManager) {
        this.plugin = plugin;
        this.bossEffectListener = bossEffectListener;
        this.crimsonBossManager = crimsonBossManager;
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
        pillager.setCustomNameVisible(true);
        pillager.setRemoveWhenFarAway(false);

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

        startAI(pillager, creeper);
        announceSpawn(loc);

        return pillager;
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

    public void cleanup() {
        for (UUID uuid : new ArrayList<>(gildedPillagers)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null) e.remove();
        }
        for (UUID uuid : new ArrayList<>(gildedRiderCreepers)) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null) e.remove();
        }
        gildedPillagers.clear();
        gildedRiderCreepers.clear();
        pillagerToCreeper.clear();
    }
}
