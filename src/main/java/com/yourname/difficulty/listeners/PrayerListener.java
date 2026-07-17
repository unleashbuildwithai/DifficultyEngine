package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.Set;

/**
 * PrayerListener — Handles the PRAYER skill.
 *
 * ── Training ────────────────────────────────────────────────────────────────
 *  Right-click a Bone (or bone-type item) on Dirt / Grass / Farmland /
 *  Podzol / Coarse Dirt → bone consumed, Prayer XP awarded.
 *
 *  XP per bone type:
 *    BONE      → 4 XP
 *    BONE_MEAL → 2 XP
 *
 * ── Protection chance ───────────────────────────────────────────────────────
 *  On any incoming damage (PvP + PvM), a prayer roll is performed.
 *  If it succeeds: the hit is nullified entirely.
 *  Chance = (prayerLevel/99)^1.5 × 30%   → Level 99 = 30% block chance.
 *  Fires at HIGHEST priority (before Defence reduction).
 */
public class PrayerListener implements Listener {

    private final SkillManager skillManager;
    private final Random       rng = new Random();

    private static final Set<Material> BONE_ITEMS = Set.of(
        Material.BONE,
        Material.BONE_MEAL
    );

    private static final Set<Material> SOIL_BLOCKS = Set.of(
        Material.DIRT,
        Material.GRASS_BLOCK,
        Material.FARMLAND,
        Material.PODZOL,
        Material.COARSE_DIRT,
        Material.ROOTED_DIRT,
        Material.MUD
    );

    public PrayerListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // ── Bone burying → Prayer XP ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        Block     block  = event.getClickedBlock();

        if (block == null) return;
        if (!BONE_ITEMS.contains(hand.getType())) return;
        if (!SOIL_BLOCKS.contains(block.getType())) return;

        // Cancel default right-click block interaction (e.g. bone meal on grass)
        event.setCancelled(true);

        // Award Prayer XP
        long xp = boneXp(hand.getType());
        awardPrayerXp(player, xp);

        // Consume 1 bone
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        // Visual effect: smoke rises from the soil
        block.getWorld().spawnParticle(
            Particle.SMOKE,
            block.getLocation().add(0.5, 1.1, 0.5),
            6, 0.2, 0.2, 0.2, 0.02
        );
        block.getWorld().spawnParticle(
            Particle.END_ROD,
            block.getLocation().add(0.5, 1.0, 0.5),
            4, 0.3, 0.3, 0.3, 0.01
        );
    }

    // ── Prayer protection roll ─────────────────────────────────────────────────

    /**
     * Fires at HIGHEST priority so prayer can cancel the hit before
     * Defence reduction (HIGH) runs — if prayer blocks, no reduction needed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Skip instant-kill causes
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID) return;
        if (cause == EntityDamageEvent.DamageCause.CUSTOM) return;

        int prayerLevel = skillManager.getLevel(player.getUniqueId(), SkillType.PRAYER);
        if (prayerLevel <= 0) return;

        double chance = SkillBonusManager.prayerProtectionChance(prayerLevel);
        if (rng.nextDouble() < chance) {
            event.setCancelled(true);

            // Prayer sparkle around player
            player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                20, 0.4, 0.6, 0.4, 0.05
            );
            player.sendActionBar("§f✦ §7Prayer protected you! §f✦  §8(Lv " + prayerLevel + ")");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static long boneXp(Material mat) {
        return switch (mat) {
            case BONE      -> 4L;
            case BONE_MEAL -> 2L;
            default        -> 1L;
        };
    }

    private void awardPrayerXp(Player player, long amount) {
        int oldLevel = skillManager.getLevel(player.getUniqueId(), SkillType.PRAYER);
        int newLevel = skillManager.addXp(player.getUniqueId(), SkillType.PRAYER, amount);

        player.sendActionBar("§f+" + amount + " §fPrayer XP §8(Lv " + newLevel + ")");

        if (newLevel > oldLevel) {
            double chance = SkillBonusManager.prayerProtectionChance(newLevel) * 100;
            player.sendMessage("");
            player.sendMessage("§6⬆ §e" + SkillType.PRAYER.colored()
                    + " §elevel up! §8(§f" + oldLevel + " §8→ §a" + newLevel + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(newLevel));
            player.sendMessage(String.format("  §f🕊 §7Block chance: §a%.1f%%", chance));
            player.sendMessage("");
        }
    }
}
