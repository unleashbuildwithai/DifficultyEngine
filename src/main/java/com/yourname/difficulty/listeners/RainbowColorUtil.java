package com.yourname.difficulty.listeners;

import org.bukkit.Color;

/**
 * Shared HSB → RGB rainbow colour helper used by {@link CapeVisualTask} and
 * its split-out helper classes ({@link MagicCapeCosmetics}, {@link FishingCapeOrbit}).
 */
final class RainbowColorUtil {

    private RainbowColorUtil() {}

    static Color hsbToColor(float hue) {
        int   h = (int)(hue * 6);
        float f = hue * 6 - h;
        float q = 1 - f;
        float r, g, b;
        switch (h % 6) {
            case 0  -> { r = 1; g = f; b = 0; }
            case 1  -> { r = q; g = 1; b = 0; }
            case 2  -> { r = 0; g = 1; b = f; }
            case 3  -> { r = 0; g = q; b = 1; }
            case 4  -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Color.fromRGB(
                Math.max(0, Math.min(255, (int)(r * 255))),
                Math.max(0, Math.min(255, (int)(g * 255))),
                Math.max(0, Math.min(255, (int)(b * 255))));
    }
}
