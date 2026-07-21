package com.yourname.difficulty.boss.voidwither;

import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.CrimsonBossManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * VoidWitherManager — manages the terrifying §0§lVoid Zurion§r (Void Wither) boss and its explosions.
 */
public class VoidWitherManager implements Listener {

    private final JavaPlugin plugin;
    private final BossEffectListener bossEffectListener;
    private final CrimsonBossManager crimsonBossManager;
    private final Random random = new Random();

    public VoidWitherManager(JavaPlugin plugin, BossEffectListener bossEffectListener, CrimsonBossManager crimsonBossManager) {
        this.plugin = plugin;
        this.bossEffectListener = bossEffectListener;
        this.crimsonBossManager = crimsonBossManager;
    }

    @EventHandler
    public void onWitherExplode(ExplosionPrimeEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof WitherSkull || ent instanceof Wither) {
            if (ent.getWorld().getName().equals("void_realm") || ent.getWorld().getName().equals("ancient_realm")) {
                // Balanced Warden spawn rate: halved to 0.5% for normal boss, tripled to 1.5% for rare bosses
                double chance = 0.005;
                for (Entity nearby : ent.getNearbyEntities(80, 80, 80)) {
                    if (nearby.hasMetadata("de_is_dual_amalgam") || nearby.hasMetadata("de_is_vortex") || nearby.hasMetadata("de_is_inferno")) {
                        chance = 0.015; // triple spawn chance on rare bosses!
                        break;
                    }
                }

                if (random.nextDouble() < chance) {
                    Warden warden = (Warden) ent.getWorld().spawnEntity(ent.getLocation(), EntityType.WARDEN);
                    warden.setCustomName("§5§lVoid Warden");
                    warden.setCustomNameVisible(true);

                    // Assign random attack-type damage negation immunity
                    String[] immunities = { "MELEE", "RANGED", "FIRE" };
                    String chosen = immunities[random.nextInt(immunities.length)];
                    warden.setMetadata("de_damage_negation", new FixedMetadataValue(plugin, chosen));

                    ent.getWorld().playSound(ent.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 1.0f);
                    for (Player p : ent.getWorld().getPlayers()) {
                        p.sendMessage("§d☠ §lA Void Warden has erupted from the Zurion explosion!");
                        p.sendTitle("§d§lWARDEN EMERGES!", "§7Freeze spells are crucial now!", 10, 70, 20);
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
        crimsonBossManager.rebuildArena(null, loc);

        // Spawn Main Wither
        Wither wither = (Wither) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
        wither.setCustomName("§0§lThe Void Zurion");
        wither.setCustomNameVisible(true);
        wither.setRemoveWhenFarAway(false);

        var hp = wither.getAttribute(Attribute.GENERIC_MAX_HEALTH);
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
        var ihp = inverted.getAttribute(Attribute.GENERIC_MAX_HEALTH);
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
}
