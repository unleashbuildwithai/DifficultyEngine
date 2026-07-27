package com.yourname.difficulty.realm;

import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * AncientPortalFrameUtil — Static helpers for detecting / validating Ancient
 * Debris portal frames (2-wide × 3-tall air/water gap fully bordered by
 * Ancient Debris blocks).
 *
 * <p>Extracted out of {@link AncientDebrisPortalListener} during the
 * 400-line-file cleanup pass. Behaviour is 100% unchanged from the original
 * private methods that lived on the listener class.
 */
public final class AncientPortalFrameUtil {

    private AncientPortalFrameUtil() {}

    /**
     * Returns true if the given entity's current block sits inside a valid
     * (already-formed) Ancient Debris portal, scanning both X and Z axis
     * orientations.
     */
    public static boolean isAncientDebrisPortalBlock(Block block) {
        if (block.getType() != Material.NETHER_PORTAL) return false;
        int[][] dirs = {{1, 0, 0}, {0, 0, 1}}; // X axis portal, Z axis portal
        for (int[] dir : dirs) {
            int dx = dir[0];
            int dz = dir[1];
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 3; h++) {
                    Block bottomLeftAir = block.getRelative(-w * dx, -h, -w * dz);
                    if (checkPortalFrameForPortalBlocks(bottomLeftAir, dx, dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Validates a candidate portal frame where the interior is expected to
     * already be AIR, WATER, or NETHER_PORTAL (used post-ignition, e.g. for
     * move-based teleport detection and "is this already lit" checks).
     */
    public static boolean checkPortalFrameForPortalBlocks(Block bottomLeftAir, int dx, int dz) {
        // Check if the 2x3 area is air, water, or portal
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 3; h++) {
                Material type = bottomLeftAir.getRelative(w * dx, h, w * dz).getType();
                if (type != Material.AIR && type != Material.WATER && type != Material.NETHER_PORTAL) {
                    return false;
                }
            }
        }
        return checkFrameBorder(bottomLeftAir, dx, dz);
    }

    /**
     * Validates a candidate portal frame where the interior is expected to
     * be un-ignited (AIR or WATER only) — used when searching for a frame to
     * ignite for the first time.
     */
    public static boolean checkPortalFrame(Block bottomLeftAir, int dx, int dz) {
        // Check if the 2x3 area is air or water
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 3; h++) {
                Material type = bottomLeftAir.getRelative(w * dx, h, w * dz).getType();
                if (type != Material.AIR && type != Material.WATER) {
                    return false;
                }
            }
        }
        return checkFrameBorder(bottomLeftAir, dx, dz);
    }

    /** Shared border-check: bottom/top/left/right must all be Ancient Debris. */
    private static boolean checkFrameBorder(Block bottomLeftAir, int dx, int dz) {
        // Check frame (bottom)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, -1, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (top)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, 3, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (left)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(-dx, h, -dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (right)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(2 * dx, h, 2 * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        return true;
    }

    /**
     * Scans a wide area around the clicked frame block to find a valid
     * un-ignited 2×3 air/water gap bordered by Ancient Debris, returning the
     * list of interior blocks to ignite (empty list if none found).
     *
     * <p>Search range was widened (from ±2 blocks / -3..+1 height to ±4 blocks
     * / -5..+3 height) to be far more tolerant of exactly where on the frame
     * the player's lightning bolt happens to land — the previous narrow range
     * silently failed to find otherwise-valid frames whenever the struck
     * block wasn't within a very specific offset window, which was the root
     * cause of "lightning on Ancient Debris doesn't trigger the portal".
     * Both cardinal orientations (frame extending along X, or along Z) are
     * tried at every offset.
     */
    public static List<Block> getPortalAirBlocks(Block clickedFrameBlock) {
        List<Block> result = new ArrayList<>();

        int[][] dirs = {{1, 0, 0}, {0, 0, 1}}; // X axis portal, Z axis portal

        for (int[] dir : dirs) {
            int dx = dir[0];
            int dz = dir[1];

            // Try to find bottom-left corner of the air gap relative to clicked block
            for (int offsetX = -4; offsetX <= 4; offsetX++) {
                for (int offsetZ = -4; offsetZ <= 4; offsetZ++) {
                    for (int offsetY = -5; offsetY <= 3; offsetY++) { // Widened offsetY scanner range
                        Block bottomLeftAir = clickedFrameBlock.getRelative(offsetX * dx, offsetY, offsetZ * dz);

                        if (checkPortalFrame(bottomLeftAir, dx, dz)) {
                            // Collect air blocks
                            for (int w = 0; w < 2; w++) {
                                for (int h = 0; h < 3; h++) {
                                    result.add(bottomLeftAir.getRelative(w * dx, h, w * dz));
                                }
                            }
                            return result;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Debug helper — builds a human-readable explanation of why no valid
     * frame was found near {@code clickedFrameBlock}, by reporting the block
     * types present at the exact positions a correctly-built 2×3 Ancient
     * Debris frame would require, for the closest candidate offset in each
     * orientation. Used by AncientDebrisPortalListener to give admins/players
     * actionable feedback instead of just "no frame found".
     */
    public static String debugNearestFrameMismatch(Block clickedFrameBlock) {
        StringBuilder sb = new StringBuilder();
        int[][] dirs = {{1, 0, 0}, {0, 0, 1}};
        String[] axisNames = {"X-axis", "Z-axis"};

        for (int i = 0; i < dirs.length; i++) {
            int dx = dirs[i][0];
            int dz = dirs[i][1];
            // Assume the clicked block IS the bottom-left interior corner (offset 0,0,0)
            // and report exactly what's wrong at that specific candidate.
            Block bottomLeftAir = clickedFrameBlock;
            sb.append("§8[").append(axisNames[i]).append(" candidate @ clicked block] ");
            boolean ok = true;
            for (int w = 0; w < 2; w++) {
                if (bottomLeftAir.getRelative(w * dx, -1, w * dz).getType() != Material.ANCIENT_DEBRIS) {
                    sb.append("bottom-").append(w).append("=").append(bottomLeftAir.getRelative(w * dx, -1, w * dz).getType()).append(" ");
                    ok = false;
                }
                if (bottomLeftAir.getRelative(w * dx, 3, w * dz).getType() != Material.ANCIENT_DEBRIS) {
                    sb.append("top-").append(w).append("=").append(bottomLeftAir.getRelative(w * dx, 3, w * dz).getType()).append(" ");
                    ok = false;
                }
            }
            for (int h = 0; h < 3; h++) {
                if (bottomLeftAir.getRelative(-dx, h, -dz).getType() != Material.ANCIENT_DEBRIS) {
                    sb.append("left-").append(h).append("=").append(bottomLeftAir.getRelative(-dx, h, -dz).getType()).append(" ");
                    ok = false;
                }
                if (bottomLeftAir.getRelative(2 * dx, h, 2 * dz).getType() != Material.ANCIENT_DEBRIS) {
                    sb.append("right-").append(h).append("=").append(bottomLeftAir.getRelative(2 * dx, h, 2 * dz).getType()).append(" ");
                    ok = false;
                }
            }
            if (ok) sb.append("(border OK — interior may be non-air/water)");
            sb.append("\n");
        }
        return sb.toString();
    }
}


