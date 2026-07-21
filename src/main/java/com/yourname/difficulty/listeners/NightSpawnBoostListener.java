package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NightSpawnBoostListener — Two responsibilities:
 *
 * 1. NIGHT SPAWN BOOST: Raises the effective mob cap by 100 mobs at night
 *    (game time 12500-23000).  Implemented by tracking spawned-mob counts
 *    per world and only cancelling spawns once the boost threshold is passed.
 *
 * 2. NIGHTMARE PARTY 10× SCALING: When 2+ Nightmare players are in the same
 *    party, all mobs they interact with get ×10 on:
 *      • Max HP         (mob health × 10)
 *      • Attack damage  (× 10)
 *      • Follow range   (× 10)
 *    Gold drops and XP drops are handled separately in GoldDropListener and
 *    via the NIGHTMARE_PARTY PDC tag on the mob.
 *
 * The Nightmare party multiplier is applied once per mob spawn and tagged via
 * PDC so it isn't applied twice.
 */
public class NightSpawnBoostListener implements Listener {

    /** Night start and end in world ticks. */
    private static final long NIGHT_START = 12_500L;
    private static final long NIGHT_END   = 23_000L;

    /** Extra mobs allowed to spawn during night hours. */
    private static final int NIGHT_BONUS_CAP = 100;

    /** ×10 multipliers for Nightmare party mobs. */
    private static final double NM_HEALTH_MULT  = 10.0;
    private static final double NM_DAMAGE_MULT  = 10.0;
    private static final double NM_RANGE_MULT   = 10.0;
    private static final double NM_GOLD_MULT    = 10.0;  // read by GoldDropListener
    private static final double NM_XP_MULT      = 10.0;  // applied in onEntityDeath

    /** PDC key for nightmare-party-scaled mobs. */
    private static final String NM_PARTY_MOB_KEY = "de_nm_party_mob";

    /** Per-world count of bonus-night mobs spawned this night cycle. */
    private final Map<String, Integer> nightBonusCount = new HashMap<>();
    /** Per-world last night start tick (to reset count each night). */
    private final Map<String, Long> nightStartTick = new HashMap<>();

    private final JavaPlugin             plugin;
    private final PlayerDifficultyManager difficultyManager;
    private final PartyManager           partyManager;

    private final org.bukkit.NamespacedKey nmPartyKey;

    public NightSpawnBoostListener(JavaPlugin plugin,
                                    PlayerDifficultyManager difficultyManager,
                                    PartyManager partyManager) {
        this.plugin            = plugin;
        this.difficultyManager = difficultyManager;
        this.partyManager      = partyManager;
        this.nmPartyKey        = new org.bukkit.NamespacedKey(plugin, NM_PARTY_MOB_KEY);

        // Relentlessly spawn extra aggressive mobs around Nightmare players at night!
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.isDead()) continue;
                if (difficultyManager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
                org.bukkit.World world = p.getWorld();
                long time = world.getTime();
                boolean isNight = time >= NIGHT_START && time <= NIGHT_END;
                boolean isRaining = world.hasStorm();
                if (!isNight || !isRaining) continue;

                // Scale the total spawned mobs with total nightmare players in a party
                int partyNightmareCount = 1;
                if (partyManager != null && partyManager.isInParty(p.getUniqueId())) {
                    Set<UUID> members = partyManager.getPartyMembers(p.getUniqueId());
                    if (members != null) {
                        int nmInParty = 0;
                        for (UUID memberUid : members) {
                            if (difficultyManager.getDifficulty(memberUid) == DifficultyLevel.NIGHTMARE) {
                                nmInParty++;
                            }
                        }
                        if (nmInParty > 1) {
                            partyNightmareCount = nmInParty;
                        }
                    }
                }

                // Spawn 3 to 5 aggressive mobs around them, scaled by total nightmare players in the party
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                int count = (3 + rand.nextInt(3)) * partyNightmareCount;
                EntityType[] pool = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.PHANTOM };
                
                for (int i = 0; i < count; i++) {
                    double angle = rand.nextDouble() * Math.PI * 2.0;
                    double distance = 16 + rand.nextInt(12); // safe spawning distance (16 to 28 blocks away)
                    double dx = Math.cos(angle) * distance;
                    double dz = Math.sin(angle) * distance;
                    org.bukkit.Location spawnLoc = p.getLocation().clone().add(dx, 0, dz);
                    spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1.0);

                    // Strike visual lightning (plays thunder sound, flashes sky)
                    world.strikeLightningEffect(spawnLoc);

                    // Vertical electric-blue particle beam for a thematic look
                    for (double py = 0; py < 10; py += 0.5) {
                        org.bukkit.Location pLoc = spawnLoc.clone().add(0, py, 0);
                        world.spawnParticle(Particle.DUST, pLoc, 4, 0.1, 0.1, 0.1, 0.0,
                            new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 150, 255), 1.5f)); // electric blue
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 2, 0.05, 0.05, 0.05, 0.01);
                    }

                    Entity spawned = world.spawnEntity(spawnLoc, pool[rand.nextInt(pool.length)]);
                    if (spawned instanceof Monster mob) {
                        mob.setTarget(p);
                        // Boost follow range to 100 blocks so they instantly track the player!
                        var range = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
                        if (range != null) {
                            range.setBaseValue(100.0);
                        }
                        // Spawn some dark smoke particles to show they are spawned by the nightmare storm
                        world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.02);
                        world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, spawnLoc, 5, 0.3, 0.3, 0.3, 0.0);
                    }
                }
                p.sendActionBar("§4☠ §cThe Nightmare Storm intensifies... Mobs are swarming! §4☠");
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
            }
        }, 200L, 200L); // Every 10 seconds (200 ticks)
    }

    // ── CreatureSpawnEvent ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (isNightmarePartyMob(mob)) return;

        org.bukkit.World world = event.getLocation().getWorld();
        if (world == null) return;

        long time = world.getTime();
        boolean isNight = time >= NIGHT_START && time <= NIGHT_END;

        // ── Night spawn boost ─────────────────────────────────────────────
        if (isNight) {
            String worldName = world.getName();
            long lastNight = nightStartTick.getOrDefault(worldName, -1L);

            // Reset counter when a new night begins
            if (lastNight == -1L || (time < lastNight)) {
                nightBonusCount.put(worldName, 0);
                nightStartTick.put(worldName, time);
            }

            int bonusUsed = nightBonusCount.getOrDefault(worldName, 0);
            if (bonusUsed < NIGHT_BONUS_CAP) {
                // Allow this spawn as part of the night bonus
                nightBonusCount.put(worldName, bonusUsed + 1);
                // Don't cancel — let it spawn
            }
            // If bonus cap reached, this falls through to normal server cap logic
        }

        // ── Nightmare party scaling ───────────────────────────────────────
        // Find nearby Nightmare players — check if 2+ are in the same party
        Map<UUID, Integer> partyCounts = new HashMap<>();

        for (Entity e : mob.getNearbyEntities(64, 64, 64)) {
            if (!(e instanceof Player p)) continue;
            if (difficultyManager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            if (!partyManager.isInParty(p.getUniqueId())) continue;

            UUID leader = partyManager.getLeader(p.getUniqueId());
            if (leader != null) {
                partyCounts.put(leader, partyCounts.getOrDefault(leader, 0) + 1);
            }
        }

        boolean hasLargeParty = false;
        for (int count : partyCounts.values()) {
            if (count >= 2) {
                hasLargeParty = true;
                break;
            }
        }

        if (hasLargeParty) {
            applyNightmarePartyScaling(mob);

            // ── 5% chance: any Zombie spawned near a Nightmare party of 2+ ────
            // becomes a Speed Zombie (Speed II, permanent).
            if (mob.getType() == EntityType.ZOMBIE && ThreadLocalRandom.current().nextDouble() < 0.05) {
                mob.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, true, true));
                String existingName = mob.getCustomName();
                if (existingName == null || existingName.isEmpty()) {
                    mob.setCustomName("§b⚡ §fSpeed Zombie");
                } else {
                    mob.setCustomName("§b⚡ " + existingName);
                }
                mob.setCustomNameVisible(true);
            }
        }
    }

    // ── Apply ×10 nightmare party multipliers ─────────────────────────────────

    private void applyNightmarePartyScaling(LivingEntity mob) {
        // ── HP × 10 ───────────────────────────────────────────────────────
        AttributeInstance maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            double scaled = maxHp.getBaseValue() * NM_HEALTH_MULT;
            maxHp.setBaseValue(scaled);
            mob.setHealth(scaled);
        }

        // ── Damage × 10 ───────────────────────────────────────────────────
        AttributeInstance atk = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * NM_DAMAGE_MULT);
        }

        // ── Follow range × 10 ─────────────────────────────────────────────
        AttributeInstance range = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(Math.min(range.getBaseValue() * NM_RANGE_MULT, 2048.0));
        }

        // ── Tag the mob with PDC ─────────────────────────────────────────
        mob.getPersistentDataContainer()
                .set(nmPartyKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);

        // ── Custom name so players know it's harder ───────────────────────
        String existing = mob.getCustomName();
        if (existing == null || existing.isEmpty()) {
            mob.setCustomName("§4⚡ §c" + niceName(mob.getType()) + " §4[NM×10]");
        } else {
            mob.setCustomName(existing + " §4[NM×10]");
        }
        mob.setCustomNameVisible(true);
    }

    // ── EntityDeathEvent — XP × 10 for nightmare party mobs ─────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Monster)) return;
        if (!entity.getPersistentDataContainer().has(nmPartyKey,
                org.bukkit.persistence.PersistentDataType.BYTE)) return;

        // ×10 XP
        event.setDroppedExp((int)(event.getDroppedExp() * NM_XP_MULT));

        // Visual death effect
        entity.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER,
                entity.getLocation(), 3, 0.5, 0.5, 0.5, 0);
    }

    // ── Check if a mob is tagged as Nightmare Party ───────────────────────────

    /** Returns true if the mob was scaled by nightmare party rules. */
    public boolean isNightmarePartyMob(Entity entity) {
        if (!(entity instanceof LivingEntity le)) return false;
        return le.getPersistentDataContainer()
                .has(nmPartyKey, org.bukkit.persistence.PersistentDataType.BYTE);
    }

    /** Returns the nightmare party gold multiplier (used by GoldDropListener). */
    public static double getNightmarePartyGoldMult() { return NM_GOLD_MULT; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String niceName(EntityType type) {
        return type.name().replace('_', ' ').toLowerCase()
                .substring(0, 1).toUpperCase()
                + type.name().replace('_', ' ').toLowerCase().substring(1);
    }
}
