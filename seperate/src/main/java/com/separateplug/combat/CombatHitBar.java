package com.separateplug.combat;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CombatHitBar — Tracks melee/spell hits in a BossBar during combat.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  • The bar appears when a player lands a hit on another player.
 *  • Each hit adds 1 segment (10 hits = full bar).
 *  • The bar persists for 30 seconds after the last hit.
 *    After 30 s with no hits the bar resets and hides.
 *  • When the bar is FULL the player's next attack delivers a STUN HIT:
 *      - The stun sword applies a bonus 0.5-second extended stun.
 *      - The spirit staff's knockback is doubled.
 *    After a stun hit the bar resets to zero.
 *
 * ── Display ───────────────────────────────────────────────────────────────────
 *  The BossBar is gold while building up, turns red when full.
 *  Title: "§e⚔ Combat Charge §8[x/10]" or "§c§lSTUN READY!"
 */
public class CombatHitBar {

    private static final int   MAX_HITS      = 10;
    private static final long  IDLE_TICKS    = 600L;  // 30 seconds

    private final JavaPlugin             plugin;
    private final Map<UUID, BossBar>     bars       = new HashMap<>();
    private final Map<UUID, Integer>     hitCounts  = new HashMap<>();
    private final Map<UUID, BukkitTask>  idleTasks  = new HashMap<>();
    /** True when the bar is full and the next hit should be a stun. */
    private final Map<UUID, Boolean>     stunReady  = new HashMap<>();

    public CombatHitBar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register a hit from {@code player}.
     * @return true if the bar just became full (stun ready!).
     */
    public boolean registerHit(Player player) {
        int hits = hitCounts.getOrDefault(player.getUniqueId(), 0) + 1;
        if (hits > MAX_HITS) hits = MAX_HITS;
        hitCounts.put(player.getUniqueId(), hits);
        updateBar(player, hits);
        resetIdleTimer(player);

        if (hits >= MAX_HITS && !isStunReady(player)) {
            stunReady.put(player.getUniqueId(), true);
            return true; // bar just filled
        }
        return false;
    }

    /** @return true if the player's stun is fully charged. */
    public boolean isStunReady(Player player) {
        return stunReady.getOrDefault(player.getUniqueId(), false);
    }

    /**
     * Consume the stun charge (call when the stun hit is delivered).
     * Resets the bar to zero.
     */
    public void consumeStun(Player player) {
        stunReady.put(player.getUniqueId(), false);
        hitCounts.put(player.getUniqueId(), 0);
        updateBar(player, 0);
    }

    /** Remove all bars and tasks (call on plugin disable). */
    public void shutdown() {
        for (BossBar bar : bars.values()) { bar.setVisible(false); bar.removeAll(); }
        bars.clear();
        for (BukkitTask task : idleTasks.values()) task.cancel();
        idleTasks.clear();
        hitCounts.clear();
        stunReady.clear();
    }

    /** Hide and evict a player's bar on quit. */
    public void remove(UUID uuid) {
        BossBar bar = bars.remove(uuid);
        if (bar != null) { bar.setVisible(false); bar.removeAll(); }
        BukkitTask task = idleTasks.remove(uuid);
        if (task != null) task.cancel();
        hitCounts.remove(uuid);
        stunReady.remove(uuid);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void updateBar(Player player, int hits) {
        BossBar bar = bars.computeIfAbsent(player.getUniqueId(), uid -> {
            BossBar b = Bukkit.createBossBar("§e⚔ Combat Charge", BarColor.YELLOW, BarStyle.SEGMENTED_10);
            b.addPlayer(player);
            return b;
        });

        double progress = hits / (double) MAX_HITS;
        bar.setProgress(Math.max(0, Math.min(1.0, progress)));
        bar.setVisible(true);

        if (hits >= MAX_HITS) {
            bar.setColor(BarColor.RED);
            bar.setTitle("§c§l⚡ STUN READY! §8— swing to unleash!");
        } else {
            bar.setColor(BarColor.YELLOW);
            bar.setTitle("§e⚔ Combat Charge §8[§f" + hits + "§8/§f" + MAX_HITS + "§8]");
        }
    }

    private void resetIdleTimer(Player player) {
        BukkitTask old = idleTasks.remove(player.getUniqueId());
        if (old != null) old.cancel();

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            idleTasks.remove(player.getUniqueId());
            hitCounts.remove(player.getUniqueId());
            stunReady.remove(player.getUniqueId());
            BossBar bar = bars.remove(player.getUniqueId());
            if (bar != null) { bar.setVisible(false); bar.removeAll(); }
        }, IDLE_TICKS);

        idleTasks.put(player.getUniqueId(), task);
    }
}
