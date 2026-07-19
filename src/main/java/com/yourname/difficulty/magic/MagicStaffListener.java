package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
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
import org.bukkit.inventory.meta.BookMeta;
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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * MagicStaffListener — Full elemental combo system.
 *
 * ── Hint gating ───────────────────────────────────────────────────────────────
 *  Spell Combo Book in inventory  → shows regular combo hints in action bar.
 *  Ancient Kill Tome in inventory → additionally shows instant-kill hints.
 *  No books                       → combos work silently (cast blind).
 *
 * ── Status Effects ────────────────────────────────────────────────────────────
 *  WET / MUDDY / CHILLED / FROZEN / STATUE / SCORCHED / BLAZING / MIND_BOMB / FALLEN
 *
 * ── Air Hover ─────────────────────────────────────────────────────────────────
 *  Right-click Air staff while airborne → slow fall + levitation (no rune cost).
 *
 * ── Ground Block Magic ────────────────────────────────────────────────────────
 *  Earth bolt → places magic dirt.
 *  Fire bolt on MAGIC DIRT (Lv50+ + lava bucket + Spell Combo Book) → lava pool (30s).
 *  Water bolt on lava → BFS obsidian trap (up to 30 blocks).
 *  Air/Earth bolt on lava → extinguish.
 */
public class MagicStaffListener implements Listener {

    // ── Status effect metadata keys ───────────────────────────────────────────
    public static final String META_WET        = "magic_wet";
    public static final String META_MUDDY      = "magic_muddy";
    public static final String META_CHILLED    = "magic_chilled";
    public static final String META_FROZEN     = "magic_frozen";
    public static final String META_STATUE     = "magic_statue";
    public static final String META_SCORCHED   = "magic_scorched";
    public static final String META_BLAZING    = "magic_blazing";
    public static final String META_MIND_BOMB  = "magic_mind_bomb";
    public static final String META_FALLEN     = "magic_fallen";
    public static final String META_STAFF_HIT    = "magic_staff_hit";
    public static final String META_EARTH_HITS   = "magic_earth_hits";
    public static final String META_EARTH_TRAPPED = "magic_earth_trapped";

    private static final int    AIR_RANGE = 20;
    private static final Random RAND      = new Random();

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;

    private final Map<UUID, MagicElement>            trackedProjectiles = new HashMap<>();
    private final Map<UUID, UUID>                    projectileShooters = new HashMap<>();
    private final Map<UUID, Integer>                 projectileLevels   = new HashMap<>();
    private final Map<UUID, Map<MagicElement, Long>> cooldowns          = new HashMap<>();
    private final Map<UUID, BukkitTask>              fallenTasks        = new HashMap<>();
    private final Map<UUID, BukkitTask>              airHoverTasks      = new HashMap<>();
    /** Projectile UUID → the EarthBlockTier thrown (null = old system / Lv1-9). */
    private final Map<UUID, EarthBlockTier>          earthBoltTiers     = new HashMap<>();
    /** Target UUID → the tier of the trap currently placed under them. */
    private final Map<UUID, EarthBlockTier>          activeTraps        = new HashMap<>();
    /** Players who received the Mage Gear Guide this session (first cast). */
    private final Set<UUID>                          guidedPlayers      = new HashSet<>();
    private final Set<Location>                      quicksandBlocks    = new HashSet<>();
    private final Set<Location>                      magicLavaBlocks    = new HashSet<>();
    /** Dirt blocks placed by earth staff — tracked so fire can ignite them into lava. */
    private final Set<Location>                      magicDirtBlocks    = new HashSet<>();
    private       SandstormManager                   sandstormManager   = null;

    private static final long MAGIC_XP_CAST  = 10L;
    private static final long MAGIC_XP_HIT   =  5L;
    private static final long MAGIC_XP_COMBO = 25L;

    public MagicStaffListener(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    public void setSandstormManager(SandstormManager sm) { this.sandstormManager = sm; }

    // ══════════════════════════════════════════════════════════════════════════
    //  RIGHT-CLICK CAST
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean isRightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        boolean isLeftClick  = (action == Action.LEFT_CLICK_AIR  || action == Action.LEFT_CLICK_BLOCK);
        if (!isRightClick && !isLeftClick) return;

        Player       player  = event.getPlayer();
        ItemStack    hand    = player.getInventory().getItemInMainHand();
        MagicElement element = itemFactory.getStaffElement(hand);
        if (element == null) return;

        event.setCancelled(true);

        int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);

        // ── Fire Staff Lv99: dual-click mode ──────────────────────────────────
        // Left-click  → normal fireball
        // Right-click → lightning strike (admin = no cooldown, no rune cost)
        if (element == MagicElement.FIRE && magicLevel >= 99) {
            if (isRightClick) {
                // ── Lightning Strike ──────────────────────────────────────────
                boolean isAdmin = player.hasPermission("difficultyengine.cape.admin");
                if (!isAdmin) {
                    long cooldownMs = getCooldownMs(player, magicLevel);
                    if (!checkAndSetCooldown(player.getUniqueId(), MagicElement.FIRE, cooldownMs)) {
                        long msLeft = msUntilReady(player.getUniqueId(), MagicElement.FIRE, cooldownMs);
                        player.sendActionBar("§e⚡ §c[Fire Lv99] §8Lightning: §e"
                                + String.format("%.1f", msLeft / 1000.0) + "s");
                        return;
                    }
                    if (!consumeRune(player, MagicElement.FIRE)) {
                        player.sendActionBar("§c✗ §7No §c🔥 Fire Rune §7for Lightning Strike!");
                        return;
                    }
                }
                awardMagicXp(player, MAGIC_XP_CAST);
                castLightning(player, magicLevel);
                return;
            } else {
                // ── Normal Fireball (left-click) ──────────────────────────────
                long cooldownMs = getCooldownMs(player, magicLevel);
                if (!checkAndSetCooldown(player.getUniqueId(), MagicElement.FIRE, cooldownMs)) {
                    long msLeft = msUntilReady(player.getUniqueId(), MagicElement.FIRE, cooldownMs);
                    player.sendActionBar("§c[Fire] §8cooldown: §e"
                            + String.format("%.1f", msLeft / 1000.0) + "s");
                    return;
                }
                if (!consumeRune(player, MagicElement.FIRE)) {
                    player.sendActionBar("§c✗ §7No §c🔥 Fire Rune§7 — craft from §e4x NETHER_BRICK§7.");
                    return;
                }
                awardMagicXp(player, MAGIC_XP_CAST);
                if (guidedPlayers.add(player.getUniqueId())) {
                    player.getInventory().addItem(itemFactory.buildMageGearGuide());
                    player.sendMessage("§5✦ §7You received a §5Mage Gear Guide§7! Check your inventory.");
                }
                castFire(player, magicLevel);
                return;
            }
        }

        // ── All other elements: right-click only ──────────────────────────────
        if (!isRightClick) return;

        if (element == MagicElement.AIR && !player.isOnGround()) {
            activateAirHover(player, magicLevel);
            return;
        }

        long cooldownMs = getCooldownMs(player, magicLevel);

        if (!checkAndSetCooldown(player.getUniqueId(), element, cooldownMs)) {
            long msLeft = msUntilReady(player.getUniqueId(), element, cooldownMs);
            player.sendActionBar(element.color + element.staffName
                    + " §8cooldown: §e" + String.format("%.1f", msLeft / 1000.0) + "s");
            return;
        }

        if (!consumeRune(player, element)) {
            player.sendActionBar("§c✗ §7No " + element.runeName
                    + " §7— craft from §e4x " + element.runeCraftIngredient.name() + "§7.");
            return;
        }

        awardMagicXp(player, MAGIC_XP_CAST);

        if (guidedPlayers.add(player.getUniqueId())) {
            player.getInventory().addItem(itemFactory.buildMageGearGuide());
            player.sendMessage("§5✦ §7You received a §5Mage Gear Guide§7! Check your inventory.");
        }

        castSpell(player, element, magicLevel, action, event.getClickedBlock());
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COOLDOWN
    // ══════════════════════════════════════════════════════════════════════════

    private long getCooldownMs(Player player, int magicLevel) {
        long cd = 3000L;
        cd -= (long) ((magicLevel / 99.0) * 2000L);
        cd -= itemFactory.getMageGearCooldownBonus(player);
        return Math.max(500L, cd);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPELL DISPATCH
    // ══════════════════════════════════════════════════════════════════════════

    private void castSpell(Player player, MagicElement element, int magicLevel,
                           Action action, Block clickedBlock) {
        if      (element == MagicElement.FIRE)  castFire(player, magicLevel);
        else if (element == MagicElement.WATER) castWater(player, magicLevel, action);
        else if (element == MagicElement.EARTH) castEarth(player, magicLevel);
        else if (element == MagicElement.AIR)   castAir(player, magicLevel);
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
        fb.setIsIncendiary(false);
        fb.setYield(0.0f);

        trackedProjectiles.put(fb.getUniqueId(), MagicElement.FIRE);
        projectileShooters.put(fb.getUniqueId(), player.getUniqueId());
        projectileLevels.put(fb.getUniqueId(), magicLevel);

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!fb.isValid()) { task.cancel(); return; }
            fb.getWorld().spawnParticle(Particle.FLAME, fb.getLocation(), 8, 0.12, 0.12, 0.12, 0.02);
            fb.getWorld().spawnParticle(Particle.LAVA,  fb.getLocation(), 2, 0.05, 0.05, 0.05, 0.0);
            fb.getWorld().spawnParticle(Particle.SMOKE, fb.getLocation(), 3, 0.1,  0.1,  0.1,  0.01);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 1.3f);
        player.sendActionBar("§c[Fire] §7Fireball launched! §8(Lv " + magicLevel + ")");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FIRE Lv99 — LIGHTNING STRIKE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Casts a lightning strike at the point the player is looking (up to 40 blocks).
     * Damages + scorches all entities within 3 × 4 × 3 blocks of the strike.
     *
     * Admin players ({@code difficultyengine.cape.admin}) have no cooldown and
     * no rune cost for this ability.
     */
    private void castLightning(Player player, int magicLevel) {
        // ── Find target location (raytrace 40 blocks) ────────────────────────
        RayTraceResult ray = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(), player.getLocation().getDirection(), 40,
            FluidCollisionMode.NEVER, true);

        Location strikeAt;
        if (ray != null && ray.getHitBlock() != null) {
            strikeAt = ray.getHitBlock().getLocation().add(0.5, 1.0, 0.5);
        } else {
            strikeAt = player.getEyeLocation()
                .add(player.getLocation().getDirection().multiply(40));
        }

        // ── Visual strike (no block damage) ───────────────────────────────────
        player.getWorld().strikeLightningEffect(strikeAt);

        // ── Damage & scorch entities nearby ───────────────────────────────────
        double baseDmg = 5.0 + (magicLevel / 99.0) * 7.0;
        for (Entity e : player.getWorld().getNearbyEntities(strikeAt, 3, 4, 3)) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(player)) continue;
            le.damage(baseDmg, player);
            applyScorched(le, 80);
            le.setFireTicks(60);
            le.getWorld().strikeLightningEffect(le.getLocation());
            le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                le.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
            if (le instanceof Player tp)
                tp.sendActionBar("§e⚡ §c§lLIGHTNING STRIKE! §7Struck by Lv99 Fire magic! §8(-" + (int)(baseDmg/2) + "❤)");
        }

        // ── Sounds & particles ────────────────────────────────────────────────
        player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, strikeAt, 60, 0.5, 2.0, 0.5, 0.1);
        player.getWorld().spawnParticle(Particle.FLAME, strikeAt, 20, 0.4, 1.0, 0.4, 0.05);
        player.getWorld().playSound(strikeAt, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.9f);
        player.getWorld().playSound(strikeAt, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,  1.0f, 1.0f);

        boolean isAdmin = player.hasPermission("difficultyengine.cape.admin");
        if (isAdmin) {
            player.sendActionBar("§e⚡ §c§l[ADMIN] LIGHTNING STRIKE! §8— No cooldown!");
        } else {
            player.sendActionBar("§e⚡ §c[Fire Lv99] §e§lLIGHTNING STRIKE! §8(" + (int)(baseDmg/2) + "❤ in 3-block radius)");
        }
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
                    player.sendActionBar("§b§7Need a Water Bucket to create a river!");
                    return;
                }
                placeWaterStream(player, ray.getHitBlock());
                player.sendActionBar("§b§7Water river placed! §8(5 blocks)");
                return;
            }
        }

        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.PRISMARINE_SHARD));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.5));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.WATER);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

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

    private boolean hasLavaBucket(Player player) {
        for (ItemStack i : player.getInventory().getContents())
            if (i != null && i.getType() == Material.LAVA_BUCKET) return true;
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
        // ── NEW TIER SYSTEM (Magic Level 10+): throw block from inventory ─────
        if (magicLevel >= 10) {
            EarthBlockTier tier = findBestEarthTier(player, magicLevel);
            if (tier == null) {
                // Refund the consumed earth rune — no valid block+page found
                player.getInventory().addItem(itemFactory.buildRune(MagicElement.EARTH, 1));
                // Tell the player exactly which Earth Magic Page to craft
                EarthBlockTier missingPage = null;
                for (EarthBlockTier t : EarthBlockTier.values()) {
                    if (magicLevel >= t.levelRequired && playerHasBlock(player, t.material)) {
                        missingPage = t;
                    }
                }
                if (missingPage != null) {
                    player.sendActionBar("§2[Earth] §7Craft §2Earth Page: " + missingPage.displayName
                        + " §8(Book + " + missingPage.material.name() + " + String)"
                        + " §7then §aright-click §7the page to read it!");
                } else {
                    player.sendActionBar("§2[Earth] §cNeed a throwable block + Earth Magic Page! "
                        + "§8Craft: §7Book + Block + String §8(Lv §a" + magicLevel + "§8+)");
                }
                return;
            }
            // Consume 1 block from inventory
            removeOneFromInventory(player, tier.material);

            Snowball bolt = player.launchProjectile(Snowball.class);
            bolt.setItem(new ItemStack(tier.material));
            bolt.setVelocity(player.getLocation().getDirection().multiply(2.2));

            trackedProjectiles.put(bolt.getUniqueId(), MagicElement.EARTH);
            projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
            projectileLevels.put(bolt.getUniqueId(), magicLevel);
            earthBoltTiers.put(bolt.getUniqueId(), tier);

            final EarthBlockTier t = tier;
            plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
                if (!bolt.isValid()) { task.cancel(); return; }
                bolt.getWorld().spawnParticle(Particle.BLOCK, bolt.getLocation(), 9,
                    0.12, 0.12, 0.12, t.material.createBlockData());
                bolt.getWorld().spawnParticle(Particle.SMOKE, bolt.getLocation(), 2,
                    0.06, 0.06, 0.06, 0.005);
            }, 0L, 1L);

            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.2f, 0.6f);
            player.sendActionBar("§2[Earth] §7Threw " + tier.displayName + "§7! §8(Lv§a" + magicLevel + "§8)");
            return;
        }

        // ── ORIGINAL SYSTEM (Level 1-9): simple dirt bolt ────────────────────
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.DIRT));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.2));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.EARTH);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);
        // No earthBoltTier entry → handleEarthHit uses old 2-hit system

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
        player.sendActionBar("§2[Earth] §7Earth bolt fired! §8(Reach §aMagic Lv 10§8 for block throwing)");
    }

    /** Returns the highest tier block the player can throw right now, or null. */
    private EarthBlockTier findBestEarthTier(Player player, int magicLevel) {
        EarthBlockTier best = null;
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            if (magicLevel < tier.levelRequired) continue;
            if (!playerHasBlock(player, tier.material)) continue;
            if (!itemFactory.hasEarthPage(player, tier)) continue;
            best = tier; // keep overwriting → highest valid tier wins
        }
        return best;
    }

    /** True if the player has ≥1 of the given material in inventory. */
    private boolean playerHasBlock(Player player, Material mat) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) return true;
        }
        return false;
    }

    /** Removes exactly 1 of the given material from the player's inventory. */
    private void removeOneFromInventory(Player player, Material mat) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else player.getInventory().remove(item);
                return;
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AIR STAFF
    // ══════════════════════════════════════════════════════════════════════════

    private void castAir(Player player, int magicLevel) {
        Snowball bolt = player.launchProjectile(Snowball.class);
        bolt.setItem(new ItemStack(Material.FEATHER));
        bolt.setVelocity(player.getLocation().getDirection().multiply(2.8));

        trackedProjectiles.put(bolt.getUniqueId(), MagicElement.AIR);
        projectileShooters.put(bolt.getUniqueId(), player.getUniqueId());
        projectileLevels.put(bolt.getUniqueId(), magicLevel);

        // AIR bolt uses vivid CYAN particles — visually distinct from all other elements
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!bolt.isValid()) { task.cancel(); return; }
            bolt.getWorld().spawnParticle(Particle.END_ROD, bolt.getLocation(), 4, 0.08, 0.08, 0.08, 0.018);
            bolt.getWorld().spawnParticle(Particle.DUST,    bolt.getLocation(), 3, 0.07, 0.07, 0.07, 0,
                    new Particle.DustOptions(Color.fromRGB(0, 220, 255), 1.4f));
            bolt.getWorld().spawnParticle(Particle.CLOUD,   bolt.getLocation(), 2, 0.06, 0.06, 0.06, 0.03);
        }, 0L, 1L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.8f);
        player.sendActionBar("§f[Air] §f⚡ Air bolt fired! §8(Lv " + magicLevel + ")");

        // Air gust also sweeps nearby magic dirt blocks
        for (int dy = 0; dy <= 1; dy++) {
            for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block b = player.getLocation().getBlock().getRelative(face).getRelative(0, dy, 0);
                if (b.getType() == Material.DIRT) {
                    b.getWorld().spawnParticle(Particle.BLOCK,
                        b.getLocation().add(0.5, 0.5, 0.5), 8, 0.3, 0.3, 0.3,
                        Material.DIRT.createBlockData());
                    magicDirtBlocks.remove(b.getLocation().toBlockLocation());
                    b.setType(Material.AIR);
                }
            }
        }
    }

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

        if (event.getHitBlock() != null) {
            handleBlockHit(event.getHitBlock(), event.getHitBlockFace(), element, shooter, lvl);
        }

        if (!(event.getHitEntity() instanceof LivingEntity target)) return;
        if (target.getUniqueId().equals(shooterId)) return;

        target.setMetadata(META_STAFF_HIT, new FixedMetadataValue(plugin,
            shooterId != null ? shooterId.toString() : ""));

        EarthBlockTier earthTier = earthBoltTiers.remove(projId);
        if      (element == MagicElement.FIRE)  handleFireHit(target, shooter, lvl);
        else if (element == MagicElement.WATER) handleWaterHit(target, shooter, lvl);
        else if (element == MagicElement.EARTH) handleEarthHit(target, shooter, lvl, earthTier);
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
            if (type == Material.WATER) {
                // Fire + water → evaporate
                block.setType(Material.AIR);
                block.getWorld().spawnParticle(Particle.CLOUD,
                    block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                block.getWorld().spawnParticle(Particle.SMOKE,
                    block.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.02);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.5f);
                if (shooter != null) shooter.sendActionBar("§c§7Fire evaporated the water!");
            } else if (face != null
                    && type == Material.DIRT
                    && magicDirtBlocks.contains(block.getLocation().toBlockLocation())
                    && shooter != null && lvl >= 50
                    && hasLavaBucket(shooter)
                    && itemFactory.hasSpellComboBook(shooter)) {
                // Fire + magic-dirt → lava pool (requires Lv50+, lava bucket, Spell Combo Book)
                magicDirtBlocks.remove(block.getLocation().toBlockLocation());
                block.setType(Material.AIR);
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().isAir()) {
                    adjacent.setType(Material.LAVA);
                    Location lavaLoc = adjacent.getLocation().toBlockLocation();
                    magicLavaBlocks.add(lavaLoc);
                    adjacent.getWorld().spawnParticle(Particle.FLAME,
                        adjacent.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                    adjacent.getWorld().spawnParticle(Particle.LAVA,
                        adjacent.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0.0);
                    adjacent.getWorld().playSound(adjacent.getLocation(), Sound.BLOCK_LAVA_AMBIENT, 1.0f, 0.8f);
                    shooter.sendActionBar("§c§7Magic dirt ignited! §c§lLAVA pool §7(30s) — §8Water = Obsidian trap!");
                    final Block lavaBlock = adjacent;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (lavaBlock.getType() == Material.LAVA
                                && magicLavaBlocks.contains(lavaBlock.getLocation().toBlockLocation())) {
                            lavaBlock.setType(Material.AIR);
                            magicLavaBlocks.remove(lavaBlock.getLocation().toBlockLocation());
                        }
                    }, 600L);
                }
            }

        } else if (element == MagicElement.WATER) {
            // Water + lava → obsidian trap (BFS)
            if (type == Material.LAVA) {
                int converted = convertLavaToObsidian(block, shooter);
                if (shooter != null && converted > 0) {
                    if (itemFactory.hasSpellComboBook(shooter)) {
                        shooter.sendActionBar("§b§8§lOBSIDIAN TRAP! §7Converted §e" + converted + " §7lava blocks!");
                    } else {
                        shooter.sendActionBar("§b§7Lava solidified!");
                    }
                }
            }

        } else if (element == MagicElement.EARTH) {
            if (type == Material.LAVA) {
                // Earth + lava → smother/extinguish
                block.setType(Material.AIR);
                magicLavaBlocks.remove(block.getLocation().toBlockLocation());
                block.getWorld().spawnParticle(Particle.BLOCK,
                    block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3,
                    Material.DIRT.createBlockData());
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 1.0f, 0.8f);
                if (shooter != null) shooter.sendActionBar("§2§7Earth smothered the lava!");
            } else if (type == Material.WATER) {
                // Earth + water → quicksand (soul sand)
                block.setType(Material.SOUL_SAND);
                quicksandBlocks.add(block.getLocation().toBlockLocation());
                block.getWorld().spawnParticle(Particle.BLOCK,
                    block.getLocation().add(0.5, 1.0, 0.5), 25, 0.4, 0.3, 0.4,
                    Material.SOUL_SAND.createBlockData());
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_SAND_PLACE, 1.0f, 0.7f);
                if (shooter != null)
                    shooter.sendActionBar("§2§7Water became §8Quicksand§7! §8Air bolt = Sandstorm!");
            } else if (face != null) {
                // Earth + solid block → place magic dirt on face
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().isAir()) {
                    adjacent.setType(Material.DIRT);
                    magicDirtBlocks.add(adjacent.getLocation().toBlockLocation());
                    adjacent.getWorld().spawnParticle(Particle.BLOCK,
                        adjacent.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3,
                        Material.DIRT.createBlockData());
                    final Block b = adjacent;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (b.getType() == Material.DIRT) b.setType(Material.AIR);
                        magicDirtBlocks.remove(b.getLocation().toBlockLocation());
                    }, 600L);
                }
            }

        } else if (element == MagicElement.AIR) {
            if (type == Material.SOUL_SAND && quicksandBlocks.contains(block.getLocation().toBlockLocation())) {
                // Air + quicksand → sandstorm
                quicksandBlocks.remove(block.getLocation().toBlockLocation());
                if (sandstormManager != null) {
                    sandstormManager.triggerSandstorm(block.getLocation(), shooter);
                    if (shooter != null)
                        shooter.sendActionBar("§6§f§lSANDSTORM TRIGGERED! §7The quicksand erupts!");
                }
            } else if (type == Material.LAVA) {
                // Air + lava → extinguish
                block.setType(Material.AIR);
                magicLavaBlocks.remove(block.getLocation().toBlockLocation());
                block.getWorld().spawnParticle(Particle.CLOUD,
                    block.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.05);
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
                if (shooter != null) shooter.sendActionBar("§7☁ §7Air gust extinguished the lava!");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  handleFireHit
    // ══════════════════════════════════════════════════════════════════════════

    private void handleFireHit(LivingEntity target, Player shooter, int lvl) {

        // Fire slime: convert slime to fire slime
        if (target instanceof org.bukkit.entity.Slime slime) {
            slime.setMetadata("magic_fire_slime",
                new FixedMetadataValue(plugin, System.currentTimeMillis() + 30_000L));
            slime.setFireTicks(Integer.MAX_VALUE);
            slime.getWorld().spawnParticle(Particle.FLAME,
                slime.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.05);
            slime.getWorld().spawnParticle(Particle.SMOKE,
                slime.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.03);
            if (shooter != null)
                shooter.sendActionBar("§c§7Fire slime! §8Sets players on fire on contact!");
            return;
        }

        // BLAZING + FIRE = INFERNO VORTEX
        if (isBlazing(target)) {
            removeBlazing(target);
            int fire = 160 + (int)((lvl / 99.0) * 120);
            target.setFireTicks(fire);
            double dmg = 5.0 + SkillBonusManager.magicDamageBonus(lvl) * 5;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME,
                target.getLocation().add(0, 1, 0), 80, 0.6, 0.6, 0.6, 0.25);
            target.getWorld().spawnParticle(Particle.SMOKE,
                target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.4f);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§c§f§l§oINFERNO VORTEX! §7Blazing + Fire = devastation!");
                } else {
                    shooter.sendActionBar("§c§f§lINFERNO VORTEX!");
                }
            }
            if (target instanceof Player tp)
                tp.sendTitle("§c§l§oINFERNO VORTEX", "§7Consumed by absolute fire!", 5, 60, 15);
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            rollMindBomb(target, shooter);
            return;
        }

        // SCORCHED + FIRE = BLAZING
        if (isScorched(target)) {
            removeScorched(target);
            applyBlazing(target, 100);
            int fire = 80 + (int)((lvl / 99.0) * 80);
            target.setFireTicks(fire);
            double dmg = 3.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME,
                target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.2);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§c§f§lBLAZING! §8(" + fire/20 + "s fire - Air to Inferno Blast!)");
                } else {
                    shooter.sendActionBar("§c§f§lBLAZING! §8(" + fire/20 + "s fire)");
                }
            }
            if (target instanceof Player tp)
                tp.sendActionBar("§c§f§lBLAZING! §7Air gust = Inferno Blast!");
            rollMindBomb(target, shooter);
            return;
        }

        // MUDDY + FIRE = STATUE
        if (isMuddy(target)) {
            removeMuddy(target);
            target.setFireTicks(0);
            applyStatue(target, 160);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 0.5, 0), 80, 0.6, 0.6, 0.6,
                Material.DIRT.createBlockData());
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1.0, 0), 50, 0.4, 0.4, 0.4,
                Material.MUD.createBlockData());
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1.5, 0), 30, 0.3, 0.3, 0.3,
                Material.BROWN_TERRACOTTA.createBlockData());
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_MUD_BREAK, 2.0f, 0.6f);
            if (shooter != null) {
                if (itemFactory.hasAncientKillTome(shooter)) {
                    shooter.sendActionBar("§6§e§lSTATUE! §7Mud hardened - target frozen! §8Air gust = §c§lDEATH!");
                } else {
                    shooter.sendActionBar("§6§e§lSTATUE! §7Mud hardened - target immobilised! §8(8s)");
                }
            }
            if (target instanceof Player tp) {
                tp.sendTitle("§6§e§lSTATUE", "§7Hardened mud - Air gust = DEATH!", 5, 80, 15);
                tp.sendActionBar("§6§7You are §e§lSTATUE§7! §8(8s - air gust = death!)");
            }
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // WET + FIRE = extinguish
        if (isWet(target)) {
            removeWet(target, true);
            target.setFireTicks(0);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> target.setFireTicks(0), 1L);
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1.5, 0), 20, 0.4, 0.4, 0.4, 0.05);
            if (target instanceof Player p)
                p.sendActionBar("§b§7Water extinguished the fireball!");
            if (shooter != null)
                shooter.sendActionBar("§b§7Target was wet - fire extinguished!");
            return;
        }

        // CHILLED + FIRE = Thaw
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
                shooter.sendActionBar("§b§7Thaw! Chill removed by fire - steam burst!");
            rollMindBomb(target, shooter);
            return;
        }

        // FROZEN + FIRE = Thaw Explosion
        if (isFrozen(target)) {
            removeFrozen(target);
            target.setFireTicks(0);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0.15);
            target.getWorld().spawnParticle(Particle.FLAME,
                target.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0.1);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
            if (shooter != null)
                shooter.sendActionBar("§c§f§lTHAW EXPLOSION! §7Frozen + Fire = massive steam burst!");
            rollMindBomb(target, shooter);
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            return;
        }

        // Normal fire hit -> SCORCHED
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        int fire = 40 + (int)((lvl / 99.0) * 40);
        applyScorched(target, 60);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) target.setFireTicks(Math.max(target.getFireTicks(), fire));
        }, 1L);
        if (shooter != null) {
            if (itemFactory.hasSpellComboBook(shooter)) {
                shooter.sendActionBar("§c§7Fireball hit! §8Scorched §8(" + fire/20 + "s - hit again to Blaze!)");
            } else {
                shooter.sendActionBar("§c§7Fireball hit! §8(Scorched " + fire/20 + "s)");
            }
        }
        rollMindBomb(target, shooter);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPELL DEFLECTION
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpellDeflect(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player hitter)) return;

        UUID projId = event.getEntity().getUniqueId();
        if (!trackedProjectiles.containsKey(projId)) return;

        ItemStack hand = hitter.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_SWORD")) return;

        event.setCancelled(true);

        Vector deflect = event.getEntity().getLocation()
            .subtract(hitter.getEyeLocation()).toVector().normalize().multiply(2.8);
        if (deflect.getY() < 0.1) deflect.setY(0.1);
        event.getEntity().setVelocity(deflect);

        projectileShooters.put(projId, hitter.getUniqueId());

        hitter.getWorld().playSound(hitter.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 2.0f);
        hitter.getWorld().spawnParticle(Particle.CRIT,
            event.getEntity().getLocation(), 12, 0.3, 0.3, 0.3, 0.2);
        hitter.sendActionBar("§f§lDEFLECTED! §7The spell flies back!");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Fire slime on-contact damage
    // ══════════════════════════════════════════════════════════════════════════

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
            hit.setFireTicks(80);
            hit.getWorld().spawnParticle(Particle.FLAME,
                hit.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            if (hit instanceof Player hp)
                hp.sendActionBar("§c§7The fire slime set you on fire!");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  handleWaterHit
    // ══════════════════════════════════════════════════════════════════════════

    private void handleWaterHit(LivingEntity target, Player shooter, int lvl) {

        // Water slime: 15% chance to split
        if (target instanceof org.bukkit.entity.Slime slime) {
            if (RAND.nextInt(100) < 15) {
                int extra = RAND.nextInt(2) + 1;
                for (int i = 0; i < extra; i++) {
                    org.bukkit.entity.Slime baby = (org.bukkit.entity.Slime)
                        slime.getWorld().spawnEntity(slime.getLocation(), org.bukkit.entity.EntityType.SLIME);
                    baby.setSize(Math.max(1, slime.getSize() - 1));
                    baby.setVelocity(new Vector(
                        (RAND.nextDouble() - 0.5) * 0.6, 0.35,
                        (RAND.nextDouble() - 0.5) * 0.6));
                }
                slime.getWorld().spawnParticle(Particle.SPLASH,
                    slime.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1);
                if (shooter != null)
                    shooter.sendActionBar("§b§7Water split the slime! §8(+" + extra + " slime!)");
            }
        }

        // FROZEN + WATER = SLUSH
        if (isFrozen(target)) {
            removeFrozen(target);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 50, 0.4, 0.4, 0.4, 0.2);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 1.2f);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§b§f§lSLUSH! §7Ice shattered - Slowness + Blindness!");
                } else {
                    shooter.sendActionBar("§b§f§lSLUSH!");
                }
            }
            if (target instanceof Player tp)
                tp.sendActionBar("§b§f§lSLUSH! §7Frozen and soaked - barely able to move!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            rollMindBomb(target, shooter);
            return;
        }

        // MUDDY + WATER = FLOOD WASH
        if (isMuddy(target)) {
            removeMuddy(target);
            double dmg = 3.5 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            int wetTicks2 = 120 + (int)((lvl / 99.0) * 80);
            applyWet(target, wetTicks2);
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 80, 0.6, 0.6, 0.6, 0.3);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, Material.MUD.createBlockData());
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_BUCKET_FILL, 1.2f, 0.6f);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§b§f§lFLOOD WASH! §7Mud washed away - target soaked again!");
                } else {
                    shooter.sendActionBar("§b§f§lFLOOD WASH!");
                }
            }
            if (target instanceof Player tp)
                tp.sendActionBar("§b§7The mud was washed away - you are Wet again!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            rollMindBomb(target, shooter);
            return;
        }

        // BLAZING + WATER = STEAM EXPLOSION (AoE)
        if (isBlazing(target)) {
            removeBlazing(target);
            target.setFireTicks(0);
            double dmg = 5.0 + SkillBonusManager.magicDamageBonus(lvl) * 5;
            target.damage(dmg, shooter);
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
                shooter.sendActionBar("§b§f§lSTEAM EXPLOSION! §7Blazing target superheated!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // SCORCHED + WATER = STEAM BURST
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
                shooter.sendActionBar("§b§7Steam Burst! §8Scorched + Water = superheated impact!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // Normal water hit -> WET
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        int wetTicks = 100 + (int)((lvl / 99.0) * 100);
        applyWet(target, wetTicks);
        target.getWorld().spawnParticle(Particle.SPLASH,
            target.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.2);
        if (shooter != null) {
            if (itemFactory.hasSpellComboBook(shooter)) {
                shooter.sendActionBar("§b§7Water hit! §bWet §7(" + wetTicks/20 + "s - Earth=Muddy, Air=Chilled)");
            } else {
                shooter.sendActionBar("§b§7Water hit! §8(" + wetTicks/20 + "s WET)");
            }
        }
        if (target instanceof Player p)
            p.sendActionBar("§b§7You are Wet! §8Earth=Muddy · Air=Chilled · Fire=Extinguish");
        rollMindBomb(target, shooter);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  handleEarthHit
    // ══════════════════════════════════════════════════════════════════════════

    private void handleEarthHit(LivingEntity target, Player shooter, int lvl, EarthBlockTier tier) {
        // Particles matching the thrown block type
        Material particleMat = (tier != null) ? tier.material : Material.DIRT;
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 1, 0), 30, 0.3, 0.3, 0.3,
            particleMat.createBlockData());

        // ── Element combos always take priority ───────────────────────────────
        // BLAZING + EARTH = SMOTHERED
        if (isBlazing(target)) {
            removeBlazing(target);
            target.setFireTicks(0);
            double dmg = 4.5 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 80, 0.6, 0.6, 0.6, Material.DIRT.createBlockData());
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, Material.COARSE_DIRT.createBlockData());
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GRAVEL_PLACE, 2.0f, 0.5f);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§2§f§lSMOTHERED! §7Dirt extinguished the blaze - heavy damage!");
                } else {
                    shooter.sendActionBar("§2§f§lSMOTHERED!");
                }
            }
            if (target instanceof Player tp)
                tp.sendActionBar("§2§7The fire was smothered by dirt!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            rollMindBomb(target, shooter);
            return;
        }

        // WET + EARTH = MUDDY
        if (isWet(target)) {
            removeWet(target, false);
            int muddyTicks = 300 + (int)((lvl / 99.0) * 300);
            applyMuddy(target, muddyTicks);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§6§7Wet + Earth = §6Muddy! §8(" + muddyTicks/20 + "s - Fire to Statue!)");
                } else {
                    shooter.sendActionBar("§6§7Muddy! §8(" + muddyTicks/20 + "s)");
                }
            }
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // CHILLED + EARTH = CRACKED ICE
        if (isChilled(target)) {
            removeChilled(target);
            double dmg = 3.5 + SkillBonusManager.magicDamageBonus(lvl) * 3;
            target.damage(dmg, shooter);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5, false, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, Material.ICE.createBlockData());
            if (shooter != null)
                shooter.sendActionBar("§b§f§lCRACKED ICE! §7Chilled + Earth = blinded + heavy slow!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // STATUE + EARTH = CRUMBLE
        if (isStatue(target)) {
            removeStatue(target);
            double dmg = 4.0 + SkillBonusManager.magicDamageBonus(lvl) * 4;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 60, 0.6, 0.6, 0.6, Material.DIRT.createBlockData());
            if (shooter != null)
                shooter.sendActionBar("§6§f§lCRUMBLE! §7Earth smashed the statue!");
            awardMagicXp(shooter, MAGIC_XP_COMBO - MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // ── TIER SYSTEM (Level 10+): trap + suffocate ─────────────────────────
        if (tier != null) {
            // Place the thrown block at target's feet (also tracked for fire→lava combo).
            // Blocks are PERMANENT — players can use Silk Touch to recover expensive blocks.
            Block feetBlock = target.getLocation().getBlock();
            if (feetBlock.getType().isAir()) {
                feetBlock.setType(tier.material);
                magicDirtBlocks.add(feetBlock.getLocation().toBlockLocation());
            }
            // Clean up trap state after 5s (the block stays, but the "trapped" debuff expires)
            final UUID targetId = target.getUniqueId();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                activeTraps.remove(targetId);
                target.removeMetadata(META_EARTH_TRAPPED, plugin);
            }, 100L);

            // 2nd hit while TRAPPED → SUFFOCATE
            if (target.hasMetadata(META_EARTH_TRAPPED)) {
                EarthBlockTier trapTier = activeTraps.getOrDefault(target.getUniqueId(), tier);
                target.removeMetadata(META_EARTH_TRAPPED, plugin);
                activeTraps.remove(target.getUniqueId());

                target.damage(trapTier.suffocateDamage, shooter);
                suffocateTarget(target, shooter, lvl);

                int hearts = (int)(trapTier.suffocateDamage / 2);
                if (shooter != null)
                    shooter.sendActionBar("§2§f§lSUFFOCATED! §7" + trapTier.displayName +
                            " §7crushed them! §8(§c-" + hearts + "❤§8)");
                if (target instanceof Player tp)
                    tp.sendTitle("§2§lSUFFOCATED", "§7Crushed by " + trapTier.displayName, 3, 40, 10);
                if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
                rollMindBomb(target, shooter);
                return;
            }

            // 1st hit: TRAP
            target.damage(tier.trapDamage, shooter);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, true, true));
            activeTraps.put(target.getUniqueId(), tier);
            target.setMetadata(META_EARTH_TRAPPED, new FixedMetadataValue(plugin, System.currentTimeMillis() + 5000L));

            int trapHearts = (int)(tier.trapDamage / 2);
            if (shooter != null)
                shooter.sendActionBar("§2§7" + tier.displayName + " §7trapped them! §8(§c-" + trapHearts +
                        "❤§8 — hit again to §f§lSUFFOCATE§8!)");
            if (target instanceof Player tp)
                tp.sendActionBar("§2§7TRAPPED by " + tier.displayName + "§7! §8(Hit again = SUFFOCATE)");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_HIT);
            rollMindBomb(target, shooter);
            return;
        }

        // ── ORIGINAL SYSTEM (Level 1-9): 2-hit suffocate ─────────────────────
        // Dirt placed at feet is permanent (Silk Touch recoverable).
        Block feetBlock = target.getLocation().getBlock();
        if (feetBlock.getType().isAir()) {
            feetBlock.setType(Material.DIRT);
            magicDirtBlocks.add(feetBlock.getLocation().toBlockLocation());
        }

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
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            target.removeMetadata(META_EARTH_HITS, plugin), 200L);

        // Normal earth hit
        double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
        target.damage(dmg, shooter);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
        if (shooter != null) {
            if (itemFactory.hasSpellComboBook(shooter)) {
                shooter.sendActionBar("§2§7Earth hit! §8(" + (dmg/2) + "❤) - hit §bWet§8 target to make §6Muddy");
            } else {
                shooter.sendActionBar("§2§7Earth hit! §8(" + (dmg/2) + "❤)");
            }
        }
        rollMindBomb(target, shooter);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  handleAirHit
    // ══════════════════════════════════════════════════════════════════════════

    private void handleAirHit(LivingEntity target, Player shooter, int lvl) {
        if (isFrozen(target)) {
            killFrozen(target, shooter, "§b§c§lSHATTERED! §7Frozen solid - launched into the ground!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        if (isStatue(target)) {
            killStatue(target, shooter, "§6§c§lCRUMBLED! §7The statue was blasted apart!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        if (isChilled(target)) {
            removeChilled(target);
            applyFrozen(target, 100);
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
            if (shooter != null) {
                if (itemFactory.hasAncientKillTome(shooter)) {
                    shooter.sendActionBar("§b§f§lFROZEN! §7Chilled target locked solid! §8Air gust = §c§lDEATH!");
                } else {
                    shooter.sendActionBar("§b§f§lFROZEN! §7Chilled target locked solid! §8(5s)");
                }
                awardMagicXp(shooter, MAGIC_XP_COMBO);
                awardMagicXp(shooter, MAGIC_XP_HIT);
            }
            return;
        }

        if (isWet(target)) {
            removeWet(target, true);
            applyChilled(target, 50);
            if (shooter != null) {
                if (itemFactory.hasSpellComboBook(shooter)) {
                    shooter.sendActionBar("§b§7Target is §bChilled§7! §8Cast Air again quickly to Freeze!");
                } else {
                    shooter.sendActionBar("§b§7Target is §bChilled§7!");
                }
                awardMagicXp(shooter, MAGIC_XP_HIT);
            }
            return;
        }

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
            if (shooter != null) shooter.sendActionBar("§6§f§lMUD LAUNCH! §7Muddy target catapulted skyward!");
            if (target instanceof Player tp) tp.sendActionBar("§6§7Mud expelled you into the sky!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

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
            if (shooter != null) shooter.sendActionBar("§c§f§lINFERNO BLAST! §7Blazing target obliterated!");
            if (target instanceof Player tp) tp.sendActionBar("§c§f§lINFERNO BLAST §7ripped through you!");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        if (target instanceof Player tp) {
            ItemStack targetHand = tp.getInventory().getItemInMainHand();
            if (itemFactory.getStaffElement(targetHand) == MagicElement.AIR
                    && tp.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                tp.getWorld().spawnParticle(Particle.CLOUD,
                    tp.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.1);
                tp.getWorld().playSound(tp.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 2.0f);
                tp.sendActionBar("§7☁ §fYour Air staff deflected the gust!");
                if (shooter != null) shooter.sendActionBar("§7☁ §7Their Air staff negated your gust!");
                return;
            }
        }

        if (isScorched(target)) {
            removeScorched(target);
            int fire = 60 + (int)((lvl / 99.0) * 80);
            target.setFireTicks(fire);
            double dmg = 2.0 + SkillBonusManager.magicDamageBonus(lvl) * 2;
            target.damage(dmg, shooter);
            target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.15);
            if (shooter != null) shooter.sendActionBar("§c§7Fanned the flames! §8(" + fire/20 + "s fire)");
            if (shooter != null) awardMagicXp(shooter, MAGIC_XP_COMBO);
            return;
        }

        // Normal air gust
        double dist    = shooter != null ? Math.max(0.5, target.getLocation().distance(shooter.getLocation())) : 5.0;
        double kb      = 3.0 + (AIR_RANGE - dist) * 1.4;
        double mult    = 1.0 + (lvl / 99.0) * 2.5;
        double gearMul = getAirGearMultiplier(shooter);
        double vel     = kb * 0.22 * mult * gearMul;
        double up      = (0.4 + (lvl / 99.0) * 0.5 + (kb / (AIR_RANGE * 1.4)) * 0.35) * gearMul;

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

    // ══════════════════════════════════════════════════════════════════════════
    //  MIND BOMB + FALLEN
    // ══════════════════════════════════════════════════════════════════════════

    private void rollMindBomb(LivingEntity target, Player shooter) {
        if (shooter == null) return;
        if (itemFactory.countMageGearPieces(shooter) < 2) return;
        if (RAND.nextInt(100) >= 5) return;
        if (isMindBombed(target)) return;
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
            tp.sendTitle("§5§dMIND BOMB", "§7Disoriented!", 5, 40, 10);
            tp.sendActionBar("§5§7You've been Mind Bombed! §8Nausea + Blind 5s");
        }
        if (shooter != null)
            shooter.sendActionBar("§5§d§lMIND BOMB! §7Mage gear proc - target disoriented!");
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

    // ══════════════════════════════════════════════════════════════════════════
    //  FALLEN
    // ══════════════════════════════════════════════════════════════════════════

    private void applyFallen(Player player) {
        player.setMetadata(META_FALLEN, new FixedMetadataValue(plugin, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       60, 255, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 60, 255, false, false, false));
        player.sendTitle("§c§l§oFALLEN!", "§7Press §fSPACE §7to get up!", 5, 50, 10);
        player.sendActionBar("§c§7You fell! Press §fSPACE §7to get up!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f);
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isValid() || !isFallen(player)) { cancelFallenTask(player.getUniqueId()); return; }
            player.setSwimming(true);
        }, 0L, 2L);
        fallenTasks.put(player.getUniqueId(), task);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isValid() && isFallen(player)) getUpFromFallen(player, false);
        }, 60L);
    }

    private boolean isFallen(Player player) { return player.hasMetadata(META_FALLEN); }

    private void getUpFromFallen(Player player, boolean pressedSpace) {
        player.removeMetadata(META_FALLEN, plugin);
        cancelFallenTask(player.getUniqueId());
        player.setSwimming(false);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        if (pressedSpace) {
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
            player.getWorld().spawnParticle(Particle.CRIT,
                player.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0.2);
            player.sendTitle("§a§l✔ GOT UP!", "", 3, 15, 7);
            player.sendActionBar("§a§7You got up!");
        } else {
            player.sendActionBar("§7The daze wore off.");
        }
    }

    private void cancelFallenTask(UUID uuid) {
        BukkitTask t = fallenTasks.remove(uuid);
        if (t != null) t.cancel();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!isFallen(p)) return;
        if (event.getTo() == null) return;
        if (event.getTo().getY() > event.getFrom().getY() + 0.05) {
            getUpFromFallen(p, true);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  STATUS EFFECT HELPERS
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

    public void applyMuddy(LivingEntity e, int ticks) {
        e.setMetadata(META_MUDDY, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, false, true, true));
        e.getWorld().spawnParticle(Particle.BLOCK,
            e.getLocation().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, Material.MUD.createBlockData());
        if (e instanceof Player p)
            p.sendActionBar("§6§7You are §6Muddy§7! §8(Slowness IV - Fire=Statue · Air=Launch)");
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

    public void applyChilled(LivingEntity e, int ticks) {
        e.setMetadata(META_CHILLED, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 2, false, true, true));
        e.getWorld().spawnParticle(Particle.SNOWFLAKE,
            e.getLocation().add(0, e.getHeight() + 0.3, 0), 12, 0.3, 0.1, 0.3, 0.0);
        if (e instanceof Player p)
            p.sendActionBar("§b§7You are §bChilled§7! §8(Air again = Frozen · Earth = Cracked Ice)");
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

    public void applyFrozen(LivingEntity e, int ticks) {
        e.setMetadata(META_FROZEN, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.setFreezeTicks(200);
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       ticks, 255, false, true, true));
        e.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 255, false, true, true));
        e.getWorld().spawnParticle(Particle.SNOWFLAKE,
            e.getLocation().add(0, 1, 0), 60, 0.5, 0.7, 0.5, 0.1);
        e.getWorld().spawnParticle(Particle.ITEM,
            e.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.1, new ItemStack(Material.ICE));
        if (e instanceof Player p) {
            p.sendTitle("§b§lFROZEN", "§7Air gust = instant death!", 5, 60, 15);
            p.sendActionBar("§b§b§lFROZEN§7! §8(5s - air gust = §c§lINSTANT DEATH!)");
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
            tp.sendTitle("§b§c§lSHATTERED", "§7Frozen solid - hit the ground!", 5, 40, 15);
        } else {
            target.setHealth(0);
        }
        if (shooter != null) shooter.sendActionBar(shooterMsg);
    }

    public void applyStatue(LivingEntity e, int ticks) {
        e.setMetadata(META_STATUE, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       ticks, 255, false, true, true));
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
            tp.sendTitle("§6§c§lCRUMBLED", "§7The statue was blasted apart!", 5, 40, 15);
        } else {
            target.setHealth(0);
        }
        if (shooter != null) shooter.sendActionBar(shooterMsg);
    }

    public void applyScorched(LivingEntity e, int ticks) {
        e.setMetadata(META_SCORCHED, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.getWorld().spawnParticle(Particle.FLAME,
            e.getLocation().add(0, e.getHeight(), 0), 6, 0.3, 0.1, 0.3, 0.02);
        if (e instanceof Player p)
            p.sendActionBar("§c§7You are §cScorched§7! §8(Fire again = Blaze · Air = Fanned Flames)");
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

    public void removeScorched(LivingEntity e) { e.removeMetadata(META_SCORCHED, plugin); }

    public void applyBlazing(LivingEntity e, int ticks) {
        e.setMetadata(META_BLAZING, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + (long) ticks * 50));
        e.getWorld().spawnParticle(Particle.FLAME,
            e.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.08);
        if (e instanceof Player p) {
            p.sendTitle("§c§lBLAZING", "§7Air gust = §cInferno Blast§7!", 5, 40, 10);
            p.sendActionBar("§c§c§lBLAZING§7! §8(Air = Inferno Blast · Water = Steam Explosion)");
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

    public void removeBlazing(LivingEntity e) { e.removeMetadata(META_BLAZING, plugin); }

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
        Map<MagicElement, Long> map = cooldowns.computeIfAbsent(id, k -> new EnumMap<>(MagicElement.class));
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
            player.sendMessage("§6§e" + SkillType.MAGIC.colored()
                + " §elevel up! §8(§f" + old + " §8-> §a" + nw + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(nw));
            long newCd = getCooldownMs(player, nw);
            player.sendMessage("  §d§7Spell cooldown: §a" + String.format("%.1f", newCd / 1000.0) + "s");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SUFFOCATE
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
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       100, 255, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 255, false, true, true));
        target.getWorld().spawnParticle(Particle.BLOCK,
            loc.add(0, 1, 0), 80, 0.4, 0.6, 0.4, Material.DIRT.createBlockData());
        target.getWorld().playSound(loc, Sound.BLOCK_GRAVEL_PLACE, 2.0f, 0.5f);
        if (shooter != null) shooter.sendActionBar("§2§f§lSUFFOCATED! §7Target buried in earth!");
        if (target instanceof Player tp) {
            tp.sendTitle("§2§l§oBURIED!", "§7Dig out - or suffocate!", 5, 60, 15);
            tp.sendActionBar("§2§7You are Buried in Earth!");
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            placed.forEach(b -> { if (b.getType() == Material.DIRT) b.setType(Material.AIR); }), 100L);
    }

    private void launchTarget(LivingEntity target, Player player, double velocity, double upward) {
        Vector dir = target.getLocation().subtract(player.getLocation()).toVector().normalize();
        dir.setY(upward);
        target.setVelocity(dir.multiply(velocity / Math.max(0.01, dir.length())));
    }

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

    // ══════════════════════════════════════════════════════════════════════════
    //  AIR HOVER
    // ══════════════════════════════════════════════════════════════════════════

    private void activateAirHover(Player player, int magicLevel) {
        UUID uid = player.getUniqueId();
        BukkitTask old = airHoverTasks.remove(uid);
        if (old != null) old.cancel();

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,   10, 0, false, false, false));

        player.getWorld().spawnParticle(Particle.CLOUD,
            player.getLocation().add(0, 0.5, 0), 8, 0.4, 0.1, 0.4, 0.02);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.6f, 2.0f);
        player.sendActionBar("§7 §fHovering... §8(land to stop · tap on ground for air blast)");

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) { cancelHoverTask(uid); return; }
            if (player.isOnGround()) { cancelHoverTask(uid); return; }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, true, true));
        }, 5L, 5L);
        airHoverTasks.put(uid, task);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> cancelHoverTask(uid), 80L);
    }

    private void cancelHoverTask(UUID uid) {
        BukkitTask t = airHoverTasks.remove(uid);
        if (t != null) t.cancel();
    }

    private double getAirGearMultiplier(Player shooter) {
        if (shooter == null) return 0.50;
        double power = itemFactory.getAirGearPower(shooter);
        return 0.50 + (power / 8.0) * 1.50;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LAVA -> OBSIDIAN BFS
    // ══════════════════════════════════════════════════════════════════════════

    private int convertLavaToObsidian(Block startBlock, Player shooter) {
        Queue<Block>  queue   = new LinkedList<>();
        Set<Location> visited = new HashSet<>();
        queue.add(startBlock);
        visited.add(startBlock.getLocation().toBlockLocation());
        int converted = 0;
        int maxBlocks = 30;

        while (!queue.isEmpty() && converted < maxBlocks) {
            Block b = queue.poll();
            if (b.getType() == Material.LAVA) {
                b.setType(Material.OBSIDIAN);
                magicLavaBlocks.remove(b.getLocation().toBlockLocation());
                b.getWorld().spawnParticle(Particle.BLOCK,
                    b.getLocation().add(0.5, 1.0, 0.5), 5, 0.3, 0.3, 0.3,
                    Material.OBSIDIAN.createBlockData());
                converted++;
                for (BlockFace f : new BlockFace[]{
                        BlockFace.NORTH, BlockFace.SOUTH,
                        BlockFace.EAST,  BlockFace.WEST,
                        BlockFace.UP,    BlockFace.DOWN}) {
                    Block adj = b.getRelative(f);
                    Location adjLoc = adj.getLocation().toBlockLocation();
                    if (!visited.contains(adjLoc) && adj.getType() == Material.LAVA) {
                        visited.add(adjLoc);
                        queue.add(adj);
                    }
                }
            }
        }

        if (converted > 0) {
            startBlock.getWorld().playSound(startBlock.getLocation(),
                Sound.BLOCK_LAVA_EXTINGUISH, 1.5f, 0.7f);
            startBlock.getWorld().spawnParticle(Particle.CLOUD,
                startBlock.getLocation().add(0.5, 1.5, 0.5), 30, 1.5, 0.5, 1.5, 0.05);
        }
        return converted;
    }
    // ══════════════════════════════════════════════════════════════════════════
    //  EARTH MAGIC PAGE — RIGHT-CLICK TO READ
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * When a player right-clicks while holding an Earth Magic Page (a BOOK item
     * with our PDC key), open a written-book GUI showing what that page does.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onReadEarthPage(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.BOOK) return;
        if (!hand.hasItemMeta()) return;

        var pdc = hand.getItemMeta().getPersistentDataContainer();
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            org.bukkit.NamespacedKey pageKey = itemFactory.getEarthPageKey(tier);
            if (pageKey == null || !pdc.has(pageKey, org.bukkit.persistence.PersistentDataType.BYTE)) continue;
            event.setCancelled(true);
            player.openBook(buildEarthPageReadable(tier));
            return;
        }
    }

    /**
     * Builds a temporary WRITTEN_BOOK describing the given Earth Magic Page tier.
     * Opened via {@link Player#openBook(ItemStack)} — displayed only, not given to the player.
     */
    private ItemStack buildEarthPageReadable(EarthBlockTier tier) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("§2Earth Magic Page");
            meta.setAuthor("DifficultyEngine");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage(
                "§2§l─ Earth Magic Page ─\n\n" +
                "§7Tier: " + tier.displayName + "\n" +
                "§8Req: §aMagic Lv " + tier.levelRequired + "\n\n" +
                "§7Carry this page in\nyour inventory to\nthrow §2" + tier.displayName +
                "§7 blocks\nwith the §2Earth Staff§7.\n\n" +
                "§8Not consumed — keep\nit in your bag!"
            );
            meta.addPage(
                "§2§l─ Block Stats ─\n\n" +
                "§7Block: §f" + tier.material.name() + "\n\n" +
                "§61st hit (TRAP):\n" +
                "§c  " + (int)(tier.trapDamage/2) + " ❤  §7+ §cSlowness\n\n" +
                "§62nd hit (SUFFOCATE):\n" +
                "§c  " + (int)(tier.suffocateDamage/2) + " ❤\n\n" +
                "§8Higher tier blocks\ndeal more damage!"
            );
            meta.addPage(
                "§2§l─ Crafting More ─\n\n" +
                "§7Once you have a page,\nthe recipe is in your\nrecipe book.\n\n" +
                "§6Recipe:\n§7Book\n+ §f" + tier.material.name() + "\n§7+ String\n\n" +
                "§8[DifficultyEngine]\n§8Earth Magic System"
            );
            book.setItemMeta(meta);
        }
        return book;
    }

}