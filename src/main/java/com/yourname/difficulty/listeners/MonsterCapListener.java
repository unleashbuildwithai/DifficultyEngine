package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MonsterCapListener â€” Per-player difficulty-aware monster population cap.
 *
 * Every online player is effectively "registered" with a live monster budget
 * based on their current {@link DifficultyLevel}:
 *
 *   PEACEFUL  â†’  10 concurrent hostile mobs allowed nearby
 *   EASY      â†’  20
 *   MEDIUM    â†’  30
 *   HARD      â†’  40
 *   NIGHTMARE â†’  80   (double HARD's budget)
 *
 * (See {@link DifficultyLevel#getMonsterCap()}.)
 *
 * â”€â”€ Party stacking â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * When the nearest player to a spawn is in a party, every online party member
 * within {@link #GROUP_RADIUS} blocks of them contributes their OWN monster
 * cap to a shared pool â€” so a full party of 4 Hard players can support 160
 * concurrent mobs between them instead of just 40. This lets monster swarms
 * scale naturally with group size instead of choking on a single player's
 * budget.
 *
 * â”€â”€ Storm-night Nightmare bonus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * If it's currently night AND the world is thundering (a genuine "storm
 * night" â€” as opposed to plain daytime/non-thundering rain), every NIGHTMARE
 * player's contribution to the shared cap is multiplied by
 * {@link #STORM_NIGHT_NIGHTMARE_MULT} (4Ã—). This is what gives Nightmare
 * players (solo or partied) roughly 4Ã— the normal monster spawns during a
 * thunderstorm night, while regular daytime/non-thunder rain gets no bonus
 * at all â€” the two weather states are treated completely differently.
 *
 * â”€â”€ Party storm strength/speed bonus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * On top of the population math, if 2+ NIGHTMARE party members are together
 * during a storm night, newly spawned mobs near them get a random Ã—5â€“Ã—10
 * bonus to attack damage and movement speed (stacking on top of the normal
 * Nightmare stat multipliers applied by {@link DifficultyEngine}). This is
 * intentionally randomised per-mob so a swarm feels chaotic rather than
 * uniformly scaled.
 *
 * â”€â”€ Spawn-reason scope â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * Only {@code SpawnReason.NATURAL} and {@code SpawnReason.REINFORCEMENTS} are
 * capped. Spawner-based farms, custom monster commands, quest NPCs, and boss
 * spawns are explicitly NOT touched by this listener so player-built
 * infrastructure and scripted content keep working exactly as before.
 */
public class MonsterCapListener implements Listener {

    /** Radius used both to gather nearby party members and to count existing mobs. */
    private static final double GROUP_RADIUS = 64.0;

    /** Night start/end in world ticks â€” matches NightSpawnBoostListener. */
    private static final long NIGHT_START = 12_500L;
    private static final long NIGHT_END   = 23_000L;

    /** Nightmare players contribute 4Ã— their normal cap during a storm night. */
    private static final double STORM_NIGHT_NIGHTMARE_MULT = 4.0;

    /** Min/max random strength+speed multiplier applied to party storm mobs. */
    private static final double PARTY_STORM_MIN_MULT = 5.0;
    private static final double PARTY_STORM_MAX_MULT = 10.0;

    /** Minimum nearby NIGHTMARE party members (including the anchor) required for the storm bonus. */
    private static final int PARTY_STORM_MIN_SIZE = 2;

    private final JavaPlugin              plugin;
    private final PlayerDifficultyManager difficultyManager;
    private final PartyManager            partyManager;

    public MonsterCapListener(JavaPlugin plugin, PlayerDifficultyManager difficultyManager, PartyManager partyManager) {
        this.plugin            = plugin;
        this.difficultyManager = difficultyManager;
        this.partyManager      = partyManager;
    }

    // â”€â”€ Population cap enforcement â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Runs at LOW priority so it gates spawns before DifficultyEngine (NORMAL)
    // and other stat-scaling listeners run.

    /** Radius (blocks) within which a No-Spawn Zone Block prevents all monster spawns. */
    private static final double NO_SPAWN_ZONE_RADIUS = 500.0;
    /** No-Spawn Zone Blocks only function in this world (case-insensitive). */
    private static final java.util.Set<String> NO_SPAWN_ZONE_WORLDS = java.util.Set.of("starter", "starter_mv");

    /** Persisted locations of all placed No-Spawn Zone blocks, keyed by world name. */
    private final java.util.Map<String, java.util.List<Location>> noSpawnZones = new java.util.HashMap<>();

    /** Registers a No-Spawn Zone block location (called on placement). */
    public void registerNoSpawnZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        if (!NO_SPAWN_ZONE_WORLDS.contains(loc.getWorld().getName().toLowerCase())) return;
        noSpawnZones.computeIfAbsent(loc.getWorld().getName(), k -> new java.util.ArrayList<>())
                .add(loc.clone());
    }

    /** Unregisters a No-Spawn Zone block location (called on removal). */
    public void unregisterNoSpawnZone(Location loc) {
        if (loc == null || loc.getWorld() == null) return;
        java.util.List<Location> list = noSpawnZones.get(loc.getWorld().getName());
        if (list == null) return;
        list.removeIf(l -> l.getBlockX() == loc.getBlockX() && l.getBlockY() == loc.getBlockY() && l.getBlockZ() == loc.getBlockZ());
    }

    /** True if the given location is within range of any No-Spawn Zone block in the same (allowed) world. */
    private boolean isInNoSpawnZone(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        if (!NO_SPAWN_ZONE_WORLDS.contains(world.getName().toLowerCase())) return false;
        java.util.List<Location> list = noSpawnZones.get(world.getName());
        if (list == null || list.isEmpty()) return false;
        for (Location zoneLoc : list) {
            if (zoneLoc.distanceSquared(loc) <= NO_SPAWN_ZONE_RADIUS * NO_SPAWN_ZONE_RADIUS) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onNoSpawnZoneCheck(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        if (isInNoSpawnZone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        if (!isCappedSpawnReason(event.getSpawnReason())) return;

        Location loc   = event.getLocation();
        World    world = loc.getWorld();
        if (world == null) return;


        Player nearest = findNearestPlayer(loc, GROUP_RADIUS);
        if (nearest == null) return; // no player nearby â€” let vanilla/server caps handle it

        boolean stormNight = isStormNight(world);

        Set<UUID> group = collectGroup(nearest);
        double effectiveCap = 0.0;
        for (UUID uuid : group) {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            DifficultyLevel lvl = difficultyManager.getDifficulty(uuid);
            double contribution = lvl.getMonsterCap();
            if (lvl == DifficultyLevel.NIGHTMARE && stormNight) {
                contribution *= STORM_NIGHT_NIGHTMARE_MULT;
            }
            effectiveCap += contribution;
        }
        if (effectiveCap <= 0) {
            effectiveCap = DifficultyLevel.EASY.getMonsterCap();
        }

        int existing = countNearbyMonsters(loc, GROUP_RADIUS);
        if (existing >= effectiveCap) {
            event.setCancelled(true);
        }
    }

    // â”€â”€ Party storm strength/speed bonus â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // Runs at HIGH priority so it stacks on top of DifficultyEngine's (NORMAL)
    // and NightSpawnBoostListener's (NORMAL) scaling, which run first.

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawnPartyBonus(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;

        Location loc   = event.getLocation();
        World    world = loc.getWorld();
        if (world == null || !isStormNight(world)) return;

        Player nearest = findNearestPlayer(loc, GROUP_RADIUS);
        if (nearest == null) return;
        if (difficultyManager.getDifficulty(nearest.getUniqueId()) != DifficultyLevel.NIGHTMARE) return;
        if (partyManager == null || !partyManager.isInParty(nearest.getUniqueId())) return;

        int nearbyNightmareParty = 0;
        for (UUID uuid : collectGroup(nearest)) {
            if (difficultyManager.getDifficulty(uuid) == DifficultyLevel.NIGHTMARE) {
                nearbyNightmareParty++;
            }
        }
        if (nearbyNightmareParty < PARTY_STORM_MIN_SIZE) return;

        double mult = PARTY_STORM_MIN_MULT
                + ThreadLocalRandom.current().nextDouble() * (PARTY_STORM_MAX_MULT - PARTY_STORM_MIN_MULT);

        var atk = mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (atk != null) {
            atk.setBaseValue(atk.getBaseValue() * mult);
        }
        var spd = mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(spd.getBaseValue() * mult);
        }

        String existingName = mob.getCustomName();
        String tag = "Â§4âš¡ Â§c[Storm x" + String.format("%.1f", mult) + "]";
        mob.setCustomName(existingName == null || existingName.isEmpty() ? tag : existingName + " " + tag);
        mob.setCustomNameVisible(true);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** True only during a genuine thunderstorm at night â€” plain rain/day gets no bonus. */
    private boolean isStormNight(World world) {
        long time = world.getTime();
        boolean night = time >= NIGHT_START && time <= NIGHT_END;
        return night && world.isThundering();
    }

    private boolean isCappedSpawnReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason == CreatureSpawnEvent.SpawnReason.NATURAL
            || reason == CreatureSpawnEvent.SpawnReason.REINFORCEMENTS;
    }

    private Player findNearestPlayer(Location loc, double radius) {
        Player nearest = null;
        double closestSq = Double.MAX_VALUE;
        World world = loc.getWorld();
        if (world == null) return null;
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (!(e instanceof Player p)) continue;
            double distSq = p.getLocation().distanceSquared(loc);
            if (distSq < closestSq) {
                closestSq = distSq;
                nearest   = p;
            }
        }
        return nearest;
    }

    /**
     * Returns the set of UUIDs that should pool their monster caps together:
     * if {@code anchor} is in a party, every online party member within
     * {@link #GROUP_RADIUS} blocks of them; otherwise just the anchor alone.
     */
    private Set<UUID> collectGroup(Player anchor) {
        Set<UUID> group = new HashSet<>();
        group.add(anchor.getUniqueId());

        if (partyManager == null || !partyManager.isInParty(anchor.getUniqueId())) {
            return group;
        }

        for (UUID memberUid : partyManager.getPartyMembers(anchor.getUniqueId())) {
            if (memberUid.equals(anchor.getUniqueId())) continue;
            Player member = plugin.getServer().getPlayer(memberUid);
            if (member == null || !member.isOnline()) continue;
            if (!member.getWorld().equals(anchor.getWorld())) continue;
            if (member.getLocation().distanceSquared(anchor.getLocation()) <= GROUP_RADIUS * GROUP_RADIUS) {
                group.add(memberUid);
            }
        }
        return group;
    }

    private int countNearbyMonsters(Location loc, double radius) {
        int count = 0;
        World world = loc.getWorld();
        if (world == null) return 0;
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof Monster && e instanceof LivingEntity le && !le.isDead()) {
                count++;
            }
        }
        return count;
    }
}
