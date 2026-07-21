package com.yourname.difficulty.boss;

import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;

/**
 * BossEffectTask — Runs every tick and processes all active boss effects.
 *
 * ── Leached ────────────────────────────────────────────────────────────────
 *  Any player within 3 blocks of a tracked boss entity is drained each tick:
 *    drain = (player.getHealth() * 0.70) / 100
 *  If the player's total armor points (defence) >= 15, drain is halved
 *  but Nausea is still applied.
 *
 * ── Nauseated ──────────────────────────────────────────────────────────────
 *  Players with the NAUSEATED effect in the registry receive Nausea for 5s.
 *  This is re-applied every 4 seconds (80 ticks) so it stays active until
 *  the effect expires.
 *
 * ── Shriek Particles ───────────────────────────────────────────────────────
 *  If a boss has a registered Shriek stand, PORTAL particles are emitted
 *  around it every 4 ticks as a visual warning to players.
 */
public class BossEffectTask extends BukkitRunnable {

    /** Horizontal radius within which a boss drains nearby players (blocks). */
    private static final double LEACH_RADIUS = 3.0;

    /** Minimum drain amount (avoids draining < 0.01 HP per tick). */
    private static final double MIN_DRAIN = 0.01;

    /**
     * Defense threshold (total armor points).  Players at or above this
     * value take halved Leach damage but still receive Nausea.
     */
    private static final int HIGH_DEFENSE_THRESHOLD = 15;

    /** Nausea re-application interval in ticks (every 4 seconds). */
    private static final int NAUSEA_REFRESH_TICKS = 80;

    private final JavaPlugin     plugin;
    private final EffectRegistry registry;

    /** Tracked boss entity UUIDs — registered by BossEffectListener. */
    private final Set<java.util.UUID> trackedBosses;

    private int tickCounter = 0;

    public BossEffectTask(JavaPlugin plugin, EffectRegistry registry,
                          Set<java.util.UUID> trackedBosses) {
        this.plugin        = plugin;
        this.registry      = registry;
        this.trackedBosses = trackedBosses;
    }

    @Override
    public void run() {
        tickCounter++;

        for (java.util.UUID bossUuid : trackedBosses) {
            Entity bossEntity = plugin.getServer().getEntity(bossUuid);
            if (bossEntity == null || !(bossEntity instanceof LivingEntity boss)
                    || boss.isDead()) continue;

            // ── Random target aggro (every 100 ticks) ─────────────────────
            if (tickCounter % 100 == 0 && bossEntity instanceof Mob mob) {
                List<Player> nearbyP = new ArrayList<>();
                for (Entity nearby : mob.getNearbyEntities(80, 80, 80)) {
                    if (nearby instanceof Player player && !player.isDead()) {
                        nearbyP.add(player);
                    }
                }
                if (!nearbyP.isEmpty()) {
                    Player randomPlayer = nearbyP.get(new java.util.Random().nextInt(nearbyP.size()));
                    mob.setTarget(randomPlayer);
                }
            }

            // ── Leached: drain players within 3 blocks ────────────────────
            for (Entity nearby : boss.getNearbyEntities(LEACH_RADIUS, LEACH_RADIUS, LEACH_RADIUS)) {
                if (!(nearby instanceof Player player)) continue;

                double currentHp = player.getHealth();
                double drain     = (currentHp * 0.70) / 100.0;
                drain = Math.max(drain, MIN_DRAIN);

                // Halve drain if player has high defense (but keep Nausea)
                int armorPoints = player.getInventory().getArmorContents() != null
                        ? totalArmorPoints(player) : 0;
                if (armorPoints >= HIGH_DEFENSE_THRESHOLD) {
                    drain /= 2.0;
                }

                // Apply the drain (don't kill — leave at 0.5 HP minimum)
                double newHp = Math.max(0.5, currentHp - drain);
                player.setHealth(newHp);

                // Apply LEACHED to registry (refreshes every tick while in range)
                registry.applyTicks(player.getUniqueId(), EffectType.LEACHED, 40); // 2s buffer

                // Particle feedback on player
                player.getWorld().spawnParticle(
                        Particle.SOUL, player.getLocation().add(0, 1, 0),
                        3, 0.3, 0.3, 0.3, 0.02);
            }

            // ── Shriek particles (every 4 ticks) ──────────────────────────
            if (tickCounter % 4 == 0 && registry.hasShriek(bossUuid)) {
                java.util.UUID standUuid = registry.getShriekStand(bossUuid);
                Entity standEntity = plugin.getServer().getEntity(standUuid);
                if (standEntity != null && !standEntity.isDead()) {
                    standEntity.getWorld().spawnParticle(
                            Particle.PORTAL, standEntity.getLocation().add(0, 1, 0),
                            30, 1.0, 1.0, 1.0, 0.5);
                    standEntity.getWorld().spawnParticle(
                            Particle.END_ROD, standEntity.getLocation().add(0, 1, 0),
                            10, 0.8, 0.8, 0.8, 0.1);
                } else {
                    // Stand was removed externally — clean up registry
                    registry.clearShriek(bossUuid);
                }
            }
        }

        // ── Nauseated: refresh nausea on players who have it ──────────────
        if (tickCounter % NAUSEA_REFRESH_TICKS == 0) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (registry.has(p.getUniqueId(), EffectType.NAUSEATED)) {
                    p.addPotionEffect(new PotionEffect(
                            PotionEffectType.NAUSEA,
                            100, // 5 seconds
                            0,
                            false, true, true));
                }
            }
        }

        // ── Periodic cleanup (every 400 ticks / 20 s) ────────────────────
        if (tickCounter % 400 == 0) {
            registry.tickCleanup();
            // Remove dead boss UUIDs
            trackedBosses.removeIf(uuid -> {
                Entity e = plugin.getServer().getEntity(uuid);
                return e == null || e.isDead();
            });
            tickCounter = 0;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Calculates total armor protection points (0–20 scale). */
    private int totalArmorPoints(Player player) {
        int points = 0;
        for (org.bukkit.inventory.ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null) continue;
            AttributeInstance armor = null;
            var meta = piece.getItemMeta();
            if (meta != null) {
                // Use Bukkit attribute API
                var attrMod = piece.getType().getDefaultAttributeModifiers(
                        org.bukkit.inventory.EquipmentSlot.CHEST);
                // Approximate by material type
                points += switch (piece.getType()) {
                    case LEATHER_HELMET, LEATHER_CHESTPLATE,
                         LEATHER_LEGGINGS, LEATHER_BOOTS -> 1;
                    case IRON_HELMET, IRON_CHESTPLATE,
                         IRON_LEGGINGS, IRON_BOOTS -> 2;
                    case DIAMOND_HELMET, DIAMOND_CHESTPLATE,
                         DIAMOND_LEGGINGS, DIAMOND_BOOTS -> 3;
                    case NETHERITE_HELMET, NETHERITE_CHESTPLATE,
                         NETHERITE_LEGGINGS, NETHERITE_BOOTS -> 4;
                    default -> 0;
                };
            }
        }
        return points;
    }
}
