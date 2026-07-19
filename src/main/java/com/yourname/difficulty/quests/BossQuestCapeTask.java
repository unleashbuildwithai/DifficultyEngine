package com.yourname.difficulty.quests;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * BossQuestCapeTask — runs every 10 ticks (0.5 s).
 *
 * Players who have earned the Boss Quest Cape (completed all 150 secret quests)
 * get a permanent fire-particle ring orbiting them at waist height.
 *
 * The cape status is stored in the player's PDC under key "has_boss_quest_cape".
 */
public class BossQuestCapeTask extends BukkitRunnable {

    private final JavaPlugin    plugin;
    private final NamespacedKey capeKey;
    private int tick = 0;

    public BossQuestCapeTask(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.capeKey = new NamespacedKey(plugin, NpcQuestManager.PDC_BOSS_QUEST_CAPE);
    }

    @Override
    public void run() {
        tick++;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!hasBossCape(player)) continue;
            spawnFireRing(player);
        }
    }

    private boolean hasBossCape(Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(capeKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    private void spawnFireRing(Player player) {
        Location center = player.getLocation().clone().add(0, 0.9, 0);
        double radius   = 0.85;
        int    points   = 10;
        double angleOff = (tick * 18.0) % 360.0; // ring slowly rotates

        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(angleOff + (360.0 / points) * i);
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            Location loc = center.clone().add(dx, 0, dz);

            // Dense flame ring
            player.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0.0);

            // Soul fire flame every other tick for variety
            if (tick % 2 == 0) {
                player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc,
                        1, 0.03, 0.03, 0.03, 0.0);
            }
        }

        // Inner lava burst every 4 ticks
        if (tick % 4 == 0) {
            player.getWorld().spawnParticle(Particle.LAVA, center, 2, 0.3, 0.1, 0.3, 0.0);
        }
    }
}
