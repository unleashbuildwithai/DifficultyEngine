package com.yourname.difficulty.boss;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Random;
import java.util.Set;

/**
 * BossTerrainUtil — Shared "boss shatters blocks in its flight path" logic
 * used by both {@code TempestOverlordManager} and {@code VoidWitherManager}.
 *
 * <p>Both managers previously duplicated an identical INDESTRUCTIBLE
 * material set and a very similar block-shattering sphere/cube scan. This
 * was extracted during the 400-line-file cleanup pass to remove duplication
 * — behaviour for each caller is unchanged (radius/shape/particle choices
 * remain exactly as they were per-boss).
 */
public final class BossTerrainUtil {

    private BossTerrainUtil() {}

    /** Blocks no boss should ever be able to destroy (arena shell / world-critical). */
    public static final Set<Material> INDESTRUCTIBLE = Set.of(
            Material.BEDROCK,
            Material.ANCIENT_DEBRIS,
            Material.OBSIDIAN,
            Material.CRYING_OBSIDIAN,
            Material.REINFORCED_DEEPSLATE,
            Material.BARRIER,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.GILDED_BLACKSTONE,
            Material.BLACK_CONCRETE
    );

    /**
     * Shatters non-indestructible, non-air blocks in a spherical radius
     * around the given center (used by Void Zurion — tight radius, checks
     * air first, occasional SOUL particle).
     */
    public static void shatterSphereSoul(World world, Block centerBlock, int radius, Random random) {
        int bx = centerBlock.getX();
        int by = centerBlock.getY();
        int bz = centerBlock.getZ();
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int y = by - radius; y <= by + radius + 1; y++) {
                for (int z = bz - radius; z <= bz + radius; z++) {
                    double distSq = (x - bx) * (x - bx) + (y - by) * (y - by) + (z - bz) * (z - bz);
                    if (distSq > radius * radius + 1) continue;
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    if (INDESTRUCTIBLE.contains(b.getType())) continue;
                    b.setType(Material.AIR, false);
                    if (random.nextDouble() < 0.15) {
                        world.spawnParticle(Particle.SOUL, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.01);
                    }
                }
            }
        }
    }

    /**
     * Shatters ANY solid, non-indestructible block in a spherical radius
     * around the given center (used by Tempest Overlord — wider radius,
     * checks isSolid(), occasional grey DUST particle).
     */
    public static void shatterSphereDust(World world, Block centerBlock, int radius, Random random) {
        int bx = centerBlock.getX();
        int by = centerBlock.getY();
        int bz = centerBlock.getZ();
        for (int x = bx - radius; x <= bx + radius; x++) {
            for (int y = by - radius; y <= by + radius; y++) {
                for (int z = bz - radius; z <= bz + radius; z++) {
                    double distSq = (x - bx) * (x - bx) + (y - by) * (y - by) + (z - bz) * (z - bz);
                    if (distSq > radius * radius) continue;
                    Block b = world.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    if (!b.getType().isSolid()) continue;
                    if (INDESTRUCTIBLE.contains(b.getType())) continue;
                    b.setType(Material.AIR);
                    if (random.nextDouble() < 0.08) {
                        world.spawnParticle(Particle.DUST, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0,
                                new Particle.DustOptions(org.bukkit.Color.GRAY, 1.0f));
                    }
                }
            }
        }
    }
}
