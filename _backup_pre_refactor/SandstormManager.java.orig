package com.yourname.difficulty.magic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SandstormManager — Controls the Air-on-Quicksand elemental sandstorm.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  1. Air bolt hits a Quicksand (soul-sand) block tagged by MagicStaffListener.
 *  2. triggerSandstorm(center, caster) is called.
 *  3. A 200-block radius storm spawns around the centre:
 *       • Sandy BLOCK particles fill the radius at random heights every 5 ticks.
 *       • 0.5 ♥ damage is dealt to every player inside every 2 seconds.
 *       • Players inside see a §bHydration BossBar (8 levels).
 *       • Hydration drains 1 level every 2 seconds.
 *       • When hydration hits 0 the player gets Weakness I.
 *  4. Duration:  first cast = 15 s.  Each subsequent cast on the SAME storm
 *     DOUBLES the remaining time, capped at 15 minutes (900 s).
 *     If the storm has expired you can re-trigger it for a fresh 15 s.
 *  5. Drinking a Water Bottle (vanilla) fills hydration to full.
 */
public class SandstormManager implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int    RADIUS_BLOCKS  = 200;
    private static final long   MIN_DURATION   = 15L * 20L;      // 15 s in ticks
    private static final long   MAX_DURATION   = 900L * 20L;     // 15 min in ticks
    private static final double DAMAGE_PER_HIT = 1.0;            // 0.5 hearts
    private static final int    MAX_HYDRATION  = 8;
    private static final Random RAND           = new Random();

    // ── State ─────────────────────────────────────────────────────────────────
    /** Active storms: key = "world:cx:cz" */
    private final Map<String, StormData>   activeStorms   = new HashMap<>();
    /** Per-player hydration levels (0-8) */
    private final Map<UUID, Integer>       hydration      = new HashMap<>();
    /** Per-player hydration BossBar */
    private final Map<UUID, BossBar>       hydrationBars  = new HashMap<>();

    private final JavaPlugin plugin;

    public SandstormManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // Global tick loop: update storms + hydration every 40 ticks (2 s)
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
        // Particle visual loop: every 5 ticks
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnParticles, 5L, 5L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Trigger or extend a sandstorm centred on {@code centre}.
     * Duration rule: new storm = 15 s.  Existing storm = min(current×2, cap).
     */
    public void triggerSandstorm(Location centre, Player caster) {
        String key = stormKey(centre);
        StormData storm = activeStorms.get(key);

        if (storm == null || !storm.isAlive()) {
            // New storm
            storm = new StormData(centre, MIN_DURATION);
            activeStorms.put(key, storm);
            broadcastStormMessage(centre, "§6☁ §e§lSANDSTORM! §7The earth has erupted!", caster);
        } else {
            // Extend: double remaining ticks, cap at MAX_DURATION
            long newTicks = Math.min(storm.remainingTicks() * 2, MAX_DURATION);
            storm.setRemainingTicks(newTicks);
            if (caster != null)
                caster.sendMessage("§6☁ §7Sandstorm extended! §8(" + (newTicks / 20) + "s remaining)");
        }
    }

    /** Cleanly shut down all storms on plugin disable. */
    public void shutdown() {
        for (StormData sd : activeStorms.values()) sd.cancel();
        activeStorms.clear();
        for (BossBar bb : hydrationBars.values()) bb.removeAll();
        hydrationBars.clear();
        hydration.clear();
    }

    // ── Tick loops ────────────────────────────────────────────────────────────

    private void tickAll() {
        Iterator<Map.Entry<String, StormData>> it = activeStorms.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, StormData> entry = it.next();
            StormData sd = entry.getValue();
            if (!sd.tickDown()) {
                it.remove();
                sd.cancel();
                // Announce end
                for (Player p : sd.centre.getWorld().getPlayers()) {
                    double distSq = p.getLocation().distanceSquared(sd.centre);
                    if (distSq <= (RADIUS_BLOCKS + 20.0) * (RADIUS_BLOCKS + 20.0))
                        p.sendActionBar("§7The sandstorm has ended.");
                }
                continue;
            }
            // Deal damage + hydration drain to players inside radius
            for (Player p : sd.centre.getWorld().getPlayers()) {
                if (insideStorm(p, sd)) {
                    applyStormEffects(p);
                } else {
                    hideHydrationBar(p);
                }
            }
        }
        // Hide bars for players not in any storm
        for (UUID uid : new ArrayList<>(hydrationBars.keySet())) {
            Player p = Bukkit.getPlayer(uid);
            if (p == null) { hideHydrationBar(uid); continue; }
            if (!isInAnyStorm(p)) hideHydrationBar(p);
        }
    }

    private void applyStormEffects(Player player) {
        // Damage (0.5 hearts)
        player.damage(DAMAGE_PER_HIT);
        // Drain hydration
        int h = hydration.getOrDefault(player.getUniqueId(), MAX_HYDRATION) - 1;
        if (h < 0) h = 0;
        hydration.put(player.getUniqueId(), h);
        // Show BossBar
        updateHydrationBar(player, h);
        // Weakness when dehydrated
        if (h == 0) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.WEAKNESS, 60, 0, false, true, true));
            player.sendActionBar("§c💀 §7You are §cDehydrated§7! Drink a water bottle!");
        } else {
            String hearts = "§b" + "♥".repeat(h) + "§8" + "♡".repeat(MAX_HYDRATION - h);
            player.sendActionBar("§6☁ §7Sandstorm! " + hearts + " §7— Drink water to stay hydrated!");
        }
    }

    private void spawnParticles() {
        for (StormData sd : activeStorms.values()) {
            if (!sd.isAlive()) continue;
            spawnSandParticles(sd.centre);
        }
    }

    private void spawnSandParticles(Location centre) {
        org.bukkit.World world = centre.getWorld();
        if (world == null) return;
        // Spawn ~80 particle bursts spread across the 200-block radius per frame
        for (int i = 0; i < 80; i++) {
            double angle  = RAND.nextDouble() * 2 * Math.PI;
            double radius = RAND.nextDouble() * RADIUS_BLOCKS;
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

    // ── Hydration bar ─────────────────────────────────────────────────────────

    private void updateHydrationBar(Player player, int level) {
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

    private void hideHydrationBar(Player player) {
        hideHydrationBar(player.getUniqueId());
    }

    private void hideHydrationBar(UUID uid) {
        BossBar bar = hydrationBars.remove(uid);
        if (bar != null) { bar.setVisible(false); bar.removeAll(); }
    }

    // ── Water bottle consumption ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrinkWater(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        // Water bottle or custom water potion (named "Water")
        boolean isWater = item.getType() == Material.POTION
            || item.getType() == Material.WATER_BUCKET;
        // Also accept a plain Glass Bottle if named "Water"
        if (!isWater && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            isWater = meta != null && "§bWater".equals(meta.getDisplayName());
        }
        if (!isWater) return;
        Player player = event.getPlayer();
        if (!isInAnyStorm(player)) return;
        // Fill hydration to full
        hydration.put(player.getUniqueId(), MAX_HYDRATION);
        updateHydrationBar(player, MAX_HYDRATION);
        player.sendActionBar("§b💧 §7You drank water — hydration restored!");
    }

    // ── Player quit cleanup ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        hideHydrationBar(event.getPlayer());
        hydration.remove(event.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean insideStorm(Player player, StormData sd) {
        if (!player.getWorld().equals(sd.centre.getWorld())) return false;
        double dx = player.getLocation().getX() - sd.centre.getX();
        double dz = player.getLocation().getZ() - sd.centre.getZ();
        return (dx * dx + dz * dz) <= (RADIUS_BLOCKS * RADIUS_BLOCKS);
    }

    private boolean isInAnyStorm(Player player) {
        for (StormData sd : activeStorms.values()) {
            if (sd.isAlive() && insideStorm(player, sd)) return true;
        }
        return false;
    }

    private String stormKey(Location loc) {
        return loc.getWorld().getName()
            + ":" + (loc.getBlockX() / 200) * 200
            + ":" + (loc.getBlockZ() / 200) * 200;
    }

    private void broadcastStormMessage(Location centre, String msg, Player caster) {
        for (Player p : centre.getWorld().getPlayers()) {
            double distSq = p.getLocation().distanceSquared(centre);
            if (distSq <= (RADIUS_BLOCKS + 50.0) * (RADIUS_BLOCKS + 50.0))
                p.sendMessage(msg);
        }
    }

    // ── Inner data class ──────────────────────────────────────────────────────

    private static class StormData {
        final Location centre;
        private long ticksLeft;

        StormData(Location centre, long ticks) {
            this.centre   = centre;
            this.ticksLeft = ticks;
        }

        /** Decrements by 40 (the tick interval). Returns true if still alive. */
        boolean tickDown() {
            ticksLeft -= 40;
            return ticksLeft > 0;
        }

        boolean isAlive() { return ticksLeft > 0; }
        long remainingTicks() { return ticksLeft; }
        void setRemainingTicks(long t) { ticksLeft = t; }
        void cancel() { ticksLeft = 0; }
    }
}
