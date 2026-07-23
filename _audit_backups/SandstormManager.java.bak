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
import java.util.Set;
import java.util.UUID;

/**
 * SandstormManager — Controls the Earth Staff's elemental Sandstorm spell.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  1. Air bolt hits a Quicksand (soul-sand) block tagged by MagicStaffListener,
 *     OR the Earth Staff's Sandstorm Gate is triggered on Magic Water while the
 *     caster is Magic Lv99 + carries the Sandstorm Book.
 *  2. triggerSandstorm(center, caster, capMs) is called.
 *  3. A 200-block radius storm spawns around the centre:
 *       • Sandy BLOCK particles fill the radius at random heights every 5 ticks.
 *       • 0.5 ♥ damage is dealt to every player inside every 2 seconds.
 *       • Players inside see a §bHydration BossBar (8 levels).
 *       • Hydration drains 1 level every 2 seconds.
 *       • When hydration hits 0 the player gets Weakness I.
 *
 * ── Duration & Cap ────────────────────────────────────────────────────────────
 *  Duration:  first cast = 15 s.  Each subsequent cast on the SAME storm
 *  DOUBLES the remaining time, capped at 15 minutes (900 s) — UNLESS a
 *  {@code capMs} was supplied (e.g. from the Earth Staff's Sandstorm Gate,
 *  which caps duration to the caster's remaining Water Downpour time). The
 *  storm can never outlast the Water spell that empowers it.
 *
 * ── Upkeep ────────────────────────────────────────────────────────────────────
 *  Every 30 real seconds, 1 Sand block is automatically consumed from the
 *  triggering caster's inventory to keep the storm alive. If the caster is
 *  offline or has no Sand, the storm immediately dissipates early.
 *
 * ── Immunity ──────────────────────────────────────────────────────────────────
 *  The caster and any of their party members (via PartyManager) take NO
 *  Sandstorm damage and do NOT lose hydration while inside the storm.
 *
 * ── Hydration Refill ──────────────────────────────────────────────────────────
 *  Drinking a Water Bottle (vanilla) fills hydration to full.
 */
public class SandstormManager implements Listener {

    // ── Constants ─────────────────────────────────────────────────────────────
    private static final int    RADIUS_BLOCKS    = 200;
    private static final long   MIN_DURATION     = 15L * 20L;      // 15 s in ticks
    private static final long   MAX_DURATION     = 900L * 20L;     // 15 min in ticks
    private static final double DAMAGE_PER_HIT   = 1.0;            // 0.5 hearts
    private static final int    MAX_HYDRATION    = 8;
    private static final long   UPKEEP_INTERVAL_TICKS = 30L * 20L; // 1 sand every 30s
    private static final Random RAND             = new Random();

    // ── State ─────────────────────────────────────────────────────────────────
    /** Active storms: key = "world:cx:cz" */
    private final Map<String, StormData>   activeStorms   = new HashMap<>();
    /** Per-player hydration levels (0-8) */
    private final Map<UUID, Integer>       hydration      = new HashMap<>();
    /** Per-player hydration BossBar */
    private final Map<UUID, BossBar>       hydrationBars  = new HashMap<>();
    /** Per-player epoch-ms timestamp when their Sandstorm damage immunity buff expires. */
    private final Map<UUID, Long>          immunityUntil  = new HashMap<>();


    private final JavaPlugin plugin;
    /** Optional PartyManager — used to grant Sandstorm immunity to the caster's party. */
    private com.yourname.difficulty.party.PartyManager partyManager = null;

    public SandstormManager(JavaPlugin plugin) {
        this.plugin = plugin;
        // Global tick loop: update storms + hydration every 40 ticks (2 s)
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 40L);
        // Particle visual loop: every 5 ticks
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::spawnParticles, 5L, 5L);
    }

    /** Wires in the PartyManager so the caster's party is immune to their own storm. */
    public void setPartyManager(com.yourname.difficulty.party.PartyManager pm) {
        this.partyManager = pm;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Instantly refills the given player's Sandstorm hydration bar to full.
     * Used by the Support Staff's Hydration Potion.
     */
    public void refillHydration(Player player) {
        if (player == null) return;
        hydration.put(player.getUniqueId(), MAX_HYDRATION);
        if (isInAnyStorm(player)) updateHydrationBar(player, MAX_HYDRATION);
        player.sendActionBar("§b💧 §aHydration fully restored!");
    }

    /**
     * Grants the given player immunity to ALL Sandstorm damage/hydration-drain
     * for the given duration (ms). Used by the Support Staff's Sandstorm
     * Immunity buff — stacks fine alongside existing caster/party immunity.
     */
    public void grantImmunity(Player player, long durationMs) {
        if (player == null) return;
        immunityUntil.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
        player.sendMessage("§e✦ §7You are now immune to §6Sandstorm §7damage for §f"
                + (durationMs / 1000L) + "s§7!");
    }

    /** Returns true if the given player currently has an active immunity buff. */
    public boolean hasBuffImmunity(Player player) {
        if (player == null) return false;
        Long expiry = immunityUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            immunityUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }


    /**
     * Trigger or extend a sandstorm centred on {@code centre} with NO duration
     * cap (used by the Air-on-Quicksand combo path, which has no Downpour tie-in).
     */
    public void triggerSandstorm(Location centre, Player caster) {
        triggerSandstorm(centre, caster, -1L);
    }

    /**
     * Trigger or extend a sandstorm centred on {@code centre}.
     * Duration rule: new storm = 15 s.  Existing storm = min(current×2, cap).
     *
     * @param capMs if {@code > 0}, the storm's total duration (in milliseconds)
     *              is hard-capped to this value — used by the Earth Staff's
     *              Sandstorm Gate to ensure the storm never outlasts the
     *              caster's remaining Water Downpour buff. Pass {@code -1}
     *              for no cap (falls back to the vanilla 15min cap).
     */
    public void triggerSandstorm(Location centre, Player caster, long capMs) {
        String key = stormKey(centre);
        StormData storm = activeStorms.get(key);

        long capTicks = capMs > 0 ? Math.max(20L, capMs / 50L) : MAX_DURATION;

        if (storm == null || !storm.isAlive()) {
            // New storm
            long initial = Math.min(MIN_DURATION, capTicks);
            storm = new StormData(centre, initial, caster != null ? caster.getUniqueId() : null, capTicks);
            activeStorms.put(key, storm);
            broadcastStormMessage(centre, "§6☁ §e§lSANDSTORM! §7The earth has erupted!", caster);
            if (caster != null) {
                caster.sendActionBar("§6☁ §7Sandstorm active! §8(1 Sand consumed per 30s to sustain it)");
            }
        } else {
            // Extend: double remaining ticks, cap at capTicks (never exceeds the
            // Downpour-derived cap if one was supplied for this storm).
            long effectiveCap = Math.min(storm.capTicks, capTicks > 0 ? capTicks : storm.capTicks);
            long newTicks = Math.min(storm.remainingTicks() * 2, effectiveCap);
            storm.setRemainingTicks(newTicks);
            storm.capTicks = effectiveCap;
            if (caster != null) storm.casterUuid = caster.getUniqueId();
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

            // ── Upkeep: consume 1 Sand from the caster every 30s ───────────────
            sd.ticksSinceUpkeep += 40; // this loop runs every 40 ticks
            if (sd.ticksSinceUpkeep >= UPKEEP_INTERVAL_TICKS) {
                sd.ticksSinceUpkeep = 0;
                if (!consumeUpkeepSand(sd)) {
                    // No caster / no sand — storm dissipates immediately
                    it.remove();
                    sd.cancel();
                    for (Player p : sd.centre.getWorld().getPlayers()) {
                        double distSq = p.getLocation().distanceSquared(sd.centre);
                        if (distSq <= (RADIUS_BLOCKS + 20.0) * (RADIUS_BLOCKS + 20.0))
                            p.sendActionBar("§7The sandstorm ran out of sand and dissipated.");
                    }
                    continue;
                }
            }

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
                    if (isImmune(p, sd)) {
                        hideHydrationBar(p);
                        continue;
                    }
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

    /**
     * Attempts to consume 1 Sand block from the storm's caster inventory.
     * Returns {@code false} (storm should dissipate) if the caster is offline
     * or has no Sand remaining.
     */
    private boolean consumeUpkeepSand(StormData sd) {
        if (sd.casterUuid == null) return true; // no caster tracked — skip upkeep enforcement
        Player caster = Bukkit.getPlayer(sd.casterUuid);
        if (caster == null || !caster.isOnline()) return false;

        for (ItemStack item : caster.getInventory().getContents()) {
            if (item != null && item.getType() == Material.SAND) {
                if (item.getAmount() > 1) item.setAmount(item.getAmount() - 1);
                else item.setAmount(0);
                caster.sendActionBar("§6☁ §7Sandstorm upkeep: §e1 Sand §7consumed to sustain the storm.");
                return true;
            }
        }
        caster.sendMessage("§c✗ §7You ran out of §eSand§7 — the Sandstorm dissipates!");
        return false;
    }

    /** Returns true if the given player should take no damage/hydration loss from this storm. */
    private boolean isImmune(Player player, StormData sd) {
        if (hasBuffImmunity(player)) return true;
        if (sd.casterUuid == null) return false;
        if (player.getUniqueId().equals(sd.casterUuid)) return true;
        if (partyManager == null) return false;
        if (!partyManager.isInParty(sd.casterUuid)) return false;
        Set<UUID> members = partyManager.getPartyMembers(sd.casterUuid);
        return members.contains(player.getUniqueId());
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
        /** UUID of the player who triggered this storm — used for immunity + upkeep. */
        UUID casterUuid;
        /** Hard cap on this storm's total duration in ticks (e.g. from remaining Downpour). */
        long capTicks;
        /** Ticks elapsed since the last upkeep (sand) consumption. */
        long ticksSinceUpkeep = 0;

        StormData(Location centre, long ticks, UUID casterUuid, long capTicks) {
            this.centre     = centre;
            this.ticksLeft  = ticks;
            this.casterUuid = casterUuid;
            this.capTicks   = capTicks;
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
