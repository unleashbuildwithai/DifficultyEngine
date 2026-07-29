package com.yourname.difficulty.magic;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks per-player Sandstorm hydration levels, their hydration BossBars, and
 * temporary Sandstorm-damage immunity buffs (e.g. granted by the Support
 * Staff's potions). Extracted from {@link SandstormManager} to keep files
 * under the 400-line limit.
 */
class SandstormHydrationTracker {

    static final int MAX_HYDRATION = 8;

    /** Per-player hydration levels (0-8) */
    private final Map<UUID, Integer> hydration     = new HashMap<>();
    /** Per-player hydration BossBar */
    private final Map<UUID, BossBar> hydrationBars = new HashMap<>();
    /** Per-player epoch-ms timestamp when their Sandstorm damage immunity buff expires. */
    private final Map<UUID, Long>    immunityUntil = new HashMap<>();

    /** Instantly refills the given player's hydration bar to full. */
    void refillHydration(Player player) {
        if (player == null) return;
        hydration.put(player.getUniqueId(), MAX_HYDRATION);
    }

    /** Grants the given player immunity to Sandstorm damage/hydration-drain for {@code durationMs}. */
    void grantImmunity(Player player, long durationMs) {
        if (player == null) return;
        immunityUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
    }

    /** Returns true if the given player currently has an active immunity buff. */
    boolean hasBuffImmunity(Player player) {
        if (player == null) return false;
        Long expiry = immunityUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            immunityUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    int getHydration(UUID uid) {
        return hydration.getOrDefault(uid, MAX_HYDRATION);
    }

    void setHydration(UUID uid, int level) {
        hydration.put(uid, level);
    }

    void updateHydrationBar(Player player, int level) {
        BossBar bar = hydrationBars.computeIfAbsent(player.getUniqueId(), uid -> {
            BossBar b = Bukkit.createBossBar(
                "§b💧 Hydration", BarColor.BLUE, BarStyle.SEGMENTED_10);
            b.addPlayer(player);
            return b;
        });
        bar.setProgress(Math.max(0, Math.min(1.0, level / (double) MAX_HYDRATION)));
        bar.setTitle("§6☁ §e§lSANDSTORM §8| §b💧 Hydration: §f" + level + "§8/§f" + MAX_HYDRATION);
        bar.setVisible(true);
        if (level <= 2) bar.setColor(BarColor.RED);
        else if (level <= 4) bar.setColor(BarColor.YELLOW);
        else bar.setColor(BarColor.BLUE);
    }

    void hideHydrationBar(Player player) {
        hideHydrationBar(player.getUniqueId());
    }

    void hideHydrationBar(UUID uid) {
        BossBar bar = hydrationBars.remove(uid);
        if (bar != null) { bar.setVisible(false); bar.removeAll(); }
    }

    void hideBarsForPlayersNotInStorm(java.util.function.Predicate<Player> isInAnyStorm) {
        for (UUID uid : new ArrayList<>(hydrationBars.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p == null) { hideHydrationBar(uid); continue; }
            if (!isInAnyStorm.test(p)) hideHydrationBar(p);
        }
    }

    void onQuit(Player player) {
        hideHydrationBar(player);
        hydration.remove(player.getUniqueId());
    }

    void shutdown() {
        for (BossBar bb : hydrationBars.values()) bb.removeAll();
        hydrationBars.clear();
        hydration.clear();
    }
}
