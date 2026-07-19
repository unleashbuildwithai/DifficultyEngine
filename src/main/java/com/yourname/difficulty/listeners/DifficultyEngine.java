package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.Main;
import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

/**
 * DifficultyEngine — Core game-mechanics listener.
 *
 * Four responsibilities:
 *  1. onCreatureSpawn  — Scale mob stats based on the highest difficulty
 *                        player within 64 blocks.
 *  2. onEntityTarget   — Redirect mob aggro toward Nightmare players (35%
 *                        chance) and protect Peaceful players from targeting.
 *  3. onEntityDeath    — Clean up display state on death to prevent the Paper
 *                        1.21 "ghost health bar floating at death location" bug.
 *  4. onEntityDamage   — When a player has /hpbar ON, update the mob's custom
 *                        name to show live HP (❤ current / max) after each hit.
 *
 * Moved to com.yourname.difficulty.listeners as part of the modularisation.
 * Party threat-aggregation for NIGHTMARE players lives in NightmareAggroListener.
 */
public class DifficultyEngine implements Listener {

    /**
     * Radius to scan for the nearest player when a mob spawns.
     * Intentionally kept smaller than before (40 vs 64 blocks) so that a
     * Nightmare player far across the map cannot accidentally scale mobs
     * that spawn next to peaceful players.
     */
    private static final double SPAWN_CHECK_RADIUS = 40.0;
    /** Radius to scan when redirecting mob aggro. */
    private static final double AGGRO_CHECK_RADIUS = 32.0;
    /**
     * % chance a mob re-targets the nearest Nightmare player instead of its
     * current target (only fires when the current target is NOT Nightmare).
     */
    private static final int NIGHTMARE_AGGRO_CHANCE = 35;

    private final Main plugin;
    private final PlayerDifficultyManager manager;
    private final Random random = new Random();

    /**
     * PDC key used to mark every mob whose stats were scaled by this plugin.
     * Checked in onEntityDeath to trigger display cleanup and prevent the
     * Paper 1.21 ghost-health-bar bug.
     */
    private final NamespacedKey scaledKey;

    public DifficultyEngine(Main plugin, PlayerDifficultyManager manager) {
        this.plugin    = plugin;
        this.manager   = manager;
        this.scaledKey = new NamespacedKey(plugin, "difficulty_scaled");
    }

    // -------------------------------------------------------------------------
    // Spawn scaling
    // -------------------------------------------------------------------------

    /**
     * Scales the spawned mob's stats based on the difficulty of the NEAREST
     * player within {@code SPAWN_CHECK_RADIUS} blocks — not the highest one.
     *
     * <p><b>Anti-grief design:</b> Using "nearest" instead of "highest nearby"
     * means a Nightmare player wandering 35 blocks away from a peaceful area
     * will not make mobs harder for the peaceful players right next to the
     * spawn.  Each player effectively gets mobs tuned to their own difficulty
     * level rather than the hardest player in the vicinity.
     */
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;

        // Use the NEAREST player's difficulty, not the highest in range
        DifficultyLevel level = getNearestPlayerDifficulty(mob, SPAWN_CHECK_RADIUS);

        // EASY is vanilla — no changes. PEACEFUL doesn't buff mobs either.
        if (level.getTier() <= DifficultyLevel.EASY.getTier()) return;

        applyStats(mob, level);
    }

    /** Applies all attribute buffs from the given difficulty tier to the mob. */
    private void applyStats(LivingEntity mob, DifficultyLevel level) {

        // Max health
        AttributeInstance maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            double scaled = maxHp.getBaseValue() * level.getHealthMult();
            maxHp.setBaseValue(scaled);
            mob.setHealth(scaled);
        }

        // Attack damage
        AttributeInstance atk = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * level.getDamageMult());
        }

        // Movement speed
        AttributeInstance spd = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(spd.getBaseValue() * level.getSpeedMult());
        }

        // Follow range (how far the mob tracks before giving up)
        AttributeInstance follow = mob.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(level.getFollowRange());
        }

        // ── Tag this mob so onEntityDeath can clean it up ─────────────────────
        // Paper 1.21 leaves a ghost health bar floating at the death location
        // for any mob whose GENERIC_MAX_HEALTH base value was modified. We tag
        // scaled mobs here so we can wipe their display state on death.
        mob.getPersistentDataContainer().set(scaledKey, PersistentDataType.BYTE, (byte) 1);
    }

    // -------------------------------------------------------------------------
    // Live HP display  —  /hpbar feature
    // -------------------------------------------------------------------------

    /**
     * When a player with /hpbar ON damages a mob, we schedule a 1-tick
     * delayed task so the damage has already been applied, then write the
     * real post-hit health as the mob's custom name:
     *
     *   §c❤ §f18 §7/ §f25
     *
     * The name is cleared automatically in onEntityDeath (see below).
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Only care about player → mob hits
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity mob))  return;
        if (mob instanceof Player) return;
        // Skip our invisible seat ArmorStands
        if (mob.getScoreboardTags().contains("DE_seat")) return;

        // Only show HP if this player has the toggle on
        if (!manager.isHpDisplayEnabled(attacker.getUniqueId())) return;

        // Wait 1 tick so damage is applied before we read health
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!mob.isValid() || mob.isDead()) return;

            AttributeInstance maxHpAttr = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : mob.getHealth();
            double curHp = mob.getHealth();

            // Format as whole numbers — keeps the tag short and readable
            String tag = String.format("§c❤ §f%d §7/ §f%d",
                    (int) Math.ceil(curHp), (int) Math.round(maxHp));

            mob.setCustomName(tag);
            mob.setCustomNameVisible(true);
        });
    }

    // -------------------------------------------------------------------------
    // Death cleanup  —  fixes the floating / ghost health-bar bug
    // -------------------------------------------------------------------------

    /**
     * Clears custom name + visibility on every non-player mob death.
     *
     * This serves two purposes:
     *  a) Prevents the Paper 1.21 ghost-health-bar bug for scaled mobs
     *     (GENERIC_MAX_HEALTH metadata desync on death).
     *  b) Removes the HP tag set by onEntityDamage so it doesn't linger
     *     after the mob is dead.
     *
     * We guard on ALL mobs (not just PDC-tagged ones) because unscaled mobs
     * can also receive an HP tag via the /hpbar feature.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) return;

        // Wipe name/display state — dismisses ghost bars AND HP tags
        entity.setCustomName(null);
        entity.setCustomNameVisible(false);

        // Clean up PDC tag if this was a scaled mob
        if (entity.getPersistentDataContainer().has(scaledKey, PersistentDataType.BYTE)) {
            entity.getPersistentDataContainer().remove(scaledKey);
        }
    }

    // -------------------------------------------------------------------------
    // Aggro management
    // -------------------------------------------------------------------------

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        DifficultyLevel targetDiff    = manager.getDifficulty(target.getUniqueId());
        DifficultyLevel highestNearby = getHighestNearbyDifficulty(mob, AGGRO_CHECK_RADIUS);

        // ── Peaceful protection ──────────────────────────────────────────────
        // A peaceful player won't be targeted UNLESS there is a Nightmare player
        // nearby (Nightmare players absorb all aggro in mixed groups).
        if (targetDiff == DifficultyLevel.PEACEFUL
                && highestNearby != DifficultyLevel.NIGHTMARE) {
            event.setCancelled(true);
            return;
        }

        // ── Nightmare aggro preference ────────────────────────────────────────
        // If the current target is NOT already a Nightmare player, there is a
        // 35% chance the mob will re-lock onto the nearest Nightmare player.
        if (targetDiff != DifficultyLevel.NIGHTMARE
                && random.nextInt(100) < NIGHTMARE_AGGRO_CHANCE) {
            Player nightmareTarget = getNearestNightmarePlayer(mob, AGGRO_CHECK_RADIUS);
            if (nightmareTarget != null && !nightmareTarget.equals(target)) {
                event.setTarget(nightmareTarget);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link DifficultyLevel} of the player NEAREST to the mob
     * within {@code radius} blocks.  Falls back to {@code EASY} if no player
     * is found.
     *
     * <p>Used for mob <em>spawn</em> scaling — mobs are tuned to the player
     * most likely to encounter them, not the strongest player in the area.
     */
    private DifficultyLevel getNearestPlayerDifficulty(LivingEntity mob, double radius) {
        Player  nearest    = null;
        double  closestSq  = Double.MAX_VALUE;

        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            double distSq = p.getLocation().distanceSquared(mob.getLocation());
            if (distSq < closestSq) {
                closestSq = distSq;
                nearest   = p;
            }
        }

        if (nearest == null) return DifficultyLevel.EASY;
        return manager.getDifficulty(nearest.getUniqueId());
    }

    /**
     * Returns the highest DifficultyLevel among all players within
     * {@code radius} blocks of the given mob. Defaults to EASY.
     *
     * <p>Still used for <em>aggro</em> management (onEntityTarget) where
     * checking the highest nearby difficulty is the correct behaviour.
     */
    DifficultyLevel getHighestNearbyDifficulty(LivingEntity mob, double radius) {
        DifficultyLevel highest = DifficultyLevel.EASY;
        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            DifficultyLevel d = manager.getDifficulty(p.getUniqueId());
            if (d.getTier() > highest.getTier()) {
                highest = d;
            }
        }
        return highest;
    }

    /**
     * Returns the closest online Nightmare player within {@code radius} blocks
     * of the mob, or {@code null} if none exists.
     */
    private Player getNearestNightmarePlayer(LivingEntity mob, double radius) {
        Player nearest = null;
        double closestSq = Double.MAX_VALUE;
        for (Entity e : mob.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            if (manager.getDifficulty(p.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
            double distSq = p.getLocation().distanceSquared(mob.getLocation());
            if (distSq < closestSq) {
                closestSq = distSq;
                nearest   = p;
            }
        }
        return nearest;
    }
}
