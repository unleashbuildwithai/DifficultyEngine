package com.yourname.difficulty.boss;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * BossDisplayUtil — small shared helper for spawning a billboarded
 * {@link ItemDisplay} prop used as a boss's "visual carrier" (Void Zurion's
 * orb, Tempest Overlord's spinning staffs, custom Blockbench boss models,
 * etc).
 *
 * <p>Extracted during the 400-line-file cleanup pass to remove near-identical
 * boilerplate that was duplicated across boss manager classes. Behaviour for
 * each individual call site is unchanged.
 */
public final class BossDisplayUtil {

    private BossDisplayUtil() {}

    /**
     * Spawns a billboarded ItemDisplay at the given carrier entity's location
     * (optionally offset), themed via the given material + custom model data.
     *
     * @param carrier        the entity to spawn the display next to
     * @param offsetY        vertical offset added to the carrier's location
     * @param material       the base item material shown by the display
     * @param customModelData reserved model id for resource-pack reskinning
     * @param scale          uniform scale applied to the display transform
     * @param brightnessBlock  block-light brightness override (0-15)
     * @param brightnessSky    sky-light brightness override (0-15)
     * @param customName     name-tag text, or null for no name
     * @param tagKey         PDC key stamped onto the display for identification
     */
    public static ItemDisplay spawnBillboardDisplay(Entity carrier, double offsetY,
                                                      Material material, int customModelData,
                                                      float scale, int brightnessBlock, int brightnessSky,
                                                      String customName, NamespacedKey tagKey) {
        return spawnDisplay(carrier, offsetY, material, customModelData, scale,
                brightnessBlock, brightnessSky, customName, tagKey, Display.Billboard.CENTER);
    }

    /**
     * Same as {@link #spawnBillboardDisplay} but lets the caller choose the
     * billboard mode. Full sculpted 3D boss models (Blockbench exports) look
     * wrong with {@code CENTER} billboarding (they would constantly spin to
     * face the camera like a flat icon) — those should use
     * {@link Display.Billboard#FIXED} instead so the model reads as a real
     * 3D shape with a stable orientation that can be spun/animated manually
     * per-tick if desired.
     */
    public static ItemDisplay spawnDisplay(Entity carrier, double offsetY,
                                            Material material, int customModelData,
                                            float scale, int brightnessBlock, int brightnessSky,
                                            String customName, NamespacedKey tagKey,
                                            Display.Billboard billboard) {
        ItemStack visual = new ItemStack(material);
        ItemMeta meta = visual.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            visual.setItemMeta(meta);
        }

        return carrier.getWorld().spawn(
                carrier.getLocation().clone().add(0, offsetY, 0), ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setBillboard(billboard);
            d.setPersistent(false);
            if (customName != null) {
                d.setCustomName(customName);
                d.setCustomNameVisible(true);
            }
            d.setBrightness(new Display.Brightness(brightnessBlock, brightnessSky));

            Transformation t = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            );
            d.setTransformation(t);

            d.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /**
     * Applies a Y-axis rotation (radians) plus a scale to an already-spawned
     * display's transformation, preserving its translation. Used to animate
     * custom boss models (spin, sway, flicker-scale) each tick without
     * re-creating the transformation object from scratch each call site.
     */
    public static void setYawRotation(ItemDisplay display, float scale, float yawRadians) {
        Transformation t = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new AxisAngle4f(0f, 0f, 0f, 1f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(yawRadians, 0f, 1f, 0f)
        );
        display.setTransformation(t);
    }
}
