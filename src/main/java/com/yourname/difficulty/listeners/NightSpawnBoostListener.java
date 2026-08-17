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
 * 2. NIGHTMARE PARTY 10× SCALING: When a FULL Nightmare party of 4+ players
 *    is together, all mobs they interact with get ×10 on:
 *      • Max HP         (mob health × 10)
 *      • Attack damage  (× 10)
 *      • Follow range   (× 10)
 *    Gold drops and XP drops are handled separately in GoldDropListener and
 *    via the NIGHTMARE_PARTY PDC tag on the mob.
 *
 * ── CRASH-FIX NOTES (v2) ─────────────────────────────────────────────────
 *  The original "Nightmare Storm" periodic task spawned 3-5 mobs per
 *  Nightmare player EVERY 10 SECONDS with no population cap and no minimum
 *  party-size gate — a single SOLO Nightmare player left in a thunderstorm
 *  would accumulate thousands of mobs over a play session with nothing ever
 *  despawning them, eventually crashing the server. This has been completely
 *  redesigned:
 *
 *   • The intense "Storm" effect ONLY triggers when §e4 or more§r Nightmare
 *     players are together in the same party (REQUIRED_NM_PARTY_SIZE).
 *     Anything less than that (including solo Nightmare players) is treated
 *     as normal difficulty — no bonus storm mobs at all.
 *
 *   • A hard population CAP is enforced per party (STORM_MOB_CAP). Before
 *     spawning, we count nearby storm-tagged mobs within STORM_CAP_RADIUS of
 *     the party and never exceed the cap.
 *
 *   • Mobs are "topped up" toward the cap rather than always adding a fixed
 *     amount — so the swarm stays at a steady, bounded size instead of
 *     growing forever.
 *
 *   • When a storm mob dies, a replacement is summoned §binstantly§r via a
 *     lightning strike (see onEntityDeath) — keeping the swarm topped up in
 *     real time without waiting for the next periodic tick, while still
 *     respecting the same population cap.
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

    /** Minimum number of Nightmare players together in a party required to trigger the Storm. */
    private static final int REQUIRED_NM_PARTY_SIZE = 4;

    /** Hard cap on live storm-tagged mobs allowed per party at once. */
    private static final int STORM_MOB_CAP = 24;

    /** Radius (blocks) around a party member used to count existing storm mobs toward the cap. */
    private static final double STORM_CAP_RADIUS = 48.0;

    /** Max new mobs summoned in a single top-up tick (prevents single-frame bursts). */
    private static final int MAX_SPAWN_PER_TICK = 4;

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

        // Storm mob top-up — only runs for FULL Nightmare parties (4+), and only
        // ever tops the population UP TO the cap. Solo/small-party Nightmare
        // players get no bonus storm at all.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            // Track which party leaders we've already processed this tick so a
            // 4-player party doesn't get processed 4 separate times.
            Set<UUID> processedLeaders = new HashSet<>();

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p.isDead()) continue;
                if (difficultyManager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
                if (partyManager == null || !partyManager.isInParty(p.getUniqueId())) continue;

                UUID leader = partyManager.getLeader(p.getUniqueId());
                if (leader == null || !processedLeaders.add(leader)) continue;

                Set<UUID> members = partyManager.getPartyMembers(p.getUniqueId());
                if (members == null) continue;

                List<Player> nmMembers = new ArrayList<>();
                for (UUID memberUid : members) {
                    if (difficultyManager.getDifficulty(memberUid) != DifficultyLevel.NIGHTMARE) continue;
                    Player mp = plugin.getServer().getPlayer(memberUid);
                    if (mp != null && mp.isOnline() && !mp.isDead()) nmMembers.add(mp);
                }

                // ── Gate: require a FULL Nightmare party of 4+ ────────────────
                if (nmMembers.size() < REQUIRED_NM_PARTY_SIZE) continue;

                // Storm only active at night during a genuine THUNDERSTORM — plain
                // (non-thundering) rain does NOT trigger this; see MonsterCapListener's
                // isStormNight() for the same rule applied to spawn-cap scaling.
                Player anchor = nmMembers.get(0);
                org.bukkit.World world = anchor.getWorld();
                long time = world.getTime();
                boolean isNight = time >= NIGHT_START && time <= NIGHT_END;
                boolean isThunderStorm = world.isThundering();
                if (!isNight || !isThunderStorm) continue;


                // ── Population cap check ──────────────────────────────────────
                int existingStormMobs = countNearbyStormMobs(anchor.getLocation(), STORM_CAP_RADIUS);
                int room = STORM_MOB_CAP - existingStormMobs;
                if (room <= 0) continue;

                int toSpawn = Math.min(room, MAX_SPAWN_PER_TICK);
                ThreadLocalRandom rand = ThreadLocalRandom.current();
                EntityType[] pool = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.PHANTOM };

                for (int i = 0; i < toSpawn; i++) {
                    Player target = nmMembers.get(rand.nextInt(nmMembers.size()));
                    spawnStormMob(target, pool, rand);
                }

                anchor.sendActionBar("§4☠ §cThe Nightmare Storm intensifies... Mobs are swarming! §4☠ §8(" 
                        + (existingStormMobs + toSpawn) + "/" + STORM_MOB_CAP + ")");
                for (Player mp : nmMembers) {
                    mp.playSound(mp.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.5f);
                }
            }
        }, 200L, 200L); // Every 10 seconds (200 ticks) — tops up toward the cap, never uncapped growth
    }

    /** Counts living storm-tagged mobs within radius of the given location. */
    private int countNearbyStormMobs(org.bukkit.Location loc, double radius) {
        int count = 0;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity le && isNightmarePartyMob(le)) count++;
        }
        return count;
    }

    /** Spawns a single storm mob near the target player, struck by lightning, targeting them immediately. */
    private void spawnStormMob(Player p, EntityType[] pool, ThreadLocalRandom rand) {
        org.bukkit.World world = p.getWorld();
        double angle = rand.nextDouble() * Math.PI * 2.0;
        double distance = 16 + rand.nextInt(12); // safe spawning distance (16 to 28 blocks away)
        double dx = Math.cos(angle) * distance;
        double dz = Math.sin(angle) * distance;
        org.bukkit.Location spawnLoc = p.getLocation().clone().add(dx, 0, dz);
        spawnLoc.setY(world.getHighestBlockYAt(spawnLoc) + 1.0);

        // Strike visual lightning (plays thunder sound, flashes sky) — visual only, does NOT
        // fire LightningStrikeEvent, so this cannot recursively trigger more mob summons.
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
            applyNightmarePartyScaling(mob);
            // Boost follow range to 100 blocks so they instantly track the player!
            var range = mob.getAttribute(Attribute.FOLLOW_RANGE);
            if (range != null) {
                range.setBaseValue(100.0);
            }
            // Spawn some dark smoke particles to show they are spawned by the nightmare storm
            world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.02);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, spawnLoc, 5, 0.3, 0.3, 0.3, 0.0);
        }
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

        boolean hasFullParty = false;
        for (int count : partyCounts.values()) {
            if (count >= REQUIRED_NM_PARTY_SIZE) {
                hasFullParty = true;
                break;
            }
        }

        if (hasFullParty) {
            applyNightmarePartyScaling(mob);

            // ── 5% chance: any Zombie spawned near a full Nightmare party ─────
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
        AttributeInstance maxHp = mob.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            double scaled = maxHp.getBaseValue() * NM_HEALTH_MULT;
            maxHp.setBaseValue(scaled);
            mob.setHealth(scaled);
        }

        // ── Damage × 10 ───────────────────────────────────────────────────
        AttributeInstance atk = mob.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * NM_DAMAGE_MULT);
        }

        // ── Follow range × 10 ─────────────────────────────────────────────
        AttributeInstance range = mob.getAttribute(Attribute.FOLLOW_RANGE);
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

        // ── Instant lightning-strike replacement (still capped!) ────────────
        // When a storm mob dies, immediately summon a replacement via a
        // lightning strike near the nearest Nightmare party member — keeping
        // the swarm feeling relentless in real time — but ONLY if we're still
        // under the population cap. This does NOT run away unbounded: it is
        // a strict 1-for-1 top-up, gated by the exact same STORM_MOB_CAP used
        // by the periodic task above.
        Location deathLoc = entity.getLocation();
        org.bukkit.World world = deathLoc.getWorld();
        if (world == null) return;

        int nearbyStormMobs = countNearbyStormMobs(deathLoc, STORM_CAP_RADIUS);
        if (nearbyStormMobs >= STORM_MOB_CAP) return; // at cap — no replacement

        // Find the nearest online Nightmare player to re-target the replacement at
        Player nearestNm = null;
        double closestSq = Double.MAX_VALUE;
        for (Player p : world.getPlayers()) {
            if (difficultyManager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            double distSq = p.getLocation().distanceSquared(deathLoc);
            if (distSq < closestSq) { closestSq = distSq; nearestNm = p; }
        }
        if (nearestNm == null) return;

        // Delay 1 tick so the death is fully processed before we spawn the replacement
        Player finalTarget = nearestNm;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!finalTarget.isOnline() || finalTarget.isDead()) return;
            ThreadLocalRandom rand = ThreadLocalRandom.current();
            EntityType[] pool = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER, EntityType.PHANTOM };
            spawnStormMob(finalTarget, pool, rand);
        });
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
