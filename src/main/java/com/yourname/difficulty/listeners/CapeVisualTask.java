package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * CapeVisualTask — Ambient particle effects for equipped skill capes.
 *
 * Runs every 10 ticks (0.5 s). For each online player who has a recognised
 * cape in the chestplate/elytra slot, a brief particle burst is spawned
 * around their torso — a subtle visual flourish that identifies which cape
 * they are wearing.
 *
 * Cape → Particle mapping
 *   MELEE       → §cCRIT         (sharp red sparks)
 *   RANGED      → §aENCHANTED_HIT (green enchant sparks)
 *   DEFENCE     → §9END_ROD       (white-blue rods)
 *   PRAYER      → §fENCHANT       (white floating letters)
 *   MAGIC       → §dWITCH         (purple witch magic)
 *   WOODCUTTING → §2HAPPY_VILLAGER (green sparkles)
 *   FISHING     → §bDRIP_WATER    (water drip)
 *   FARMING     → §eHAPPY_VILLAGER (green sparkles, different rate)
 *   BOSS Cape   → §5SOUL_FIRE_FLAME (blue-green soul flame)
 *   Max Cape    → §6FIREWORK       (firework star sparkles)
 */
public class CapeVisualTask extends BukkitRunnable {

    private final SkillCapeManager capeManager;
    private final JavaPlugin        plugin;
    private int                     tick = 0;

    public CapeVisualTask(SkillCapeManager capeManager, JavaPlugin plugin) {
        this.capeManager = capeManager;
        this.plugin      = plugin;
    }

    @Override
    public void run() {
        tick++;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack chest = player.getInventory().getChestplate();
            if (chest == null) continue;
            if (!capeManager.isAnyCape(chest)) continue;

            // Only emit every other tick for subtlety (every 20 ticks = 1 s)
            if (!shouldEmit(chest, tick)) continue;

            spawnCapeParticles(player, chest);
        }
    }

    private boolean shouldEmit(ItemStack cape, int tick) {
        // Max and Boss capes pulse every tick; others every 2 ticks
        if (capeManager.isMaxCape(cape) || capeManager.isBossCape(cape)) return true;
        return (tick % 2) == 0;
    }

    private void spawnCapeParticles(Player player, ItemStack cape) {
        // Position: slightly above player centre (cape hangs from upper back)
        var loc = player.getLocation().add(0, 1.0, 0);

        // ── Boss Cape — Soul Fire Flame ───────────────────────────────────────
        if (capeManager.isBossCape(cape)) {
            player.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME, loc, 6, 0.35, 0.45, 0.35, 0.02);
            return;
        }

        // ── Max Cape — Firework sparkles ─────────────────────────────────────
        if (capeManager.isMaxCape(cape)) {
            player.getWorld().spawnParticle(
                Particle.FIREWORK, loc, 8, 0.4, 0.5, 0.4, 0.05);
            return;
        }

        // ── Skill capes ───────────────────────────────────────────────────────
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return;

        switch (skill) {
            case MELEE ->
                player.getWorld().spawnParticle(
                    Particle.CRIT, loc, 5, 0.3, 0.4, 0.3, 0.05);

            case RANGED ->
                player.getWorld().spawnParticle(
                    Particle.ENCHANTED_HIT, loc, 5, 0.3, 0.4, 0.3, 0.05);

            case DEFENCE ->
                player.getWorld().spawnParticle(
                    Particle.END_ROD, loc, 4, 0.3, 0.4, 0.3, 0.01);

            case PRAYER ->
                player.getWorld().spawnParticle(
                    Particle.ENCHANT, loc, 6, 0.4, 0.5, 0.4, 0.1);

            case MAGIC ->
                player.getWorld().spawnParticle(
                    Particle.WITCH, loc, 5, 0.3, 0.4, 0.3, 0.02);

            case WOODCUTTING ->
                player.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER, loc, 4, 0.35, 0.4, 0.35, 0.0);

            case FISHING ->
                player.getWorld().spawnParticle(
                    Particle.FALLING_WATER, loc, 5, 0.3, 0.4, 0.3, 0.0);

            case FARMING ->
                player.getWorld().spawnParticle(
                    Particle.COMPOSTER, loc, 4, 0.35, 0.4, 0.35, 0.0);
        }
    }
}
