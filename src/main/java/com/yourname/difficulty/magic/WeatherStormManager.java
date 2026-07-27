package com.yourname.difficulty.magic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * WeatherStormManager - Controls the Water Lv99 "Downpour Rain" weather
 * effect and the Earth Lv99 "Sand Rain" damage-over-time upgrade.
 *
 * How it works:
 *  1. Water Staff right-click at Magic Lv99 (+ Water Book) starts the vanilla
 *     Downpour ritual (handled in MagicStaffListener). Once the ritual
 *     completes, startDownpourRain(Player) is called - this sets
 *     the caster's world to raining (thundering off) and begins a
 *     snow-with-sand-dust particle overlay for all nearby players, purely
 *     cosmetic and harmless on its own.
 *  2. While that rain is active, an Earth Staff Lv99 right-click (+ Earth
 *     Book) calls activateSandRain(Player). If a Downpour rain is
 *     currently active in the caster's world, this flags it as "Sand Rain" -
 *     from that point on, every player standing in the open (can see sky)
 *     while the rain is active takes periodic sand damage until either the
 *     rain ends or they gain immunity.
 *
 * Immunity:
 *  Players can gain a temporary Sand Rain immunity buff (2 minutes) via the
 *  Support Staff's Hydration/Immunity potion - see grantSandRainImmunity.
 *
 * Duration:
 *  Tied to the same 8-minute Downpour window tracked by MagicStaffListener.
 *  When that expires, the rain is cleared automatically.
 */
public class WeatherStormManager implements Listener {

    private static final double SAND_RAIN_DAMAGE = 1.0; // 0.5 heart per tick
    private static final long   SAND_TICK_INTERVAL = 40L; // every 2s
    private static final Random RAND = new Random();

    private final JavaPlugin plugin;

    /** World UUID -> active downpour rain state. */
    private final Map<UUID, RainState> activeRains = new HashMap<>();
    /** Player UUID -> epoch-ms when their Sand Rain immunity buff expires. */
    private final Map<UUID, Long> sandRainImmunity = new HashMap<>();

    private BukkitTask particleTask;
    private BukkitTask damageTask;

    public WeatherStormManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickParticles, 5L, 5L);
        this.damageTask   = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickDamage, SAND_TICK_INTERVAL, SAND_TICK_INTERVAL);
    }

    // ---- Public API ----

    /**
     * Starts the Downpour Rain visual/weather effect in the caster's world.
     * Called once the Water Staff's Lv99 Downpour ritual completes.
     */
    public void startDownpourRain(Player caster) {
        if (caster == null || caster.getWorld() == null) return;
        World world = caster.getWorld();
        world.setStorm(true);
        world.setThundering(false);
        world.setWeatherDuration(8 * 60 * 20); // matches the 8-minute Downpour buff

        RainState state = new RainState(caster.getUniqueId());
        activeRains.put(world.getUID(), state);

        caster.sendMessage("§b§lDOWNPOUR RAIN! §7The skies open above §b" + world.getName() + "§7!");
    }

    /**
     * Attempts to flag the currently-active Downpour rain (if any) in the
     * caster's world as "Sand Rain". Returns false if there is no
     * active rain to upgrade.
     */
    public boolean activateSandRain(Player caster) {
        if (caster == null || caster.getWorld() == null) return false;
        RainState state = activeRains.get(caster.getWorld().getUID());
        if (state == null || !caster.getWorld().hasStorm()) return false;
        state.sandRain = true;
        state.triggeredBy = caster.getUniqueId();

        for (Player p : caster.getWorld().getPlayers()) {
            p.sendMessage("§2§lSAND RAIN! §7The rain has turned to stinging sand!");
        }
        return true;
    }

    /**
     * Grants the given player immunity to Sand Rain damage for the given
     * duration (ms). Used by the Support Staff's Hydration/Immunity potion.
     */
    public void grantSandRainImmunity(Player player, long durationMs) {
        if (player == null) return;
        sandRainImmunity.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        player.sendMessage("§e✦ §7You are now immune to §2Sand Rain §7damage for §f"
                + (durationMs / 1000L) + "s§7!");
    }

    /** Returns true if the given player currently has active Sand Rain immunity. */
    public boolean hasSandRainImmunity(Player player) {
        if (player == null) return false;
        Long expiry = sandRainImmunity.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            sandRainImmunity.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * Returns true if there is an active Downpour/weather event (rain or
     * sand rain) currently running in the given world. Used to gate the
     * Fire Staff Lv99 Lightning Strike's Downpour damage modifier.
     */
    public boolean isWeatherEventActive(org.bukkit.World world) {
        if (world == null) return false;
        RainState state = activeRains.get(world.getUID());
        return state != null && world.hasStorm();
    }

    /**
     * Returns true if the active weather event in the given world has been
     * upgraded to Sand Rain (Earth Lv99 right-click). Used to determine
     * whether lightning should be buffed (+15%, pure Downpour) or nerfed
     * (-15%, Sand Rain — "it's grounded" by the sand).
     */
    public boolean isSandRainActive(org.bukkit.World world) {
        if (world == null) return false;
        RainState state = activeRains.get(world.getUID());
        return state != null && state.sandRain && world.hasStorm();
    }


    /**
     * Fully stops the Downpour/Sand Rain weather event in the given world —
     * clears the vanilla storm and removes the tracked rain state. Used when
     * a player manually deactivates Downpour (right-click while active) or
     * when the rune upkeep runs dry.
     */
    public void stopDownpour(org.bukkit.World world) {
        if (world == null) return;
        world.setStorm(false);
        world.setThundering(false);
        activeRains.remove(world.getUID());
    }

    /** Cleanly shuts down all tasks and state on plugin disable. */
    public void shutdown() {

        if (particleTask != null) particleTask.cancel();
        if (damageTask   != null) damageTask.cancel();
        activeRains.clear();
        sandRainImmunity.clear();
    }

    // ---- Tick loops ----

    private void tickParticles() {
        for (Map.Entry<UUID, RainState> entry : activeRains.entrySet()) {
            World world = Bukkit.getWorld(entry.getKey());
            if (world == null || !world.hasStorm()) continue;
            RainState state = entry.getValue();

            for (Player p : world.getPlayers()) {
                if (!canSeeSky(p)) continue;
                Location loc = p.getLocation().add(
                    (RAND.nextDouble() - 0.5) * 6,
                    6 + RAND.nextDouble() * 2,
                    (RAND.nextDouble() - 0.5) * 6);

                if (state.sandRain) {
                    // ── Sand Rain visual — heavy, dense sand fill, no snow/rain mixed in ──
                    // Thick enough to be genuinely hard to see through, matching the
                    // density of the old snowflake fall it replaces.
                    for (int i = 0; i < 3; i++) {
                        Location sandLoc = p.getLocation().add(
                            (RAND.nextDouble() - 0.5) * 7,
                            5 + RAND.nextDouble() * 3,
                            (RAND.nextDouble() - 0.5) * 7);
                        world.spawnParticle(Particle.BLOCK, sandLoc, 6, 0.4, 0.3, 0.4, 0.0,
                            org.bukkit.Material.SAND.createBlockData());
                    }
                    // Windy swirl — GUST/CLOUD gives the "hard to see, blowing sand" feel
                    world.spawnParticle(Particle.GUST, loc, 2, 0.5, 0.3, 0.5, 0.02);
                    world.spawnParticle(Particle.CLOUD, loc, 3, 0.6, 0.2, 0.6, 0.01);
                } else {
                    // Plain Downpour — normal rain-like fall (unaffected by this fix)
                    world.spawnParticle(Particle.SNOWFLAKE, loc, 3, 0.3, 0.1, 0.3, 0.02);
                }
            }
        }
    }

    private void tickDamage() {
        var it = activeRains.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            World world = Bukkit.getWorld(entry.getKey());
            RainState state = entry.getValue();

            if (world == null || !world.hasStorm()) {
                it.remove();
                continue;
            }
            if (!state.sandRain) continue;

            // Sand Rain damages EVERY player in the entire area the Downpour
            // weather covers (the whole world, since Downpour is world-wide
            // weather) — not just players who can currently see open sky.
            for (Player p : world.getPlayers()) {
                if (hasSandRainImmunity(p)) continue;
                if (isImmuneCaster(p, state)) continue;

                p.damage(SAND_RAIN_DAMAGE);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SAND_HIT, 0.6f, 1.2f);
                p.sendActionBar("§2§7Sand Rain stings you! §8(Immunity potion available from Support Staff)");
            }
        }
    }


    private boolean isImmuneCaster(Player p, RainState state) {
        return state.triggeredBy != null && state.triggeredBy.equals(p.getUniqueId());
    }

    private boolean canSeeSky(Player p) {
        try {
            return p.getWorld().getHighestBlockYAt(p.getLocation()) <= p.getLocation().getBlockY();
        } catch (Exception e) {
            return true;
        }
    }

    // ---- Cleanup ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        sandRainImmunity.remove(event.getPlayer().getUniqueId());
    }

    // ---- Inner state ----

    private static class RainState {
        boolean sandRain = false;
        UUID triggeredBy;

        RainState(UUID triggeredBy) {
            this.triggeredBy = triggeredBy;
        }
    }
}
