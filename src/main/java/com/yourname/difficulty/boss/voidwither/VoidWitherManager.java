package com.yourname.difficulty.boss.voidwither;

import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.CrimsonBossManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
 * ── Clean custom-entity architecture ─────────────────────────────────────
 * Rather than stacking two vanilla Withers (the old "double Wither" hack),
 * the Zurion is built from an invisible/silent {@link Wither} "physics
 * carrier" (native flight AI, hitbox, damage/death handling only — model
 * never shown) paired with a billboarded {@link ItemDisplay} void-orb visual
 * with orbiting skull props, entirely independent of the vanilla silhouette.
 *
 * ── Key fixes ─────────────────────────────────────────────────────────────
 * Custom flight AI + active block-shattering prevents the carrier getting
 * physically stuck when {@code mobGriefing} is off. A manually-driven
 * {@link BossBar} covers for the fact invisible entities don't reliably show
 * the vanilla Wither health bar. Only ONE Zurion may be alive at a time, and
 * the Warden-on-explosion roll is gated to once per 5 minutes with only one
 * bonus Void Warden alive at a time.
 */
public class VoidWitherManager implements Listener {

    /** Visual scale of the Zurion's custom void_boss model display. */
    private static final float ZURION_DISPLAY_SCALE = 5.5f;
    /** How fast the void_boss model slowly rotates in place (radians per tick). */
    private static final double ZURION_SPIN_SPEED = 0.02;


    /** Minimum time between Void Warden spawn rolls while the boss is alive. */
    private static final long WARDEN_ROLL_COOLDOWN_MS = 5L * 60L * 1000L; // 5 minutes

    private final JavaPlugin plugin;
    private final BossEffectListener bossEffectListener;
    private final CrimsonBossManager crimsonBossManager;
    private final Random random = new Random();

    /** Carrier UUID → paired visual display UUID (position-synced every tick). */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    /** Carrier UUID → live health BossBar shown to nearby players. */
    private final Map<UUID, BossBar> healthBars = new HashMap<>();
    /** Carrier UUID → current spin angle (radians), advanced each sync tick. */
    private final Map<UUID, Double> spinAngles = new HashMap<>();
    private final NamespacedKey displayTagKey;


    /** Only one Zurion (Wither carrier) may be alive at a time. */
    private UUID activeZurionUuid = null;

    /** Only one bonus Void Warden may be alive at a time, and only one roll per cooldown window. */
    private UUID activeWardenUuid = null;
    private long lastWardenRollTime = 0L;

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
                        spinAngles.remove(entry.getKey());
                        continue;
                    }
                    if (!(display instanceof ItemDisplay id) || display.isDead() || !display.isValid()) {
                        it.remove();
                        spinAngles.remove(entry.getKey());
                        continue;
                    }
                    Location target = carrier.getLocation().clone().add(0, 1.0, 0);
                    if (!display.getLocation().getWorld().equals(target.getWorld())
                            || display.getLocation().distanceSquared(target) > 0.0004) {
                        display.teleport(target);
                    }

                    double angle = spinAngles.getOrDefault(entry.getKey(), 0.0) + ZURION_SPIN_SPEED;
                    spinAngles.put(entry.getKey(), angle);
                    com.yourname.difficulty.boss.BossDisplayUtil.setYawRotation(id, ZURION_DISPLAY_SCALE, (float) angle);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }


    /** Returns true if the given living entity is still alive/valid. */
    private boolean isAliveEntity(UUID uuid) {
        if (uuid == null) return false;
        Entity e = plugin.getServer().getEntity(uuid);
        return e instanceof LivingEntity le && !le.isDead() && le.isValid();
    }

    @EventHandler
    public void onWitherExplode(ExplosionPrimeEvent event) {
        Entity ent = event.getEntity();
        if (ent instanceof WitherSkull || ent instanceof Wither) {
            if (ent.getWorld().getName().equals("void_realm") || ent.getWorld().getName().equals("ancient_realm")) {

                // ── Hard gates: at most 1 alive bonus Warden, and only 1 roll per 5 minutes ──
                if (isAliveEntity(activeWardenUuid)) return; // a Warden from this boss is already alive
                long now = System.currentTimeMillis();
                if (now - lastWardenRollTime < WARDEN_ROLL_COOLDOWN_MS) return; // still on cooldown
                lastWardenRollTime = now; // consume this roll window regardless of outcome

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
                    activeWardenUuid = warden.getUniqueId();

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
        // ── Hard gate: only ONE Void Zurion may exist at a time ─────────────────
        if (isAliveEntity(activeZurionUuid)) {
            Entity existing = plugin.getServer().getEntity(activeZurionUuid);
            if (existing instanceof Wither w) {
                for (Player p : w.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(w.getLocation()) <= 14400.0) {
                        p.sendActionBar("§0☠ §7The Void Zurion already roams this realm — it cannot be duplicated.");
                    }
                }
                return w;
            }
        }

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
        wither.setInvulnerable(false); // safety: never let residual invulnerability persist
        wither.setAI(true);            // ensure native flight AI stays enabled

        activeZurionUuid = wither.getUniqueId();

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

        // ── Manual health BossBar (invisible entities don't reliably show the
        // vanilla Wither boss-bar UI to the client) ─────────────────────────────
        BossBar healthBar = Bukkit.createBossBar("§0☠ §lThe Void Zurion", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        healthBar.setProgress(1.0);
        healthBars.put(wither.getUniqueId(), healthBar);

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
                    BossBar bar = healthBars.remove(wither.getUniqueId());
                    if (bar != null) bar.removeAll();
                    if (wither.getUniqueId().equals(activeZurionUuid)) activeZurionUuid = null;
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

                // ── Update health BossBar + nearby player visibility ─────────
                BossBar bar = healthBars.get(wither.getUniqueId());
                if (bar != null) {
                    AttributeInstance maxHpAttr = wither.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double maxHp = maxHpAttr != null ? maxHpAttr.getValue() : 2000.0;
                    double pct = Math.max(0.0, Math.min(1.0, wither.getHealth() / maxHp));
                    bar.setProgress(pct);
                    bar.setTitle("§0☠ §lThe Void Zurion §7— §f" + (int) Math.ceil(wither.getHealth())
                            + " §7/ §f" + (int) Math.round(maxHp));

                    Set<Player> shouldSee = new HashSet<>();
                    for (Player p : wither.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(wither.getLocation()) <= 14400.0) { // 120 blocks
                            shouldSee.add(p);
                        }
                    }
                    for (Player p : new ArrayList<>(bar.getPlayers())) {
                        if (!shouldSee.contains(p)) bar.removePlayer(p);
                    }
                    for (Player p : shouldSee) bar.addPlayer(p);
                }

                Player target = null;
                if (wither.getTarget() instanceof Player t) {
                    target = t;
                } else {
                    // No native target acquired (can happen if walls block line-of-sight) —
                    // actively find + set the nearest player so Zurion never idles.
                    double closest = 100.0;
                    for (Player p : wither.getWorld().getPlayers()) {
                        if (p.isDead()) continue;
                        double d = p.getLocation().distance(wither.getLocation());
                        if (d < closest) { closest = d; target = p; }
                    }
                    if (target != null) wither.setTarget(target);
                }

                if (target != null) {
                    // ── Active flight pull toward target (never gets "stuck idle") ──
                    Location pLoc = target.getLocation().add(0, 1.5, 0);
                    Vector dir = pLoc.toVector().subtract(wither.getLocation().toVector());
                    double dist = dir.length();
                    if (dist > 4.0) {
                        dir.normalize();
                        double pullSpeed = Math.min(0.6, 0.15 + dist * 0.01);
                        Vector blended = wither.getVelocity().add(
                                dir.multiply(pullSpeed).subtract(wither.getVelocity()).multiply(0.25));
                        wither.setVelocity(blended);
                    }

                    // ── Fireball attack (every 3 seconds) ────────────────────
                    if (aiTick % 60 == 0) {
                        Location origin = wither.getLocation().add(0, 2.5, 0);
                        Vector fdir = target.getLocation().toVector().subtract(origin.toVector()).normalize();
                        LargeFireball fb = (LargeFireball) wither.getWorld().spawnEntity(origin.add(fdir.multiply(1.5)), EntityType.FIREBALL);
                        fb.setShooter(wither);
                        fb.setDirection(fdir.multiply(1.5));
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

                // ── Block-breaking flight path (every 4 ticks) — never gets stuck ──
                // Fixes the "boss stuck in place because mobGriefing is off" bug:
                // instead of relying on vanilla Wither block-breaking (which
                // respects mobGriefing and can leave it wedged against terrain),
                // Zurion actively shatters solid blocks in its immediate vicinity
                // every few ticks so it can always keep flying freely.
                if (aiTick % 4 == 0) {
                    World world = wither.getWorld();
                    if (world != null) {
                        com.yourname.difficulty.boss.BossTerrainUtil.shatterSphereSoul(
                                world, wither.getLocation().getBlock(), 2, random);
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
     * Builds the Void Zurion's custom Blockbench void_boss visual — a
     * fixed-orientation ItemDisplay, entirely independent from the invisible
     * Wither carrier underneath (no stacked/inverted vanilla Wither models).
     * Slowly rotates in place each tick to feel alive.
     */
    private ItemDisplay spawnZurionDisplay(Wither carrier) {
        return com.yourname.difficulty.boss.BossDisplayUtil.spawnDisplay(
                carrier, 1.0, Material.NETHER_STAR, 3003, ZURION_DISPLAY_SCALE,
                15, 15,
                "§0§lThe Void Zurion",
                displayTagKey,
                Display.Billboard.FIXED);
    }


    // ── Void Boss (Wither) & Warden peaceful team non-hostility alliance ──────
    // (kept in BossEffectListener for shared logic across boss types)
}
