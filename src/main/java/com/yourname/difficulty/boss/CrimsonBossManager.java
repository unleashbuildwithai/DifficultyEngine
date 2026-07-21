package com.yourname.difficulty.boss;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
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
 * CrimsonBossManager — manages §c🔥 The Infernal Blazefiend§r.
 *
 * ── Triggering ───────────────────────────────────────────────────────────────
 *  • Striking any ANCIENT_DEBRIS block while boss is dead → boss spawns near it.
 *  • After death a 15-minute (18 000 tick) timer re-spawns the boss.
 *  • Admin: /spawnboss crimson
 *
 * ── Starting location ────────────────────────────────────────────────────────
 *  Crimson Pit:  -107.964,  -26,  -14.444
 *  But the boss WANDERS freely through the caves after spawning.
 *
 * ── Wandering & Block-breaking ───────────────────────────────────────────────
 *  Every 3 s the boss picks a random target 10-25 blocks away in a random
 *  horizontal+vertical direction and flies toward it.  Any breakable blocks
 *  (stone, deepslate, netherrack, gravel, etc.) directly in its path are
 *  broken, letting it carve through solid rock.
 *  Indestructible blocks (bedrock, ancient debris, obsidian, reinforced
 *  deepslate) are not touched.
 *
 * ── Attacks ──────────────────────────────────────────────────────────────────
 *  Same 6 attacks as before (Hellfire, Molten Barrage, Void Pearls,
 *  Fire Blobs, Flame Slimes, Scorch).
 */
public class CrimsonBossManager implements Listener {

    // ── Default spawn coords (used by /spawnboss crimson) ─────────────────────
    public static final double SPAWN_X = -107.964;
    public static final double SPAWN_Y = -26.0;
    public static final double SPAWN_Z = -14.444;

    // ── Tunables ──────────────────────────────────────────────────────────────
    /** Standard world-boss HP — tuned for Lv99 parties. */
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

    private final JavaPlugin         plugin;
    private final ItemFactory        itemFactory;
    private final BossEffectListener bossEffectListener;
    private final Random             random = new Random();

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

    public CrimsonBossManager(JavaPlugin plugin,
                               ItemFactory itemFactory,
                               BossEffectListener bossEffectListener) {
        this.plugin             = plugin;
        this.itemFactory        = itemFactory;
        this.bossEffectListener = bossEffectListener;

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
    }

    private void spawnGuardNear(Location loc) {
        if (isBossAlive()) return; // do not spawn guards while the boss is active

        int guardsCount = 0;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 15, 10, 15)) {
            if (e instanceof Blaze blaze && "§c🔥 Blazefiend".equals(blaze.getCustomName())) {
                guardsCount++;
            }
        }
        if (guardsCount >= 3) return;

        double rx = loc.getX() + 0.5 + (random.nextDouble() - 0.5) * 6;
        double rz = loc.getZ() + 0.5 + (random.nextDouble() - 0.5) * 6;
        Location gl = new Location(loc.getWorld(), rx, loc.getY() + 1.0, rz);

        if (!gl.getBlock().getType().isAir()) return;

        Blaze guard = (Blaze) loc.getWorld().spawnEntity(gl, EntityType.BLAZE);
        guard.setCustomName("§c🔥 Blazefiend");
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

    // ══ Public API ═══════════════════════════════════════════════════════════

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
        String name = "§c🔥 §l§4The Infernal Blazefiend";
        boolean legendary = false;
        boolean amalgam = false;
        boolean vortex = false;
        boolean inferno = false;

        if (isSplitInferno) {
            hp = 10000.0;
            name = "§c🔥 §l§4The Blaze Inferno §7(Amalgam Split)";
            inferno = true;
        } else {
            if (roll < 0.001) { // 0.1% chance: Dual Blaze Amalgam
                name = "§d☠ §5§lTHE DUAL BLAZE AMALGAM §d☠";
                hp = 20000.0;
                amalgam = true;
                legendary = true;
            } else if (roll < 0.01) { // 0.9% chance: Blaze Vortex
                name = "§d☠ §c§l⚡ THE BLAZE VORTEX ⚡ §d☠";
                hp = 20000.0;
                vortex = true;
                legendary = true;
            } else if (roll < 0.03) { // 2% chance: Blaze Inferno
                name = "§c🔥 §l§4The Blaze Inferno";
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
                    name = "§4☠ §c§lTHE APOCALYPTIC BLAZEFIEND §4☠";
                } else if (nightmareCount > 1) {
                    hp = 7500.0;
                    name = "§c🔥 §l§4The Nightmare Blazefiend";
                } else {
                    hp = 5000.0;
                }
            }
        }

        Blaze boss = (Blaze) loc.getWorld().spawnEntity(loc, EntityType.BLAZE);
        boss.setCustomName(name);
        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setFireTicks(Integer.MAX_VALUE);

        // Boost HP safely preventing Health value must be between 0 and 1024 Exception
        var hpAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
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
        var scaleAttr = boss.getAttribute(Attribute.GENERIC_SCALE);
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

        startBossAI();
        if (legendary) {
            // Server-wide legendary announcement
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.sendMessage("");
                online.sendMessage("§4§l⚡ ☠ §c§l" + name.replaceAll("§.", "") + " HAS AWAKENED! §4§l☠ ⚡");
                online.sendMessage("§7§oA supreme terror rises from the Crimson Pit...");
                online.sendMessage("§6§o  §e" + (int)hp + " HP §6— §cBring everything you have!");
                online.sendMessage("");
                online.sendTitle("§4§l⚡ LEGENDARY BOSS ⚡", "§c" + (int)hp + " HP — §6Crimson Pit!", 10, 100, 20);
            }
        } else {
            announceSpawn(loc);
        }
        
        customSpawnSequence(boss);

        return boss;
    }

    /** Convenience overload — uses default Crimson Pit coords. */
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

    // ══ Spawner Block Click & Bedrock Hardness Trigger ════════════════════════

    private boolean isSpawnerBlock(Block block) {
        if (block == null) return false;
        if (block.getType() != Material.GILDED_BLACKSTONE) return false;
        if (block.hasMetadata("de_blazefiend_spawner")) return true;
        if (block.getWorld().getName().equals("ancient_realm")) {
            if (block.getX() == -108 && block.getY() == -26 && block.getZ() == -14) {
                return true;
            }
        }
        return false;
    }

    private boolean isTempestSpawnerBlock(Block block) {
        if (block == null) return false;
        if (block.getType() != Material.CRYING_OBSIDIAN) return false;
        if (block.hasMetadata("de_tempest_spawner")) return true;
        if (block.getWorld().getName().equals("ancient_realm")) {
            if (block.getX() == 115 && block.getY() == -38 && block.getZ() == -47) {
                return true;
            }
        }
        return false;
    }

    private boolean isVoidSpawnerBlock(Block block) {
        if (block == null) return false;
        if (block.getType() != Material.BLACK_CONCRETE) return false;
        if (block.hasMetadata("de_void_spawner")) return true;
        if (block.getWorld().getName().equals("void_realm") || block.getWorld().getName().equals("ancient_realm")) {
            if (block.getX() == 0 && block.getY() == 64 && block.getZ() == 0) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Block block = event.getBlockPlaced();
        if (itemFactory.isBlazefiendSpawner(item)) {
            block.setMetadata("de_blazefiend_spawner", new FixedMetadataValue(plugin, true));
            event.getPlayer().sendMessage("§a✓ §7Placed Blazefiend Spawner block!");
        } else if (itemFactory.isTempestSpawner(item)) {
            block.setMetadata("de_tempest_spawner", new FixedMetadataValue(plugin, true));
            event.getPlayer().sendMessage("§a✓ §7Placed Tempest Spawner block!");
        } else if (itemFactory.isVoidSpawner(item)) {
            block.setMetadata("de_void_spawner", new FixedMetadataValue(plugin, true));
            event.getPlayer().sendMessage("§a✓ §7Placed Void Spawner block!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block)) {
            Player player = event.getPlayer();
            if (!player.hasPermission("difficultyengine.cape.admin") && !player.isOp()) {
                event.setCancelled(true);
                player.sendMessage("§c✗ §7This spawner block is protected like bedrock! Only admins can remove it.");
            } else {
                block.removeMetadata("de_blazefiend_spawner", plugin);
                block.removeMetadata("de_tempest_spawner", plugin);
                block.removeMetadata("de_void_spawner", plugin);
                player.sendMessage("§a✓ §7Removed protected spawner block.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerStrike(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block)) {
            handleSpawnerActivation(event.getPlayer(), block);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSpawnerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (isSpawnerBlock(block) || isTempestSpawnerBlock(block) || isVoidSpawnerBlock(block)) {
            event.setCancelled(true);
            handleSpawnerActivation(event.getPlayer(), block);
        }
    }

    public void handleSpawnerActivation(Player player, Block block) {
        if (block == null) return;

        if (isSpawnerBlock(block)) {
            if (isBossAlive()) {
                player.sendActionBar("§c🔥 §7The Blazefiend already roams these caves...");
                return;
            }
            if (!block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Blazefiend Spawner only works inside the §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§c☠ §4The Spawner has been activated, awakening the Infernal Blazefiend!");
            rebuildArena(player, block.getLocation());
            spawnBoss(block.getLocation());

        } else if (isTempestSpawnerBlock(block)) {
            boolean isTempestAlive = false;
            for (Entity ent : block.getWorld().getEntitiesByClass(Phantom.class)) {
                String cName = ent.getCustomName();
                if (cName != null && cName.contains("Tempest Overlord") && !ent.isDead()) {
                    isTempestAlive = true;
                    break;
                }
            }
            if (isTempestAlive) {
                player.sendActionBar("§c⚡ §7The Tempest Overlord already roams these skies...");
                return;
            }
            if (!block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Tempest Spawner only works inside the §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§c☠ §4The Spawner has been activated, awakening the Tempest Overlord!");
            rebuildArena(player, block.getLocation());
            spawnTempestOverlord(block.getLocation());

        } else if (isVoidSpawnerBlock(block)) {
            boolean isWitherAlive = false;
            for (Entity ent : block.getWorld().getEntitiesByClass(Wither.class)) {
                String cName = ent.getCustomName();
                if (cName != null && (cName.contains("Void Wither") || cName.contains("Void Zurion")) && !ent.isDead()) {
                    isWitherAlive = true;
                    break;
                }
            }
            if (isWitherAlive) {
                player.sendActionBar("§0☠ §7The Void Wither already roams this realm...");
                return;
            }
            if (!block.getWorld().getName().equals("void_realm") && !block.getWorld().getName().equals("ancient_realm")) {
                player.sendMessage("§c✗ §7The Void Spawner only works inside the §5Void Realm§7 or §5Ancient Realm§7!");
                return;
            }
            player.sendMessage("§0☠ §4The Spawner has been activated, awakening the Void Wither!");
            rebuildArena(player, block.getLocation());
            spawnVoidWither(block.getLocation());
        }
    }

    // ══ AI Task ═══════════════════════════════════════════════════════════════

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

                    // ── Wandering AI ─────────────────────────────────────────
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

                    // ── Block-breaking in path (every 5 ticks) ───────────────────
                    if (tick % 5 == 0) breakBlocksInPath(boss);

                    // ── Phase Mechanics ──────────────────────────────────────────
                    boolean isPhase2 = boss.getHealth() <= (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 0.30);
                    
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
                        if (boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null && boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).getBaseValue() < 0.4) {
                            boss.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.5);
                        }
                        if (tick % 5 == 0) spawnSoulFlameSpiral(boss.getLocation());
                    } else {
                        if (tick % 5 == 0) spawnFlameSpiral(boss.getLocation());
                    }
                    
                    if (tick % 10 == 0) spawnLavaDrips(boss.getLocation());

                    List<Player> nearby = nearbyPlayers(boss.getLocation(), ENGAGE_RADIUS);
                    if (nearby.isEmpty()) continue;

                    // ── Attack 1: Homing Hellfire (every 60 ticks / 3 s) ─────────
                    if (tick % 60 == 0) attackHomingHellfire(boss, nearby);

                    // ── Attack 2: Molten Barrage (every 200 ticks / 10 s) ────────
                    if (tick % 200 == 0) attackMoltenBarrage(boss, nearby);

                    // ── Attack 3: Void Pearl Volley (every 300 ticks / 15 s) ─────
                    if (tick % 300 == 0) attackVoidPearls(boss, nearby);

                    // ── Attack 4: Fire Blob Summon (every 400 ticks / 20 s) ──────
                    if (tick % 400 == 0) attackSummonBlobs(boss, nearby);

                    // ── Attack 5: Flame Minion Summon (every 600 ticks / 30 s) ───
                    if (tick % 600 == 0) attackSummonMinions(boss, nearby);

                    // ── Attack 6: Passive Scorch (every 80 ticks / 4 s) ──────────
                    if (tick % 80 == 0) attackScorch(boss, nearby);
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

    // ══ Block-breaking ════════════════════════════════════════════════════════

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
        var scaleAttr = boss.getAttribute(Attribute.GENERIC_SCALE);
        if (scaleAttr != null) scale = scaleAttr.getBaseValue();

        if (scale > 1.0) {
            int radius = (int) Math.max(1, scale / 4.0);
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

                        double chance = scale >= 50.0 ? 0.8 : (scale >= 35.0 ? 0.5 : 0.3);
                        if (random.nextDouble() < chance) {
                            if (random.nextDouble() < 0.05) {
                                b.breakNaturally();
                            } else {
                                b.setType(Material.AIR);
                            }
                            if (random.nextDouble() < 0.1) {
                                world.spawnParticle(Particle.FLAME, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02);
                            }
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

            // Only break "cave" materials — not player-built stuff like wood/planks
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

    // ══ Attacks ════════════════════════════════════════════════════════════════

    private void attackHomingHellfire(Blaze boss, List<Player> players) {
        int targeted = 0;
        for (Player target : players) {
            if (targeted >= 3) break;
            targeted++;

            Location origin = boss.getLocation().add(0, 1.5, 0);
            Location dest   = target.getLocation().add(0, 1.2, 0);
            Vector   dir    = vecTo(origin, dest).normalize();

            Fireball fb = (Fireball) boss.getWorld().spawnEntity(
                    origin.clone().add(dir.clone().multiply(1.5)), EntityType.FIREBALL);
            fb.setShooter(boss);
            fb.setDirection(dir.multiply(1.2));
            fb.setYield(1.2f);
            fb.setIsIncendiary(true);
            target.sendActionBar("§c🔥 §7Homing Hellfire incoming! Dodge!");

            for (int i = 1; i <= 2; i++) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!isBossAlive()) return;
                    Entity bossNow = plugin.getServer().getEntity(bossUuid);
                    if (!(bossNow instanceof Blaze b)) return;
                    Location o2 = b.getLocation().add(0, 1.5, 0);
                    Location d2 = target.getLocation().add(0, 1.0, 0);
                    Vector dv = vecTo(o2, d2).normalize();
                    dv.add(new Vector(
                            (random.nextDouble() - 0.5) * 0.35,
                            (random.nextDouble() - 0.5) * 0.15,
                            (random.nextDouble() - 0.5) * 0.35
                    )).normalize();
                    SmallFireball sfb = (SmallFireball) boss.getWorld().spawnEntity(
                            o2.clone().add(dv.clone().multiply(1.5)), EntityType.SMALL_FIREBALL);
                    sfb.setShooter(b);
                    sfb.setDirection(dv.multiply(2.0));
                }, i * 5L);
            }
        }
    }

    private void attackMoltenBarrage(Blaze boss, List<Player> players) {
        for (Player p : players) {
            p.sendMessage("§c☠ §4MOLTEN BARRAGE! §7Use §bWater §7or §aEarth §7magic to resist!");
            p.sendTitle("§c🌋", "§4Lava eruption incoming!", 3, 20, 5);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isBossAlive()) return;
            Entity be = plugin.getServer().getEntity(bossUuid);
            if (!(be instanceof Blaze boss2)) return;
            int hit = 0;
            for (Player target : players) {
                if (hit >= 2) break;
                hit++;
                Location center = target.getLocation();
                for (double dx = -3; dx <= 3; dx += 0.8) {
                    for (double dz = -3; dz <= 3; dz += 0.8) {
                        if (Math.abs(dx) + Math.abs(dz) > 5) continue;
                        Location pt = center.clone().add(dx, 0.1, dz);
                        target.getWorld().spawnParticle(Particle.LAVA,  pt, 2, 0.2, 0.05, 0.2, 0);
                        target.getWorld().spawnParticle(Particle.FLAME, pt, 3, 0.2, 0.2,  0.2, 0.04);
                        target.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                                pt.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                center.getWorld().playSound(center, Sound.BLOCK_LAVA_AMBIENT, 1.5f, 0.7f);
                center.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
                for (Player victim : nearbyPlayers(center, 4.0)) {
                    ItemStack held = victim.getInventory().getItemInMainHand();
                    MagicElement el = itemFactory.getStaffElement(held);
                    if (el == MagicElement.WATER) {
                        victim.setFireTicks(0);
                        victim.sendActionBar("§b💧 §7Water magic shields you from the lava!");
                    } else if (el == MagicElement.EARTH) {
                        victim.setFireTicks(0);
                        victim.damage(2.0, boss2);
                        victim.sendActionBar("§2🌿 §7Earth deflects the lava! (reduced damage)");
                    } else {
                        victim.setFireTicks(120);
                        victim.damage(6.0, boss2);
                        victim.sendActionBar("§c🔥 §7Burning! Use §bWater §cmagic!");
                    }
                }
            }
        }, 30L);
    }

    private void attackVoidPearls(Blaze boss, List<Player> players) {
        for (Player p : players)
            p.sendMessage("§5☠ §d§lVOID PEARLS! §7If they hit you, you'll be banished to spawn!");
        int thrown = 0;
        for (Player target : players) {
            if (thrown >= 3) break;
            thrown++;
            Location origin = boss.getLocation().add(0, 1.8, 0);
            Location dest   = target.getLocation().add(0, 1.2, 0);
            Vector dir = vecTo(origin, dest).normalize().add(new Vector(0, 0.25, 0));
            EnderPearl pearl = (EnderPearl) boss.getWorld()
                    .spawnEntity(origin, EntityType.ENDER_PEARL);
            pearl.setShooter(boss);
            pearl.setVelocity(dir.multiply(1.6));
            bossEnderPearls.add(pearl.getUniqueId());
            target.sendActionBar("§5⚠ §dVoid Pearl incoming! DODGE!");
        }
    }

    private void attackSummonBlobs(Blaze boss, List<Player> players) {
        int count = 2 + random.nextInt(3);
        for (int i = 0; i < count; i++) {
            Location sp = boss.getLocation().add(
                    (random.nextDouble() - 0.5) * 10, 0,
                    (random.nextDouble() - 0.5) * 10);
            MagmaCube cube = (MagmaCube) boss.getWorld().spawnEntity(sp, EntityType.MAGMA_CUBE);
            cube.setSize(2 + random.nextInt(2));
            cube.setCustomName("§c🔥 Fire Blob");
            cube.setCustomNameVisible(true);
            cube.setFireTicks(Integer.MAX_VALUE);
        }
        for (Player p : players) p.sendMessage("§c🔥 §7Fire Blobs erupt from the rock!");
    }

    private void attackSummonMinions(Blaze boss, List<Player> players) {
        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2.0 / count;
            Location sp = boss.getLocation().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5);
            Blaze minion = (Blaze) boss.getWorld().spawnEntity(sp, EntityType.BLAZE);
            minion.setCustomName("§c🔥 Flame Slime");
            minion.setCustomNameVisible(true);
            minion.setFireTicks(Integer.MAX_VALUE);
            minion.setRemoveWhenFarAway(true);
            var hpAttr = minion.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.setBaseValue(40.0);
                minion.setHealth(Math.min(40.0, hpAttr.getValue()));
            } else {
                minion.setHealth(40.0);
            }
            minion.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, 99999, 1, false, false, false));
        }
        for (Player p : players) p.sendMessage("§c🔥 §7Flame Slimes claw out of the rock!");
    }

    private void attackScorch(Blaze boss, List<Player> players) {
        for (Player p : players) {
            if (boss.getLocation().distance(p.getLocation()) > SCORCH_RADIUS) continue;
            ItemStack held = p.getInventory().getItemInMainHand();
            MagicElement el = itemFactory.getStaffElement(held);
            if (el == MagicElement.WATER) {
                p.setFireTicks(0);
                p.sendActionBar("§b💧 §7Water magic protects you from the infernal heat!");
            } else if (el == MagicElement.EARTH) {
                if (p.getFireTicks() <= 0) {
                    p.setFireTicks(20);
                    p.sendActionBar("§2🌿 §7Earth dampens the heat somewhat.");
                }
            } else {
                if (p.getFireTicks() <= 10) {
                    p.setFireTicks(60);
                    p.sendActionBar("§c🔥 §7Infernal heat! Use §bWater §cmagic!");
                }
            }
        }
    }

    // ══ Particle FX ════════════════════════════════════════════════════════════

    private void spawnFlameSpiral(Location center) {
        double time = (System.currentTimeMillis() % 10_000) / 500.0;
        for (int i = 0; i < 6; i++) {
            double angle = time + (i * Math.PI * 2.0 / 6.0);
            double x = Math.cos(angle) * 1.8;
            double z = Math.sin(angle) * 1.8;
            double y = Math.sin(time * 2 + i) * 0.4 + 1.2;
            center.getWorld().spawnParticle(Particle.FLAME,
                    center.clone().add(x, y, z), 2, 0.05, 0.05, 0.05, 0.005);
        }
    }
    
    private void spawnSoulFlameSpiral(Location center) {
        double time = (System.currentTimeMillis() % 10_000) / 500.0;
        for (int i = 0; i < 6; i++) {
            double angle = time + (i * Math.PI * 2.0 / 6.0);
            double x = Math.cos(angle) * 1.8;
            double z = Math.sin(angle) * 1.8;
            double y = Math.sin(time * 2 + i) * 0.4 + 1.2;
            center.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    center.clone().add(x, y, z), 2, 0.05, 0.05, 0.05, 0.005);
        }
    }

    private void spawnLavaDrips(Location center) {
        center.getWorld().spawnParticle(Particle.DRIPPING_LAVA,
                center.clone().add(0, 2.6, 0), 4, 0.4, 0.1, 0.4, 0);
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                center.clone().add(0, 2.4, 0), 3, 0.3, 0.1, 0.3, 0.015);
    }

    // ══ Event Handlers ═════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamagePlayer(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!activeBossUuids.contains(event.getDamager().getUniqueId())) return;
        
        if (event.getDamager() instanceof Blaze boss) {
            boolean isPhase2 = boss.getHealth() <= (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() * 0.30);
            if (isPhase2) {
                // Double physical "clobber" damage in Phase 2
                event.setDamage(event.getDamage() * 2.0);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!bossEnderPearls.remove(event.getEntity().getUniqueId())) return;
        event.setCancelled(true);
        event.getEntity().remove();
        if (!(event.getHitEntity() instanceof Player player)) return;

        World world = player.getWorld();
        Location spawn = world.getSpawnLocation();
        player.teleport(spawn);
        player.playSound(spawn, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        player.sendMessage("");
        player.sendMessage("§5☠ §d§lVOID PEARL STRUCK! §7You have been banished to world spawn!");
        player.sendMessage("§7§oThe Blazefiend cackles as your soul is cast aside...");
        player.sendMessage("");
        player.sendTitle("§5☠ BANISHED!", "§7Hurry back — the boss is regenerating!", 10, 70, 20);

        for (UUID uuid : activeBossUuids) {
            Entity be = plugin.getServer().getEntity(uuid);
            if (be instanceof LivingEntity le && !le.isDead()) {
                double newHp = Math.min(le.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(),
                        le.getHealth() + PEARL_REGEN);
                le.setHealth(newHp);
                le.getWorld().spawnParticle(Particle.HEART,
                        le.getLocation().add(0, 2.5, 0), 12, 1.2, 0.5, 1.2, 0);
                le.getWorld().playSound(le.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.0f, 0.5f);
                for (Player p : nearbyPlayers(le.getLocation(), ENGAGE_RADIUS)) {
                    p.sendMessage("§c🔥 §4" + player.getName()
                            + " §cwas banished! §4The Blazefiend regenerates §c+"
                            + (int) PEARL_REGEN + " HP§4!");
                    p.sendActionBar("§c§l⚠ Boss regenerating!");
                }
            }
        }
    }

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
                p.sendMessage("§d☠ §5§lTHE DUAL BLAZE AMALGAM SPLITS! §d☠");
                p.sendMessage("§7Two fierce Blaze Infernos rise from the molten remains!");
                p.sendTitle("§5§lAMALGAM SPLIT!", "§dTwo Blaze Infernos awaken!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_SPIDER_DEATH, 1.0f, 0.5f); // Play sound
            }
            // Spawn 2 split Infernos (each with 10k HP)
            spawnBoss(loc, true);
            spawnBoss(loc.clone().add(1, 0, 1), true);
            return; // Exit early to prevent victory announcement and respawn timer
        }

        if (isBossAlive()) {
            // There is still another split boss alive — do not announce victory yet
            return;
        }

        bossUuid = null;
        cancelTask();
        bossEnderPearls.clear();

        Location loc = event.getEntity().getLocation();

        // Victory announcements
        for (Player p : nearbyPlayers(loc, ENGAGE_RADIUS * 2)) {
            p.sendMessage("");
            p.sendMessage("§6✦ §c§l🔥 THE INFERNAL BLAZEFIEND HAS BEEN SLAIN! 🔥 §6✦");
            p.sendMessage("§7The rock grows cool as the ancient fire fades...");
            p.sendMessage("§8§oThe Blazefiend will return in §715 minutes§8§o...");
            p.sendMessage("");
            p.sendTitle("§6⚔ BOSS DEFEATED!", "§7The Blazefiend falls to darkness!", 10, 80, 20);
        }

        // Award Boss Cape to nearby players who participated and did NOT die!
        for (Player p : nearbyPlayers(loc, 100.0)) {
            if (!p.hasMetadata("died_during_boss")) {
                ItemStack bossCape = itemFactory.buildBossCape();
                p.getInventory().addItem(bossCape);
                p.sendMessage("");
                p.sendMessage("§5✦ §6§l✦ BOSS CAPE AWARDED! ✦ §5✦");
                p.sendMessage("§7You defeated the legendary world boss without dying!");
                p.sendMessage("");
                p.sendTitle("§5§l✦ BOSS CAPE! ✦", "§7Awarded for flawless victory!", 10, 80, 20);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            } else {
                p.sendMessage("§c✗ §7You died during the fight, so you did not qualify for the flawless Boss Cape reward!");
            }
            p.removeMetadata("died_during_boss", plugin); // clear tag
        }

        // ── 15% chance: drop the GunZ Sword ──────────────────────────────────
        if (random.nextDouble() < GUNZ_DROP_CHANCE) {
            loc.getWorld().dropItemNaturally(loc, itemFactory.buildGunZSword());
            for (Player p : nearbyPlayers(loc, ENGAGE_RADIUS * 2)) {
                p.sendMessage("§4⚔ §c§l★ RARE DROP: §4GunZ Sword §c§l★ §8— The Blazefiend's Blade dropped!");
                p.sendTitle("§4§l★ RARE DROP!", "§c§l⚔ GunZ Sword — §7claim it fast!", 10, 80, 20);
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

        // ── Schedule 15-minute respawn ─────────────────────────────────────────
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
                p.sendMessage("§c🔥 §4The Infernal Blazefiend has re-awoken somewhere in the depths!");
                p.sendMessage("§7§oFind it by striking §6Spawner Block §7§oor §6searching the caves§7§o...");
            }
        }, RESPAWN_TICKS);
    }

    // ══ Internal helpers ════════════════════════════════════════════════════════

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
            schematicName = "crimson_pit.schem mce";
        } else if (block.getType() == Material.CRYING_OBSIDIAN) {
            schematicName = "tempest_sanctum.schem mce";
        } else if (block.getType() == Material.BLACK_CONCRETE) {
            schematicName = "void_sanctum.schem mce";
        } else {
            schematicName = "void_sanctum.schem mce"; // fallback
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
            
            // Teleport exactly to block center so paste -o aligns perfectly
            Location pasteLoc = spawnerLoc.clone().add(0.5, 1.0, 0.5);
            player.teleport(pasteLoc);
            
            // Execute WorldEdit commands with a delay, and restore position
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                finalPlayer.performCommand("schematic load " + finalSchem);
                finalPlayer.performCommand("/paste");
                
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
                    }
                }, 3L);
            }, 1L);
        }
    }

    public Phantom spawnTempestOverlord(Location loc) {
        if (loc == null) {
            World w = plugin.getServer().getWorld("ancient_realm");
            if (w == null) {
                w = plugin.getServer().getWorlds().get(0);
            }
            loc = new org.bukkit.Location(w, 114.924, -38.0, -47.278);
        }

        // Rebuild Tempest Sanctum before spawning to repair any block breaks
        rebuildArena(null, loc);

        // Spawn a colossal Phantom as the Tempest Overlord (looks amazing and wind/sky themed!)
        Phantom phantom = (Phantom) loc.getWorld().spawnEntity(loc, EntityType.PHANTOM);
        phantom.setCustomName("§5⚡ §l§dThe Tempest Overlord");
        phantom.setCustomNameVisible(true);
        phantom.setSize(18); // Giant sky dragon size
        phantom.setRemoveWhenFarAway(false);

        var hp = phantom.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(400.0);
            phantom.setHealth(Math.min(400.0, hp.getValue()));
        } else {
            phantom.setHealth(400.0);
        }

        // Register with effect system (Shriek, Leached, etc.)
        bossEffectListener.registerBoss(phantom);
        bossEffectListener.spawnShriek(phantom);

        // Announce to all players in range
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) > 120) continue;
            p.sendMessage("");
            p.sendMessage("§5⚡ §b§l⛈ THE TEMPEST OVERLORD HAS AWAKENED! ⛈ §5⚡");
            p.sendMessage("§7The Tempest Sanctum §5crackles§7 with deadly storm energy!");
            p.sendMessage("§6⚠ §eBring §7Air §eand §7Water §emagic — lightning is everywhere!");
            p.sendMessage("§5⚠ §dDestroy its §5Shriek §5⚡ §dwith an §bAir Staff §dto expose its weakness!");
            p.sendMessage("");
            p.sendTitle("§5§l⚡ BOSS AWAKENS!", "§7§oThe Tempest Overlord roars...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        // Dramatic lightning entrance ring
        final Location finalLoc = loc;
        for (int i = 0; i < 10; i++) {
            final int fi = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double angle = fi * Math.PI * 2.0 / 10.0;
                finalLoc.getWorld().strikeLightningEffect(finalLoc.clone().add(
                        Math.cos(angle) * 7, 0, Math.sin(angle) * 7));
            }, fi * 3L);
        }

        // Extra lightning bolts 1 second later for drama
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (int i = 0; i < 4; i++) {
                final int fi = i;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    double dx = (random.nextDouble() - 0.5) * 12;
                    double dz = (random.nextDouble() - 0.5) * 12;
                    finalLoc.getWorld().strikeLightningEffect(finalLoc.clone().add(dx, 0, dz));
                }, fi * 5L);
            }
        }, 20L);

        return phantom;
    }

    @EventHandler
    public void onWitherExplode(org.bukkit.event.entity.ExplosionPrimeEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof WitherSkull || ent instanceof Wither) {
            if (ent.getWorld().getName().equals("void_realm") || ent.getWorld().getName().equals("ancient_realm")) {
                if (random.nextDouble() < 0.01) {
                    Warden warden = (Warden) ent.getWorld().spawnEntity(ent.getLocation(), EntityType.WARDEN);
                    warden.setCustomName("§0§lVoid Warden");
                    warden.setCustomNameVisible(true);
                    ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 1.0f);
                    for (Player p : ent.getWorld().getPlayers()) {
                        p.sendMessage("§0☠ §lA Void Warden has erupted from the Zurion explosion!");
                        p.sendTitle("§0§lWARDEN EMERGES!", "§7Freeze spells are crucial now!", 10, 70, 20);
                    }
                }
            }
        }
    }

    public Wither spawnVoidWither(Location loc) {
        if (loc == null) {
            World w = plugin.getServer().getWorld("void_realm");
            if (w == null) {
                w = plugin.getServer().getWorld("ancient_realm");
            }
            if (w == null) {
                w = plugin.getServer().getWorlds().get(0);
            }
            loc = new Location(w, 0.0, 64.0, 0.0);
        }

        // Rebuild Void Realm before spawning
        rebuildArena(null, loc);

        // Spawn Main Wither
        Wither wither = (Wither) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
        wither.setCustomName("§0§lThe Void Zurion");
        wither.setCustomNameVisible(true);
        wither.setRemoveWhenFarAway(false);

        var hp = wither.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(1000.0);
            wither.setHealth(Math.min(1000.0, hp.getValue()));
        } else {
            wither.setHealth(1000.0);
        }

        // Spawn Inverted Passenger Wither (Dinnerbone)
        Wither inverted = (Wither) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
        inverted.setCustomName("Dinnerbone");
        inverted.setCustomNameVisible(false);
        inverted.setRemoveWhenFarAway(false);
        var ihp = inverted.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        if (ihp != null) {
            ihp.setBaseValue(1000.0);
            inverted.setHealth(Math.min(1000.0, ihp.getValue()));
        } else {
            inverted.setHealth(1000.0);
        }

        wither.addPassenger(inverted);

        // Register with effect system (Shriek, Leached, etc.)
        bossEffectListener.registerBoss(wither);
        bossEffectListener.registerBoss(inverted);
        bossEffectListener.spawnShriek(wither);

        // Spawn 3 floating orbiting skull ArmorStands
        List<ArmorStand> heads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArmorStand head = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
                s.setMarker(true);
                s.setInvisible(true);
                s.setGravity(false);
                s.setSmall(true);
                s.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
            });
            heads.add(head);
        }

        // Run dedicated Custom AI task for the Void Zurion
        new BukkitRunnable() {
            int aiTick = 0;

            @Override
            public void run() {
                if (wither.isDead() || !wither.isValid()) {
                    if (inverted.isValid()) inverted.remove();
                    for (ArmorStand h : heads) if (h.isValid()) h.remove();
                    cancel();
                    return;
                }

                aiTick++;

                // ── Update orbiting heads position ─────────────────────────
                for (int i = 0; i < heads.size(); i++) {
                    ArmorStand head = heads.get(i);
                    if (head.isValid()) {
                        double angle = (aiTick * 0.08) + (i * Math.PI * 2.0 / 3.0);
                        Location headLoc = wither.getLocation().clone().add(
                            Math.cos(angle) * 3.0, 
                            1.5 + Math.sin(aiTick * 0.15 + i) * 0.4, 
                            Math.sin(angle) * 3.0
                        );
                        head.teleport(headLoc);
                    }
                }

                if (wither.getTarget() instanceof Player target) {
                    // ── Fireball attack (every 3 seconds) ────────────────────
                    if (aiTick % 60 == 0) {
                        Location origin = wither.getLocation().add(0, 2.5, 0);
                        Vector dir = target.getLocation().toVector().subtract(origin.toVector()).normalize();
                        LargeFireball fb = (LargeFireball) wither.getWorld().spawnEntity(origin.add(dir.multiply(1.5)), EntityType.FIREBALL);
                        fb.setShooter(wither);
                        fb.setDirection(dir.multiply(1.5));
                        fb.setYield(0.0f); // Protect arena blocks from breaking
                        fb.setIsIncendiary(false);
                    }

                    // ── Warden Charge Leap AI (every 8 seconds) ──────────────
                    if (aiTick % 160 == 0) {
                        wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.5f, 0.8f);
                        for (Player p : wither.getWorld().getPlayers()) {
                            if (p.getLocation().distance(wither.getLocation()) < 80) {
                                p.sendMessage("§0☠ §lThe Void Zurion is charging!");
                                p.sendTitle("§0§lZURION CHARGES!", "§7Get out of the way!", 5, 30, 5);
                            }
                        }
                        Vector chargeDir = target.getLocation().toVector().subtract(wither.getLocation().toVector()).setY(0.2).normalize();
                        wither.setVelocity(chargeDir.multiply(1.5));
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        // Announce to all players in range
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) > 120) continue;
            p.sendMessage("");
            p.sendMessage("§0☠ §lThe Void Zurion has awakened in the Void Realm! §0☠");
            p.sendMessage("§7The Void Realm §8trembles§7 as inverted dark forces accumulate!");
            p.sendMessage("§5⚠ §dDestroy its §5Shriek §5⚡ §dwith an §bAir Staff §dto expose its weakness!");
            p.sendMessage("");
            p.sendTitle("§0§lZURION AWAKENS!", "§7§oThe double Wither roars...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        return wither;
    }

    private void announceSpawn(Location loc) {
        for (Player p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distance(loc) > ENGAGE_RADIUS * 3) continue;
            p.sendMessage("");
            p.sendMessage("§4☠ §c§l🔥 THE INFERNAL BLAZEFIEND HAS AWAKENED! 🔥 §4☠");
            p.sendMessage("§7It carves through the rock — §4HUNT IT DOWN§7 in the caves!");
            p.sendMessage("§6⚠ §eBring §bWater §eand §aEarth §emagic — it WILL find you first!");
            p.sendMessage("");
            p.sendTitle("§c§l🔥 BOSS AWAKENS!", "§7§oSearch the caves...", 10, 70, 20);
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
