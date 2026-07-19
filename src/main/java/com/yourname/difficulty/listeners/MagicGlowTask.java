package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * MagicGlowTask — Ambient particle glow for held elemental staffs.
 *
 * Runs every 4 ticks (0.2 s).  When a player holds any elemental staff,
 * a deep element-specific glow is emitted near their hand position:
 *
 *   FIRE  → intense orange-red FLAME + deep crimson DUST
 *   WATER → blue DRIPPING_WATER + vivid azure DUST
 *   EARTH → emerald green DUST + nature HAPPY_VILLAGER sparks
 *   AIR   → bright cyan END_ROD + luminous cyan DUST   (DISTINCT from others)
 *
 * The task also spawns a secondary "aura" ring at torso height when the
 * player is holding their staff, reinforcing the "deeper glow" visual.
 */
public class MagicGlowTask extends BukkitRunnable {

    // ── Vibrant element glow colours ─────────────────────────────────────────
    private static final Color FIRE_DEEP  = Color.fromRGB(255,  55,   0);
    private static final Color FIRE_BRIGHT = Color.fromRGB(255, 140,  20);
    private static final Color WATER_DEEP  = Color.fromRGB( 20, 100, 255);
    private static final Color WATER_BRIGHT = Color.fromRGB( 80, 200, 255);
    private static final Color EARTH_DEEP  = Color.fromRGB(  0, 140,  30);
    private static final Color EARTH_BRIGHT = Color.fromRGB( 80, 210,  40);
    // AIR gets a VERY different colour — vivid cyan / electric blue
    private static final Color AIR_DEEP    = Color.fromRGB(  0, 220, 255);
    private static final Color AIR_BRIGHT  = Color.fromRGB(160, 245, 255);

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;
    private int tick = 0;

    public MagicGlowTask(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    @Override
    public void run() {
        tick++;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            MagicElement el = itemFactory.getStaffElement(hand);
            if (el == null) continue;

            // ── Staff glow is a level-99 Magic perk only ────────────────────
            if (skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC) < 99) continue;

            // Position near the player's right-hand hold area
            Location eye  = player.getEyeLocation();
            Location hand1 = eye.clone()
                    .add(player.getLocation().getDirection().multiply(0.55))
                    .add(0, -0.45, 0);
            // Secondary aura at mid-chest
            Location aura = player.getLocation().clone().add(0, 1.1, 0);

            spawnElementGlow(player, el, hand1, aura);
        }
    }

    private void spawnElementGlow(Player player, MagicElement el, Location hand1, Location aura) {
        switch (el) {
            case FIRE -> {
                // Deep crimson flame glow at hand
                player.getWorld().spawnParticle(Particle.FLAME, hand1,
                        4, 0.07, 0.07, 0.07, 0.005);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.06, 0.06, 0.06, 0,
                        new Particle.DustOptions(FIRE_DEEP, 1.6f));
                // Vivid orange aura ring every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(FIRE_BRIGHT, 1.3f));
                    player.getWorld().spawnParticle(Particle.SMALL_FLAME, aura,
                            2, 0.18, 0.04, 0.18, 0.01);
                }
            }
            case WATER -> {
                // Deep azure water glow at hand
                player.getWorld().spawnParticle(Particle.DRIPPING_WATER, hand1,
                        3, 0.07, 0.07, 0.07, 0.01);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.06, 0.06, 0.06, 0,
                        new Particle.DustOptions(WATER_DEEP, 1.6f));
                // Vivid blue aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(WATER_BRIGHT, 1.3f));
                    player.getWorld().spawnParticle(Particle.BUBBLE_POP, aura,
                            2, 0.15, 0.04, 0.15, 0.02);
                }
            }
            case EARTH -> {
                // Deep emerald earth glow at hand
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.07, 0.07, 0.07, 0,
                        new Particle.DustOptions(EARTH_DEEP, 1.6f));
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, hand1,
                        2, 0.06, 0.06, 0.06, 0.01);
                // Vivid green aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(EARTH_BRIGHT, 1.3f));
                }
            }
            case AIR -> {
                // DISTINCT vibrant CYAN glow — completely different from other elements
                player.getWorld().spawnParticle(Particle.END_ROD, hand1,
                        4, 0.08, 0.08, 0.08, 0.015);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.07, 0.07, 0.07, 0,
                        new Particle.DustOptions(AIR_DEEP, 1.7f));
                // Bright electric cyan aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            5, 0.22, 0.06, 0.22, 0,
                            new Particle.DustOptions(AIR_BRIGHT, 1.4f));
                    player.getWorld().spawnParticle(Particle.END_ROD, aura,
                            2, 0.18, 0.04, 0.18, 0.01);
                }
            }
        }
    }
}
