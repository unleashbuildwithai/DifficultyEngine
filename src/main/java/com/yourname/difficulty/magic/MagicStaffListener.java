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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicStaffListener — Handles elemental staff casting and crafting.
 *
 * ── Cast trigger ────────────────────────────────────────────────────────────
 *  Right-click (air or block) with a staff in main hand.
 *  • 3-second cooldown per element per player.
 *  • Consumes 1 rune of the matching element.
 *  • Awards 10 MAGIC XP per cast.
 *
 * ── Spell effects ───────────────────────────────────────────────────────────
 *  FIRE  – Launches a SmallFireball. On entity hit: base + magic bonus damage,
 *           sets target on fire for 2 seconds.
 *  WATER – Look at ground (within 5 blocks) → places 5-block water river
 *           (requires 1 Water Bucket in inventory; bucket not consumed).
 *           Look at entity (within 7 blocks) → 1 heart + splash particles.
 *  EARTH – Nearest entity within 7 blocks: 1 heart + dirt particles.
 *           If no target: snowball visual that breaks harmlessly.
 *  AIR   – Nearest entity within 7 blocks: knockback 10 blocks away + 0.5 heart.
 *           If no target: poof particles.
 *
 * ── Crafting verification ───────────────────────────────────────────────────
 *  Validates that staff crafting uses a real Enchanted Shard (not just any
 *  Amethyst Shard from the world). Cancels the craft otherwise.
 */
public class MagicStaffListener implements Listener {

    private static final long COOLDOWN_MS    = 3_000L;
    private static final long MAGIC_XP_CAST = 10L;
    private static final long MAGIC_XP_HIT  =  5L;
    private static final int  RANGE_BLOCKS  =  7;

    private final ItemFactory   itemFactory;
    private final SkillManager  skillManager;
    private final JavaPlugin    plugin;

    /** projectile UUID → MagicElement (only for earth/water snowballs) */
    private final Map<UUID, MagicElement> trackedProjectiles = new HashMap<>();
    /** projectile UUID → shooter UUID */
    private final Map<UUID, UUID> projectileShooters = new HashMap<>();
    /** player UUID → (element → last cast time ms) */
    private final Map<UUID, Map<MagicElement, Long>> cooldowns = new HashMap<>();

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

        // Cancel default block interaction (prevent placing water bucket etc.)
        event.setCancelled(true);

        // Cooldown check
        if (!checkAndSetCooldown(player.getUniqueId(), element)) {
            long msLeft = msUntilReady(player.getUniqueId(), element);
            player.sendActionBar(element.color + element.staffName
                    + " §8cooldown: §e" + String.format("%.1f", msLeft / 1000.0) + "s");
            return;
        }

        // Rune check
        if (!consumeRune(player, element)) {
            player.sendActionBar("§c✗ §7No " + element.runeName
                    + " §7— craft from §e4× " + element.runeCraftIngredient.name() + "§7.");
            return;
        }

        // Award cast XP
        awardMagicXp(player, MAGIC_XP_CAST);

        // Cast
        int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        castSpell(player, element, magicLevel, action, event.getClickedBlock());
    }

    // ── Spell dispatch ────────────────────────────────────────────────────────

    private void castSpell(Player player, MagicElement element, int magicLevel,
                           Action action, Block clickedBlock) {
        switch (element) {
            case FIRE  -> castFire(player, magicLevel);
            case WATER -> castWater(player, magicLevel, action, clickedBlock);
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
        fb.setYield(0.3f); // small explosion

        // Track it for damage override in hit event
        trackedProjectiles.put(fb.getUniqueId(), MagicElement.FIRE);
        projectileShooters.put(fb.getUniqueId(), player.getUniqueId());

        player.sendActionBar("§c🔥 §7Fireball launched!");
    }

    // ── WATER ─────────────────────────────────────────────────────────────────

    private void castWater(Player player, int magicLevel, Action action, Block clickedBlock) {
        // Mode 1: looking at a solid block (ground cast) → place river
        RayTraceResult rayResult = player.getWorld().rayTraceBlocks(
            player.getEyeLocation(), player.getLocation().getDirection(), 5,
            FluidCollisionMode.NEVER, true
        );
        if (rayResult != null && rayResult.getHitBlock() != null
                && action == Action.RIGHT_CLICK_BLOCK) {
            // Ground/wall cast: place 5-block water stream
            if (!hasWaterBucket(player)) {
                player.sendActionBar("§b💧 §7Need a §bWater Bucket §7to create a river!");
                return;
            }
            placeWaterStream(player, rayResult.getHitBlock(), rayResult.getHitBlockFace());
            player.sendActionBar("§b💧 §7Water river placed!");
            return;
        }

        // Mode 2: aimed at entity within RANGE_BLOCKS
        LivingEntity target = nearestEntity(player, RANGE_BLOCKS);
        if (target != null) {
            double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
            target.damage(damage, player);
            awardMagicXp(player, MAGIC_XP_HIT);
            // Splash particles
            target.getWorld().spawnParticle(Particle.SPLASH,
                target.getLocation().add(0, 1, 0), 30, 0.4, 0.4, 0.4, 0.2);
            player.sendActionBar("§b💧 §7Water hit! §8(" + (damage / 2) + " hearts)");
        } else {
            // No target — just a splash
            player.getWorld().spawnParticle(Particle.SPLASH,
                player.getLocation().add(player.getLocation().getDirection().multiply(3)).add(0, 1, 0),
                40, 0.5, 0.5, 0.5, 0.2);
            player.sendActionBar("§b💧 §7No target in range.");
        }
    }

    private boolean hasWaterBucket(Player player) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.getType() == Material.WATER_BUCKET) return true;
        }
        return false;
    }

    private void placeWaterStream(Player player, Block hitBlock, BlockFace face) {
        if (face == null) face = player.getFacing().getOppositeFace();
        BlockFace direction = getHorizontalFacing(player);
        // Start placing water on top of the hit block surface
        Block start = hitBlock.getRelative(BlockFace.UP);
        for (int i = 0; i < 5; i++) {
            Block b = start.getRelative(direction, i);
            if (b.getType().isAir() || b.getType() == Material.WATER) {
                b.setType(Material.WATER);
            } else {
                break; // blocked by solid block
            }
        }
    }

    private BlockFace getHorizontalFacing(Player player) {
        float yaw = player.getLocation().getYaw();
        yaw = ((yaw % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    // ── EARTH ─────────────────────────────────────────────────────────────────

    private void castEarth(Player player, int magicLevel) {
        LivingEntity target = nearestEntity(player, RANGE_BLOCKS);
        if (target != null) {
            double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
            target.damage(damage, player);
            awardMagicXp(player, MAGIC_XP_HIT);
            // Dirt particle burst
            target.getWorld().spawnParticle(Particle.BLOCK,
                target.getLocation().add(0, 1, 0), 25,
                0.3, 0.3, 0.3, Material.DIRT.createBlockData());
            player.sendActionBar("§2🌿 §7Earth hit! §8(" + (damage / 2) + " hearts)");
        } else {
            // Shoot a visual snowball that breaks on impact
            Snowball sb = player.launchProjectile(Snowball.class);
            sb.setItem(new ItemStack(Material.DIRT));
            trackedProjectiles.put(sb.getUniqueId(), MagicElement.EARTH);
            projectileShooters.put(sb.getUniqueId(), player.getUniqueId());
            player.sendActionBar("§2🌿 §7No target in range — dirt toss.");
        }
    }

    // ── AIR ───────────────────────────────────────────────────────────────────

    private void castAir(Player player, int magicLevel) {
        LivingEntity target = nearestEntity(player, RANGE_BLOCKS);
        if (target != null) {
            // Knockback: vector from player to target, scaled for ~10 blocks
            Vector dir = target.getLocation().subtract(player.getLocation()).toVector()
                .normalize().multiply(1.8).setY(0.4);
            target.setVelocity(dir);
            // 0.5 hearts = 1 HP damage
            double damage = 1.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
            target.damage(damage, player);
            awardMagicXp(player, MAGIC_XP_HIT);
            // Wind poof particles
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            player.sendActionBar("§7💨 §7Air gust! Enemy launched!");
        } else {
            // Poof cloud in front of player
            player.getWorld().spawnParticle(Particle.CLOUD,
                player.getLocation().add(player.getLocation().getDirection().multiply(3)).add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.1);
            player.sendActionBar("§7💨 §7No target in range.");
        }
    }

    // ── Fireball hit override ─────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        UUID projId = event.getEntity().getUniqueId();
        MagicElement element = trackedProjectiles.remove(projId);
        UUID shooterId = projectileShooters.remove(projId);
        if (element == null || shooterId == null) return;

        Player shooter = plugin.getServer().getPlayer(shooterId);
        if (shooter == null) return;

        int magicLevel = skillManager.getLevel(shooterId, SkillType.MAGIC);

        if (element == MagicElement.FIRE && event.getHitEntity() instanceof LivingEntity target) {
            // Override fireball damage: apply scaled magic damage instead
            double damage = 2.0 + SkillBonusManager.magicDamageBonus(magicLevel) * 2;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                target.setFireTicks(40); // 2 seconds
            }, 1L);
            awardMagicXp(shooter, MAGIC_XP_HIT);
            shooter.sendActionBar("§c🔥 §7Fireball hit! §8(+" + target.getFireTicks() / 20 + "s fire)");
        }
    }

    // ── Crafting validation ───────────────────────────────────────────────────

    /**
     * Ensures that when a staff is crafted, an actual custom Enchanted Shard
     * (with PDC) was used — not just any amethyst shard from the ground.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (itemFactory.getStaffElement(result) == null) return; // not a staff

        // Check that at least one ingredient is the custom Enchanted Shard
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (itemFactory.isEnchantedShard(ingredient)) return; // valid
        }
        // No custom shard found — cancel
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player p) {
            p.sendMessage("§c✗ §7Crafting a staff requires an §5Enchanted Shard§7 (mob drop).");
            p.sendMessage("§8  Regular Amethyst Shards won't work — kill mobs to find one.");
        }
    }

    // ── Rune consumption ─────────────────────────────────────────────────────

    private boolean consumeRune(Player player, MagicElement element) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (itemFactory.isRune(item, element)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                }
                return true;
            }
        }
        return false;
    }

    // ── Cooldown helpers ──────────────────────────────────────────────────────

    private boolean checkAndSetCooldown(UUID playerId, MagicElement element) {
        long now = System.currentTimeMillis();
        Map<MagicElement, Long> map = cooldowns.computeIfAbsent(playerId, k -> new EnumMap<>(MagicElement.class));
        long last = map.getOrDefault(element, 0L);
        if (now - last < COOLDOWN_MS) return false;
        map.put(element, now);
        return true;
    }

    private long msUntilReady(UUID playerId, MagicElement element) {
        Map<MagicElement, Long> map = cooldowns.get(playerId);
        if (map == null) return 0;
        long last = map.getOrDefault(element, 0L);
        return Math.max(0, COOLDOWN_MS - (System.currentTimeMillis() - last));
    }

    // ── Magic XP award ────────────────────────────────────────────────────────

    private void awardMagicXp(Player player, long amount) {
        int oldLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        int newLevel = skillManager.addXp(player.getUniqueId(), SkillType.MAGIC, amount);
        if (newLevel > oldLevel) {
            player.sendMessage("§6⬆ §e" + SkillType.MAGIC.colored()
                    + " §elevel up! §8(§f" + oldLevel + " §8→ §a" + newLevel + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(newLevel));
            double bonus = SkillBonusManager.magicDamageBonus(newLevel);
            if (bonus > 0) {
                player.sendMessage("  §d✦ §7Spell damage: §a+" + (bonus) + " §7hearts bonus");
            }
        }
    }

    // ── Nearest entity helper ─────────────────────────────────────────────────

    private LivingEntity nearestEntity(Player player, double range) {
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), range, range, range
        );
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(player)) continue;
            double dist = e.getLocation().distanceSquared(player.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = le;
            }
        }
        return nearest;
    }
}
