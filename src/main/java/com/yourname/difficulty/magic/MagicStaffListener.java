package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicStaffListener — Handles elemental staff casting with status effects.
 *
 * ── Cooldown formula ────────────────────────────────────────────────────────
 *  Base: 3000ms
 *  Level reduction: (magicLevel/99) × 2000ms  → Lv99 = 1000ms base
 *  Mage gear: 250ms off per piece equipped     → full set = −1000ms more
 *  Minimum: 500ms regardless
 *
 * ── Status Effects ───────────────────────────────────────────────────────────
 *  WET    (5–10s)  : Applied by Water hit. Re-applied on each water hit.
 *  MUDDY  (15–30s) : Applied when Earth hits a WET target. Slowness IV.
 *  FROZEN (5s)     : Applied when Fire hits a MUDDY target. Total immobility.
 *
 *  Combo chain:
 *    WATER → EARTH  = MUDDY  (Slowness IV)
 *    MUDDY + FIRE   = FROZEN (5s — can't move)
 *    FROZEN + AIR   = INSTANT DEATH (shattered on ground impact)
 *
 *  Other interactions:
 *    FIRE  + WET target  → extinguishes Wet (no fire), steam particles
 *    AIR   + WET target  → dries target (no knockback), steam particles
 *    EARTH + WET target  → removes Wet, applies MUDDY (Slowness IV)
 *    EARTH + dry target  → damage + dirt particles
 *
 * ── Ranges / Projectiles ─────────────────────────────────────────────────────
 *  FIRE  : SmallFireball — travels until it hits
 *  WATER : Snowball projectile — travels until it hits (same range as fire)
 *          RIGHT_CLICK_BLOCK + bucket → 5-block water river on ground
 *  EARTH : Snowball projectile — travels until it hits
 *  AIR   : Direct range-check (20 blocks), closer = more knockback × magic level
 *
 * ── Level scaling ─────────────────────────────────────────────────────────────
 *  Damage:         floor(level/33) extra hearts
 *  Wet duration:   5s + (level/99)×5s  = 5–10s
 *  Muddy duration: 15s + (level/99)×15s = 15–30s
 *  Fire duration:  2s + (level/99)×2s  = 2–4s
 *  Air strength:   1.0 + (level/99)×2.5 × distance factor (massively buffed)
 */
public class MagicStaffListener implements Listener {

    // ── Metadata keys for status effects ─────────────────────────────────────
    public static final String META_WET    = "magic_wet";    // value = expiry ms
    public static final String META_MUDDY  = "magic_muddy";  // value = expiry ms
    public static final String META_FROZEN = "magic_frozen"; // value = expiry ms

    private static final int  AIR_RANGE = 20;   // max range for air gust (blocks)

    private final ItemFactory   itemFactory;
    private final SkillManager  skillManager;
    private final JavaPlugin    plugin;

    /** projectile UUID → MagicElement */
    private final Map<UUID, MagicElement> trackedProjectiles = new HashMap<>();
    /** projectile UUID → shooter UUID */
    private final Map<UUID, UUID> projectileShooters = new HashMap<>();
    /** projectile UUID → magic level at cast time (for scaling effects) */
    private final Map<UUID, Integer> projectileLevels = new HashMap<>();
    /** player UUID → (element → last cast time ms) */
    private final Map<UUID, Map<MagicElement, Long>> cooldowns = new HashMap<>();

    private static final long MAGIC_XP_CAST = 10L;
    private static final long MAGIC_XP_HIT  =  5L;

    public MagicStaffListener(ItemFactory itemFactory, SkillManager skillManager,
                               JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    // ── Right-click cast ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player  = event.getPlayer();
        ItemStack hand    = player.getInventory().getItemInMainHand();
        MagicElement element = itemFactory.getStaffElement(hand);
        if (element == null) return;

        event.setCancelled(true);

        int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        long cooldownMs = getCooldownMs(player, magicLevel);

        if (!checkAndSetCooldown(player.getUniqueId(), element, cooldownMs)) {
            long msLeft = msUntilReady(player.getUniqueId(), element, cooldownMs);
            player.sendActionBar(element.color + element.staffName
                    + " §8cooldown: §e" + String.format("%.1f", msLeft / 1000.0) + "s");
            return;
        }

        if (!consumeRune(player, element)) {
            player.sendActionBar("§c✗ §7No " + element.runeName
                    + " §7— craft from §e4× " + element.runeCraftIngredient.name() + "§7.");
            return;
        }

        awardMagicXp(player, MAGIC_XP_CAST);
        castSpell(player, element, magicLevel, action, event.getClickedBlock());
    }

    // ── Cooldown formula ──────────────────────────────────────────────────────

    /**
     * Dynamic cooldown: 3000ms base, reduced by magic level and mage gear.
     * Level: (level/99) × 2000ms reduction  → Lv99 = −2000ms
     * Gear:  250ms per mage gear piece worn  → full set = −1000ms
     * Minimum: 500ms.
     */
    private long getCooldownMs(Player player, int magicLevel) {
        long cd = 3000L;
        cd -= (long) ((magicLevel / 99.0) * 2000L);
        cd -= itemFactory.countMageGearPieces(player) * 250L;
        return Math.max(500L, cd);
    }

    // ── Spell dispatch ────────────────────────────────────────────────────────

    private void castSpell(Player player, MagicElement element, int magicLevel,
                           Action action, Block clickedBlock) {
        switch (element) {
            case FIRE  -> castFire(player, magicLevel);
            case WATER -> castWater(player, magicLevel, action);
            case EARTH -> castEarth(player, magicLevel);
            case AIR   -> castAir(player, magicLevel);
        }
    }

    // ── FIRE ──────────────────────────────────────────────────────────────────

    private void castFire(Player player, int magicLevel) {
        SmallFireball fb = player.getWorld().spawn(
            player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5)),
            SmallFireball.class
        );
        fb.setDirection(player.getLocation().getDirection().multiply(1.5));
        fb.setShooter(player);
        fb.setIsIncendiary(true);
        fb.setYield(0.3f);

        trackedProjectiles.put(fb.getUniqueId(), MagicElement.FIRE);
        projectileShooters.put(fb.getUniqueId(), player.getUniqueId());
        projectileLevels.put(fb.getUniqueId(), magicLevel);

        player.sendActionBar("§c🔥 §7Fireball launched! §8(Lv " + magicLevel + ")");
    }

    // ── WATER ─────────────────────────────────────────────────────────────────

    private void castWater(Player player, int magicLevel, Action action) {
        // Ground cast (right-click a solid block with bucket in inventory) → river
        if (action == Action.RIGHT_CLICK_BLOCK) {
            RayTraceResult ray = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getLocation().getDirection(), 5,
                FluidCollisionMode.NEVER, true
            );
            if (ray != null && ray.getHitBlock() != null) {
                if (!hasWaterBucket(player)) {
                    player.sendActionBar("§b💧 §7Need a §bWater Bucket §7to create a river!");
                    return;
                }
                placeWaterStream(player, ray.getHitBlock());
                player.sendActionBar("§b💧 §7Water river placed! §8(5 blocks)");
                return;
            }
        }

        // Combat cast → shoot water bolt projectile (travels until it hits)
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.PRISMARINE_SHARD)); // blue-tinted appearance
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.5)); // fast like fire

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.WATER);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        // Trailing water particles
        plugin.getServer().getScheduler().runTaskTimer(plugin,
            task -> {
                if (!bolt.isValid()) { task.cancel(); return; }
                bolt.getWorld().spawnParticle(Particle.SPLASH,
                    bolt.getLocation(), 3, 0.1, 0.1, 0.1, 0.0);
            }, 0L, 1L);

        player.sendActionBar("§b💧 §7Water bolt fired!");
    }

    private boolean hasWaterBucket(Player player) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.getType() == Material.WATER_BUCKET) return true;
        }
        return false;
    }

    private void placeWaterStream(Player player, Block hitBlock) {
        BlockFace direction = getHorizontalFacing(player);
        Block start = hitBlock.getRelative(BlockFace.UP);
        for (int i = 0; i < 5; i++) {
            Block b = start.getRelative(direction, i);
            if (b.getType().isAir() || b.getType() == Material.WATER) {
                b.setType(Material.WATER);
            } else break;
        }
    }

    private BlockFace getHorizontalFacing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // ── EARTH ─────────────────────────────────────────────────────────────────

    private void castEarth(Player player, int magicLevel) {
        // Shoot a dirt-looking snowball projectile (same range as fire)
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.DIRT));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.2));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.EARTH);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        player.sendActionBar("§2🌿 §7Earth bolt fired!");
    }

    // ── AIR ───────────────────────────────────────────────────────────────────

    /**
     * Air gust: massively powerful distance-based knockback.
     *
     * ── FROZEN COMBO ─────────────────────────────────────────────────────────
     *  If the target is FROZEN (hardened mud), gusting them causes instant death.
     *  They are launched while frozen solid and shatter on impact with the ground.
     *
     * ── Knockback formula ────────────────────────────────────────────────────
     *  knockbackBlocks = 3.0 + (AIR_RANGE − distance) × 1.4
     *    At dist=20:  3 blocks knockback
     *    At dist= 1: ~29 blocks knockback
     *
     *  velocity = knockbackBlocks × 0.22 × levelMult
     *  levelMult = 1.0 at Lv1 → 3.5 at Lv99  (was 0.5→2.0)
     */
    private void castAir(Player player, int magicLevel) {
        LivingEntity target = nearestEntity(player, AIR_RANGE);
        if (target != null) {

            // ── FROZEN + AIR = INSTANT DEATH ──────────────────────────────────
            if (isFrozen(target)) {
                removeFrozen(target);
                // Ice-shatter particle burst
                target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                    target.getLocation().add(0, 1, 0), 80, 0.8, 0.8, 0.8, 0.3);
                target.getWorld().spawnParticle(Particle.CLOUD,
                    target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.15);
                target.getWorld().spawnParticle(Particle.ITEM,
                    target.getLocation().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.3,
                    new ItemStack(Material.ICE));
                // Instant kill
                if (target instanceof Player tp) {
                    tp.setHealth(0);
                    tp.sendTitle("§b❄ §c§lSHATTERED",
                        "§7Frozen solid — you hit the ground and shattered!", 5, 40, 15);
                } else {
                    target.setHealth(0);
                }
                awardMagicXp(player, MAGIC_XP_HIT * 5);
                player.sendActionBar(
                    "§b❄ §c§lSHATTERED! §7Frozen target obliterated on ground impact!");
                return;
            }

            // ── WET target → dry, no knockback ────────────────────────────────
            if (isWet(target)) {
                removeWet(target, true);
                player.sendActionBar("§7💨 §7Air §bdried §7the wet target! No knockback.");
                return;
            }

            // ── Normal gust (heavily buffed) ───────────────────────────────────
            double dist = Math.max(0.5, target.getLocation().distance(player.getLocation()));
            // Knockback blocks — strongly distance-scaled
            double knockbackBlocks = 3.0 + (AIR_RANGE - dist) * 1.4;
            // Level multiplier: 1.0 at Lv1 → 3.5 at Lv99 (was 0.5→2.0)
            double levelMult = 1.0 + (magicLevel / 99.0) * 2.5;
            // Velocity — heavily buffed (was 0.09, now 0.22)
            double velocity = knockbackBlocks * 0.22 * levelMult;
            // Strong upward launch
            double upward = 0.4 + (magicLevel / 99.0) * 0.5 + (knockbackBlocks / (AIR_RANGE * 1.4)) * 0.35;

            Vector dir = target.getLocation().subtract(player.getLocation())
                .toVector().normalize();
            dir.setY(upward);
            dir = dir.multiply(velocity / dir.length()); // normalize then scale
            target.setVelocity(dir);

            double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 3;
            target.damage(damage, player);
            awardMagicXp(player, MAGIC_XP_HIT);

            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 50, 0.8, 0.8, 0.8, 0.3);

            player.sendActionBar(String.format(
                "§7💨 §f§lGUST! §8dist: §e%.1fb §8→ §7~%.0f §8blocks §8(×%.1f level)",
                dist, knockbackBlocks, levelMult));
            if (target instanceof Player tp)
                tp.sendActionBar(String.format(
                    "§7💨 §f§lYou were blasted ~%.0f blocks!", knockbackBlocks));

        } else {
            player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().add(player.getLocation().getDirection().multiply(4)).add(0, 1, 0),
                40, 0.6, 0.6, 0.6, 0.12);
            player.sendActionBar("§7💨 §7No target within " + AIR_RANGE + " blocks.");
        }
    }

    // ── Projectile hit ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projId = event.getEntity().getUniqueId();
        MagicElement element   = trackedProjectiles.remove(projId);
        UUID         shooterId = projectileShooters.remove(projId);
        int          magicLevel = projectileLevels.getOrDefault(projId, 1);
        projectileLevels.remove(projId);
        if (element == null || shooterId == null) return;

        Player shooter = plugin.getServer().getPlayer(shooterId);

        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target.getUniqueId().equals(shooterId)) return; // don't hit yourself

        switch (element) {
            case FIRE  -> handleFireHit(target, shooter, magicLevel);
            case WATER -> handleWaterHit(target, shooter, magicLevel);
            case EARTH -> handleEarthHit(target, shooter, magicLevel);
            default -> {}
        }

        if (shooter != null) awardMagicXp(shooter, MAGIC_XP_HIT);
    }

    private void handleFireHit(LivingEntity target, Player shooter, int magicLevel) {
        // ── MUDDY + FIRE = FROZEN ──────────────────────────────────────────────
        if (isMuddy(target)) {
            removeMuddy(target);
            target.setFireTicks(0);
            applyFrozen(target, 100); // 5 seconds = 100 ticks
            // Steam + ice particles (mud hardening)
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1.5, 0), 25, 0.4, 0.4, 0.4, 0.05);
            if (shooter != null) shooter.sendActionBar(
                "§b❄ §6Mud hardened by fire — target is §b§lFROZEN§6! §7Gust for §c§lINSTANT DEATH§7!");
            return;
        }

        // ── WET + FIRE = extinguish ────────────────────────────────────────────
        if (isWet(target)) {
            removeWet(target, true);
            target.setFireTicks(0);
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> target.setFireTicks(0), 1L);
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1.5, 0), 20, 0.4, 0.4, 0.4, 0.05);
            if (target instanceof Player p)
                p.sendActionBar("§b💧 §7Water §fextinguished §7the fireball!");
            if (shooter != null)
                shooter.sendActionBar("§b💧 §7Target was §bwet §7— fire extinguished!");
            return;
        }

        // ── Normal fire hit ────────────────────────────────────────────────────
        double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
        target.damage(damage, shooter);
        int fireTicks = 40 + (int) ((magicLevel / 99.0) * 40); // 2–4 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }, 1L);
        if (shooter != null)
            shooter.sendActionBar("§c🔥 §7Fireball hit! §8(" + fireTicks / 20 + "s fire)");
    }

    private void handleWaterHit(LivingEntity target, Player shooter, int magicLevel) {
        double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
        target.damage(damage, shooter);

        int wetTicks = 100 + (int) ((magicLevel / 99.0) * 100); // 5–10 seconds
        applyWet(target, wetTicks);

        target.getWorld().spawnParticle(Particle.SPLASH,
            target.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.2);

        if (shooter != null)
            shooter.sendActionBar("§b💧 §7Water hit! Target is §bWet §7(" + wetTicks / 20 + "s)");
        if (target instanceof Player p)
            p.sendActionBar("§b💧 §7You are §bWet! §7Fire & Air weakened. Earth = §6Muddy§7!");
    }

    private void handleEarthHit(LivingEntity target, Player shooter, int magicLevel) {
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3,
            Material.DIRT.createBlockData());

        if (isWet(target)) {
            // WET + EARTH = MUDDY
            removeWet(target, false); // silent removal
            int muddyTicks = 300 + (int) ((magicLevel / 99.0) * 300); // 15–30 seconds
            applyMuddy(target, muddyTicks);
            if (shooter != null)
                shooter.sendActionBar("§2🌿 §7Wet + Earth = §6Muddy! §8(" + muddyTicks / 20 + "s) — §7Hit with §cFire §7to freeze!");
        } else {
            double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
            target.damage(damage, shooter);
            // Slight slowness from impact (normal, not muddy)
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
            if (shooter != null)
                shooter.sendActionBar("§2🌿 §7Earth hit! §8(" + (damage / 2) + " hearts)");
        }
    }

    // ── Status: WET ───────────────────────────────────────────────────────────

    public void applyWet(LivingEntity entity, int durationTicks) {
        long expiryMs = System.currentTimeMillis() + (long) durationTicks * 50;
        entity.setMetadata(META_WET, new FixedMetadataValue(plugin, expiryMs));
        // Blue drip particles
        entity.getWorld().spawnParticle(Particle.DRIPPING_WATER,
            entity.getLocation().add(0, entity.getHeight() + 0.3, 0),
            8, 0.3, 0.1, 0.3, 0.0);
    }

    public boolean isWet(LivingEntity entity) {
        if (!entity.hasMetadata(META_WET)) return false;
        long expiry = (long) entity.getMetadata(META_WET).get(0).value();
        if (System.currentTimeMillis() > expiry) {
            entity.removeMetadata(META_WET, plugin);
            return false;
        }
        return true;
    }

    /** Remove wet status. If showEffect=true, show steam particles. */
    public void removeWet(LivingEntity entity, boolean showEffect) {
        entity.removeMetadata(META_WET, plugin);
        if (showEffect) {
            entity.getWorld().spawnParticle(Particle.CLOUD,
                entity.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
        }
    }

    // ── Status: MUDDY ─────────────────────────────────────────────────────────

    public void applyMuddy(LivingEntity entity, int durationTicks) {
        long expiryMs = System.currentTimeMillis() + (long) durationTicks * 50;
        entity.setMetadata(META_MUDDY, new FixedMetadataValue(plugin, expiryMs));
        // Slowness IV for the full duration
        entity.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, durationTicks, 3, false, true, true
        ));
        // Brown dirt particles
        entity.getWorld().spawnParticle(Particle.BLOCK,
            entity.getLocation().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4,
            Material.MUD.createBlockData());
        if (entity instanceof Player p)
            p.sendActionBar("§6🌿 §7You are §6Muddy! §8(Slowness IV — §cFire §8= §bFrozen§8)");
    }

    public boolean isMuddy(LivingEntity entity) {
        if (!entity.hasMetadata(META_MUDDY)) return false;
        long expiry = (long) entity.getMetadata(META_MUDDY).get(0).value();
        if (System.currentTimeMillis() > expiry) {
            entity.removeMetadata(META_MUDDY, plugin);
            return false;
        }
        return true;
    }

    public void removeMuddy(LivingEntity entity) {
        entity.removeMetadata(META_MUDDY, plugin);
        entity.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ── Status: FROZEN ────────────────────────────────────────────────────────

    /**
     * Freeze the target solid for 5 seconds (100 ticks).
     * Triggered by: FIRE hitting a MUDDY target.
     * Breaking condition: AIR gust while frozen = instant death.
     *
     * Uses powder-snow freeze vignette + Slowness 255 + Mining Fatigue 255.
     */
    public void applyFrozen(LivingEntity entity, int durationTicks) {
        long expiryMs = System.currentTimeMillis() + (long) durationTicks * 50;
        entity.setMetadata(META_FROZEN, new FixedMetadataValue(plugin, expiryMs));

        // Powder-snow freeze visual (200+ ticks = full icy vignette)
        entity.setFreezeTicks(200);

        // Total immobility
        entity.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, durationTicks, 255, false, true, true));
        entity.addPotionEffect(new PotionEffect(
            PotionEffectType.MINING_FATIGUE, durationTicks, 255, false, true, true));

        // Ice particle burst
        entity.getWorld().spawnParticle(Particle.SNOWFLAKE,
            entity.getLocation().add(0, 1, 0), 60, 0.5, 0.7, 0.5, 0.1);
        entity.getWorld().spawnParticle(Particle.ITEM,
            entity.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1,
            new ItemStack(Material.ICE));

        if (entity instanceof Player p) {
            p.sendTitle("§b❄ §lFROZEN", "§7Hardened solid — air gust = death!", 5, 60, 15);
            p.sendActionBar("§b❄ §7You are §b§lFROZEN§7! §8(5s — §7air gust = §c§lINSTANT DEATH§8!)");
        }

        // Auto-thaw after duration if still alive and still frozen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (entity.isValid() && isFrozen(entity)) {
                removeFrozen(entity);
                if (entity instanceof Player p)
                    p.sendActionBar("§b❄ §7The freeze wore off.");
            }
        }, durationTicks);
    }

    public boolean isFrozen(LivingEntity entity) {
        if (!entity.hasMetadata(META_FROZEN)) return false;
        long expiry = (long) entity.getMetadata(META_FROZEN).get(0).value();
        if (System.currentTimeMillis() > expiry) {
            entity.removeMetadata(META_FROZEN, plugin);
            return false;
        }
        return true;
    }

    public void removeFrozen(LivingEntity entity) {
        entity.removeMetadata(META_FROZEN, plugin);
        entity.setFreezeTicks(0);
        entity.removePotionEffect(PotionEffectType.SLOWNESS);
        entity.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    // ── Crafting note (no validation — any amethyst shard works in the recipe) ──
    // The CraftItemEvent validation was removed. The recipe uses AMETHYST_SHARD
    // as the base material. Custom Enchanted Shards (from mob drops, with PDC)
    // work the same as regular amethyst shards from geodes in the crafting table.
    // Admins can use /registry to get the custom shard, or find amethyst geodes.

    // ── Rune consumption ──────────────────────────────────────────────────────

    private boolean consumeRune(Player player, MagicElement element) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (itemFactory.isRune(contents[i], element)) {
                ItemStack item = contents[i];
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().setItem(i, new ItemStack(Material.AIR));
                return true;
            }
        }
        return false;
    }

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    private boolean checkAndSetCooldown(UUID id, MagicElement el, long cooldownMs) {
        long now = System.currentTimeMillis();
        Map<MagicElement, Long> map = cooldowns.computeIfAbsent(id, k -> new EnumMap<>(MagicElement.class));
        long last = map.getOrDefault(el, 0L);
        if (now - last < cooldownMs) return false;
        map.put(el, now);
        return true;
    }

    private long msUntilReady(UUID id, MagicElement el, long cooldownMs) {
        Map<MagicElement, Long> map = cooldowns.get(id);
        if (map == null) return 0;
        return Math.max(0, cooldownMs - (System.currentTimeMillis() - map.getOrDefault(el, 0L)));
    }

    // ── Magic XP ─────────────────────────────────────────────────────────────

    private void awardMagicXp(Player player, long amount) {
        int old = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        int nw  = skillManager.addXp(player.getUniqueId(), SkillType.MAGIC, amount);
        if (nw > old) {
            player.sendMessage("§6⬆ §e" + SkillType.MAGIC.colored()
                + " §elevel up! §8(§f" + old + " §8→ §a" + nw + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(nw));
            long newCd = getCooldownMs(player, nw);
            player.sendMessage("  §d✦ §7Spell cooldown: §a" + String.format("%.1f", newCd / 1000.0) + "s");
        }
    }

    // ── Nearest entity helper ─────────────────────────────────────────────────

    private LivingEntity nearestEntity(Player player, double range) {
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), range, range, range);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity le) || e.equals(player)) continue;
            double d = e.getLocation().distanceSquared(player.getLocation());
            if (d < nearestDist) { nearestDist = d; nearest = le; }
        }
        return nearest;
    }
}
