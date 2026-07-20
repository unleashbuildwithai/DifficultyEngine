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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
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
            Material.END_PORTAL
    );

    private final JavaPlugin         plugin;
    private final ItemFactory        itemFactory;
    private final BossEffectListener bossEffectListener;
    private final Random             random = new Random();

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
    }

    // ══ Public API ═══════════════════════════════════════════════════════════

    /**
     * Spawns (or replaces) the boss.
     * 1% chance the spawn is a Legendary variant with 25 000 HP instead of 2 500.
     *
     * @param loc Spawn location.  If null, uses the default Crimson Pit coords.
     */
    public Blaze spawnBoss(Location loc) {
        if (loc == null) {
            World w = plugin.getServer().getWorlds().get(0);
            loc = new Location(w, SPAWN_X, SPAWN_Y, SPAWN_Z);
        }

        dismissExisting();

        // ── Legendary roll ────────────────────────────────────────────────────
        boolean legendary = random.nextDouble() < LEGENDARY_CHANCE;
        double  hp        = legendary ? LEGENDARY_HP : BOSS_MAX_HP;

        Blaze boss = (Blaze) loc.getWorld().spawnEntity(loc, EntityType.BLAZE);

        if (legendary) {
            boss.setCustomName("§4☠ §c§l⚡ THE LEGENDARY BLAZEFIEND ⚡ §4☠");
            // Server-wide legendary announcement
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                online.sendMessage("");
                online.sendMessage("§4§l⚡ ☠ §c§lLEGENDARY BLAZEFIEND HAS AWAKENED! §4§l☠ ⚡");
                online.sendMessage("§7§oA once-in-a-hundred terror rises from the Crimson Pit...");
                online.sendMessage("§6§o  §e25 000 HP §6— §cBring everything you have!");
                online.sendMessage("");
                online.sendTitle("§4§l⚡ LEGENDARY BOSS ⚡", "§c25 000 HP — §6Crimson Pit!", 10, 100, 20);
            }
        } else {
            boss.setCustomName("§c🔥 §l§4The Infernal Blazefiend");
        }

        boss.setCustomNameVisible(true);
        boss.setRemoveWhenFarAway(false);
        boss.setFireTicks(Integer.MAX_VALUE);

        // Boost HP
        var hpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(hp);
        boss.setHealth(hp);

        // Register with effect system
        bossEffectListener.registerBoss(boss);
        bossEffectListener.spawnShriek(boss);

        bossUuid = boss.getUniqueId();

        startBossAI();
        if (!legendary) announceSpawn(loc); // legendary already announced globally above
        thunderEntrance(loc, loc.getWorld());

        return boss;
    }

    /** Convenience overload — uses default Crimson Pit coords. */
    public Blaze spawnBoss() { return spawnBoss(null); }

    public boolean isBossAlive() {
        if (bossUuid == null) return false;
        Entity e = plugin.getServer().getEntity(bossUuid);
        return e instanceof LivingEntity le && !le.isDead();
    }

    public void cleanup() {
        cancelTask();
        cancelRespawn();
        bossEnderPearls.clear();
    }

    // ══ Ancient Debris Trigger ════════════════════════════════════════════════

    /**
     * When a player STRIKES (left-click damages) an Ancient Debris block and
     * the boss is not currently alive, spawn it near that block.
     *
     * This also announces to the player what they just triggered.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDebrisStrike(BlockDamageEvent event) {
        if (event.getBlock().getType() != Material.ANCIENT_DEBRIS) return;
        if (isBossAlive()) {
            // Boss is already roaming — warn the player
            event.getPlayer().sendActionBar(
                    "§c🔥 §7The Infernal Blazefiend already roams these caves...");
            return;
        }
        if (respawnTask != null) {
            // Boss died recently, timer is counting down — accelerate spawn
            cancelRespawn();
            event.getPlayer().sendMessage(
                    "§c☠ §4Disturbing the Ancient Debris awakens the Blazefiend early!");
        } else {
            event.getPlayer().sendMessage(
                    "§c☠ §4Your strike on the Ancient Debris has awakened the Infernal Blazefiend!");
        }
        // Always spawn at the Crimson Pit home location (-107.964, -26, -14.444)
        // regardless of which debris block was struck — this is a world boss.
        spawnBoss(null);
    }

    // ══ AI Task ═══════════════════════════════════════════════════════════════

    private void startBossAI() {
        cancelTask();

        bossTask = new BukkitRunnable() {
            int   tick         = 0;
            int   wanderTick   = 0;
            // Current wander target velocity
            Vector wanderVel   = new Vector(0, 0, 0);

            @Override
            public void run() {
                if (!isBossAlive()) { cancel(); return; }

                Entity entity = plugin.getServer().getEntity(bossUuid);
                if (!(entity instanceof Blaze boss)) { cancel(); return; }

                tick++;
                wanderTick++;

                // Keep burning
                boss.setFireTicks(200);

                // ── Wandering AI (every WANDER_INTERVAL ticks) ───────────────
                if (wanderTick >= WANDER_INTERVAL) {
                    wanderTick = 0;
                    pickNewWanderTarget(boss);
                }

                // Apply wander velocity (smooth movement)
                if (!wanderVel.isZero()) {
                    Vector current = boss.getVelocity();
                    // Blend toward target velocity for smooth motion
                    Vector blended = current.add(wanderVel.clone().subtract(current).multiply(0.15));
                    boss.setVelocity(blended);
                }

                // ── Block-breaking in path (every 5 ticks) ───────────────────
                if (tick % 5 == 0) breakBlocksInPath(boss);

                // ── Flame particles (every 5 ticks) ──────────────────────────
                if (tick % 5 == 0) spawnFlameSpiral(boss.getLocation());
                if (tick % 10 == 0) spawnLavaDrips(boss.getLocation());

                List<Player> nearby = nearbyPlayers(boss.getLocation(), ENGAGE_RADIUS);
                if (nearby.isEmpty()) return;

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
                wanderVel = new Vector(vx, vy, vz);
                if (wanderVel.length() > 0.6) wanderVel.normalize().multiply(0.6);
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
            ThrownEnderpearl pearl = (ThrownEnderpearl) boss.getWorld()
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
            var hpAttr = minion.getAttribute(Attribute.MAX_HEALTH);
            if (hpAttr != null) hpAttr.setBaseValue(40.0);
            minion.setHealth(40.0);
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

    private void spawnLavaDrips(Location center) {
        center.getWorld().spawnParticle(Particle.DRIPPING_LAVA,
                center.clone().add(0, 2.6, 0), 4, 0.4, 0.1, 0.4, 0);
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                center.clone().add(0, 2.4, 0), 3, 0.3, 0.1, 0.3, 0.015);
    }

    // ══ Event Handlers ═════════════════════════════════════════════════════════

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

        if (isBossAlive()) {
            Entity be = plugin.getServer().getEntity(bossUuid);
            if (be instanceof LivingEntity le) {
                double newHp = Math.min(le.getAttribute(Attribute.MAX_HEALTH).getValue(),
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (bossUuid == null) return;
        if (!event.getEntity().getUniqueId().equals(bossUuid)) return;

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
            World world = plugin.getServer().getWorlds().get(0);
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
                p.sendMessage("§7§oFind it by striking §6Ancient Debris §7§oor §6searching the caves§7§o...");
            }
        }, RESPAWN_TICKS);
    }

    // ══ Internal helpers ════════════════════════════════════════════════════════

    private void dismissExisting() {
        if (bossUuid == null) return;
        Entity e = plugin.getServer().getEntity(bossUuid);
        if (e != null && !e.isDead()) e.remove();
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

    private void thunderEntrance(Location loc, World world) {
        for (int i = 0; i < 8; i++) {
            final int fi = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                double angle = fi * Math.PI * 2.0 / 8.0;
                world.strikeLightningEffect(loc.clone().add(
                        Math.cos(angle) * 5, 0, Math.sin(angle) * 5));
            }, fi * 4L);
        }
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
