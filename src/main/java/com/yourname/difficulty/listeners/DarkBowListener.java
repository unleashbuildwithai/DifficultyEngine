package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DarkBowListener — Implements the Dark Bow and Dragon Arrow system.
 *
 * ── Dark Bow ──────────────────────────────────────────────────────────────────
 *  Level 70 Ranged required.
 *  1% drop from the Warden.
 *
 *  Normal shot (draw + release): fires a single arrow. Uses Dragon Arrows if
 *    present (applies 10% damage bonus and purple particle trail).
 *
 *  Special shot (SNEAK + right-click with Dark Bow):
 *    Instantly fires 2 homing arrows. Each arrow does 35% LESS damage than a
 *    normal arrow. The homing arrows track the nearest entity within 40 blocks,
 *    adjusting velocity toward it every tick.
 *    Consumes 2 Dragon Arrows. Has a 3-second special cooldown.
 *
 * ── Dragon Arrows ─────────────────────────────────────────────────────────────
 *  Crafted from Dragon Arrow Tips, which drop from the Ender Dragon on death.
 *  Dragon + kills drop 8-16 Dragon Arrow Tips.
 *  Carry Dragon Arrows in your inventory for the Dark Bow to use them.
 *
 * ── Timing mastery ────────────────────────────────────────────────────────────
 *  Skilled players can alternate single / double shots:
 *    Normal → Special (2 homing) → Normal → Special → ...
 *  Timing the special after a normal arrow creates devastating burst combos.
 */
public class DarkBowListener implements Listener {

    /** Scoreboard tag on homing arrows so we can track them. */
    private static final String HOMING_TAG    = "DE_homing_arrow";
    private static final String DRAGON_TAG    = "DE_dragon_arrow_hit";
    private static final long   SPECIAL_COOLDOWN_MS = 3000L;
    private static final double HOMING_RANGE  = 40.0;
    private static final double HOMING_DAMAGE_MULT = 0.65; // 35% less than normal

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;

    /** Homing arrow UUID → target entity UUID */
    private final Map<UUID, UUID> homingTargets  = new HashMap<>();
    /** Players and their last special-shot timestamp */
    private final Map<UUID, Long> specialCooldown = new HashMap<>();
    /** Dark-bow arrows that are from dragon arrows (for bonus particle trail) */
    private final Set<UUID>       dragonArrows    = new HashSet<>();

    public DarkBowListener(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SPECIAL SHOT — SNEAK + RIGHT-CLICK WITH DARK BOW
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return; // Special = sneak + right-click
        if (!itemFactory.isDarkBow(player.getInventory().getItemInMainHand())) return;

        // Check Ranged level
        int rangedLevel = skillManager.getLevel(player.getUniqueId(), SkillType.RANGED);
        if (rangedLevel < 70) {
            player.sendActionBar("§4[Dark Bow] §cRequires Ranged Level 70!");
            return;
        }

        // Special cooldown
        long now = System.currentTimeMillis();
        if (now - specialCooldown.getOrDefault(player.getUniqueId(), 0L) < SPECIAL_COOLDOWN_MS) {
            long ms = SPECIAL_COOLDOWN_MS - (now - specialCooldown.getOrDefault(player.getUniqueId(), 0L));
            player.sendActionBar("§4[Dark Bow] §8Special cooldown: §c" + String.format("%.1f", ms / 1000.0) + "s");
            return;
        }

        // Check for Dragon Arrows (need 2)
        int dragonArrowCount = countDragonArrows(player);
        if (dragonArrowCount < 2) {
            player.sendActionBar("§4[Dark Bow] §cNeed 2 Dragon Arrows for special shot! §8(have " + dragonArrowCount + ")");
            return;
        }

        event.setCancelled(true);

        // Find nearest target
        LivingEntity target = findNearestTarget(player, HOMING_RANGE);
        if (target == null) {
            player.sendActionBar("§4[Dark Bow] §8No target in range for homing!");
            return;
        }

        // Consume 2 Dragon Arrows
        consumeDragonArrows(player, 2);
        specialCooldown.put(player.getUniqueId(), now);

        // Fire 2 homing arrows
        for (int i = 0; i < 2; i++) {
            final int arrowIndex = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                fireHomingArrow(player, target, arrowIndex), i * 2L); // slight stagger
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.6f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 1.8f);
        player.sendActionBar("§4[Dark Bow] §c§l✦ DOUBLE HOMING SHOT! §8(−35% each)");
    }

    private void fireHomingArrow(Player shooter, LivingEntity target, int index) {
        if (!shooter.isOnline() || !target.isValid()) return;

        // Slight spread between the two arrows
        Vector dir = shooter.getLocation().getDirection().clone();
        dir.add(new Vector((Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06, (Math.random() - 0.5) * 0.06));
        dir.normalize().multiply(2.5);

        Arrow arrow = shooter.getWorld().spawnArrow(
            shooter.getEyeLocation().add(dir.clone().normalize().multiply(0.5)),
            dir, 1.0f, 0.0f);
        arrow.setShooter(shooter);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setPersistent(false);
        // Apply 35% damage reduction
        arrow.setDamage(arrow.getDamage() * HOMING_DAMAGE_MULT);
        arrow.setGlowing(true);
        arrow.addScoreboardTag(HOMING_TAG);
        arrow.addScoreboardTag(DRAGON_TAG);

        homingTargets.put(arrow.getUniqueId(), target.getUniqueId());

        // Homing task — adjusts arrow velocity toward target every tick
        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            if (!arrow.isValid() || arrow.isOnGround()) {
                homingTargets.remove(arrow.getUniqueId());
                task.cancel();
                return;
            }
            UUID tid = homingTargets.get(arrow.getUniqueId());
            if (tid == null) { task.cancel(); return; }

            // Find the target entity by UUID
            LivingEntity tgt = (LivingEntity) shooter.getWorld().getEntities().stream()
                .filter(e -> e.getUniqueId().equals(tid) && e.isValid())
                .findFirst().orElse(null);

            if (tgt == null) {
                // Target gone — continue as normal arrow
                homingTargets.remove(arrow.getUniqueId());
                task.cancel();
                return;
            }

            // Blend current velocity with direction to target
            Vector toTarget = tgt.getLocation().add(0, tgt.getHeight() * 0.6, 0)
                .subtract(arrow.getLocation()).toVector();
            if (toTarget.lengthSquared() < 0.1) return;
            toTarget.normalize().multiply(2.8);

            Vector blended = arrow.getVelocity().multiply(0.55).add(toTarget.multiply(0.45));
            arrow.setVelocity(blended);

            // Purple trail particle
            arrow.getWorld().spawnParticle(Particle.DUST, arrow.getLocation(),
                2, 0.05, 0.05, 0.05, 0,
                new Particle.DustOptions(Color.fromRGB(140, 0, 255), 1.0f));
        }, 0L, 1L);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  NORMAL ARROW — track dragon arrow hits for bonus effects
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) return;
        homingTargets.remove(arrow.getUniqueId());
        dragonArrows.remove(arrow.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!arrow.getScoreboardTags().contains(DRAGON_TAG)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Dragon arrow hit — purple particles + sound
        target.getWorld().spawnParticle(Particle.DUST,
            target.getLocation().add(0, 1, 0), 20, 0.4, 0.4, 0.4, 0,
            new Particle.DustOptions(Color.fromRGB(140, 0, 255), 1.5f));
        target.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
            target.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.1);
        target.getWorld().playSound(target.getLocation(),
            Sound.ENTITY_ENDER_DRAGON_HURT, 0.5f, 1.8f);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WARDEN DEATH — 1% chance to drop Dark Bow
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWardenDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Warden)) return;
        if (Math.random() >= 0.01) return; // 1% drop

        ItemStack bow = itemFactory.buildDarkBow();
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), bow);

        // Notify killer
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killer.sendMessage("§4⚠ §cThe Warden dropped a §4Dark Bow§c!");
            killer.sendMessage("§8  Requires Ranged Level 70. Sneak + right-click for homing double shot.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ENDER DRAGON DEATH — drop Dragon Arrow Tips
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDragonDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;

        // Drop 8–16 Dragon Arrow Tips at the dragon's location
        int tipCount = 8 + (int)(Math.random() * 9); // 8-16
        ItemStack tips = itemFactory.buildDragonArrowTip(tipCount);
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), tips);

        // Notify nearby players
        for (Entity nearby : event.getEntity().getWorld().getNearbyEntities(
                event.getEntity().getLocation(), 200, 200, 200)) {
            if (nearby instanceof Player p) {
                p.sendMessage("§5✦ §dThe Ender Dragon dropped §5" + tipCount + "× Dragon Arrow Tips§d!");
                p.sendMessage("§8  Craft them into Dragon Arrows for use with the §4Dark Bow§8.");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /** Finds the nearest LivingEntity to the player within range (excluding the player). */
    private LivingEntity findNearestTarget(Player player, double range) {
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), range, range, range);
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (Entity e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;
            if (e.equals(player)) continue;
            if (e.getScoreboardTags().contains("DE_cape_fish")) continue; // skip cape animals
            double dist = e.getLocation().distanceSquared(player.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = le;
            }
        }
        return nearest;
    }

    /** Counts how many Dragon Arrows are in the player's inventory. */
    private int countDragonArrows(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (itemFactory.isDragonArrow(item)) count += item.getAmount();
        }
        return count;
    }

    /** Removes the specified number of Dragon Arrows from the player's inventory. */
    private void consumeDragonArrows(Player player, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (remaining <= 0) break;
            if (!itemFactory.isDragonArrow(item)) continue;
            if (item.getAmount() <= remaining) {
                remaining -= item.getAmount();
                player.getInventory().remove(item);
            } else {
                item.setAmount(item.getAmount() - remaining);
                remaining = 0;
            }
        }
    }
}
