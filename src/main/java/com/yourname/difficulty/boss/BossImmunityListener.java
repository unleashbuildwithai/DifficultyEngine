package com.yourname.difficulty.boss;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Warden;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * BossImmunityListener — Handles boss-related "non-hostility" and damage
 * immunity rules that were extracted out of {@link BossEffectListener}
 * during the 400-line-file cleanup pass:
 *
 * ── Responsibilities ──────────────────────────────────────────────────────
 *  1. Void Boss (Wither) & Warden peaceful-team alliance — they never
 *     target or damage each other.
 *  2. Void spawner block explosion protection (BLACK_CONCRETE /
 *     CRYING_OBSIDIAN / GILDED_BLACKSTONE survive explosions).
 *  3. Raid Boss attack negation immunities (MELEE / RANGED / FIRE), driven
 *     by the "de_damage_negation" metadata key on the boss entity.
 *
 * Behaviour is unchanged from the original BossEffectListener implementation.
 */
public class BossImmunityListener implements Listener {

    // ── Void Boss (Wither) & Warden peaceful team non-hostility alliance ──────

    @EventHandler(priority = EventPriority.HIGH)
    public void onWardenTargetVoidWither(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Warden && event.getTarget() instanceof Wither) {
            String name = event.getTarget().getCustomName();
            if (name != null && (name.contains("Void Zurion") || name.equals("Dinnerbone"))) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Wither && event.getTarget() instanceof Warden) {
            String name = event.getEntity().getCustomName();
            if (name != null && (name.contains("Void Zurion") || name.equals("Dinnerbone"))) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onWardenDamageVoidWither(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Warden && event.getEntity() instanceof Wither) {
            String name = event.getEntity().getCustomName();
            if (name != null && (name.contains("Void Zurion") || name.equals("Dinnerbone"))) {
                event.setCancelled(true);
            }
        } else if (event.getDamager() instanceof Wither && event.getEntity() instanceof Warden) {
            String name = event.getDamager().getCustomName();
            if (name != null && (name.contains("Void Zurion") || name.equals("Dinnerbone"))) {
                event.setCancelled(true);
            }
        }
    }

    // ── Void spawner block explosion protection ──────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onVoidBlockExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> b.getType() == Material.BLACK_CONCRETE
                || b.getType() == Material.CRYING_OBSIDIAN
                || b.getType() == Material.GILDED_BLACKSTONE);
    }

    // ── Raid Boss attack negation immunities (MELEE / RANGED / FIRE) ─────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossDamageNegation(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity le)) return;
        if (!le.hasMetadata("de_damage_negation")) return;

        String immunity = le.getMetadata("de_damage_negation").get(0).asString();

        // Determine damage type
        boolean isMelee = false;
        boolean isRanged = false;
        boolean isFire = false;

        if (event.getDamager() instanceof Player) {
            isMelee = true; // physical sword/staff strike
        } else if (event.getDamager() instanceof Projectile proj) {
            isRanged = true; // arrow or other projectile
            if (proj instanceof SmallFireball || proj instanceof Fireball) {
                isFire = true; // fire magic!
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA) {
            isFire = true;
        }

        // Apply immunities
        if (immunity.equals("MELEE") && isMelee) {
            event.setCancelled(true);
            playImmuneEffect(le, "MELEE");
        } else if (immunity.equals("RANGED") && isRanged && !isFire) {
            event.setCancelled(true);
            playImmuneEffect(le, "RANGED");
        } else if (immunity.equals("FIRE") && isFire) {
            event.setCancelled(true);
            playImmuneEffect(le, "FIRE/ELEMENTAL magic");
        }
    }

    private void playImmuneEffect(LivingEntity target, String attackType) {
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.8f);
        target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, Material.IRON_BLOCK.createBlockData());

        // Alert nearby players in action bar
        for (Entity nearby : target.getNearbyEntities(30, 30, 30)) {
            if (nearby instanceof Player player) {
                player.sendActionBar("§c✗ §7The boss is §e§lIMMUNE §7to §c§l" + attackType + " §7attacks! Switch styles!");
            }
        }
    }
}
