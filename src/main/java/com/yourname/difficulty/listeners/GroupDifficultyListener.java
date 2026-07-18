package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * GroupDifficultyListener — Group Nightmare mechanic.
 *
 * When 4 or more NIGHTMARE players are within 50 blocks of each other:
 *   • Mobs that spawn near them receive ×10 stats on top of base NIGHTMARE scaling.
 *   • Mobs that die near them give ×10 drops and ×10 XP ("super-buffed rewards").
 *   • Players receive an action-bar notification every 2 seconds.
 *
 * Group detection runs every 2 seconds (40 ticks). Each pass clears and rebuilds
 * the group-member set so it automatically disbands when players move apart.
 *
 * Priority = HIGH so this fires AFTER DifficultyEngine's NORMAL-priority spawn
 * handler — the 10× multiplier therefore stacks on top of existing Nightmare scaling.
 */
public class GroupDifficultyListener implements Listener {

    // ── Config constants ──────────────────────────────────────────────────────
    private static final double GROUP_RADIUS         = 50.0;
    private static final double GROUP_RADIUS_SQ      = GROUP_RADIUS * GROUP_RADIUS;
    private static final int    GROUP_MIN_SIZE       = 4;
    private static final double GROUP_HEALTH_MULT    = 10.0;
    private static final double GROUP_DAMAGE_MULT    = 10.0;
    private static final int    GROUP_LOOT_MULT      = 10;

    /** UUIDs of players currently inside an active nightmare group. */
    private final Set<UUID> inGroupNightmare = Collections.synchronizedSet(new HashSet<>());

    private final PlayerDifficultyManager diffManager;
    private final JavaPlugin              plugin;

    public GroupDifficultyListener(PlayerDifficultyManager diffManager, JavaPlugin plugin) {
        this.diffManager = diffManager;
        this.plugin      = plugin;
        startGroupScan();
    }

    // ── Periodic group detection (every 2 s) ──────────────────────────────────

    private void startGroupScan() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Set<UUID> newGroup = new HashSet<>();

                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (diffManager.getDifficulty(player.getUniqueId()) != DifficultyLevel.NIGHTMARE) continue;
                    if (newGroup.contains(player.getUniqueId())) continue;

                    // Count nightmare neighbours within 50 blocks
                    List<Player> cluster = new ArrayList<>();
                    cluster.add(player);
                    for (Entity nearby : player.getNearbyEntities(GROUP_RADIUS, GROUP_RADIUS, GROUP_RADIUS)) {
                        if (!(nearby instanceof Player p)) continue;
                        if (diffManager.getDifficulty(p.getUniqueId()) == DifficultyLevel.NIGHTMARE) {
                            cluster.add(p);
                        }
                    }

                    if (cluster.size() >= GROUP_MIN_SIZE) {
                        for (Player member : cluster) newGroup.add(member.getUniqueId());
                        // Action-bar pulse for every member
                        for (Player member : cluster) {
                            member.sendActionBar(
                                "§4☠ §c§lGROUP NIGHTMARE §4[" + cluster.size() + " players]§c"
                                + " §7— §cx10 difficulty §8| §ax10 rewards"
                            );
                        }
                    }
                }

                inGroupNightmare.clear();
                inGroupNightmare.addAll(newGroup);
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ── Spawn scaling (×10 health & damage for group nightmare mobs) ──────────
    // Priority HIGH ensures this fires after DifficultyEngine (NORMAL priority)
    // so the ×10 stacks on the existing nightmare multiplier.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;

        // Only apply if a group-nightmare player is nearby
        if (!hasGroupNightmarePlayerNearby(mob)) return;

        AttributeInstance maxHp = mob.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHp != null) {
            double newMax = maxHp.getBaseValue() * GROUP_HEALTH_MULT;
            maxHp.setBaseValue(newMax);
            mob.setHealth(newMax);
        }
        AttributeInstance atk = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * GROUP_DAMAGE_MULT);
        }
    }

    // ── Buffed loot (×10 drops & XP for group nightmare kills) ───────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (!inGroupNightmare.contains(killer.getUniqueId())) return;

        // Duplicate every existing drop (GROUP_LOOT_MULT - 1) extra times
        List<ItemStack> original = new ArrayList<>(event.getDrops());
        for (int copy = 1; copy < GROUP_LOOT_MULT; copy++) {
            for (ItemStack drop : original) {
                event.getDrops().add(drop.clone());
            }
        }
        event.setDroppedExp(event.getDroppedExp() * GROUP_LOOT_MULT);

        killer.sendActionBar("§6★ §eGroup Nightmare Bonus! §8(§ax" + GROUP_LOOT_MULT + " loot & XP§8)");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasGroupNightmarePlayerNearby(Monster mob) {
        for (Entity nearby : mob.getNearbyEntities(GROUP_RADIUS, GROUP_RADIUS, GROUP_RADIUS)) {
            if (nearby instanceof Player p && inGroupNightmare.contains(p.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** Returns true if the player is currently inside an active nightmare group. */
    public boolean isInGroupNightmare(UUID uuid) {
        return inGroupNightmare.contains(uuid);
    }
}
