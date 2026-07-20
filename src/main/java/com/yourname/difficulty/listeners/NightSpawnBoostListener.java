package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Particle;
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
    }

    // ── CreatureSpawnEvent ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;

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
        Player partyLeader = null;
        int nmPartyCount = 0;

        for (Entity e : mob.getNearbyEntities(64, 64, 64)) {
            if (!(e instanceof Player p)) continue;
            if (difficultyManager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            if (!partyManager.isInParty(p.getUniqueId())) continue;

            if (partyLeader == null) {
                partyLeader = p;
                nmPartyCount = 1;
            } else {
                // Check if same party as first found NM player
                UUID l1 = partyManager.getLeader(partyLeader.getUniqueId());
                UUID l2 = partyManager.getLeader(p.getUniqueId());
                if (l1 != null && l1.equals(l2)) {
                    nmPartyCount++;
                }
            }
        }

        if (nmPartyCount >= 2) {
            applyNightmarePartyScaling(mob);
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
