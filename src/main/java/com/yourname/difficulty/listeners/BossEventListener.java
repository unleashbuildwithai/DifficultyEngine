package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillCapeManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * BossEventListener — Boss fight enhancements.
 *
 * ── DOUBLE BOSS (1% chance) ────────────────────────────────────────────────
 *  When a boss spawns (WITHER, ENDER_DRAGON, ELDER_GUARDIAN), there is a 1%
 *  chance that an identical second boss spawns at the same location.
 *  Nearby players receive a chat announcement and are registered as participants.
 *
 * ── BOSS FIGHT MOBS ────────────────────────────────────────────────────────
 *  Every 30 seconds, while any tracked boss is alive, 3–5 hostile mobs spawn
 *  within 8–24 blocks of each player who is within 80 blocks of the boss.
 *
 * ── BOSS CAPE (double-boss reward) ────────────────────────────────────────
 *  If a double-boss event concludes and ALL conditions are met:
 *    • Both bosses are defeated.
 *    • No participating player died after the double-boss announcement.
 *  → Every surviving participant receives the §5Boss Cape §7via inventory.
 *
 * Bosses tracked: WITHER, ENDER_DRAGON, ELDER_GUARDIAN
 */
public class BossEventListener implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final double DOUBLE_BOSS_CHANCE    = 0.01;   // 1 %
    private static final double BOSS_ALERT_RADIUS     = 80.0;
    private static final double BOSS_ALERT_RADIUS_SQ  = BOSS_ALERT_RADIUS * BOSS_ALERT_RADIUS;
    private static final double FIGHT_MOB_MIN_DIST    = 8.0;
    private static final double FIGHT_MOB_MAX_DIST    = 24.0;
    private static final int    FIGHT_MOBS_PER_CYCLE  = 5;
    private static final long   FIGHT_MOB_INTERVAL    = 600L;   // 30 s

    private static final Set<EntityType> BOSS_TYPES = Set.of(
        EntityType.WITHER,
        EntityType.ENDER_DRAGON,
        EntityType.ELDER_GUARDIAN
    );

    private static final EntityType[] FIGHT_MOB_POOL = {
        EntityType.ZOMBIE,
        EntityType.ZOMBIE,
        EntityType.SKELETON,
        EntityType.SPIDER,
        EntityType.CREEPER,
        EntityType.ZOMBIE_VILLAGER,
        EntityType.HUSK,
        EntityType.STRAY
    };

    private final JavaPlugin       plugin;
    private final SkillCapeManager capeManager;
    private final Random           random = new Random();

    /**
     * Active double-boss events.
     * Keyed by BOTH boss UUIDs so either death can look up the event.
     */
    private final Map<UUID, DoubleBossEvent> activeEvents = new HashMap<>();

    public BossEventListener(JavaPlugin plugin, SkillCapeManager capeManager) {
        this.plugin      = plugin;
        this.capeManager = capeManager;
        startBossFightMobTask();
    }

    // ── Double boss spawn (1 %) ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossSpawn(CreatureSpawnEvent event) {
        if (!BOSS_TYPES.contains(event.getEntityType())) return;
        if (random.nextDouble() >= DOUBLE_BOSS_CHANCE) return;

        LivingEntity firstBoss = (LivingEntity) event.getEntity();
        EntityType   type      = event.getEntityType();
        Location     loc       = firstBoss.getLocation();

        // Schedule second boss 1 tick later so the first is fully initialised
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Entity second = loc.getWorld().spawnEntity(
                loc.clone().add(4 + random.nextInt(4), 0, 4 + random.nextInt(4)), type);

            if (!(second instanceof LivingEntity)) return;

            // Collect participants within 80 blocks
            Set<UUID> participants = new HashSet<>();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= BOSS_ALERT_RADIUS_SQ) {
                    participants.add(p.getUniqueId());
                }
            }

            // Register the event
            DoubleBossEvent evt = new DoubleBossEvent(
                firstBoss.getUniqueId(), second.getUniqueId(), participants);
            activeEvents.put(firstBoss.getUniqueId(), evt);
            activeEvents.put(second.getUniqueId(), evt);

            // Announce
            String bossName = bossDisplayName(type);
            for (UUID uid : participants) {
                Player p = plugin.getServer().getPlayer(uid);
                if (p == null) continue;
                p.sendMessage("");
                p.sendMessage("§4☠ §c§l⚡ DOUBLE BOSS EVENT! ⚡ §4☠");
                p.sendMessage("§7Two §c§l" + bossName + "§r§7 have appeared!");
                p.sendMessage("§6Defeat §c§lBOTH §r§6without dying to earn the §5★ Boss Cape§6!");
                p.sendMessage("");
            }
        }, 1L);
    }

    // ── Track player deaths inside active events ──────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uid = event.getEntity().getUniqueId();
        // A player dying disqualifies them from the boss cape in every active event
        for (DoubleBossEvent evt : new ArrayList<>(activeEvents.values())) {
            if (evt.participants.contains(uid)) {
                evt.deadParticipants.add(uid);
            }
        }
    }

    // ── Boss death — check for cape award ─────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!BOSS_TYPES.contains(dead.getType())) return;

        DoubleBossEvent evt = activeEvents.get(dead.getUniqueId());
        if (evt == null) return; // not a double-boss event

        evt.defeatedBosses.add(dead.getUniqueId());

        // Both bosses down — resolve the event
        if (evt.defeatedBosses.size() >= 2) {
            resolveBossEvent(evt);
            activeEvents.remove(evt.firstBossUuid);
            activeEvents.remove(evt.secondBossUuid);
        }
    }

    private void resolveBossEvent(DoubleBossEvent evt) {
        boolean anyWinner = false;
        for (UUID uid : evt.participants) {
            if (evt.deadParticipants.contains(uid)) continue; // died → no reward

            Player p = plugin.getServer().getPlayer(uid);
            if (p == null || !p.isOnline()) continue;

            // Award Boss Cape
            ItemStack bossCape = capeManager.buildBossCape();
            HashMap<Integer, ItemStack> leftover = p.getInventory().addItem(bossCape);
            // Drop at feet if inventory full
            for (ItemStack leftoverItem : leftover.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), leftoverItem);
            }

            p.sendMessage("");
            p.sendMessage("§5✦ §6§l✦ BOSS CAPE AWARDED! ✦ §5✦");
            p.sendMessage("§7You defeated §c§lBOTH §r§7bosses without dying!");
            p.sendMessage("§5The §d★ Boss Cape §5has been added to your inventory!");
            p.sendMessage("§7Equip it via §e/cape §7to wear it on your back.");
            p.sendMessage("");
            anyWinner = true;
        }

        if (!anyWinner) {
            // All participants died — broadcast a consolation message
            for (UUID uid : evt.participants) {
                Player p = plugin.getServer().getPlayer(uid);
                if (p != null) {
                    p.sendMessage("§8[§6DifficultyEngine§8] §c✗ Double Boss complete — but at least one player died.");
                    p.sendMessage("§7Next time, defeat §cboth §7without dying to earn the §5Boss Cape§7.");
                }
            }
        }
    }

    // ── Boss fight mob spawns (every 30 s) ───────────────────────────────────

    private void startBossFightMobTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (World world : plugin.getServer().getWorlds()) {
                    for (Entity entity : new ArrayList<>(world.getEntities())) {
                        if (!BOSS_TYPES.contains(entity.getType())) continue;
                        if (!(entity instanceof LivingEntity boss) || boss.isDead()) continue;

                        Location bossLoc = boss.getLocation();

                        for (Player player : world.getPlayers()) {
                            if (player.getLocation().distanceSquared(bossLoc)
                                    > BOSS_ALERT_RADIUS_SQ) continue;

                            // Spawn 3–5 mobs near this player
                            int count = 3 + random.nextInt(FIGHT_MOBS_PER_CYCLE - 2);
                            for (int i = 0; i < count; i++) {
                                Location spawnLoc = findSpawnNear(player.getLocation());
                                if (spawnLoc == null) continue;
                                EntityType mobType = FIGHT_MOB_POOL[random.nextInt(FIGHT_MOB_POOL.length)];
                                world.spawnEntity(spawnLoc, mobType);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, FIGHT_MOB_INTERVAL, FIGHT_MOB_INTERVAL);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Location findSpawnNear(Location origin) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int sign = random.nextBoolean() ? 1 : -1;
            int dx   = sign * ((int) FIGHT_MOB_MIN_DIST
                             + random.nextInt((int) (FIGHT_MOB_MAX_DIST - FIGHT_MOB_MIN_DIST) + 1));
            sign     = random.nextBoolean() ? 1 : -1;
            int dz   = sign * ((int) FIGHT_MOB_MIN_DIST
                             + random.nextInt((int) (FIGHT_MOB_MAX_DIST - FIGHT_MOB_MIN_DIST) + 1));

            Location candidate = origin.clone().add(dx, 0, dz);
            int surfaceY = candidate.getWorld().getHighestBlockYAt(candidate);
            candidate.setY(surfaceY + 1);

            if (!candidate.getBlock().getType().isAir()) continue;
            if (!candidate.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) continue;
            return candidate;
        }
        return null;
    }

    private static String bossDisplayName(EntityType type) {
        return switch (type) {
            case WITHER        -> "Wither";
            case ENDER_DRAGON  -> "Ender Dragon";
            case ELDER_GUARDIAN -> "Elder Guardian";
            default            -> type.name();
        };
    }

    // ── Inner state class ─────────────────────────────────────────────────────

    private static final class DoubleBossEvent {
        final UUID      firstBossUuid;
        final UUID      secondBossUuid;
        final Set<UUID> participants    = new HashSet<>();
        final Set<UUID> deadParticipants = new HashSet<>();
        final Set<UUID> defeatedBosses  = new HashSet<>();

        DoubleBossEvent(UUID first, UUID second, Collection<UUID> participants) {
            this.firstBossUuid  = first;
            this.secondBossUuid = second;
            this.participants.addAll(participants);
        }
    }
}
