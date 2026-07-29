package com.yourname.difficulty.magic;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;

import java.util.Random;

/**
 * Handles Sandstorm's visual/audio particle effects.
 * Extracted from {@link SandstormManager} to keep files under the 400-line limit.
 */
final class SandstormEffects {

    private static final Random RAND = new Random();

    private SandstormEffects() {}

    /** Spawns a burst of sandy BLOCK particles across the storm's radius, plus an occasional sound. */
    static void spawnSandParticles(Location centre, int radiusBlocks) {
        World world = centre.getWorld();
        if (world == null) return;
        // Spawn ~80 particle bursts spread across the radius per frame
        for (int i = 0; i < 80; i++) {
            double angle  = RAND.nextDouble() * 2 * Math.PI;
            double radius = RAND.nextDouble() * radiusBlocks;
            double x = centre.getX() + Math.cos(angle) * radius;
            double z = centre.getZ() + Math.sin(angle) * radius;
            double y = centre.getY() + RAND.nextDouble() * 6 - 1; // ±1→+5 blocks above ground
            Location pt = new Location(world, x, y, z);
            // Use BLOCK particles with sand/gravel/soul_sand randomly
            Material mat = switch (RAND.nextInt(3)) {
                case 0 -> Material.SAND;
                case 1 -> Material.GRAVEL;
                default -> Material.SOUL_SAND;
            };
            world.spawnParticle(Particle.BLOCK, pt, 3,
                0.5, 0.3, 0.5, 0.08, mat.createBlockData());
        }
        // Occasional sand swirl sound
        if (RAND.nextInt(4) == 0) {
            world.playSound(centre, Sound.BLOCK_SAND_BREAK, 0.4f, 0.5f + RAND.nextFloat() * 0.5f);
        }
    }
}
