package com.separateplug.combat;

import com.separateplug.spirit.SpiritItems;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * StunSwordListener — Handles the Ghost Sword (Stun Sword) on-hit effect.
 *
 * ── Normal stun (every hit with Ghost Sword) ──────────────────────────────────
 *  • Applies Slowness 255 (full freeze) for 3 seconds to the target.
 *  • Applies rapidly alternating Blindness for the same 3 seconds:
 *      ON 4 ticks → OFF 4 ticks → ON 4 ticks → …
 *    This creates the "black and white flashing" vision effect.
 *  • A white title "§f§l★ STUNNED ★" appears on the victim's screen.
 *
 * ── Charged stun (when CombatHitBar is full) ──────────────────────────────────
 *  • Duration extends to 4.5 seconds.
 *  • Deals +4.0 bonus damage.
 *  • Larger particle burst.
 *  • Consumes the hit-bar charge.
 */
public class StunSwordListener implements Listener {

    private final JavaPlugin  plugin;
    private final SpiritItems spiritItems;
    private final CombatHitBar combatBar;

    public StunSwordListener(JavaPlugin plugin, SpiritItems spiritItems, CombatHitBar combatBar) {
        this.plugin      = plugin;
        this.spiritItems = spiritItems;
        this.combatBar   = combatBar;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGhostSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Must be holding the Ghost Sword
        if (!spiritItems.isStunSword(attacker.getInventory().getItemInMainHand())) return;

        // ── Determine stun duration ───────────────────────────────────────────
        boolean charged = combatBar.isStunReady(attacker);
        int stunTicks = charged ? 90 : 60; // 4.5 s or 3 s

        if (charged) {
            combatBar.consumeStun(attacker);
            event.setDamage(event.getDamage() + 4.0); // bonus damage
            // Big burst
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 60, 0.8, 0.8, 0.8, 0.25);
            target.getWorld().spawnParticle(Particle.ENCHANT,
                target.getLocation().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.2);
            attacker.getWorld().playSound(attacker.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.4f);
            attacker.sendActionBar("§c§l⚡ CHARGED STUN! §7+" + 4 + " bonus damage!");
        } else {
            target.getWorld().spawnParticle(Particle.CLOUD,
                target.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.15);
            attacker.sendActionBar("§f§l★ §7Ghost Sword stun applied! §8(3s)");
        }

        // ── Apply Slowness 255 (total movement freeze) ────────────────────────
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.SLOWNESS, stunTicks, 255, false, false, false));

        // ── Flashing Blindness (black-and-white effect) ───────────────────────
        // Schedule 8 cycles of Blindness ON/OFF every 4 ticks (0.2s per flash)
        int cycles = stunTicks / 8; // each cycle = 4t ON + 4t OFF = 8 ticks
        for (int i = 0; i < cycles; i++) {
            final int delayOn  = i * 8;
            final int delayOff = i * 8 + 4;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) return;
                target.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, 5, 0, false, false, false));
            }, delayOn);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid()) return;
                target.removePotionEffect(PotionEffectType.BLINDNESS);
            }, delayOff);
        }

        // ── Title for the victim ───────────────────────────────────────────────
        if (target instanceof Player victim) {
            victim.sendTitle(
                "§f§l★ STUNNED ★",
                "§7Stunned for " + (stunTicks / 20) + " seconds!",
                3, stunTicks - 5, 10
            );
            victim.playSound(victim.getLocation(),
                Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.8f);
        }
    }
}
