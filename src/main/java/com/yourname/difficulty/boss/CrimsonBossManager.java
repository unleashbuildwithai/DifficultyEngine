package com.yourname.difficulty.boss;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.boss.crimson.CrimsonBossAttacks;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;


/**
 * CrimsonBossManager â€” manages Â§cðŸ”¥ The Infernal BlazefiendÂ§r.
 *
 * â”€â”€ Triggering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  â€¢ Striking any ANCIENT_DEBRIS block while boss is dead â†’ boss spawns near it.
 *  â€¢ After death a 15-minute (18 000 tick) timer re-spawns the boss.
 *  â€¢ Admin: /spawnboss crimson
 *
 * â”€â”€ Starting location â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Crimson Pit:  -107.964,  -26,  -14.444
 *  But the boss WANDERS freely through the caves after spawning.
 *
 * â”€â”€ Wandering & Block-breaking â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Every 3 s the boss picks a random target 10-25 blocks away in a random
 *  horizontal+vertical direction and flies toward it.  Any breakable blocks
 *  (stone, deepslate, netherrack, gravel, etc.) directly in its path are
 *  broken, letting it carve through solid rock.
 *  Indestructible blocks (bedrock, ancient debris, obsidian, reinforced
 *  deepslate) are not touched.
 *
 * â”€â”€ Attacks â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Same 6 attacks as before (Hellfire, Molten Barrage, Void Pearls,
 *  Fire Blobs, Flame Slimes, Scorch).
 */
public class CrimsonBossManager implements Listener {

    // â”€â”€ Default spawn coords (used by /spawnboss crimson) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    public static final double SPAWN_X = -107.964;
    public static final double SPAWN_Y = -26.0;
    public static final double SPAWN_Z = -14.444;

    // â”€â”€ Tunables â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** Standard world-boss HP â€” tuned for Lv99 parties. */
    private static final double BOSS_MAX_HP       = 2_500.0;
    /** Legendary variant HP (1% chance on any spawn). */
    private static final double LEGENDARY_HP      = 25_000.0;
    /** 1% chance any spawn becomes the Legendary variant. */
    private static final double LEGENDARY_CHANCE  = 0.01;
    private static final double ENGAGE_RADIUS     = 80.0;
    private static final double SCORCH_RADIUS     =  6.0;
    private static final double PEARL_REGEN       = 30.0;
    /** How long before auto-respawn after death (15 minutes). */
    private static final long   RESPAWN_TICKS     = 18_000L;
    /** Wandering: how often (ticks) the boss picks a new target. */
    private static final long   WANDER_INTERVAL   = 60L; // 3 s
    /** Wandering: max horizontal wander distance per pick. */
    private static final double WANDER_DIST       = 25.0;

    /** 15% chance to drop the GunZ Sword on death. */
    private static final double GUNZ_DROP_CHANCE  = 0.15;

    /** Materials the boss will NOT break as it passes through. */
    private static final Set<Material> INDESTRUCTIBLE = Set.of(
            Material.BEDROCK,
            Material.ANCIENT_DEBRIS,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.REINFORCED_DEEPSLATE,
            Material.BARRIER,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.GILDED_BLACKSTONE,
            Material.BLACK_CONCRETE
    );

    // â”€â”€ Crimson Cube mini-fiend population tunables â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** Radius (blocks) around each Crimson Cube that counts toward its fiend population.
     *  Increased to 300 per request â€” mini-fiends roam a much wider area. */
    private static final double CUBE_FIEND_RADIUS = 300.0;
    /** Minimum number of mini Blazefiends kept alive within CUBE_FIEND_RADIUS of each cube.
     *  Lowered from 30 -> 12 so the caves aren't overrun ("keeps spawning and never stops"). */
    private static final int    CUBE_MIN_FIENDS   = 12;
    /** Max fiends spawned per balance-check pass (rate limit to avoid lag spikes). */
    private static final int    CUBE_SPAWN_BATCH  = 3;
    /**
     * HARD SERVER-WIDE CAP on total live mini Blazefiends (across ALL cubes
     * combined), preventing the population-upkeep system from spiralling out
     * of control and breaking the server (the reported "spawns going out of
     * control" bug). Overridable via config.yml key "crimson.maxAliveFiends".
     * Enforced BEFORE spawning any new mini-fiend batch, in both the cube
     * population upkeep task and the proximity guard spawner.
     */
    private final int globalMaxFiends;
    private static final int GLOBAL_MAX_FIENDS_DEFAULT = 200;


    private final JavaPlugin         plugin;
    private final ItemFactory        itemFactory;
    private final BossEffectListener bossEffectListener;
    private final Random             random = new Random();


    /** Base visual scale divisor applied to the old vanilla scale-factor to size the custom crimson_boss display. */
    private static final float CRIMSON_DISPLAY_SCALE_DIVISOR = 3.0f;
    /** How fast the crimson_boss display spins/flickers (radians per tick). */
    private static final double CRIMSON_SPIN_SPEED = 0.05;

    /** Boss carrier UUID â†’ paired visual ItemDisplay UUID (position-synced every tick). */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    /** Boss carrier UUID â†’ current animation angle (radians), advanced each sync tick. */
    private final Map<UUID, Double> displaySpinAngles = new HashMap<>();
    private final NamespacedKey displayTagKey;

    /** All known Crimson Cube (Blazefiend spawner) locations, tracked for population upkeep. */
    private final Set<Location> crimsonCubes = Collections.synchronizedSet(new HashSet<>());

    /** UUIDs of all active boss entities (supports split/multiple). */
    private final Set<UUID> activeBossUuids = new HashSet<>();

    /** UUID of the active boss, null when not alive. */
    private UUID           bossUuid      = null;
    /** Tracks ender pearls thrown by the boss. */
    private final Set<UUID> bossEnderPearls = new HashSet<>();
    /** Repeating AI / wander task. */
    private BukkitRunnable bossTask      = null;
    /** Handle for the 15-min respawn task so we can cancel it on demand. */
    private org.bukkit.scheduler.BukkitTask respawnTask = null;
    /** Delegate for boss attacks/FX (injected after construction to avoid circular dependency). */
    private CrimsonBossAttacks bossAttacks = null;

    /** Injects the attack delegate. Must be called once after both objects are constructed. */
    public void setBossAttacks(CrimsonBossAttacks bossAttacks) {
        this.bossAttacks = bossAttacks;
    }

    /** UUID of the currently active single boss (used by attack helpers). */
    public UUID getBossUuid() {
        return bossUuid;
    }

    /** All active boss entity UUIDs (supports multiple/split bosses). */
    public Set<UUID> getActiveBossUuids() {
        return activeBossUuids;
    }

    /** Tracks ender pearls thrown by the boss (for custom hit detection). */
    public Set<UUID> getBossEnderPearls() {
        return bossEnderPearls;
    }

    public CrimsonBossManager(JavaPlugin plugin,
                               ItemFactory itemFactory,
                               BossEffectListener bossEffectListener) {

        this.plugin             = plugin;
        this.itemFactory        = itemFactory;
        this.bossEffectListener = bossEffectListener;
        this.displayTagKey      = new NamespacedKey(plugin, "de_crimson_display");

        // Read the global fiend cap from config.yml (crimson.maxAliveFiends),
        // falling back to GLOBAL_MAX_FIENDS_DEFAULT if missing/invalid.
        int configuredCap = plugin.getConfig().getInt("crimson.maxAliveFiends", GLOBAL_MAX_FIENDS_DEFAULT);
        this.globalMaxFiends = configuredCap > 0 ? configuredCap : GLOBAL_MAX_FIENDS_DEFAULT;



        // Position-sync task: streams each carrier's live location to its
        // paired crimson_boss ItemDisplay and applies a gentle flicker
        // spin/scale-pulse animation so the flame model feels alive.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (carrierToDisplay.isEmpty()) return;
            Iterator<Map.Entry<UUID, UUID>> it = carrierToDisplay.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> entry = it.next();
                Entity carrier = plugin.getServer().getEntity(entry.getKey());
                Entity display = plugin.getServer().getEntity(entry.getValue());

                if (!(carrier instanceof Blaze b) || b.isDead() || !b.isValid()) {
                    if (display != null && !display.isDead()) display.remove();
                    it.remove();
                    displaySpinAngles.remove(entry.getKey());
                    continue;
                }
                if (!(display instanceof ItemDisplay id) || display.isDead() || !display.isValid()) {
                    it.remove();
                    displaySpinAngles.remove(entry.getKey());
                    continue;
                }

                Location target = carrier.getLocation().clone().add(0, 0.2, 0);
                if (!display.getLocation().getWorld().equals(target.getWorld())
                        || display.getLocation().distanceSquared(target) > 0.0004) {
                    display.teleport(target);
                }

                double scaleAttrVal = 10.0;
                var scaleAttr = b.getAttribute(Attribute.SCALE);
                if (scaleAttr != null) scaleAttrVal = scaleAttr.getBaseValue();
                float baseScale = (float) (scaleAttrVal / CRIMSON_DISPLAY_SCALE_DIVISOR);

                double angle = displaySpinAngles.getOrDefault(entry.getKey(), 0.0) + CRIMSON_SPIN_SPEED;
                displaySpinAngles.put(entry.getKey(), angle);
                float flicker = baseScale * (float) (1.0 + 0.06 * Math.sin(angle * 3.0));
                BossDisplayUtil.setYawRotation(id, flicker, (float) angle);
            }
        }, 1L, 1L);

        // Automatically spawn Blazefiends near any Gilded Blackstone block in player vicinity

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                Location playerLoc = player.getLocation();
                World world = playerLoc.getWorld();
                if (world == null) continue;

                int px = playerLoc.getBlockX();
                int py = playerLoc.getBlockY();
                int pz = playerLoc.getBlockZ();

                int radius = 24;
                for (int x = px - radius; x <= px + radius; x += 4) {
                    for (int y = py - 12; y <= py + 12; y += 4) {
                        for (int z = pz - radius; z <= pz + radius; z += 4) {
                            Block b = world.getBlockAt(x, y, z);
                            if (b.getType() == Material.GILDED_BLACKSTONE) {
                                registerCrimsonCube(b.getLocation());
                                spawnGuardNear(b.getLocation());
                            }
                        }
                    }
                }
            }
        }, 100L, 300L); // check every 15 seconds

        // Periodically make Blazefiends wander around the map using pathfinder navigation
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (World world : plugin.getServer().getWorlds()) {
                for (Blaze blaze : world.getEntitiesByClass(Blaze.class)) {
                    String name = blaze.getCustomName();
                    if (name != null && name.contains("Blazefiend")) {
                        if (blaze.getTarget() == null) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double dist = 8 + random.nextInt(12);
                            double rx = blaze.getLocation().getX() + Math.cos(angle) * dist;
                            double rz = blaze.getLocation().getZ() + Math.sin(angle) * dist;
                            Location targetLoc = new Location(world, rx, blaze.getLocation().getY(), rz);
                            blaze.getPathfinder().moveTo(targetLoc, 1.0);
                        }
                    }
                }
            }
        }, 120L, 80L); // wander every 4 seconds

        // â”€â”€ Crimson Cube population upkeep â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Ensures at least CUBE_MIN_FIENDS mini Blazefiends are always alive
        // within CUBE_FIEND_RADIUS blocks of every known Crimson Cube. If a
        // fiend wanders outside that radius (or dies), it no longer counts
        // toward the cube's population, and a fresh one spawns near the cube.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::maintainCrimsonCubePopulations, 60L, 100L); // every 5s
    }

    /** Registers a Crimson Cube location so its mini-fiend population is maintained. */
    public void registerCrimsonCube(Location loc) {
        if (loc == null) return;
        crimsonCubes.add(loc.getBlock().getLocation());
    }

    /** Unregisters a Crimson Cube (e.g. if the spawner block is removed). */
    public void unregisterCrimsonCube(Location loc) {
        if (loc == null) return;
        crimsonCubes.remove(loc.getBlock().getLocation());
    }

    /**
     * For every known Crimson Cube, counts mini Blazefiends within
     * CUBE_FIEND_RADIUS and tops up to CUBE_MIN_FIENDS if short.
     * Fiends that wander outside the radius no longer count, causing a
     * fresh one to spawn near the cube â€” net effect: 1 new fiend spawns
     * near the cube every time one wanders outside its radius.
     *
     * A hard server-wide cap (globalMaxFiends) is enforced BEFORE spawning
     * any new batch â€” this is the fix for mini-fiends spiralling out of
     * control and breaking the server. Once the global cap is reached, no
     * further mini-fiends are spawned by this task until some die off.
     */
    private void maintainCrimsonCubePopulations() {
        // Only top-up mini-fiends while the MAIN Blazefiend boss is not alive.
        // Once the real boss awakens, mini-fiend population upkeep pauses so
        // the fight isn't drowned out by an ever-growing swarm.
        if (isBossAlive()) return;

        for (Location cube : new ArrayList<>(crimsonCubes)) {
            World world = cube.getWorld();
            if (world == null) continue;

            // â”€â”€ Global hard cap check â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            int globalCount = countLiveFiends(world);
            if (globalCount >= globalMaxFiends) continue; // server-wide limit reached, skip entirely

            int count = 0;
            for (Entity e : world.getNearbyEntities(cube, CUBE_FIEND_RADIUS, CUBE_FIEND_RADIUS, CUBE_FIEND_RADIUS)) {
                if (e instanceof Blaze blaze && "Â§cðŸ”¥ Blazefiend".equals(blaze.getCustomName()) && !blaze.isDead()) {
                    count++;
                }
            }

            int deficit = CUBE_MIN_FIENDS - count;
            if (deficit <= 0) continue;

            // Clamp the batch so we never exceed the global cap even mid-batch
            int toSpawn = Math.min(deficit, CUBE_SPAWN_BATCH);
            toSpawn = Math.min(toSpawn, globalMaxFiends - globalCount);
            for (int i = 0; i < toSpawn; i++) {
                spawnMiniFiendNearCube(cube);
            }
        }
    }

    /** Counts all live "Â§cðŸ”¥ Blazefiend" mini-fiends currently in the given world. */
    private int countLiveFiends(World world) {
        int count = 0;
        for (Entity e : world.getEntitiesByClass(Blaze.class)) {
            if (e instanceof Blaze blaze && "Â§cðŸ”¥ Blazefiend".equals(blaze.getCustomName()) && !blaze.isDead()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Spawns a single mini Blazefiend at a random safe point somewhere within
     * CUBE_FIEND_RADIUS of the given Crimson Cube. The distance is rolled
     * across the FULL radius (not just a small ring near the cube) so
     * fiends actually spread out across the whole 300-block roaming area
     * instead of clumping right on top of the cube/each other.
     */
    private void spawnMiniFiendNearCube(Location cube) {
        World world = cube.getWorld();
        if (world == null) return;

        for (int attempt = 0; attempt < 5; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            // Spread across the full CUBE_FIEND_RADIUS (with a small minimum
            // floor so fiends don't spawn literally on top of the cube block).
            double dist  = 8 + random.nextDouble() * (CUBE_FIEND_RADIUS - 8);
            double rx = cube.getX() + Math.cos(angle) * dist;
            double rz = cube.getZ() + Math.sin(angle) * dist;
            double ry = cube.getY() + (random.nextDouble() - 0.3) * 8;
            Location spawnLoc = new Location(world, rx, ry, rz);

            if (!spawnLoc.getBlock().getType().isAir()) continue;

            Blaze fiend = (Blaze) world.spawnEntity(spawnLoc, EntityType.BLAZE);
            fiend.setCustomName("Â§cðŸ”¥ Blazefiend");
            fiend.setCustomNameVisible(true);
            fiend.setRemoveWhenFarAway(true);
            return;
        }
    }

    private void spawnGuardNear(Location loc) {
        if (isBossAlive()) return; // do not spawn guards while the boss is active

        World world = loc.getWorld();
        if (world == null) return;

        // â”€â”€ Global hard cap check â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Prevents the proximity guard-spawner from pushing the server-wide
        // fiend count above the configured limit (in addition to its own
        // local 15-block/3-guard check below).
        if (countLiveFiends(world) >= globalMaxFiends) return;

        int guardsCount = 0;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 15, 10, 15)) {
            if (e instanceof Blaze blaze && "Â§cðŸ”¥ Blazefiend".equals(blaze.getCustomName())) {
                guardsCount++;
            }
        }
        if (guardsCount >= 3) return;

        double rx = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 6;
        double rz = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 6;
        Location gl = new Location(loc.getWorld(), rx, loc.getY() + 1.0, rz);

        if (!gl.getBlock().getType().isAir()) return;

        Blaze guard = (Blaze) loc.getWorld().spawnEntity(gl, EntityType.BLAZE);
        guard.setCustomName("Â§cðŸ”¥ Blazefiend");
        guard.setCustomNameVisible(true);
        guard.setRemoveWhenFarAway(true);
    }


    /**
     * Shared pack aggro: if one Blazefiend targets a player, all other
     * nearby Blazefiends within 45 blocks are alerted to attack that player too.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlazefiendTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Blaze blaze)) return;
        if (!(event.getTarget() instanceof Player player)) return;

        String name = blaze.getCustomName();
        if (name == null || (!name.contains("Blazefiend") && !name.contains("Infernal Blazefiend") && !name.contains("Blaze Inferno"))) return;

        // Force all other nearby Blazefiends to target the player
        for (Entity nearby : blaze.getWorld().getNearbyEntities(blaze.getLocation(), 45.0, 30.0, 45.0)) {
            if (nearby instanceof Blaze otherBlaze && !otherBlaze.getUniqueId().equals(blaze.getUniqueId())) {
                String otherName = otherBlaze.getCustomName();
                if (otherName != null && (otherName.contains("Blazefiend") || otherName.contains("Infernal") || otherName.contains("Blaze Inferno"))) {
                    if (otherBlaze.getTarget() == null) {
                        otherBlaze.setTarget(player);
                    }
                }
            }
        }
    }

    // â•â• Public API â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Spawns (or replaces) the boss.
     * Supports normal, inferno, vortex, amalgam types and splits.
     *
     * @param loc Spawn location. If null, uses the default Crimson Pit coords.
     */
    public Blaze spawnBoss(Location loc) {
        return spawnBoss(loc, false);
    }

    public Blaze spawnBoss(Location loc, boolean isSplitInferno) {
        if (loc == null) {
            World w = plugin.getServer().getWorld("ancient_realm");
            if (w == null) {
                w = plugin.getServer().getWorlds().get(0);
            }
            loc = new Location(w, SPAWN_X, SPAWN_Y, SPAWN_Z);
        }

        if (!isSplitInferno) {
            dismissExisting();
            // Rebuild the Crimson Pit room schematic on spawn to repair the arena
            rebuildArena(null, loc);
        }

        double roll = random.nextDouble();
        double hp = 5000.0;
        String name = "Â§cðŸ”¥ Â§lÂ§4The Infernal Blazefiend";
        boolean legendary = false;
        boolean amalgam = false;
        boolean vortex = false;
        boolean inferno = false;

        if (isSplitInferno) {
            hp = 10000.0;
            name = "Â§cðŸ”¥ Â§lÂ§4The Blaze Inferno Â§7(Amalgam Split)";
            inferno = true;
        } else {
            if (roll < 0.001) { // 0.1% chance: Dual Blaze Amalgam
                name = "Â§dâ˜  Â§5Â§lTHE DUAL BLAZE AMALGAM Â§dâ˜ ";
                hp = 20000.0;
                amalgam = true;
                legendary = true;
            } else if (roll < 0.01) { // 0.9% chance: Blaze Vortex
                name = "Â§dâ˜  Â§cÂ§lâš¡ THE BLAZE VORTEX âš¡ Â§dâ˜ ";
                hp = 20000.0;
                vortex = true;
                legendary = true;
            } else if (roll < 0.03) { // 2% chance: Blaze Inferno
                name = "Â§cðŸ”¥ Â§lÂ§4The Blaze Inferno";
                hp = 15000.0;
                inferno = true;
                legendary = true;
            } else {
                // Normal Blazefiend (97% chance)
                // Scale HP if multiple Nightmare players are nearby
                int nightmareCount = 0;
                for (Player p : nearbyPlayers(loc, 80.0)) {
                    if (p.hasMetadata("difficultyengine_hardcore") || p.hasMetadata("difficultyengine_nightmare") || p.getWorld().getName().equals("ancient_realm")) {
                        nightmareCount++;
                    }
                }
                if (nightmareCount >= 4) {
                    hp = 10000.0;
                    name = "Â§4â˜  Â§cÂ§lTHE APOCALYPTIC BLAZEFIEND Â§4â˜ ";
                } else if (nightmareCount > 1) {
                    hp = 7500.0;
                    name = "Â§cðŸ”¥ Â§lÂ§4The Nightmare Blazefiend";
                } else {
                    hp = 5000.0;
                }
            }
        }

        Blaze boss = (Blaze) loc.getWorld().spawnEntity(loc, EntityType.BLAZE);
        boss.setCustomName(name);
        boss.setCustomNameVisible(false); // name lives on the crimson_boss display instead
        boss.setRemoveWhenFarAway(false);
        boss.setFireTicks(Integer.MAX_VALUE);
        // Hide the vanilla Blaze silhouette â€” only the custom crimson_boss
        // ItemDisplay model should be visible. The Blaze remains as an
        // invisible physics/AI/hitbox carrier.
        boss.setInvisible(true);
        boss.setSilent(true);


        // Boost HP safely preventing Health value must be between 0 and 1024 Exception
        var hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hp);
            boss.setHealth(Math.min(hp, hpAttr.getValue()));
        } else {
            boss.setHealth(Math.min(hp, 1024.0));
        }

        // Determine scale factor based on boss variant size requirements:
        // Normal Boss: 10x size
        // Special Boss (Inferno, Vortex, Amalgam split): 35x size
        // Legendary/Rare Rare Boss (Dual Blaze Amalgam, Apocalyptic, etc.): 50x size (up to 50 blocks wide!)
        double scaleFactor = 10.0;
        if (amalgam || name.contains("APOCALYPTIC") || name.contains("NIGHTMARE") || name.contains("LEGENDARY")) {
            scaleFactor = 50.0;
        } else if (vortex || inferno || isSplitInferno) {
            scaleFactor = 35.0;
        }

        // Apply scale factor using Paperweight scale attribute
        var scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(scaleFactor);
        }

        // Metadata tags
        if (amalgam) {
            boss.setMetadata("de_is_dual_amalgam", new FixedMetadataValue(plugin, true));
        }
        if (vortex) {
            boss.setMetadata("de_is_vortex", new FixedMetadataValue(plugin, true));
        }
        if (inferno) {
            boss.setMetadata("de_is_inferno", new FixedMetadataValue(plugin, true));
        }
        if (isSplitInferno) {
            boss.setMetadata("de_is_split_inferno", new FixedMetadataValue(plugin, true));
        }

        // Register with effect system
        bossEffectListener.registerBoss(boss);
        bossEffectListener.spawnShriek(boss);

        // Clear death tags for the flawless Boss Cape run when a boss fight starts
        for (Player p : loc.getWorld().getPlayers()) {
            p.removeMetadata("died_during_boss", plugin);
        }

        activeBossUuids.add(boss.getUniqueId());
        bossUuid = boss.getUniqueId();

        // Spawn the custom crimson_boss ItemDisplay visual paired to this carrier
        ItemDisplay display = spawnCrimsonDisplay(boss, name);
        carrierToDisplay.put(boss.getUniqueId(), display.getUniqueId());
        displaySpinAngles.put(boss.getUniqueId(), 0.0);

        startBossAI();

        if (legendary) {
            // Server-wide legendary announcement
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.sendMessage("");
                online.sendMessage("Â§4Â§lâš¡ â˜  Â§cÂ§l" + name.replaceAll("Â§.", "") + " HAS AWAKENED! Â§4Â§lâ˜  âš¡");
                online.sendMessage("Â§7Â§oA supreme terror rises from the Crimson Pit...");
                online.sendMessage("Â§6Â§o  Â§e" + (int)hp + " HP Â§6â€” Â§cBring everything you have!");
                online.sendMessage("");
                online.sendTitle("Â§4Â§lâš¡ LEGENDARY BOSS âš¡", "Â§c" + (int)hp + " HP â€” Â§6Crimson Pit!", 10, 100, 20);
            }
        } else {
            announceSpawn(loc);
        }
        
        customSpawnSequence(boss);

        return boss;
    }

    /** Convenience overload â€” uses default Crimson Pit coords. */
    public Blaze spawnBoss() { return spawnBoss(null); }

    public boolean isBossAlive() {
        activeBossUuids.removeIf(uuid -> {
            Entity e = plugin.getServer().getEntity(uuid);
            return !(e instanceof LivingEntity le && !le.isDead());
        });
        return !activeBossUuids.isEmpty();
    }

    public void cleanup() {
        cancelTask();
        cancelRespawn();
        bossEnderPearls.clear();
    }

    // â•â• AI Task â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // (Spawner block placement/break/interact/activation is now handled by
    //  com.yourname.difficulty.boss.crimson.CrimsonBossSpawner â€” registered separately in Main.java)

    private void startBossAI() {
        if (bossTask != null) return; // Only start AI once

        bossTask = new BukkitRunnable() {
            int   tick         = 0;
            int   wanderTick   = 0;

            @Override
            public void run() {
                if (!isBossAlive()) { cancel(); bossTask = null; return; }

                tick++;
                wanderTick++;

                for (UUID uuid : new ArrayList<>(activeBossUuids)) {
                    Entity entity = plugin.getServer().getEntity(uuid);
                    if (!(entity instanceof Blaze boss) || boss.isDead()) continue;

                    // Keep burning
                    boss.setFireTicks(200);

                    // â”€â”€ Wandering AI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (wanderTick >= WANDER_INTERVAL) {
                        pickNewWanderTarget(boss);
                    }

                    // Apply wander velocity per boss (stored in metadata)
                    if (boss.hasMetadata("de_wander_vel")) {
                        Vector wanderVel = (Vector) boss.getMetadata("de_wander_vel").get(0).value();
                        if (wanderVel != null && !wanderVel.isZero()) {
                            Vector current = boss.getVelocity();
                            Vector blended = current.add(wanderVel.clone().subtract(current).multiply(0.15));
                            boss.setVelocity(blended);
                        }
                    }

                    // â”€â”€ Block-breaking in path (every 5 ticks) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    if (tick % 5 == 0) breakBlocksInPath(boss);

                    // â”€â”€ Phase Mechanics â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                    boolean isPhase2 = boss.getHealth() <= (boss.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.30);
                    
                    // Distinct boss animations/trails
                    if (boss.hasMetadata("de_is_vortex")) {
                        if (tick % 3 == 0) {
                            boss.getWorld().spawnParticle(Particle.DRAGON_BREATH, boss.getLocation().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.05);
                            boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.05);
                        }
                    } else if (boss.hasMetadata("de_is_inferno") || boss.hasMetadata("de_is_split_inferno")) {
                        if (tick % 3 == 0) {
                            boss.getWorld().spawnParticle(Particle.LAVA, boss.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.05);
                            boss.getWorld().spawnParticle(Particle.LARGE_SMOKE, boss.getLocation().add(0, 1.2, 0), 4, 0.3, 0.3, 0.3, 0.02);
                        }
                    } else if (boss.hasMetadata("de_is_dual_amalgam")) {
                        if (tick % 3 == 0) {
                            boss.getWorld().spawnParticle(Particle.SOUL, boss.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.04);
                            boss.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, boss.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.05);
                            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation().add(0, 1, 0), 4, 0.3, 0.3, 0.3, 0.04);
                        }
                    }

                    if (isPhase2) {
                        if (boss.getAttribute(Attribute.MOVEMENT_SPEED) != null && boss.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() < 0.4) {
                            boss.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.5);
                        }
                        if (tick % 5 == 0 && bossAttacks != null) bossAttacks.spawnSoulFlameSpiral(boss.getLocation());
                    } else {
                        if (tick % 5 == 0 && bossAttacks != null) bossAttacks.spawnFlameSpiral(boss.getLocation());
                    }
                    
                    if (tick % 10 == 0 && bossAttacks != null) bossAttacks.spawnLavaDrips(boss.getLocation());

                    List<Player> nearby = nearbyPlayers(boss.getLocation(), ENGAGE_RADIUS);
                    if (nearby.isEmpty()) continue;

                    if (bossAttacks != null) {
                        // â”€â”€ Attack 1: Homing Hellfire (every 60 ticks / 3 s) â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        if (tick % 60 == 0) bossAttacks.attackHomingHellfire(boss, nearby);

                        // â”€â”€ Attack 2: Molten Barrage (every 200 ticks / 10 s) â”€â”€â”€â”€â”€â”€â”€â”€
                        if (tick % 200 == 0) bossAttacks.attackMoltenBarrage(boss, nearby);

                        // â”€â”€ Attack 3: Void Pearl Volley (every 300 ticks / 15 s) â”€â”€â”€â”€â”€
                        if (tick % 300 == 0) bossAttacks.attackVoidPearls(boss, nearby);

                        // â”€â”€ Attack 4: Fire Blob Summon (every 400 ticks / 20 s) â”€â”€â”€â”€â”€â”€
                        if (tick % 400 == 0) bossAttacks.attackSummonBlobs(boss, nearby);

                        // â”€â”€ Attack 5: Flame Minion Summon (every 600 ticks / 30 s) â”€â”€â”€
                        if (tick % 600 == 0) bossAttacks.attackSummonMinions(boss, nearby);

                        // â”€â”€ Attack 6: Passive Scorch (every 80 ticks / 4 s) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                        if (tick % 80 == 0) bossAttacks.attackScorch(boss, nearby);
                    }
                }

                if (wanderTick >= WANDER_INTERVAL) {
                    wanderTick = 0;
                }

                if (tick >= 1200) tick = 0;
            }

            /** Picks a new random cave direction and stores the velocity. */
            private void pickNewWanderTarget(Blaze boss) {
                Location loc = boss.getLocation();

                // Random direction with slight downward bias (stay underground)
                double yaw   = random.nextDouble() * Math.PI * 2;
                double pitch = (random.nextDouble() - 0.6) * Math.PI / 3; // bias down
                double dist  = 10 + random.nextDouble() * (WANDER_DIST - 10);

                double vx = Math.cos(yaw) * Math.cos(pitch) * dist / (WANDER_INTERVAL / 20.0);
                double vy = Math.sin(pitch) * dist / (WANDER_INTERVAL / 20.0);
                double vz = Math.sin(yaw) * Math.cos(pitch) * dist / (WANDER_INTERVAL / 20.0);

                // Clamp Y so boss stays underground (below Y=20)
                double targetY = loc.getY() + vy * (WANDER_INTERVAL / 20.0);
                if (targetY > -10 && vy > 0) vy = -Math.abs(vy); // force down
                if (targetY < -60 && vy < 0) vy =  Math.abs(vy); // don't go too deep

                // Speed limit: 0.6 blocks/tick max
                Vector localWanderVel = new Vector(vx, vy, vz);
                if (localWanderVel.length() > 0.6) localWanderVel.normalize().multiply(0.6);
                boss.setMetadata("de_wander_vel", new FixedMetadataValue(plugin, localWanderVel));
            }
        };

        bossTask.runTaskTimer(plugin, 20L, 1L);
    }

    // â•â• Block-breaking â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    /**
     * Breaks any breakable solid block that the boss is currently inside or
     * immediately in front of (in its velocity direction).  Gives drops.
     * Indestructible materials (bedrock, ancient debris, obsidian, etc.)
     * are never touched.
     */
    private void breakBlocksInPath(Blaze boss) {
        Location loc = boss.getLocation();
        Vector vel  = boss.getVelocity();

        double scale = 1.0;
        var scaleAttr = boss.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scale = scaleAttr.getBaseValue();

        if (scale > 1.0) {
            // Super aggressive block breaking with increased radius:
            // 10x scale normal boss -> 4 blocks radius.
            // 35x scale special boss -> 14 blocks radius.
            // 50x scale legendary boss -> 20 blocks radius.
            int radius = (int) Math.max(2, scale / 2.5);
            World world = loc.getWorld();
            if (world == null) return;
            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            // Break blocks in a sphere/box around the giant boss
            for (int x = bx - radius; x <= bx + radius; x++) {
                for (int y = by - radius; y <= by + radius; y++) {
                    for (int z = bz - radius; z <= bz + radius; z++) {
                        double distSq = (x - bx)*(x - bx) + (y - by)*(y - by) + (z - bz)*(z - bz);
                        if (distSq > radius * radius) continue;

                        Block b = world.getBlockAt(x, y, z);
                        if (b.getType().isAir()) continue;
                        if (!b.getType().isSolid()) continue;
                        if (INDESTRUCTIBLE.contains(b.getType())) continue;
                        if (!isCaveMaterial(b.getType())) continue;

                        // 100% instant block destruction for any blocks touched!
                        b.setType(Material.AIR);
                        if (random.nextDouble() < 0.1) {
                            world.spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }
            }
            return;
        }

        // Check current block + 1 block ahead in velocity direction
        Location[] checks = {
            loc,
            loc.clone().add(vel.clone().normalize()),
            loc.clone().add(0, 1, 0)
        };

        for (Location check : checks) {
            Block b = check.getBlock();
            if (b.getType().isAir()) continue;
            if (!b.getType().isSolid()) continue;
            if (INDESTRUCTIBLE.contains(b.getType())) continue;

            // Only break "cave" materials â€” not player-built stuff like wood/planks
            if (!isCaveMaterial(b.getType())) continue;

            // Small chance of dropping the block (so world isn't stripped bare)
            if (random.nextDouble() < 0.3) {
                b.breakNaturally(); // drops items
            } else {
                b.setType(Material.AIR);
            }

            // Particle + sound
            loc.getWorld().spawnParticle(Particle.BLOCK,
                    b.getLocation().clone().add(0.5, 0.5, 0.5),
                    8, 0.3, 0.3, 0.3, 0,
                    b.getBlockData());
            loc.getWorld().playSound(b.getLocation(),
                    Sound.BLOCK_STONE_BREAK, 0.4f, 0.8f);
        }
    }

    /** Returns true if this material is a naturally occurring cave block. */
    private boolean isCaveMaterial(Material m) {
        return switch (m) {
            case STONE, DEEPSLATE, COBBLESTONE, COBBLED_DEEPSLATE,
                 GRAVEL, DIRT, COARSE_DIRT, ROOTED_DIRT,
                 GRANITE, DIORITE, ANDESITE,
                 NETHERRACK, MAGMA_BLOCK, BASALT, BLACKSTONE,
                 TUFF, CALCITE, DRIPSTONE_BLOCK,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COAL_ORE, DEEPSLATE_COAL_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 SMOOTH_BASALT, MUD -> true;
            default -> false;
        };
    }

    // â•â• Event Handlers â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
    // (Damage/pearl attack event handling now lives in
    //  com.yourname.difficulty.boss.crimson.CrimsonBossAttacks â€” registered separately in Main.java)

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeathDuringBoss(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        player.setMetadata("died_during_boss", new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (!activeBossUuids.contains(event.getEntity().getUniqueId())) return;

        UUID deadUuid = event.getEntity().getUniqueId();
        activeBossUuids.remove(deadUuid);
        
        // Handle Amalgam splitting logic on death
        if (event.getEntity().hasMetadata("de_is_dual_amalgam")) {
            Location loc = event.getEntity().getLocation();
            for (Player p : nearbyPlayers(loc, ENGAGE_RADIUS * 2)) {
                p.sendMessage("");
                p.sendMessage("Â§dâ˜  Â§5Â§lTHE DUAL BLAZE AMALGAM SPLITS! Â§dâ˜ ");
                p.sendMessage("Â§7Two fierce Blaze Infernos rise from the molten remains!");
                p.sendTitle("Â§5Â§lAMALGAM SPLIT!", "Â§dTwo Blaze Infernos awaken!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1.0f, 0.5f); // Play sound
            }
            // Spawn 2 split Infernos (each with 10k HP)
            spawnBoss(loc, true);
            spawnBoss(loc.clone().add(1, 0, 1), true);
            return; // Exit early to prevent victory announcement and respawn timer
        }

        if (isBossAlive()) {
            // There is still another split boss alive â€” do not announce victory yet
            return;
        }

        bossUuid = null;
        cancelTask();
        bossEnderPearls.clear();

        Location loc = event.getEntity().getLocation();

        // Victory announcements
        for (Player p : nearbyPlayers(loc, ENGAGE_RADIUS * 2)) {
            p.sendMessage("");
            p.sendMessage("Â§6âœ¦ Â§cÂ§lðŸ”¥ THE INFERNAL BLAZEFIEND HAS BEEN SLAIN! ðŸ”¥ Â§6âœ¦");
            p.sendMessage("Â§7The rock grows cool as the ancient fire fades...");
            p.sendMessage("Â§8Â§oThe Blazefiend will return in Â§715 minutesÂ§8Â§o...");
            p.sendMessage("");
            p.sendTitle("Â§6âš” BOSS DEFEATED!", "Â§7The Blazefiend falls to darkness!", 10, 80, 20);
        }

        // Award Boss Cape to nearby players who participated and did NOT die!
        for (Player p : nearbyPlayers(loc, 100.0)) {
            if (!p.hasMetadata("died_during_boss")) {
                ItemStack bossCape = itemFactory.buildBossCape();
                p.getInventory().addItem(bossCape);
                p.sendMessage("");
                p.sendMessage("Â§5âœ¦ Â§6Â§lâœ¦ BOSS CAPE AWARDED! âœ¦ Â§5âœ¦");
                p.sendMessage("Â§7You defeated the legendary world boss without dying!");
                p.sendMessage("");
                p.sendTitle("Â§5Â§lâœ¦ BOSS CAPE! âœ¦", "Â§7Awarded for flawless victory!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                p.sendMessage("Â§câœ— Â§7You died during the fight, so you did not qualify for the flawless Boss Cape reward!");
            }
            p.removeMetadata("died_during_boss", plugin); // clear tag
        }

        // â”€â”€ 15% chance: drop the GunZ Sword â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        if (random.nextDouble() < GUNZ_DROP_CHANCE) {
            loc.getWorld().dropItemNaturally(loc, itemFactory.buildGunZSword());
            for (Player p : nearbyPlayers(loc, ENGAGE_RADIUS * 2)) {
                p.sendMessage("Â§4âš” Â§cÂ§lâ˜… RARE DROP: Â§4GunZ Sword Â§cÂ§lâ˜… Â§8â€” The Blazefiend's Blade dropped!");
                p.sendTitle("Â§4Â§lâ˜… RARE DROP!", "Â§cÂ§lâš” GunZ Sword â€” Â§7claim it fast!", 10, 80, 20);
            }
        }

        // Death FX
        for (int i = 0; i < 8; i++) {
            final int fi = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) return;
                double dx = (random.nextDouble() - 0.5) * 4;
                double dz = (random.nextDouble() - 0.5) * 4;
                Location el = loc.clone().add(dx, 0, dz);
                loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, el, 2, 0.5, 0.5, 0.5, 0);
                loc.getWorld().spawnParticle(Particle.FLAME, el, 40, 1.5, 1.5, 1.5, 0.3);
                loc.getWorld().strikeLightningEffect(el);
            }, fi * 8L);
        }

        // â”€â”€ Schedule 15-minute respawn â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        respawnTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            respawnTask = null;
            // Find a random deepslate cave-level location near the original pit
            World world = plugin.getServer().getWorld("ancient_realm");
            if (world == null) {
                world = plugin.getServer().getWorlds().get(0);
            }
            double rx = SPAWN_X + (random.nextDouble() - 0.5) * 60;
            double rz = SPAWN_Z + (random.nextDouble() - 0.5) * 60;
            double ry = -20 - random.nextDouble() * 20; // between -20 and -40
            Location reLoc = new Location(world, rx, ry, rz);

            // Move to air if needed
            for (int i = 0; i < 10; i++) {
                if (reLoc.getBlock().getType().isAir()) break;
                reLoc.add(0, 1, 0);
            }

            spawnBoss(reLoc);

            // Server-wide hint (so players know to search for it)
            for (Player p : world.getPlayers()) {
                p.sendMessage("Â§cðŸ”¥ Â§4The Infernal Blazefiend has re-awoken somewhere in the depths!");
                p.sendMessage("Â§7Â§oFind it by striking Â§6Spawner Block Â§7Â§oor Â§6searching the cavesÂ§7Â§o...");
            }
        }, RESPAWN_TICKS);
    }

    // â•â• Internal helpers â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•

    private void dismissExisting() {
        for (UUID uuid : activeBossUuids) {
            Entity e = plugin.getServer().getEntity(uuid);
            if (e != null && !e.isDead()) e.remove();
        }
        activeBossUuids.clear();
        cancelTask();
        bossUuid = null;
        bossEnderPearls.clear();
    }

    private void cancelTask() {
        if (bossTask != null) { bossTask.cancel(); bossTask = null; }
    }

    private void cancelRespawn() {
        if (respawnTask != null) { respawnTask.cancel(); respawnTask = null; }
    }

    public void rebuildArena(Player player, Location spawnerLoc) {
        if (spawnerLoc == null) return;
        Block block = spawnerLoc.getBlock();
        String schematicName = null;
        if (block.getType() == Material.GILDED_BLACKSTONE) {
            schematicName = "crimson_pit.schem";
        } else if (block.getType() == Material.CRYING_OBSIDIAN) {
            schematicName = "tempest_sanctum.schem";
        } else if (block.getType() == Material.BLACK_CONCRETE) {
            schematicName = "void_sanctum.schem";
        } else if (block.getType() == Material.GOLD_BLOCK) {
            schematicName = "gilded_sanctum.schem";
        } else {
            schematicName = "void_sanctum.schem"; // fallback
        }


        if (player == null) {
            for (Player p : spawnerLoc.getWorld().getPlayers()) {
                if (p.getLocation().distance(spawnerLoc) < 150) {
                    player = p;
                    break;
                }
            }
            if (player == null && !Bukkit.getOnlinePlayers().isEmpty()) {
                player = Bukkit.getOnlinePlayers().iterator().next();
            }
        }

        if (player != null) {
            final Player finalPlayer = player;
            final String finalSchem = schematicName;
            final Location finalLoc = spawnerLoc.clone();
            
            // Save location
            Location prevLoc = player.getLocation().clone();
            
            // Teleport exactly to the NW-bottom corner offset so the pasted room centers perfectly around the spawner block!
            // Boss rooms are 30x13x30 blocks, so offset is (-15.0, -1.0, -15.0) relative to spawner block center.
            Location pasteLoc = spawnerLoc.clone().add(-15.0, -1.0, -15.0);
            player.teleport(pasteLoc);
            
            // Execute WorldEdit commands with a delay, and restore position
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                boolean wasOp = finalPlayer.isOp();
                boolean loadOk = false;
                boolean pasteOk = false;
                try {
                    if (!wasOp) finalPlayer.setOp(true);
                    // WorldEdit registers these as "//schem load" and "//paste"
                    // (the leading "/" is stripped by performCommand, so we pass
                    // a single leading slash for the WorldEdit "//" prefix).
                    String schemNameNoExt = finalSchem.endsWith(".schem")
                            ? finalSchem.substring(0, finalSchem.length() - ".schem".length())
                            : finalSchem;
                    loadOk = finalPlayer.performCommand("/schem load " + schemNameNoExt);
                    pasteOk = finalPlayer.performCommand("/paste -a");
                } catch (Exception ex) {
                    plugin.getLogger().warning("[CrimsonBossManager] rebuildArena WorldEdit command threw: " + ex.getMessage());
                } finally {
                    if (!wasOp) finalPlayer.setOp(false);
                }

                if (!loadOk || !pasteOk) {
                    plugin.getLogger().warning("[CrimsonBossManager] rebuildArena FAILED for schematic '"
                            + finalSchem + "' at " + finalLoc + " (loadOk=" + loadOk + ", pasteOk=" + pasteOk
                            + "). Verify WorldEdit is installed, the schematic exists in "
                            + "plugins/WorldEdit/schematics/, and the player has worldedit.* permission.");
                } else {
                    plugin.getLogger().info("[CrimsonBossManager] rebuildArena OK: pasted '" + finalSchem
                            + "' at " + finalLoc);
                }

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {

                    finalPlayer.teleport(prevLoc);
                    // Restore metadata
                    if (finalSchem.startsWith("crimson_pit")) {
                        finalLoc.getBlock().setType(Material.GILDED_BLACKSTONE);
                        finalLoc.getBlock().setMetadata("de_blazefiend_spawner", new FixedMetadataValue(plugin, true));
                    } else if (finalSchem.startsWith("tempest_sanctum")) {
                        finalLoc.getBlock().setType(Material.CRYING_OBSIDIAN);
                        finalLoc.getBlock().setMetadata("de_tempest_spawner", new FixedMetadataValue(plugin, true));
                    } else if (finalSchem.startsWith("void_sanctum") || finalSchem.startsWith("void_realm")) {
                        finalLoc.getBlock().setType(Material.BLACK_CONCRETE);
                        finalLoc.getBlock().setMetadata("de_void_spawner", new FixedMetadataValue(plugin, true));
                    } else if (finalSchem.startsWith("gilded_sanctum")) {
                        finalLoc.getBlock().setType(Material.GOLD_BLOCK);
                        finalLoc.getBlock().setMetadata("de_gilded_spawner", new FixedMetadataValue(plugin, true));
                    }
                }, 3L);
            }, 1L);
        }
    }

    // NOTE: Tempest Overlord spawning/AI now lives in
    // com.yourname.difficulty.boss.tempest.TempestOverlordManager
    // NOTE: Void Wither (Zurion) spawning/AI + Warden-on-explode logic now lives in
    // com.yourname.difficulty.boss.voidwither.VoidWitherManager

    /**
     * Builds the Blazefiend's custom Blockbench flame visual â€” a
     * fixed-orientation ItemDisplay showing the crimson_boss model, entirely
     * independent from the now-invisible vanilla Blaze carrier underneath.
     */
    private ItemDisplay spawnCrimsonDisplay(Blaze carrier, String customName) {
        double scaleAttrVal = 10.0;
        var scaleAttr = carrier.getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttrVal = scaleAttr.getBaseValue();
        float baseScale = (float) (scaleAttrVal / CRIMSON_DISPLAY_SCALE_DIVISOR);

        return BossDisplayUtil.spawnDisplay(
                carrier, 0.2, Material.BLAZE_ROD, 3001, baseScale,
                15, 15,
                customName,
                displayTagKey,
                Display.Billboard.FIXED);
    }


    private void announceSpawn(Location loc) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) > ENGAGE_RADIUS * 3) continue;
            p.sendMessage("");
            p.sendMessage("Â§4â˜  Â§cÂ§lðŸ”¥ THE INFERNAL BLAZEFIEND HAS AWAKENED! ðŸ”¥ Â§4â˜ ");
            p.sendMessage("Â§7It carves through the rock â€” Â§4HUNT IT DOWNÂ§7 in the caves!");
            p.sendMessage("Â§6âš  Â§eBring Â§bWater Â§eand Â§aEarth Â§emagic â€” it WILL find you first!");
            p.sendMessage("");
            p.sendTitle("Â§cÂ§lðŸ”¥ BOSS AWAKENS!", "Â§7Â§oSearch the caves...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
        }
    }

    private void customSpawnSequence(Blaze boss) {
        Location startLoc = boss.getLocation();
        World world = boss.getWorld();
        
        // 1. Warden emerge animation
        world.playSound(startLoc, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 1.0f);
        world.spawnParticle(Particle.BLOCK, startLoc, 100, 1.0, 1.0, 1.0, 0, Material.CRIMSON_NYLIUM.createBlockData());
        
        boss.setAI(false);
        boss.setInvulnerable(true);
        
        // 2. 5 Teleports with 10 tick delay
        new BukkitRunnable() {
            int teleports = 0;
            
            @Override
            public void run() {
                if (!isBossAlive() || teleports >= 5) {
                    if (isBossAlive()) {
                        boss.setAI(true);
                        boss.setInvulnerable(false);
                        
                        // 3. Custom lightning cluster
                        for (int i = 0; i < 6; i++) {
                            double angle = i * Math.PI * 2.0 / 6.0;
                            Location lLoc = boss.getLocation().clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
                            spawnCustomLightningVisual(lLoc);
                        }
                        
                        // 4. Sky flash globally (200 block radius)
                        Location skyFlash = boss.getLocation().clone();
                        skyFlash.setY(319);
                        for (Player p : nearbyPlayers(boss.getLocation(), 200.0)) {
                            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0f, 0.8f);
                        }
                        world.strikeLightningEffect(skyFlash);

                        // 5. 8-Direction Firestorm
                        world.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1.0f);
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, boss.getLocation(), 5, 0.5, 0.5, 0.5, 0.1);
                        double[][] directions = {
                            {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                            {1, 0, 1}, {-1, 0, -1}, {1, 0, -1}, {-1, 0, 1}
                        };
                        for (double[] d : directions) {
                            Vector dirVec = new Vector(d[0], d[1], d[2]).normalize();
                            SmallFireball sfb = (SmallFireball) world.spawnEntity(
                                boss.getLocation().add(0, 1.5, 0).add(dirVec.clone().multiply(1.5)), 
                                EntityType.SMALL_FIREBALL
                            );
                            sfb.setShooter(boss);
                            sfb.setDirection(dirVec.multiply(1.5));
                        }
                    }
                    cancel();
                    return;
                }
                
                // Teleport randomly near spawn
                double rx = SPAWN_X + (random.nextDouble() - 0.5) * 15;
                double rz = SPAWN_Z + (random.nextDouble() - 0.5) * 15;
                Location tpLoc = new Location(world, rx, SPAWN_Y, rz);
                
                world.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                
                // Spawn variant-specific particles on teleporting
                if (boss.hasMetadata("de_is_vortex")) {
                    world.spawnParticle(Particle.DRAGON_BREATH, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else if (boss.hasMetadata("de_is_inferno") || boss.hasMetadata("de_is_split_inferno")) {
                    world.spawnParticle(Particle.LAVA, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else if (boss.hasMetadata("de_is_dual_amalgam")) {
                    world.spawnParticle(Particle.SOUL, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else {
                    world.spawnParticle(Particle.PORTAL, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                }
                
                boss.teleport(tpLoc);
                
                world.playSound(boss.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                if (boss.hasMetadata("de_is_vortex")) {
                    world.spawnParticle(Particle.DRAGON_BREATH, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else if (boss.hasMetadata("de_is_inferno") || boss.hasMetadata("de_is_split_inferno")) {
                    world.spawnParticle(Particle.LAVA, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else if (boss.hasMetadata("de_is_dual_amalgam")) {
                    world.spawnParticle(Particle.SOUL, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                } else {
                    world.spawnParticle(Particle.PORTAL, boss.getLocation(), 50, 1.0, 1.0, 1.0, 0.1);
                }
                
                teleports++;
            }
        }.runTaskTimer(plugin, 20L, 10L);
    }
    
    private void spawnCustomLightningVisual(Location loc) {
        Location start = loc.clone().add(0, 15, 0);
        double distance = start.distance(loc);
        Vector dir = loc.toVector().subtract(start.toVector()).normalize();
        
        for (double d = 0; d < distance; d += 0.5) {
            Location pt = start.clone().add(dir.clone().multiply(d));
            loc.getWorld().spawnParticle(Particle.DUST, pt, 5, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.WHITE, 1.5f));
            loc.getWorld().spawnParticle(Particle.DUST, pt, 10, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.BLUE, 1.0f));
            loc.getWorld().spawnParticle(Particle.DUST, pt, 3, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(Color.BLACK, 0.5f));
        }
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 15, 1.0, 0.5, 1.0, 0.05);
        loc.getWorld().strikeLightningEffect(loc); 
    }

    private List<Player> nearbyPlayers(Location loc, double radius) {
        List<Player> result = new ArrayList<>();
        for (Player p : loc.getWorld().getPlayers()) {
            if (!p.isDead() && p.getLocation().distance(loc) <= radius) result.add(p);
        }
        return result;
    }

    private static Vector vecTo(Location from, Location to) {
        return to.toVector().subtract(from.toVector());
    }
}
