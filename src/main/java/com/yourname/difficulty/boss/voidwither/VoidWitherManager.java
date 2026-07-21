package com.yourname.difficulty.boss.voidwither;

import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.CrimsonBossManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * VoidWitherManager — manages the terrifying §0§lVoid Zurion§r boss.
 *
 * ── Clean custom-entity architecture ────────────────────────────────────────
 * Instead of stacking two vanilla Withers on top of each other (the old
 * "double Wither" hack), the Zurion is now built as:
 *
 *  1. §7Physics carrier§r — a single completely §finvisible, silent§r
 *     {@link Wither} used ONLY for its native flight AI, hitbox, damage,
 *     and death handling. Its vanilla three-skulled model is never shown.
 *
 *  2. §7Visual display§r — an {@link ItemDisplay} entity themed around the
 *     void/abyss (a large billboarded dark orb with orbiting skull props),
 *     independent of any vanilla Wither silhouette.
 */
public class VoidWitherManager implements Listener {

    /** Visual scale of the Zurion's void-orb display. */
    private static final float ZURION_DISPLAY_SCALE = 5.5f;

    private final JavaPlugin plugin;
    private final BossEffectListener bossEffectListener;
    private final CrimsonBossManager crimsonBossManager;
    private final Random random = new Random();

    /** Carrier UUID → paired visual display UUID (position-synced every tick). */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    private final NamespacedKey displayTagKey;

    public VoidWitherManager(JavaPlugin plugin, BossEffectListener bossEffectListener, CrimsonBossManager crimsonBossManager) {
        this.plugin = plugin;
        this.bossEffectListener = bossEffectListener;
        this.crimsonBossManager = crimsonBossManager;
        this.displayTagKey = new NamespacedKey(plugin, "de_zurion_display");

        // Position-sync task: streams each carrier's live location to its display
        new BukkitRunnable() {
            @Override
            public void run() {
                if (carrierToDisplay.isEmpty()) return;
                Iterator<Map.Entry<UUID, UUID>> it = carrierToDisplay.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, UUID> entry = it.next();
                    Entity carrier = plugin.getServer().getEntity(entry.getKey());
                    Entity display = plugin.getServer().getEntity(entry.getValue());

                    if (!(carrier instanceof Wither w) || w.isDead() || !w.isValid()) {
                        if (display != null && !display.isDead()) display.remove();
                        it.remove();
                        continue;
                    }
                    if (display == null || display.isDead() || !display.isValid()) {
                        it.remove();
                        continue;
                    }
                    Location target = carrier.getLocation().clone().add(0, 1.0, 0);
                    if (!display.getLocation().getWorld().equals(target.getWorld())
                            || display.getLocation().distanceSquared(target) > 0.0004) {
                        display.teleport(target);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
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

        // ── 1. Single invisible physics carrier (no stacked vanilla Withers) ────
        Wither wither = (Wither) loc.getWorld().spawnEntity(loc, EntityType.WITHER);
        wither.setCustomNameVisible(false); // name lives on the display instead
        wither.setRemoveWhenFarAway(false);
        wither.setInvisible(true);
        wither.setSilent(true);

        var hp = wither.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(2000.0); // combined HP of the old double-wither stack
            wither.setHealth(Math.min(2000.0, hp.getValue()));
        } else {
            wither.setHealth(2000.0);
        }

        // Register with effect system (Shriek, Leached, etc.)
        bossEffectListener.registerBoss(wither);
        bossEffectListener.spawnShriek(wither);

        // ── 2. Custom void/abyss visual display (billboarded, no vanilla model) ──
        ItemDisplay display = spawnZurionDisplay(wither);
        carrierToDisplay.put(wither.getUniqueId(), display.getUniqueId());

        // Spawn 3 floating orbiting skull ArmorStands (purely cosmetic props)
        List<ArmorStand> heads = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ArmorStand head = loc.getWorld().spawn(loc, ArmorStand.class, s -> {
                s.setMarker(true);
                s.setInvisible(true);
                s.setGravity(false);
                s.setSmall(true);
                s.getEquipment().setHelmet(new ItemStack(Material.WITHER_SKELETON_SKULL));
                s.setPersistent(false);
            });
            heads.add(head);
        }

        // Run dedicated Custom AI task for the Void Zurion
        new BukkitRunnable() {
            int aiTick = 0;

            @Override
            public void run() {
                if (wither.isDead() || !wither.isValid()) {
                    for (ArmorStand h : heads) if (h.isValid()) h.remove();
                    UUID displayUuid = carrierToDisplay.remove(wither.getUniqueId());
                    if (displayUuid != null) {
                        Entity d = plugin.getServer().getEntity(displayUuid);
                        if (d != null && !d.isDead()) d.remove();
                    }
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

                // ── Ambient void particles around the display ────────────────
                if (aiTick % 4 == 0) {
                    wither.getWorld().spawnParticle(Particle.SOUL, wither.getLocation().add(0, 1.5, 0), 8, 1.0, 1.0, 1.0, 0.02);
                    wither.getWorld().spawnParticle(Particle.PORTAL, wither.getLocation().add(0, 1.5, 0), 10, 1.2, 1.2, 1.2, 0.05);
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
            p.sendTitle("§0§lZURION AWAKENS!", "§7§oThe abyss stirs...", 10, 70, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
        }

        return wither;
    }

    /**
     * Builds the Void Zurion's custom void/abyss visual — a large
     * billboarded ItemDisplay, entirely independent from the invisible
     * Wither carrier underneath (no stacked/inverted vanilla Wither models).
     */
    private ItemDisplay spawnZurionDisplay(Wither carrier) {
        ItemStack visual = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = visual.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(2002); // reserved model id for Void Zurion — resource pack can reskin freely
            visual.setItemMeta(meta);
        }

        ItemDisplay display = carrier.getWorld().spawn(carrier.getLocation().add(0, 1.0, 0), ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setCustomName("§0§lThe Void Zurion");
            d.setCustomNameVisible(true);
            d.setBrightness(new Display.Brightness(15, 15));

            Transformation t = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(ZURION_DISPLAY_SCALE, ZURION_DISPLAY_SCALE, ZURION_DISPLAY_SCALE),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            );
            d.setTransformation(t);

            d.getPersistentDataContainer().set(displayTagKey, PersistentDataType.BYTE, (byte) 1);
        });

        return display;
    }

    // ── Void Boss (Wither) & Warden peaceful team non-hostility alliance ──────
    // (kept in BossEffectListener for shared logic across boss types)
}
