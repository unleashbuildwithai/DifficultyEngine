package com.yourname.difficulty.boss.gilded;

import com.yourname.difficulty.boss.BossDisplayUtil;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Pillager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Streams each Gilded Enforcer Pillager carrier's live location to its paired
 * gilded_boss ItemDisplay and applies a gentle bob/spin animation. Extracted
 * from {@link GildedBossManager} to keep files under the 400-line limit.
 */
final class GildedDisplaySync {

    /** Visual scale of the Enforcer's custom gilded_boss model display. */
    static final float GILDED_DISPLAY_SCALE = 4.0f;
    /** How fast the gilded_boss model bobs/rotates in place (radians per tick). */
    private static final double GILDED_SPIN_SPEED = 0.015;

    private GildedDisplaySync() {}

    /** Registers the repeating position-sync task on the given plugin's scheduler. */
    static void start(JavaPlugin plugin, GildedBossState state) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(plugin, state), 1L, 1L);
    }

    private static void tick(JavaPlugin plugin, GildedBossState state) {
        if (state.carrierToDisplay.isEmpty()) return;
        Iterator<Map.Entry<UUID, UUID>> it = state.carrierToDisplay.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            Entity carrier = plugin.getServer().getEntity(entry.getKey());
            Entity display = plugin.getServer().getEntity(entry.getValue());

            if (!(carrier instanceof Pillager p) || p.isDead() || !p.isValid()) {
                if (display != null && !display.isDead()) display.remove();
                it.remove();
                state.spinAngles.remove(entry.getKey());
                continue;
            }
            if (!(display instanceof ItemDisplay id) || display.isDead() || !display.isValid()) {
                it.remove();
                state.spinAngles.remove(entry.getKey());
                continue;
            }

            double angle = state.spinAngles.getOrDefault(entry.getKey(), 0.0) + GILDED_SPIN_SPEED;
            state.spinAngles.put(entry.getKey(), angle);
            double bob = Math.sin(angle * 2.0) * 0.15;
            Location target = carrier.getLocation().clone().add(0, bob, 0);
            if (!display.getLocation().getWorld().equals(target.getWorld())
                    || display.getLocation().distanceSquared(target) > 0.0004) {
                display.teleport(target);
            }
            BossDisplayUtil.setYawRotation(id, GILDED_DISPLAY_SCALE, (float) angle);
        }
    }
}
