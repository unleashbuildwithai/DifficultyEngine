package com.yourname.difficulty.boss.crimson;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.boss.CrimsonBossManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * CrimsonBossAttacks — handles all the devastating spells, passive scorch, particles, and projectile events
 * of §c🔥 The Infernal Blazefiend§r.
 */
public class CrimsonBossAttacks implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory;
    private final CrimsonBossManager bossManager;
    private final Random random = new Random();

    private static final double ENGAGE_RADIUS = 80.0;
    private static final double SCORCH_RADIUS = 6.0;
    private static final double PEARL_REGEN = 30.0;

    public CrimsonBossAttacks(JavaPlugin plugin, ItemFactory itemFactory, CrimsonBossManager bossManager) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.bossManager = bossManager;
    }

    // ══ Attack Triggers (called from CrimsonBossManager AI task) ════════════════

    public void attackHomingHellfire(Blaze boss, List<Player> players) {
        int targeted = 0;
        for (Player target : players) {
            if (targeted >= 3) break;
            targeted++;

            Location origin = boss.getLocation().add(0, 1.5, 0);
            Location dest = target.getLocation().add(0, 1.2, 0);
            Vector dir = vecTo(origin, dest).normalize();

            Fireball fb = (Fireball) boss.getWorld().spawnEntity(
                    origin.clone().add(dir.clone().multiply(1.5)), EntityType.FIREBALL);
            fb.setShooter(boss);
            fb.setDirection(dir.multiply(1.2));
            fb.setYield(1.2f);
            fb.setIsIncendiary(true);
            target.sendActionBar("§c🔥 §7Homing Hellfire incoming! Dodge!");

            for (int i = 1; i <= 2; i++) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!bossManager.isBossAlive()) return;
                    Entity bossNow = plugin.getServer().getEntity(bossManager.getBossUuid());
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

    public void attackMoltenBarrage(Blaze boss, List<Player> players) {
        for (Player p : players) {
            p.sendMessage("§c☠ §4MOLTEN BARRAGE! §7Use §bWater §7or §aEarth §7magic to resist!");
            p.sendTitle("§c🌋", "§4Lava eruption incoming!", 3, 20, 5);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!bossManager.isBossAlive()) return;
            Entity be = plugin.getServer().getEntity(bossManager.getBossUuid());
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
                        target.getWorld().spawnParticle(Particle.LAVA, pt, 2, 0.2, 0.05, 0.2, 0);
                        target.getWorld().spawnParticle(Particle.FLAME, pt, 3, 0.2, 0.2, 0.2, 0.04);
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

    public void attackVoidPearls(Blaze boss, List<Player> players) {
        for (Player p : players)
            p.sendMessage("§5☠ §d§lVOID PEARLS! §7If they hit you, you'll be banished to spawn!");
        int thrown = 0;
        for (Player target : players) {
            if (thrown >= 3) break;
            thrown++;
            Location origin = boss.getLocation().add(0, 1.8, 0);
            Location dest = target.getLocation().add(0, 1.2, 0);
            Vector dir = vecTo(origin, dest).normalize().add(new Vector(0, 0.25, 0));
            EnderPearl pearl = (EnderPearl) boss.getWorld()
                    .spawnEntity(origin, EntityType.ENDER_PEARL);
            pearl.setShooter(boss);
            pearl.setVelocity(dir.multiply(1.6));
            bossManager.getBossEnderPearls().add(pearl.getUniqueId());
            target.sendActionBar("§5⚠ §dVoid Pearl incoming! DODGE!");
        }
    }

    public void attackSummonBlobs(Blaze boss, List<Player> players) {
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

    public void attackSummonMinions(Blaze boss, List<Player> players) {
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

    public void attackScorch(Blaze boss, List<Player> players) {
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

    // ══ Particle FX helpers ════════════════════════════════════════════════════

    public void spawnFlameSpiral(Location center) {
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

    public void spawnSoulFlameSpiral(Location center) {
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

    public void spawnLavaDrips(Location center) {
        center.getWorld().spawnParticle(Particle.DRIPPING_LAVA,
                center.clone().add(0, 2.6, 0), 4, 0.4, 0.1, 0.4, 0);
        center.getWorld().spawnParticle(Particle.LARGE_SMOKE,
                center.clone().add(0, 2.4, 0), 3, 0.3, 0.1, 0.3, 0.015);
    }

    // ══ Event Handlers ═════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamagePlayer(EntityDamageByEntityEvent event) {
        if (!bossManager.getActiveBossUuids().contains(event.getDamager().getUniqueId())) return;

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
        if (!bossManager.getBossEnderPearls().remove(event.getEntity().getUniqueId())) return;
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

        for (UUID uuid : bossManager.getActiveBossUuids()) {
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

    // ══ Utility Methods ════════════════════════════════════════════════════════

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
