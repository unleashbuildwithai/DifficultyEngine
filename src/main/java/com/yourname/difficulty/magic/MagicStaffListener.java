package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * MagicStaffListener — Full elemental combo system with 9 status effects.
 *
 * ── Status Effects ────────────────────────────────────────────────────────────
 *  WET      (5–10s)  : Water hit. Enables combo transitions.
 *  MUDDY    (15–30s) : Earth + WET → Slowness IV.
 *  CHILLED  (2.5s)   : Air + WET → Slowness II. Short window = high level needed.
 *  FROZEN   (5s)     : Air + CHILLED → Total freeze. Air gust = INSTANT DEATH.
 *  STATUE   (8s)     : Fire + MUDDY → Dirt statue. Air gust = INSTANT DEATH.
 *  SCORCHED (3s)     : First Fire hit → mild burn. Short window for follow-up.
 *  BLAZING  (5s)     : Fire + SCORCHED → Intense fire, strong DOT.
 *  MIND_BOMB(5s)     : 5% mage-gear chance → Nausea + Blindness.
 *  FALLEN   (3s)     : 30% Mind Bomb chance → Crawl pose. Press SPACE to stand.
 *
 * ── Full Combo Table ──────────────────────────────────────────────────────────
 *  FIRE on SCORCHED   → BLAZING (intense burn)
 *  FIRE on WET        → Extinguish (steam, no fire)
 *  FIRE on MUDDY      → STATUE (dirt shatter, 8s immobile, Air=death)
 *  FIRE on CHILLED    → Thaw (small blast, remove chill)
 *  FIRE on FROZEN     → Thaw Explosion (fire+steam, area damage)
 *  FIRE on dry        → SCORCHED (3s)
 *
 *  WATER on SCORCHED  → Steam Burst (extra damage, remove scorch)
 *  WATER on BLAZING   → Steam Explosion (AoE damage, heavy knockback)
 *  WATER on dry       → WET
 *
 *  EARTH on WET       → MUDDY (Slowness IV)
 *  EARTH on CHILLED   → Cracked Ice (Blindness + Slowness + damage)
 *  EARTH on STATUE    → Crumble (bonus damage, remove statue)
 *  EARTH on dry       → Normal damage
 *
 *  AIR on FROZEN      → INSTANT DEATH (shattered)
 *  AIR on STATUE      → INSTANT DEATH (crumbled)
 *  AIR on CHILLED     → FROZEN (5s)
 *  AIR on WET         → CHILLED (2.5s, short window)
 *  AIR on MUDDY       → Mud Launch (massive upward knockback)
 *  AIR on BLAZING     → Inferno Blast (fire + huge knockback)
 *  AIR on SCORCHED    → Fanned Flames (extra fire ticks)
 *  AIR on dry         → Heavy knockback (greatly buffed)
 *
 *  MAGE GEAR (2+ pieces) on any hit → 5% MIND BOMB
 *  MIND BOMB → 30% chance FALLEN (crawl, press SPACE to get up)
 *
 * ── Cooldown formula ──────────────────────────────────────────────────────────
 *  Base 3000ms − (level/99)×2000ms − (mage pieces)×250ms, minimum 500ms.
 *  Higher levels cast faster → chain combos within tight status windows.
 */
public class MagicStaffListener implements Listener {

    // ── Status effect metadata keys ───────────────────────────────────────────
    public static final String META_WET       = "magic_wet";
    public static final String META_MUDDY     = "magic_muddy";
    public static final String META_CHILLED   = "magic_chilled";
    public static final String META_FROZEN    = "magic_frozen";
    public static final String META_STATUE    = "magic_statue";
    public static final String META_SCORCHED  = "magic_scorched";
    public static final String META_BLAZING   = "magic_blazing";
    public static final String META_MIND_BOMB = "magic_mind_bomb";
    public static final String META_FALLEN    = "magic_fallen";

    private static final int    AIR_RANGE     = 20;
    private static final Random RAND          = new Random();

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;

    private final Map<UUID, MagicElement>              trackedProjectiles = new HashMap<>();
    private final Map<UUID, UUID>                      projectileShooters = new HashMap<>();
    private final Map<UUID, Integer>                   projectileLevels   = new HashMap<>();
    private final Map<UUID, Map<MagicElement, Long>>   cooldowns          = new HashMap<>();
    /** tracks the repeating task keeping FALLEN players in swim pose */
    private final Map<UUID, BukkitTask>                fallenTasks        = new HashMap<>();
    /** World blocks converted from water → quicksand (soul sand) by Earth-on-Water combo. */
    private final Set<Location>                        quicksandBlocks    = new HashSet<>();
    /** SandstormManager reference — injected after construction via setSandstormManager(). */
    private       SandstormManager                    sandstormManager   = null;
    /** Metadata key tracking successive earth hits on a target (for suffocate combo). */
    public  static final String META_EARTH_HITS = "magic_earth_hits";

    private static final long MAGIC_XP_CAST  = 10L;
    private static final long MAGIC_XP_HIT   =  5L;
    private static final long MAGIC_XP_COMBO = 25L;

    public MagicStaffListener(ItemFactory itemFactory, SkillManager skillManager,
                               JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RIGHT-CLICK CAST
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player       player  = event.getPlayer();
        ItemStack    hand    = player.getInventory().getItemInMainHand();
        MagicElement element = itemFactory.getStaffElement(hand);
        if (element == null) return;

        event.setCancelled(true);

        int  magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
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

    // ══════════════════════════════════════════════════════════════════════════
    //  COOLDOWN FORMULA
    // ══════════════════════════════════════════════════════════════════════════

    private long getCooldownMs(Player player, int magicLevel) {
        long cd = 3000L;
        cd -= (long) ((magicLevel / 99.0) * 2000L);
        cd -= itemFactory.countMageGearPieces(player) * 250L;
        return Math.max(500L, cd);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPELL DISPATCH
    // ══════════════════════════════════════════════════════════════════════════

    private void castSpell(Player player, MagicElement element, int magicLevel,
                           Action action, Block clickedBlock) {
        if (element == MagicElement.FIRE)  { castFire(player, magicLevel); }
        else if (element == MagicElement.WATER) { castWater(player, magicLevel, action); }
        else if (element == MagicElement.EARTH) { castEarth(player, magicLevel); }
        else if (element == MagicElement.AIR)   { castAir(player, magicLevel); }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FIRE STAFF
    // ══════════════════════════════════════════════════════════════════════════

    private void castFire(Player player, int magicLevel) {
        SmallFireball fb = player.getWorld().spawn(
            player.getEyeLocation().add(player.getLocation().getDirection().multiply(1.5)),
            SmallFireball.class);
        fb.setDirection(player.getLocation().getDirection().multiply(1.5));
        fb.setShooter(player);
        fb.setIsIncendiary(false); // we handle ignite manually on hit
        fb.setYield(0.0f);        // no block damage

        trackedProjectiles.put(fb.getUniqueId(), MagicElement.FIRE);
        projectileShooters.put(fb.getUniqueId(), player.getUniqueId());
        projectileLevels.put(fb.getUniqueId(), magicLevel);

        // Rich FLAME + LAVA particle trail follows the fireball
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!fb.isValid()) { task.cancel(); return; }
            fb.getWorld().spawnParticle(Particle.FLAME,  fb.getLocation(), 8, 0.12, 0.12, 0.12, 0.02);
            fb.getWorld().spawnParticle(Particle.LAVA,   fb.getLocation(), 2, 0.05, 0.05, 0.05, 0.0);
            fb.getWorld().spawnParticle(Particle.SMOKE,  fb.getLocation(), 3, 0.1,  0.1,  0.1,  0.01);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.3f);
        player.sendActionBar("§c[Fire] §7Fireball launched! §8(Lv " + magicLevel + ")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WATER STAFF
    // ══════════════════════════════════════════════════════════════════════════

    private void castWater(Player player, int magicLevel, Action action) {
        if (action == Action.RIGHT_CLICK_BLOCK) {
            RayTraceResult ray = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), player.getLocation().getDirection(), 5,
                FluidCollisionMode.NEVER, true);
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

        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.PRISMARINE_SHARD));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.5));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.WATER);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        // Dense WATER trail — dripping water + bubble column
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!bolt.isValid()) { task.cancel(); return; }
            bolt.getWorld().spawnParticle(Particle.DRIPPING_WATER, bolt.getLocation(), 8, 0.12, 0.12, 0.12, 0.0);
            bolt.getWorld().spawnParticle(Particle.SPLASH,         bolt.getLocation(), 5, 0.08, 0.08, 0.08, 0.02);
            bolt.getWorld().spawnParticle(Particle.BUBBLE_POP,     bolt.getLocation(), 3, 0.05, 0.05, 0.05, 0.05);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_BUCKET_FILL, 0.9f, 1.5f);
        player.sendActionBar("§b[Water] §7Water bolt fired!");
    }

    private boolean hasWaterBucket(Player player) {
        for (ItemStack i : player.getInventory().getContents())
            if (i != null && i.getType() == Material.WATER_BUCKET) return true;
        return false;
    }

    private void placeWaterStream(Player player, Block hitBlock) {
        BlockFace dir = getHorizontalFacing(player);
        Block start = hitBlock.getRelative(BlockFace.UP);
        for (int i = 0; i < 5; i++) {
            Block b = start.getRelative(dir, i);
            if (b.getType().isAir() || b.getType() == Material.WATER) b.setType(Material.WATER);
            else break;
        }
    }

    private BlockFace getHorizontalFacing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  EARTH STAFF
    // ══════════════════════════════════════════════════════════════════════════

    private void castEarth(Player player, int magicLevel) {
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.DIRT));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.2));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.EARTH);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        // DIRT + GRAVEL block-particle trail streams behind the shot
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!bolt.isValid()) { task.cancel(); return; }
            bolt.getWorld().spawnParticle(Particle.BLOCK, bolt.getLocation(), 9,
                0.12, 0.12, 0.12, Material.DIRT.createBlockData());
            bolt.getWorld().spawnParticle(Particle.BLOCK, bolt.getLocation(), 5,
                0.08, 0.08, 0.08, Material.GRAVEL.createBlockData());
            bolt.getWorld().spawnParticle(Particle.SMOKE, bolt.getLocation(), 2,
                0.06, 0.06, 0.06, 0.005);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.2f, 0.6f);
        player.sendActionBar("§2[Earth] §7Earth bolt fired!");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AIR STAFF — skill-based projectile (aim with crosshair, not aimbot!)
    // ══════════════════════════════════════════════════════════════════════════

    private void castAir(Player player, int magicLevel) {
        // Fire a feather-snowball in the exact direction the player is looking.
        // No auto-targeting — player aims manually, just like Fire / Water / Earth.
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.FEATHER));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.8));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.AIR);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        // Trailing CLOUD + ENCHANT particles so the bolt is visible in flight
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!bolt.isValid()) { task.cancel(); return; }
            bolt.getWorld().spawnParticle(Particle.CLOUD,   bolt.getLocation(), 5, 0.10, 0.10, 0.10, 0.06);
            bolt.getWorld().spawnParticle(Particle.ENCHANT, bolt.getLocation(), 3, 0.10, 0.10, 0.10, 0.12);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.8f);
        player.sendActionBar("§7[Air] §7Air bolt fired! §8(Lv " + magicLevel + ")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  handleAirHit — combo logic applied when the air bolt hits an entity
    // ══════════════════════════════════════════════════════════════════════════

    private void handleAirHit(LivingEntity target, Player shooter, int lvl) {
        // ── FROZEN + AIR = INSTANT DEATH ──────────────────────────────────────
        if (isFrozen(target)) {
            killFrozen(target, shooter, "§b❄ §c§lSHATTERED! §7Frozen solid — launched into the ground!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // ── STATUE + AIR = INSTANT DEATH ──────────────────────────────────────
        if (isStatue(target)) {
            killStatue(target, shooter, "§6🏺 §c§lCRUMBLED! §7The statue was blasted apart!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // ── CHILLED + AIR = FROZEN ────────────────────────────────────────────
        if (isChilled(target)) {
            removeChilled(target);
            applyFrozen(target, 100); // 5 seconds
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            if (shooter != null) shooter.sendActionBar(
                "§b❄ §f§lFROZEN! §7Chilled target locked solid! §8Air gust = §c§lDEATH§8!");
            if (shooter != null) { awardMagicXp(shooter, MAGIC_XP_COMBO); awardMagicXp(shooter, MAGIC_XP_HIT); }
            return;
        }

        // ── WET + AIR = CHILLED ───────────────────────────────────────────────
        if (isWet(target)) {
            removeWet(target, true);
            applyChilled(target, 50); // 2.5 seconds — tight window for Frozen combo
            if (shooter != null) {
                shooter.sendActionBar("§b❄ §7Target is §bChilled§7! §8Cast Air again quickly to §bFreeze§8!");
                awardMagicXp(shooter, MAGIC_XP_HIT);
            }
            return;
        }

        // ── MUDDY + AIR = massive upward launch ───────────────────────────────
        if (isMuddy(target)) {
            removeMuddy(target);
            Vector up = new Vector(0, 2.5 + (lvl / 99.0) * 2.0, 0)
                .add(shooter != null
                    ? target.getLocation().subtract(shooter.getLocation()).toVector().normalize().multiply(0.8)
                    : new Vector(0, 0, 0));
            target.setVelocity(up);
            double dmg = 3.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, Material.MUD.createBlockData());
            if (shooter != null) shooter.sendActionBar("§6🌿 §f§lMUD LAUNCH! §7Muddy target catapulted skyward!");
            if (target instanceof Player tp) tp.sendActionBar("§6🌿 §7Mud expelled you §finto the sky§7!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // ── BLAZING + AIR = INFERNO BLAST ─────────────────────────────────────
        if (isBlazing(target)) {
            removeBlazing(target);
            double dist = shooter != null ? Math.max(0.5, target.getLocation().distance(shooter.getLocation())) : AIR_RANGE / 2.0;
            double kb   = 4.0 + (AIR_RANGE - dist) * 1.6;
            double mult = 1.2 + (lvl / 99.0) * 2.8;
            launchTarget(target, shooter, kb * 0.22 * mult, 0.5 + (lvl / 99.0) * 0.6);
            int fire = 100 + (int)((lvl / 99.0) * 100);
            target.setFireTicks(fire);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME,  target.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, 0.2);
            target.getWorld().spawnParticle(Particle.CLOUD,  target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            if (shooter != null) shooter.sendActionBar("§c🔥 §f§lINFERNO BLAST! §7Blazing target obliterated!");
            if (target instanceof Player tp) tp.sendActionBar("§c🔥 §f§lINFERNO BLAST §7ripped through you!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // ── SCORCHED + AIR = FANNED FLAMES ────────────────────────────────────
        if (isScorched(target)) {
            removeScorched(target);
            int fire = 60 + (int)((lvl / 99.0) * 80);
            target.setFireTicks(fire);
            double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.15);
            if (shooter != null) shooter.sendActionBar("§c🔥 §7Fanned the flames! §8(" + fire/20 + "s fire)");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // ── NORMAL AIR GUST ───────────────────────────────────────────────────
        double dist = shooter != null ? Math.max(0.5, target.getLocation().distance(shooter.getLocation())) : 5.0;
        double kb   = 3.0 + (AIR_RANGE - dist) * 1.4;
        double mult = 1.0 + (lvl / 99.0) * 2.5;
        double vel  = kb * 0.22 * mult;
        double up   = 0.4 + (lvl / 99.0) * 0.5 + (kb / (AIR_RANGE * 1.4)) * 0.35;

        launchTarget(target, shooter, vel, up);
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
        target.damage(dmg, shooter);
        if (shooter != null) awardMagicXp(shooter, MAGIC_XP_HIT);

        target.getWorld().spawnParticle(Particle.CLOUD,   target.getLocation().add(0, 1, 0), 50, 0.8, 0.8, 0.8, 0.3);
        target.getWorld().spawnParticle(Particle.ENCHANT, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.15);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.8f);

        if (shooter != null) shooter.sendActionBar(String.format(
            "§7[Air] §f§lGUST! §8dist: §e%.1fb §8-> §7~%.0f §8blocks §8(x%.1f lv)", dist, kb, mult));
        if (target instanceof Player tp)
            tp.sendActionBar(String.format("§7[Air] §f§lBlasted ~%.0f blocks!", kb));

        rollMindBomb(target, shooter);
    }

    /** Setter — call from Main after constructing both managers. */
    public void setSandstormManager(SandstormManager sm) { this.sandstormManager = sm; }

    // ══════════════════════════════════════════════════════════════════════════
    //  PROJECTILE HIT
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID         projId    = event.getEntity().getUniqueId();
        MagicElement element   = trackedProjectiles.remove(projId);
        UUID         shooterId = projectileShooters.remove(projId);
        int          lvl       = projectileLevels.getOrDefault(projId, 1);
        projectileLevels.remove(projId);
        if (element == null || shooterId == null) return;

        Player shooter = plugin.getServer().getPlayer(shooterId);

        // ── Block hit interactions (fire evap, earth→quicksand, air→sandstorm) ─
        if (event.getHitBlock() != null) {
            handleBlockHit(event.getHitBlock(), event.getHitBlockFace(), element, shooter, lvl);
        }

        // ── Entity hit ────────────────────────────────────────────────────────
        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target.getUniqueId().equals(shooterId)) return;

        if (element == MagicElement.FIRE)  handleFireHit(target, shooter, lvl);
        else if (element == MagicElement.WATER) handleWaterHit(target, shooter, lvl);
        else if (element == MagicElement.EARTH) handleEarthHit(target, shooter, lvl);
        else if (element == MagicElement.AIR)   handleAirHit(target, shooter, lvl);

        if (shooter != null) awardMagicXp(shooter, MAGIC_XP_HIT);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  BLOCK HIT INTERACTIONS
    // ══════════════════════════════════════════════════════════════════════════

    private void handleBlockHit(Block block, BlockFace face, MagicElement element,
                                 Player shooter, int lvl) {
        Material type = block.getType();

        if (element == MagicElement.FIRE) {
            // Fire + Water block → evaporate (steam)
            if (type == Material.WATER) {
                block.setType(Material.AIR);
                block.getWorld().spawnParticle(Particle.CLOUD,
                    block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                block.getWorld().spawnParticle(Particle.SMOKE,
                    block.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
                if (shooter != null) shooter.sendActionBar("§c🔥 §7Fire evaporated the water!");
            }

        } else if (element == MagicElement.EARTH) {
            // Earth + Water block → quicksand (soul sand)
            if (type == Material.WATER) {
                block.setType(Material.SOUL_SAND);
                quicksandBlocks.add(block.getLocation().toBlockLocation());
                block.getWorld().spawnParticle(Particle.BLOCK,
                    block.getLocation().add(0.5, 1.0, 0.5), 25, 0.4, 0.3, 0.4,
                    Material.SOUL_SAND.createBlockData());
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_SAND_PLACE, 1.0f, 0.7f);
                if (shooter != null)
                    shooter.sendActionBar("§2🌿 §7Water became §8Quicksand§7! §8Air bolt on it = §6☁ Sandstorm§7!");
            }
            // Earth hitting any other solid block → place a dirt block on its face
            else if (face != null) {
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().isAir()) {
                    adjacent.setType(Material.DIRT);
                    adjacent.getWorld().spawnParticle(Particle.BLOCK,
                        adjacent.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3,
                        Material.DIRT.createBlockData());
                    // Auto-remove after 30 s (600 ticks)
                    final Block b = adjacent;
                    plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> { if (b.getType() == Material.DIRT) b.setType(Material.AIR); }, 600L);
                }
            }

        } else if (element == MagicElement.AIR) {
            // Air bolt + Quicksand block → trigger sandstorm
            if (type == Material.SOUL_SAND
                    && quicksandBlocks.contains(block.getLocation().toBlockLocation())) {
                quicksandBlocks.remove(block.getLocation().toBlockLocation());
                if (sandstormManager != null) {
                    sandstormManager.triggerSandstorm(block.getLocation(), shooter);
                    if (shooter != null)
                        shooter.sendActionBar("§6☁ §f§lSANDSTORM TRIGGERED! §7The quicksand erupts!");
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  handleFireHit
    // ──────────────────────────────────────────────────────────────────────────

    private void handleFireHit(LivingEntity target, Player shooter, int lvl) {

        // ── Fire slime check ─────────────────────────────────────────────────
        if (target instanceof org.bukkit.entity.Slime slime) {
            // Convert to fire slime
            slime.setMetadata("magic_fire_slime",
                new FixedMetadataValue(plugin, System.currentTimeMillis() + 30_000L));
            slime.setFireTicks(Integer.MAX_VALUE); // keep it on fire visually
            slime.getWorld().spawnParticle(Particle.FLAME,
                slime.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
            slime.getWorld().spawnParticle(Particle.SMOKE,
                slime.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.03);
            if (shooter != null)
                shooter.sendActionBar("§c🔥 §7Fire slime! §8It sets players on §cfire §8on contact!");
            return;
        }

        // SCORCHED → BLAZING
        if (isScorched(target)) {
            removeScorched(target);
            applyBlazing(target, 100); // 5 seconds
            int fire = 80 + (int)((lvl / 99.0) * 80);
            target.setFireTicks(fire);
            double dmg = 3.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME,
                target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.2);
            if (shooter != null) shooter.sendActionBar(
                "§c🔥 §f§lBLAZING! §8(" + fire/20 + "s fire — Air to §fInferno Blast§8!)");
            if (target instanceof Player tp)
                tp.sendActionBar("§c🔥 §f§lBLAZING! §7Air gust = §cInferno Blast§7!");
            rollMindBomb(target, shooter);
            return;
        }

        // MUDDY → STATUE (dirt shattering)
        if (isMuddy(target)) {
            removeMuddy(target);
            target.setFireTicks(0);
            applyStatue(target, 160); // 8 seconds
            // Dirt shatter burst
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 0.5, 0), 80, 0.6, 0.6, 0.6,
                Material.DIRT.createBlockData());
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1.0, 0), 50, 0.4, 0.4, 0.4,
                Material.MUD.createBlockData());
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3,
                Material.BROWN_TERRACOTTA.createBlockData());
            target.getWorld().playSound(target.getLocation(),
                Sound.BLOCK_MUD_BREAK, 2.0f, 0.6f);
            if (shooter != null) shooter.sendActionBar(
                "§6🏺 §e§lSTATUE! §7Mud hardened — target frozen! §8Air gust = §c§lDEATH§8!");
            if (target instanceof Player tp) {
                tp.sendTitle("§6🏺 §e§lSTATUE", "§7Hardened mud — §cAir gust = DEATH!", 5, 80, 15);
                tp.sendActionBar("§6🏺 §7You are §e§lSTATUE§7! §8(8s — air gust = §cinst death§8!)");
            }
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // WET → extinguish
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

        // CHILLED → Thaw (small pop)
        if (isChilled(target)) {
            removeChilled(target);
            target.setFireTicks(0);
            double dmg = 1.5 + SkillBonusManager.magicDamageBonus(lvl) * 2;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.08);
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
            if (shooter != null)
                shooter.sendActionBar("§b💧 §7Thaw! Chill removed by fire — steam burst!");
            rollMindBomb(target, shooter);
            return;
        }

        // FROZEN → Thaw Explosion (big area steam)
        if (isFrozen(target)) {
            removeFrozen(target);
            target.setFireTicks(0);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            // Area particles
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0.15);
            target.getWorld().spawnParticle(Particle.FLAME,
                target.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0.1);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
            if (shooter != null)
                shooter.sendActionBar("§c🔥 §f§lTHAW EXPLOSION! §7Frozen + Fire = massive steam burst!");
            rollMindBomb(target, shooter);
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            return;
        }

        // Normal fire hit → SCORCHED
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        int fire = 40 + (int)((lvl / 99.0) * 40);
        applyScorched(target, 60); // 3 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) target.setFireTicks(Math.max(target.getFireTicks(), fire));
        }, 1L);
        if (shooter != null)
            shooter.sendActionBar("§c🔥 §7Fireball hit! §8Scorched §8(" + fire/20 + "s — §7hit again to §cBlaze§8!)");
        rollMindBomb(target, shooter);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  handleWaterHit
    // ──────────────────────────────────────────────────────────────────────────

    // ──────────────────────────────────────────────────────────────────────────
    //  Fire slime on-contact damage handler
    // ──────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFireSlimeContact(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof org.bukkit.entity.Slime slime)) return;
        if (!slime.hasMetadata("magic_fire_slime")) return;
        long expiry = (long) slime.getMetadata("magic_fire_slime").get(0).value();
        if (System.currentTimeMillis() > expiry) {
            slime.removeMetadata("magic_fire_slime", plugin);
            slime.setFireTicks(0);
            return;
        }
        if (event.getEntity() instanceof LivingEntity hit) {
            hit.setFireTicks(80); // 4 seconds on fire
            hit.getWorld().spawnParticle(Particle.FLAME,
                hit.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            if (hit instanceof Player hp)
                hp.sendActionBar("§c🔥 §7The fire slime set you §con fire§7!");
        }
    }

    private void handleWaterHit(LivingEntity target, Player shooter, int lvl) {

        // ── Water slime check — 15 % chance to split ─────────────────────────
        if (target instanceof org.bukkit.entity.Slime slime) {
            if (RAND.nextInt(100) < 15) {
                int extra = RAND.nextInt(2) + 1;
                for (int i = 0; i < extra; i++) {
                    org.bukkit.entity.Slime baby = (org.bukkit.entity.Slime)
                        slime.getWorld().spawnEntity(slime.getLocation(),
                            org.bukkit.entity.EntityType.SLIME);
                    baby.setSize(Math.max(1, slime.getSize() - 1));
                    baby.setVelocity(new Vector(
                        (RAND.nextDouble() - 0.5) * 0.6, 0.35,
                        (RAND.nextDouble() - 0.5) * 0.6));
                }
                slime.getWorld().spawnParticle(Particle.SPLASH,
                    slime.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1);
                if (shooter != null)
                    shooter.sendActionBar("§b💧 §7Water split the slime! §8(+" + extra + " slime!)");
            }
        }

        // BLAZING → STEAM EXPLOSION (AoE)
        if (isBlazing(target)) {
            removeBlazing(target);
            target.setFireTicks(0);
            double dmg = 5.0 + SkillBonusManager.magicDamageBonus(lvl) * 5;
            target.damage(dmg, shooter);
            // Knockback nearby entities
            for (Entity e : target.getWorld().getNearbyEntities(target.getLocation(), 4, 4, 4)) {
                if (e instanceof LivingEntity le && !e.equals(shooter)) {
                    Vector away = e.getLocation().subtract(target.getLocation()).toVector();
                    if (away.lengthSquared() > 0.01)
                        e.setVelocity(away.normalize().multiply(1.8).add(new Vector(0, 0.5, 0)));
                }
            }
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 100, 1.2, 1.2, 1.2, 0.2);
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0.3);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.8f);
            if (shooter != null)
                shooter.sendActionBar("§b💧 §f§lSTEAM EXPLOSION! §7Blazing target superheated!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // SCORCHED → STEAM BURST
        if (isScorched(target)) {
            removeScorched(target);
            target.setFireTicks(0);
            double dmg = 3.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.15);
            if (shooter != null)
                shooter.sendActionBar("§b💧 §7Steam Burst! §8Scorched + Water = superheated impact!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // Normal water hit → WET
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        int wetTicks = 100 + (int)((lvl / 99.0) * 100);
        applyWet(target, wetTicks);
        target.getWorld().spawnParticle(Particle.SPLASH,
            target.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.2);
        if (shooter != null)
            shooter.sendActionBar("§b💧 §7Water hit! §bWet §7(" + wetTicks/20 + "s — Earth=Muddy, Air=Chilled)");
        if (target instanceof Player p)
            p.sendActionBar("§b💧 §7You are §bWet§7! §8Earth=Muddy · Air=Chilled · Fire=Extinguish");
        rollMindBomb(target, shooter);
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  handleEarthHit
    // ──────────────────────────────────────────────────────────────────────────

    private void handleEarthHit(LivingEntity target, Player shooter, int lvl) {
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3,
            Material.DIRT.createBlockData());

        // ── Place a dirt block at entity's feet on every earth hit ───────────
        Block feetBlock = target.getLocation().getBlock();
        if (feetBlock.getType().isAir()) {
            feetBlock.setType(Material.DIRT);
            final Block fb = feetBlock;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (fb.getType() == Material.DIRT) fb.setType(Material.AIR); }, 100L);
        }

        // ── Track earth hits; 2nd hit → suffocate ───────────────────────────
        int hits = 1;
        if (target.hasMetadata(META_EARTH_HITS)) {
            hits = (int) target.getMetadata(META_EARTH_HITS).get(0).value() + 1;
        }
        if (hits >= 2) {
            target.removeMetadata(META_EARTH_HITS, plugin);
            suffocateTarget(target, shooter, lvl);
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }
        target.setMetadata(META_EARTH_HITS, new FixedMetadataValue(plugin, hits));
        // Clear the counter after 10 s if they don't get hit again
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            target.removeMetadata(META_EARTH_HITS, plugin), 200L);

        // WET → MUDDY
        if (isWet(target)) {
            removeWet(target, false);
            int muddyTicks = 300 + (int)((lvl / 99.0) * 300);
            applyMuddy(target, muddyTicks);
            if (shooter != null)
                shooter.sendActionBar("§6🌿 §7Wet + Earth = §6Muddy! §8(" + muddyTicks/20 + "s — §7Fire to §eStatue§8!)");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // CHILLED → CRACKED ICE (blindness + heavy slow + damage)
        if (isChilled(target)) {
            removeChilled(target);
            double dmg = 3.5 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5, false, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4,
                Material.ICE.createBlockData());
            if (shooter != null)
                shooter.sendActionBar("§b❄ §f§lCRACKED ICE! §7Chilled + Earth = blinded + heavy slow!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // STATUE → Crumble (bonus damage, remove early)
        if (isStatue(target)) {
            removeStatue(target);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6,
                Material.DIRT.createBlockData());
            if (shooter != null)
                shooter.sendActionBar("§6🌿 §f§lCRUMBLE! §7Earth smashed the statue!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // Normal earth hit
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
        if (shooter != null)
            shooter.sendActionBar("§2🌿 §7Earth hit! §8(" + (dmg/2) + " hearts) — §7hit §bwet §7target to make §6Muddy");
        rollMindBomb(target, shooter);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MIND BOMB + FALLEN
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * 5% chance to inflict MIND BOMB when 2+ mage gear pieces are worn.
     * Mind Bomb: Nausea + Blindness 5s, 30% chance of FALLEN (crawl).
     */
    private void rollMindBomb(LivingEntity target, Player shooter) {
        if (shooter == null) return;
        if (itemFactory.countMageGearPieces(shooter) < 2) return;
        if (RAND.nextInt(100) >= 5) return; // 5% chance
        if (isMindBombed(target)) return;   // already affected

        applyMindBomb(target, shooter);
    }

    private void applyMindBomb(LivingEntity target, Player shooter) {
        long expiry = System.currentTimeMillis() + 5000L;
        target.setMetadata(META_MIND_BOMB, new FixedMetadataValue(plugin, expiry));

        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,    100, 1, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0, false, true, true));

        target.getWorld().spawnParticle(Particle.WITCH,
            target.getLocation().add(0, 2, 0), 20, 0.5, 0.3, 0.5, 0.1);

        if (target instanceof Player tp) {
            tp.sendTitle("§5💀 §dMIND BOMB", "§7Disoriented!", 5, 40, 10);
            tp.sendActionBar("§5💀 §7You've been §dMind Bombed§7! §8Nausea + Blind 5s");
        }
        if (shooter != null)
            shooter.sendActionBar("§5💀 §d§lMIND BOMB! §7Mage gear proc — target disoriented!");

        // 30% chance of FALLEN (only players can crawl)
        if (target instanceof Player tp && RAND.nextInt(100) < 30 && !isFallen(tp)) {
            applyFallen(tp);
        }
    }

    private boolean isMindBombed(LivingEntity e) {
        if (!e.hasMetadata(META_MIND_BOMB)) return false;
        long expiry = (long) e.getMetadata(META_MIND_BOMB).get(0).value();
        if (System.currentTimeMillis() > expiry) { e.removeMetadata(META_MIND_BOMB, plugin); return false; }
        return true;
    }

    // ──────────────────────────────────────────────────────────────────────────
    //  FALLEN mechanic
    // ──────────────────────────────────────────────────────────────────────────

    private void applyFallen(Player player) {
        player.setMetadata(META_FALLEN, new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,     60, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 255, false, false, false));
        player.sendTitle("§c⚠ §l§oFALLEN!", "§7Press §fSPACE §7to get up!", 5, 50, 10);
        player.sendActionBar("§c⚠ §7You fell! Press §fSPACE §7to get up!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);

        // Maintain swimming (crawl) pose every 2 ticks while fallen
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isValid() || !isFallen(player)) {
                cancelFallenTask(player.getUniqueId());
                return;
            }
            player.setSwimming(true);
        }, 0L, 2L);
        fallenTasks.put(player.getUniqueId(), task);

        // Auto-recover after 3 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isValid() && isFallen(player)) getUpFromFallen(player, false);
        }, 60L);
    }

    private boolean isFallen(Player player) {
        return player.hasMetadata(META_FALLEN);
    }

    /** Called when player presses SPACE while fallen — get-up animation. */
    private void getUpFromFallen(Player player, boolean pressedSpace) {
        player.removeMetadata(META_FALLEN, plugin);
        cancelFallenTask(player.getUniqueId());
        player.setSwimming(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        if (pressedSpace) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
            player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.2);
            player.sendTitle("§a§l✔ GOT UP!", "", 3, 15, 7);
            player.sendActionBar("§a✔ §7You got up!");
        } else {
            player.sendActionBar("§7The daze wore off.");
        }
    }

    private void cancelFallenTask(UUID uuid) {
        BukkitTask t = fallenTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    /**
     * Detects jump (space bar) while FALLEN by watching for upward Y movement.
     * Slowness 255 prevents horizontal movement but NOT jumping — so to.y > from.y
     * reliably fires when the player presses Space while crawling on the ground.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!isFallen(p)) return;
        if (event.getTo() == null) return;
        // Upward Y delta > 0.05 means the player pressed Space (jump impulse)
        if (event.getTo().getY() > event.getFrom().getY() + 0.05) {
            getUpFromFallen(p, true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: WET
    // ══════════════════════════════════════════════════════════════════════════

    public void applyWet(LivingEntity e, int ticks) {
        e.setMetadata(META_WET, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.getWorld().spawnParticle(Particle.DRIPPING_WATER,
            e.getLocation().add(0, e.getHeight() + 0.3, 0), 8, 0.3, 0.1, 0.3, 0.0);
    }

    public boolean isWet(LivingEntity e) {
        if (!e.hasMetadata(META_WET)) return false;
        long ex = (long) e.getMetadata(META_WET).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_WET, plugin); return false; }
        return true;
    }

    public void removeWet(LivingEntity e, boolean steam) {
        e.removeMetadata(META_WET, plugin);
        if (steam) e.getWorld().spawnParticle(Particle.CLOUD,
            e.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: MUDDY
    // ══════════════════════════════════════════════════════════════════════════

    public void applyMuddy(LivingEntity e, int ticks) {
        e.setMetadata(META_MUDDY, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, false, true, true));
        e.getWorld().spawnParticle(Particle.BLOCK,
            e.getLocation().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, Material.MUD.createBlockData());
        if (e instanceof Player p)
            p.sendActionBar("§6🌿 §7You are §6Muddy§7! §8(Slowness IV — §cFire §8= §eStatue§8 · §7Air = §fLaunch§8)");
    }

    public boolean isMuddy(LivingEntity e) {
        if (!e.hasMetadata(META_MUDDY)) return false;
        long ex = (long) e.getMetadata(META_MUDDY).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_MUDDY, plugin); return false; }
        return true;
    }

    public void removeMuddy(LivingEntity e) {
        e.removeMetadata(META_MUDDY, plugin);
        e.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: CHILLED
    // ══════════════════════════════════════════════════════════════════════════

    public void applyChilled(LivingEntity e, int ticks) {
        e.setMetadata(META_CHILLED, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 2, false, true, true));
        e.getWorld().spawnParticle(Particle.SNOWFLAKE,
            e.getLocation().add(0, e.getHeight() + 0.3, 0), 12, 0.3, 0.1, 0.3, 0.0);
        if (e instanceof Player p)
            p.sendActionBar("§b❄ §7You are §bChilled§7! §8(Air again = §bFrozen§8 · Earth = §fCracked Ice§8)");
    }

    public boolean isChilled(LivingEntity e) {
        if (!e.hasMetadata(META_CHILLED)) return false;
        long ex = (long) e.getMetadata(META_CHILLED).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_CHILLED, plugin); return false; }
        return true;
    }

    public void removeChilled(LivingEntity e) {
        e.removeMetadata(META_CHILLED, plugin);
        e.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: FROZEN
    // ══════════════════════════════════════════════════════════════════════════

    public void applyFrozen(LivingEntity e, int ticks) {
        e.setMetadata(META_FROZEN, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.setFreezeTicks(200);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,      ticks, 255, false, true, true));
        e.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 255, false, true, true));
        e.getWorld().spawnParticle(Particle.SNOWFLAKE,
            e.getLocation().add(0, 1, 0), 60, 0.5, 0.7, 0.5, 0.1);
        e.getWorld().spawnParticle(Particle.ITEM,
            e.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1, new ItemStack(Material.ICE));
        if (e instanceof Player p) {
            p.sendTitle("§b❄ §lFROZEN", "§7Air gust = instant death!", 5, 60, 15);
            p.sendActionBar("§b❄ §b§lFROZEN§7! §8(5s — §7air gust = §c§lINSTANT DEATH§8!)");
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid() && isFrozen(e)) removeFrozen(e);
        }, ticks);
    }

    public boolean isFrozen(LivingEntity e) {
        if (!e.hasMetadata(META_FROZEN)) return false;
        long ex = (long) e.getMetadata(META_FROZEN).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_FROZEN, plugin); return false; }
        return true;
    }

    public void removeFrozen(LivingEntity e) {
        e.removeMetadata(META_FROZEN, plugin);
        e.setFreezeTicks(0);
        e.removePotionEffect(PotionEffectType.SLOWNESS);
        e.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    private void killFrozen(LivingEntity target, Player shooter, String shooterMsg) {
        removeFrozen(target);
        target.getWorld().spawnParticle(Particle.SNOWFLAKE,
            target.getLocation().add(0, 1, 0), 80, 0.8, 0.8, 0.8, 0.3);
        target.getWorld().spawnParticle(Particle.CLOUD,
            target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.15);
        target.getWorld().spawnParticle(Particle.ITEM,
            target.getLocation().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.3, new ItemStack(Material.ICE));
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
        if (target instanceof Player tp) {
            tp.setHealth(0);
            tp.sendTitle("§b❄ §c§lSHATTERED", "§7Frozen solid — hit the ground!", 5, 40, 15);
        } else {
            target.setHealth(0);
        }
        if (shooter != null) shooter.sendActionBar(shooterMsg);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: STATUE
    // ══════════════════════════════════════════════════════════════════════════

    public void applyStatue(LivingEntity e, int ticks) {
        e.setMetadata(META_STATUE, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,      ticks, 255, false, true, true));
        e.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 255, false, true, true));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid() && isStatue(e)) removeStatue(e);
        }, ticks);
    }

    public boolean isStatue(LivingEntity e) {
        if (!e.hasMetadata(META_STATUE)) return false;
        long ex = (long) e.getMetadata(META_STATUE).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_STATUE, plugin); return false; }
        return true;
    }

    public void removeStatue(LivingEntity e) {
        e.removeMetadata(META_STATUE, plugin);
        e.removePotionEffect(PotionEffectType.SLOWNESS);
        e.removePotionEffect(PotionEffectType.MINING_FATIGUE);
    }

    private void killStatue(LivingEntity target, Player shooter, String shooterMsg) {
        removeStatue(target);
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 1, 0), 100, 0.8, 0.8, 0.8, Material.DIRT.createBlockData());
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, Material.MUD.createBlockData());
        target.getWorld().spawnParticle(Particle.CLOUD,
            target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.15);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_MUD_BREAK, 2.0f, 0.4f);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        if (target instanceof Player tp) {
            tp.setHealth(0);
            tp.sendTitle("§6🏺 §c§lCRUMBLED", "§7The statue was blasted apart!", 5, 40, 15);
        } else {
            target.setHealth(0);
        }
        if (shooter != null) shooter.sendActionBar(shooterMsg);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: SCORCHED
    // ══════════════════════════════════════════════════════════════════════════

    public void applyScorched(LivingEntity e, int ticks) {
        e.setMetadata(META_SCORCHED, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.getWorld().spawnParticle(Particle.FLAME,
            e.getLocation().add(0, e.getHeight(), 0), 6, 0.3, 0.1, 0.3, 0.02);
        if (e instanceof Player p)
            p.sendActionBar("§c🔥 §7You are §cScorched§7! §8(Fire again = §cBlaze§8 · Air = §cFanned Flames§8)");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid() && isScorched(e)) e.removeMetadata(META_SCORCHED, plugin);
        }, ticks);
    }

    public boolean isScorched(LivingEntity e) {
        if (!e.hasMetadata(META_SCORCHED)) return false;
        long ex = (long) e.getMetadata(META_SCORCHED).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_SCORCHED, plugin); return false; }
        return true;
    }

    public void removeScorched(LivingEntity e) {
        e.removeMetadata(META_SCORCHED, plugin);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS: BLAZING
    // ══════════════════════════════════════════════════════════════════════════

    public void applyBlazing(LivingEntity e, int ticks) {
        e.setMetadata(META_BLAZING, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.getWorld().spawnParticle(Particle.FLAME,
            e.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.08);
        if (e instanceof Player p) {
            p.sendTitle("§c🔥 §lBLAZING", "§7Air gust = §cInferno Blast§7!", 5, 40, 10);
            p.sendActionBar("§c🔥 §c§lBLAZING§7! §8(Air = §cInferno Blast§8 · Water = §fSteam Explosion§8)");
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (e.isValid() && isBlazing(e)) e.removeMetadata(META_BLAZING, plugin);
        }, ticks);
    }

    public boolean isBlazing(LivingEntity e) {
        if (!e.hasMetadata(META_BLAZING)) return false;
        long ex = (long) e.getMetadata(META_BLAZING).get(0).value();
        if (System.currentTimeMillis() > ex) { e.removeMetadata(META_BLAZING, plugin); return false; }
        return true;
    }

    public void removeBlazing(LivingEntity e) {
        e.removeMetadata(META_BLAZING, plugin);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RUNE CONSUMPTION
    // ══════════════════════════════════════════════════════════════════════════

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

    // ══════════════════════════════════════════════════════════════════════════
    //  COOLDOWN HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private boolean checkAndSetCooldown(UUID id, MagicElement el, long ms) {
        long now = System.currentTimeMillis();
        Map<MagicElement, Long> map = cooldowns.computeIfAbsent(id,
            k -> new EnumMap<>(MagicElement.class));
        long last = map.getOrDefault(el, 0L);
        if (now - last < ms) return false;
        map.put(el, now);
        return true;
    }

    private long msUntilReady(UUID id, MagicElement el, long ms) {
        Map<MagicElement, Long> map = cooldowns.get(id);
        if (map == null) return 0;
        return Math.max(0, ms - (System.currentTimeMillis() - map.getOrDefault(el, 0L)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAGIC XP
    // ══════════════════════════════════════════════════════════════════════════

    private void awardMagicXp(Player player, long amount) {
        if (player == null) return;
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

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    //  SUFFOCATE — buries target under 3 dirt blocks for 5 s
    // ══════════════════════════════════════════════════════════════════════════

    private void suffocateTarget(LivingEntity target, Player shooter, int lvl) {
        org.bukkit.Location loc = target.getLocation();
        List<Block> placed = new ArrayList<>();
        for (int y = 0; y <= 2; y++) {
            Block b = loc.getBlock().getRelative(0, y, 0);
            if (b.getType().isAir() || b.getType() == Material.WATER) {
                b.setType(Material.DIRT);
                placed.add(b);
            }
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,      100, 255, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 255, false, true, true));
        target.getWorld().spawnParticle(Particle.BLOCK,
            loc.add(0, 1, 0), 80, 0.4, 0.6, 0.4, Material.DIRT.createBlockData());
        target.getWorld().playSound(loc, Sound.BLOCK_GRAVEL_PLACE, 2.0f, 0.5f);
        if (shooter != null) shooter.sendActionBar("§2🌿 §f§lSUFFOCATED! §7Target buried in earth!");
        if (target instanceof Player tp) {
            tp.sendTitle("§2🌿 §l§oBURIED!", "§7Dig out — or suffocate!", 5, 60, 15);
            tp.sendActionBar("§2🌿 §7You are §aBuried in Earth§7!");
        }
        // Remove blocks after 5 s (100 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            placed.forEach(b -> { if (b.getType() == Material.DIRT) b.setType(Material.AIR); }), 100L);
    }

    /** Launch target in the direction away from player with given velocity & upward. */
    private void launchTarget(LivingEntity target, Player player, double velocity, double upward) {
        Vector dir = target.getLocation().subtract(player.getLocation()).toVector().normalize();
        dir.setY(upward);
        target.setVelocity(dir.multiply(velocity / Math.max(0.01, dir.length())));
    }

    /** Null-safe shooter ref (always a Player here). */
    private Player shooter(Player p) { return p; }

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
