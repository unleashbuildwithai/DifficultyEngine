package com.yourname.difficulty.listeners;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Magic Cape exclusive "flawless Nightmare" cosmetic — slow rainbow colour
 * shift, blue flame drip, and a randomly breaking potion bottle. Extracted
 * from {@link CapeVisualTask} to keep it under the 400-line limit.
 */
final class MagicCapeCosmetics {

    /** Hue advances this much per run cycle — a full rainbow loop takes ~2 minutes. */
    private static final double MAGIC_HUE_SPEED = 0.0007;
    /** Minimum seconds in Nightmare difficulty required for the cosmetic (150 hours). */
    private static final int NIGHTMARE_HOURS_REQUIRED_SEC = 150 * 3600;
    /** Chance per run cycle (every ~0.5s when active) that a potion bottle falls and breaks. */
    private static final double POTION_BREAK_CHANCE = 0.01;

    private final JavaPlugin plugin;
    private final Map<UUID, Double> magicHue = new HashMap<>();
    private final Random cosmeticRandom = new Random();

    MagicCapeCosmetics(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * If the player has 0 deaths in Nightmare difficulty AND has played
     * 150+ hours in Nightmare, their Magic Cape slowly cycles colors and
     * occasionally drips falling blue flames plus a randomly breaking
     * potion bottle cosmetic.
     */
    void spawnMagicCapeCosmetics(Player player, Location loc) {
        if (!qualifiesForFlawlessNightmareCosmetic(player)) return;

        UUID uuid = player.getUniqueId();
        double hue = magicHue.getOrDefault(uuid, 0.0) + MAGIC_HUE_SPEED;
        if (hue > 1.0) hue -= 1.0;
        magicHue.put(uuid, hue);

        Color slowColor = RainbowColorUtil.hsbToColor((float) hue);
        Particle.DustOptions slowDust = new Particle.DustOptions(slowColor, 1.4f);
        player.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.25, 0.35, 0.25, 0, slowDust);

        // ── Falling blue flame drip ────────────────────────────────────
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.3, 0),
                2, 0.15, 0.05, 0.15, 0.01);
        if (cosmeticRandom.nextDouble() < 0.15) {
            dripBlueFlame(player, loc.clone());
        }

        // ── Random potion bottle falling + breaking cosmetic ───────────
        if (cosmeticRandom.nextDouble() < POTION_BREAK_CHANCE) {
            spawnBreakingPotionBottle(player, loc.clone());
        }
    }

    /** Returns true if the player has 0 Nightmare deaths and 150+ hours played in Nightmare. */
    private boolean qualifiesForFlawlessNightmareCosmetic(Player player) {
        var pdc = player.getPersistentDataContainer();
        int nightmareDeaths = pdc.getOrDefault(
                new NamespacedKey(plugin, "diff_deaths_nightmare"),
                PersistentDataType.INTEGER, 0);
        int nightmareSec = pdc.getOrDefault(
                new NamespacedKey(plugin, "diff_time_nightmare"),
                PersistentDataType.INTEGER, 0);
        return nightmareDeaths == 0 && nightmareSec >= NIGHTMARE_HOURS_REQUIRED_SEC;
    }

    /** Animates a single droplet of blue flame falling from the cape down to the ground. */
    private void dripBlueFlame(Player player, Location start) {
        for (int i = 0; i <= 12; i += 2) {
            final int step = i;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || player.isDead()) return;
                Location drip = start.clone().add(0, -0.10 * step, 0);
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, drip, 1, 0.02, 0.02, 0.02, 0.0);
            }, step);
        }
    }

    /**
     * Spawns a temporary falling potion-bottle ItemDisplay-free cosmetic:
     * an actual falling glass-bottle item that "breaks" with splash particles
     * and a glass-break sound when it reaches the ground/short lifespan.
     */
    private void spawnBreakingPotionBottle(Player player, Location loc) {
        Location dropLoc = loc.clone().add(0, 0.5, 0);
        ItemStack bottle = new ItemStack(org.bukkit.Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) bottle.getItemMeta();
        if (meta != null) {
            meta.setBasePotionType(PotionType.WATER);
            meta.setColor(Color.fromRGB(30, 60, 220));
            bottle.setItemMeta(meta);
        }

        org.bukkit.entity.Item dropped = player.getWorld().dropItem(dropLoc, bottle);
        dropped.setVelocity(new Vector(
                (cosmeticRandom.nextDouble() - 0.5) * 0.05,
                -0.05,
                (cosmeticRandom.nextDouble() - 0.5) * 0.05));
        dropped.setPickupDelay(Integer.MAX_VALUE); // players cannot pick this up
        dropped.setInvulnerable(true);
        dropped.setGravity(true);
        dropped.setPersistent(false);

        // "Break" after a short fall (10 ticks / 0.5s)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!dropped.isDead()) {
                Location breakLoc = dropped.getLocation();
                breakLoc.getWorld().spawnParticle(Particle.SPLASH, breakLoc, 12, 0.2, 0.1, 0.2, 0.05);
                breakLoc.getWorld().spawnParticle(Particle.DUST, breakLoc, 10, 0.2, 0.1, 0.2, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 60, 220), 1.2f));
                breakLoc.getWorld().playSound(breakLoc, Sound.ENTITY_SPLASH_POTION_BREAK, 0.6f, 1.2f);
                dropped.remove();
            }
        }, 10L);
    }
}
