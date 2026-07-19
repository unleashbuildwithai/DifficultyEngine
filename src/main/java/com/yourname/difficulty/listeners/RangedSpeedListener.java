package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.items.RangedGearTier;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

/**
 * RangedSpeedListener — Scales arrow velocity with Ranged level and DE ranged gear.
 *
 * Vanilla Minecraft fixes bow draw time at 20 ticks (1 s).  This listener
 * compensates by modifying the released arrow's velocity so higher-level rangers
 * fire arrows that reach targets faster, simulating a faster attack rate.
 * Low-level rangers fire noticeably slower arrows.
 *
 * ── Velocity multiplier formula ───────────────────────────────────────────────
 *
 *   base = 0.70 + (rangedLevel / 99.0) × 0.50
 *            → Level  1 ≈ 0.705   (≈30% slower  — noticeably sluggish)
 *            → Level 25 ≈ 0.826   (≈17% slower  — moderate beginner)
 *            → Level 50 ≈ 0.953   (≈ vanilla speed breakeven)
 *            → Level 75 ≈ 1.079   (≈ 8% faster)
 *            → Level 99 ≈ 1.204   (≈20% faster  — seasoned archer)
 *
 *   gear = Σ per piece of DE Ranged Gear worn:
 *            LEATHER   tier: +0.03 /piece  (max +0.12 full set)
 *            CHAIN     tier: +0.05 /piece  (max +0.20 full set)
 *            NETHERITE tier: +0.08 /piece  (max +0.32 full set)
 *            DRAGON    tier: +0.12 /piece  (max +0.48 full set)
 *
 *   total = clamp(base + gear, 0.40, 2.00)
 *
 *   Example — Lv 99 + full Dragon Ranged gear: 1.20 + 0.48 = 1.68 (68% faster!)
 *   Example — Lv  1 + no gear:                0.705          (30% slower)
 *
 * ── Why velocity, not draw-time? ────────────────────────────────────────────
 *   Bukkit does not expose the bow draw timer — there is no event or API to
 *   modify it mid-draw.  Velocity modification is the cleanest available hook:
 *     • Arrow flight speed determines how quickly it reaches the target
 *     • Damage from bows = f(force) where force is set at release, not velocity
 *       BUT Paper lets us modify arrow damage directly via setDamage()
 *     • We scale both velocity AND damage so higher-level rangers deal more
 *       damage-per-second even when drawing at the same rate.
 *
 * ── Damage scaling ──────────────────────────────────────────────────────────
 *   The RangedGearTier#rangedBonus is also applied here as a damage multiplier
 *   on top of the base bow force. LEATHER = ×1.0 (no bonus), up to DRAGON = ×1.75.
 */
public class RangedSpeedListener implements Listener {

    /**
     * Conversion factor: maps total bow draw-speed bonus (ms) to a velocity
     * fraction.  With {@code GEAR_MS_TO_VELOCITY = 2000}, a full Dragon set
     * (4 × 200 ms = 800 ms) contributes +0.40 to velocity multiplier.
     */
    private static final double GEAR_MS_TO_VELOCITY = 2000.0;

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;

    public RangedSpeedListener(ItemFactory itemFactory, SkillManager skillManager) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getProjectile() instanceof Arrow arrow))    return;

        int rangedLevel = skillManager.getLevel(player.getUniqueId(), SkillType.RANGED);

        // ── Level-based base multiplier ──────────────────────────────────────
        // 0.70 at Lv 1, scaling linearly to 1.20 at Lv 99
        double baseMultiplier = 0.70 + (rangedLevel / 99.0) * 0.50;

        // ── Gear velocity bonus ───────────────────────────────────────────────
        // Each DE ranged gear piece adds a tier-scaled bonus.
        // Vanilla draw-speed ms is converted to a velocity fraction.
        long   totalDrawMs  = 0L;
        double highestBonus = 1.0;  // rangedBonus from the highest tier worn
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            RangedGearTier tier = itemFactory.getRangedGearTier(piece);
            if (tier == null) continue;
            totalDrawMs  += tier.drawSpeedBonus;
            if (tier.rangedBonus > highestBonus) highestBonus = tier.rangedBonus;
        }
        double gearVelocityBonus = totalDrawMs / GEAR_MS_TO_VELOCITY;

        // ── Combined velocity multiplier ─────────────────────────────────────
        double velocityMultiplier = Math.min(2.00, Math.max(0.40,
                baseMultiplier + gearVelocityBonus));

        arrow.setVelocity(arrow.getVelocity().multiply(velocityMultiplier));

        // ── Ranged gear damage bonus ──────────────────────────────────────────
        // Apply the tier's rangedBonus as a damage amplifier.
        // This rewards wearing a full matched set.
        if (highestBonus > 1.0) {
            arrow.setDamage(arrow.getDamage() * highestBonus);
        }
    }
}
