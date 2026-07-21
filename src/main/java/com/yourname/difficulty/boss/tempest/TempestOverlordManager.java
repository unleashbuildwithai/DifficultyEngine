package com.yourname.difficulty.boss.tempest;

import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.CrimsonBossManager;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * TempestOverlordManager — manages the colossal §5⚡ Tempest Overlord§r boss.
 *
 * ── Clean custom-entity architecture ────────────────────────────────────────
 * Instead of a giant scaled vanilla Phantom acting as a reskinned "big bat",
 * the Overlord is now composed of TWO paired entities:
 *
 *  1. §7Flight carrier§r — a completely §finvisible, silent§r {@link Phantom}
 *     used ONLY for its native flight AI/pathing, hitbox, and damage/death
 *     handling. Its vanilla bat-like model is never rendered to the client.
 *
 *  2. §7Visual display§r — an {@link ItemDisplay} entity themed around wind
 *     and storms (a large billboarded arcane orb), which is what players
 *     actually see. It streams its position from the carrier every tick.
 */
public class TempestOverlordManager implements Listener {

    /** 0.2% chance to drop the rare Sandstorm Book on death. */
    private static final double SANDSTORM_BOOK_DROP_CHANCE = 0.002;

    /** Visual scale of the Overlord's storm-orb display. */
    private static final float  OVERLORD_DISPLAY_SCALE = 6.0f;

    private final JavaPlugin plugin;
    private final BossEffectListener bossEffectListener;
    private final CrimsonBossManager crimsonBossManager;
    private final Random random = new Random();
    /** UUIDs of currently-alive Tempest Overlord carriers — used to gate the death drop. */
    private final Set<UUID> activeTempestUuids = new HashSet<>();
    /** Carrier UUID → paired visual display UUID (position-synced every tick). */
    private final Map<UUID, UUID> carrierToDisplay = new HashMap<>();
    /** Optional ItemFactory — wired in via setter to avoid constructor churn elsewhere. */
    private ItemFactory itemFactory = null;

    private final NamespacedKey displayTagKey;

    public void setItemFactory(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }


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

    public TempestOverlordManager(JavaPlugin plugin, BossEffectListener bossEffectListener, CrimsonBossManager crimsonBossManager) {
        this.plugin = plugin;
        this.bossEffectListener = bossEffectListener;
        this.crimsonBossManager = crimsonBossManager;
        this.displayTagKey = new NamespacedKey(plugin, "de_tempest_display");

        // Position-sync task: streams each carrier's live location to its display
        new BukkitRunnable() {
            @Override
            public void run() {
                if (carrierToDisplay.isEmpty()) return;
                Iterator<Map.Entry<UUID, UUID>> it = carrierToDisplay.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, UUID> entry = it.next();
                    org.bukkit.entity.Entity carrier = plugin.getServer().getEntity(entry.getKey());
                    org.bukkit.entity.Entity display = plugin.getServer().getEntity(entry.getValue());

                    if (!(carrier instanceof Phantom p) || p.isDead() || !p.isValid()) {
                        if (display != null && !display.isDead()) display.remove();
                        it.remove();
                        continue;
                    }
                    if (display == null || display.isDead() || !display.isValid()) {
                        it.remove();
                        continue;
                    }
                    Location target = carrier.getLocation().clone();
                    if (!display.getLocation().getWorld().equals(target.getWorld())
                            || display.getLocation().distanceSquared(target) > 0.0004) {
                        display.teleport(target);
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public Phantom spawnTempestOverlord(Location loc) {
        if (loc == null) {
            World w = plugin.getServer().getWorld("ancient_realm");
            if (w == null) {
                w = plugin.getServer().getWorlds().get(0);
            }
            loc = new Location(w, 114.924, -38.0, -47.278);
        }

        // Rebuild Tempest Sanctum before spawning to repair any block breaks
        crimsonBossManager.rebuildArena(null, loc);

        // ── 1. Invisible flight carrier (physics/AI/hitbox only) ─────────────
        Phantom phantom = (Phantom) loc.getWorld().spawnEntity(loc, EntityType.PHANTOM);
        phantom.setCustomNameVisible(false); // name lives on the display instead
        phantom.setSize(4); // small hitbox — visual size comes entirely from the display
        phantom.setRemoveWhenFarAway(false);
        phantom.setInvisible(true);
        phantom.setSilent(true);

        var hp = phantom.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(400.0);
            phantom.setHealth(Math.min(400.0, hp.getValue()));
        } else {
            phantom.setHealth(400.0);
        }

        // Track this carrier for the death-drop gate
        activeTempestUuids.add(phantom.getUniqueId());

        // Register with effect system (Shriek, Leached, etc.)
        bossEffectListener.registerBoss(phantom);
        bossEffectListener.spawnShriek(phantom);

        // ── 2. Custom storm-themed visual display (billboarded, no vanilla model) ──
        ItemDisplay display = spawnOverlordDisplay(phantom);
        carrierToDisplay.put(phantom.getUniqueId(), display.getUniqueId());

        // Start Custom Tempest Overlord AI task
        new BukkitRunnable() {
            int aiTick = 0;

            @Override
            public void run() {
                if (phantom.isDead() || !phantom.isValid()) {
                    UUID displayUuid = carrierToDisplay.remove(phantom.getUniqueId());
                    if (displayUuid != null) {
                        org.bukkit.entity.Entity d = plugin.getServer().getEntity(displayUuid);
                        if (d != null && !d.isDead()) d.remove();
                    }
                    cancel();
                    return;
                }

                aiTick++;

                // ── 1. Target Tracking & Aggressive Dive AI ──────────────────
                Player target = null;
                double closestDist = 80.0;
                for (Player p : phantom.getWorld().getPlayers()) {
                    if (p.isDead()) continue;
                    double dist = p.getLocation().distance(phantom.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = p;
                    }
                }

                if (target != null) {
                    phantom.setTarget(target);

                    // Swoop down and pull its velocity directly towards the player
                    Location pLoc = target.getLocation().add(0, 1.0, 0);
                    Location tLoc = phantom.getLocation();
                    Vector dir = pLoc.toVector().subtract(tLoc.toVector());
                    double dist = dir.length();

                    if (dist > 3.0) {
                        dir.normalize();
                        // Pull speed: faster if further away
                        double pullSpeed = Math.min(0.7, 0.2 + (dist * 0.01));
                        Vector velocity = phantom.getVelocity();
                        Vector blended = velocity.add(dir.multiply(pullSpeed).subtract(velocity).multiply(0.2));
                        phantom.setVelocity(blended);
                    }

                    // ── 2. Attack 1: Fire-Spitting (every 50 ticks / 2.5 seconds) ──
                    if (aiTick % 50 == 0) {
                        Location origin = phantom.getLocation().add(0, 1.0, 0);
                        Vector spitDir = pLoc.toVector().subtract(origin.toVector()).normalize();

                        // Spawn custom flaming fireballs targeting the player's chest
                        Fireball fb = (Fireball) phantom.getWorld().spawnEntity(
                                origin.clone().add(spitDir.clone().multiply(3.0)), EntityType.FIREBALL);
                        fb.setShooter(phantom);
                        fb.setDirection(spitDir.multiply(1.5));
                        fb.setYield(1.5f);
                        fb.setIsIncendiary(true);

                        phantom.getWorld().playSound(phantom.getLocation(), Sound.ENTITY_PHANTOM_BITE, 2.0f, 0.5f);
                        phantom.getWorld().playSound(phantom.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
                        target.sendActionBar("§5⚡ §cThe Tempest Overlord spits Hellfire! Dodge!");
                    }

                    // ── 3. Attack 2: Thunderstorm Strikes (every 80 ticks / 4 seconds) ──
                    if (aiTick % 80 == 0) {
                        Location targetLoc = target.getLocation();
                        phantom.getWorld().strikeLightningEffect(targetLoc);

                        // Deal custom lightning damage (bypass armor slightly to feel like a storm!)
                        target.damage(5.0, phantom);
                        target.setFireTicks(60);

                        phantom.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc, 2, 0.5, 0.5, 0.5, 0);
                        target.sendMessage("§5⚡ §dLightning strikes from the Tempest! §cUse Earth magic or dodge!");
                        target.playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.8f);
                    }
                }

                // ── 4. Colossal Block-Breaking & Tunnel Carving (every 5 ticks) ──
                if (aiTick % 5 == 0) {
                    Location center = phantom.getLocation();
                    World world = phantom.getWorld();
                    if (world != null) {
                        int radius = 7; // Storm titan has a 7 block reach!
                        int bx = center.getBlockX();
                        int by = center.getBlockY();
                        int bz = center.getBlockZ();

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

                                    // Shatter instantly!
                                    b.setType(Material.AIR);
                                    if (random.nextDouble() < 0.1) {
                                        world.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.GRAY, 1.0f));
                                        world.spawnParticle(Particle.CLOUD, b.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
                                    }
                                }
                            }
                        }
                        if (aiTick % 10 == 0) {
                            world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.5f, 0.7f);
                        }
                    }
                }

                // ── 5. Ambient Gale Storm particles ──────────────────────────
                if (aiTick % 3 == 0) {
                    phantom.getWorld().spawnParticle(Particle.CLOUD, phantom.getLocation().add(0, 1.0, 0), 20, 3.0, 1.5, 3.0, 0.1);
                    phantom.getWorld().spawnParticle(Particle.GUST, phantom.getLocation().add(0, 1.0, 0), 10, 2.5, 1.0, 2.5, 0.15);
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);

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

    /**
     * Builds the Tempest Overlord's custom storm-orb visual — a large
     * billboarded ItemDisplay themed around wind/lightning, entirely
     * independent from the invisible Phantom carrier underneath.
     */
    private ItemDisplay spawnOverlordDisplay(Phantom carrier) {
        ItemStack visual = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = visual.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(2001); // reserved model id for Tempest Overlord — resource pack can reskin freely
            visual.setItemMeta(meta);
        }

        ItemDisplay display = carrier.getWorld().spawn(carrier.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setBillboard(Display.Billboard.CENTER);
            d.setPersistent(false);
            d.setCustomName("§5⚡ §l§dThe Tempest Overlord");
            d.setCustomNameVisible(true);
            d.setBrightness(new Display.Brightness(15, 15));

            Transformation t = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(OVERLORD_DISPLAY_SCALE, OVERLORD_DISPLAY_SCALE, OVERLORD_DISPLAY_SCALE),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            );
            d.setTransformation(t);

            d.getPersistentDataContainer().set(displayTagKey, PersistentDataType.BYTE, (byte) 1);
        });

        return display;
    }

    // ── Death drop: 0.2% Sandstorm Book (Tempest Overlord ONLY) ────────────────
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTempestDeath(EntityDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (!activeTempestUuids.remove(uuid)) return;

        // Immediately clean up the paired visual display
        UUID displayUuid = carrierToDisplay.remove(uuid);
        if (displayUuid != null) {
            org.bukkit.entity.Entity d = plugin.getServer().getEntity(displayUuid);
            if (d != null && !d.isDead()) d.remove();
        }

        if (itemFactory == null) return;

        if (random.nextDouble() < SANDSTORM_BOOK_DROP_CHANCE) {
            Location loc = event.getEntity().getLocation();
            loc.getWorld().dropItemNaturally(loc, itemFactory.buildSandstormBook());
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= 10000.0) {
                    p.sendMessage("§6✦ §e§lTHE SANDSTORM BOOK §7dropped from the §5Tempest Overlord§7!");
                }
            }
        }
    }

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
}
