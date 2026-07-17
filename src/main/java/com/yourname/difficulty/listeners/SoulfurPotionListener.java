package com.yourname.difficulty.listeners;

import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * SoulfurPotionListener — Escalating sip-counter system for the Soulfur Potion.
 *
 * ── Sip counter ───────────────────────────────────────────────────────────────
 * Every consumption adds 1 to the player's sip counter. A decay task fires
 * every 60 seconds and decrements the counter by 1. When it reaches 0 all
 * visual/damage effects stop ("effects slowly negate").
 *
 * ── Darkness progression (refreshed every second by main task) ────────────────
 *  1–4   sips → DARKNESS amplitude 0  (mild dimming)
 *  5–9   sips → DARKNESS amplitude 1
 *  10–14 sips → DARKNESS amplitude 2
 *  15+   sips → BLINDNESS — pitch black
 *
 * ── Damage progression ────────────────────────────────────────────────────────
 *  25–34 sips → 1 HP (½ heart)   every 30 seconds
 *  35–49 sips → 2 HP (1 heart)   every 60 seconds
 *  50+   sips → 6 HP (3 hearts)  every second (lethal)
 *
 * ── Drunken Sway ─────────────────────────────────────────────────────────────
 * Repeating task (every 20 ticks) that rotates yaw ±5–15°.
 * Cleansed by: entering WATER block or entering a bed.
 *
 * ── 50-sip death curse ────────────────────────────────────────────────────────
 * If a player dies with 50+ sips they receive a 24-hour Soulfur Curse on respawn:
 *   • SLOWNESS amplifier 1 (permanent until curse expires)
 *   • Sunlight deals ½ heart per second while exposed
 * Curse timestamp is persisted in PlayerDifficultyManager → player_data.yml.
 */
public class SoulfurPotionListener implements Listener {

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final JavaPlugin              plugin;
    private final ItemFactory             itemFactory;
    private final PlayerDifficultyManager manager;

    // ── Per-player state ──────────────────────────────────────────────────────
    /** Total sips consumed (current session, decays over time). */
    private final Map<UUID, Integer>    sipCount            = new HashMap<>();
    /** Active drunken-sway BukkitTasks. */
    private final Map<UUID, BukkitTask> activeSways         = new HashMap<>();
    /** Epoch-ms timestamp for when next sip-damage fires. */
    private final Map<UUID, Long>       nextDamageTime      = new HashMap<>();
    /** Epoch-ms timestamp for when next sunlight-curse damage fires. */
    private final Map<UUID, Long>       nextSunlightDmgTime = new HashMap<>();
    /** Players who died with 50+ sips — awaiting PlayerRespawnEvent. */
    private final Set<UUID>             pendingCurse        = new HashSet<>();

    private final Random random = new Random();

    // ── Constructor ───────────────────────────────────────────────────────────

    public SoulfurPotionListener(JavaPlugin plugin,
                                  ItemFactory itemFactory,
                                  PlayerDifficultyManager manager) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
        this.manager     = manager;
        startTasks();
    }

    // ── Scheduled tasks ───────────────────────────────────────────────────────

    private void startTasks() {
        // Main task — every 20 ticks (1 second): darkness, damage, curse
        new BukkitRunnable() {
            @Override public void run() {
                tickSipEffects();
                tickCurseEffects();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Decay task — every 1200 ticks (60 seconds): decrement sip counter
        new BukkitRunnable() {
            @Override public void run() { tickDecay(); }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    // ── Consumption ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        if (!itemFactory.isSoulfurPotion(event.getItem())) return;

        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        int sips = sipCount.merge(uuid, 1, Integer::sum);

        // Always refresh nausea on each sip
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.NAUSEA, 3600, 0, false, true, true));

        // Restart / continue drunken sway
        startDrunkenSway(player);

        // Milestone messages
        String msg = switch (sips) {
            case 1  -> "§5☠ §7Your vision warps. The world tilts around you...";
            case 5  -> "§5☠ §7The shadows grow deeper...";
            case 10 -> "§4☠ §cYou can barely see.";
            case 15 -> "§4☠ §4The world goes dark. §cYou are blind.";
            case 25 -> "§4☠ §cYour body begins to fail. The poison damages you.";
            case 35 -> "§4☠ §4The rot accelerates. §cDeath draws near.";
            case 50 -> "§4☠ §4☠ §4You have consumed too much. Death will mark you. §4☠ §4☠";
            default -> "§5☠ §7The madness deepens... §8(" + sips + " sips)";
        };
        player.sendMessage(msg);
    }

    // ── Main-task effects ─────────────────────────────────────────────────────

    private void tickSipEffects() {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID    uuid = player.getUniqueId();
            Integer sips = sipCount.get(uuid);
            if (sips == null || sips <= 0 || player.isDead()) continue;

            // ── Darkness (refreshed every second, 2-second duration) ────────────
            if (sips >= 15) {
                // Pitch black — BLINDNESS
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false), true);
            } else if (sips >= 10) {
                applyDarkness(player, 2);
            } else if (sips >= 5) {
                applyDarkness(player, 1);
            } else {
                applyDarkness(player, 0);
            }

            // ── Damage ────────────────────────────────────────────────────────
            if (sips >= 50) {
                // 3 hearts per second — lethal
                player.damage(6.0);
            } else if (sips >= 35) {
                Long next = nextDamageTime.get(uuid);
                if (next == null || now >= next) {
                    player.damage(2.0);
                    nextDamageTime.put(uuid, now + 60_000L); // 60 seconds
                }
            } else if (sips >= 25) {
                Long next = nextDamageTime.get(uuid);
                if (next == null || now >= next) {
                    player.damage(1.0);
                    nextDamageTime.put(uuid, now + 30_000L); // 30 seconds
                }
            }
        }
    }

    /** Applies DARKNESS at the given amplifier (0-2) with a 2-second refresh window. */
    private void applyDarkness(Player player, int amplifier) {
        try {
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.DARKNESS, 40, amplifier, false, false, true), true);
        } catch (Exception e) {
            // Fallback for servers where DARKNESS may not be registered
            if (amplifier >= 2) {
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false), true);
            }
        }
    }

    // ── Decay task ────────────────────────────────────────────────────────────

    private void tickDecay() {
        sipCount.entrySet().removeIf(entry -> {
            UUID uuid   = entry.getKey();
            int newSips = entry.getValue() - 1;

            if (newSips <= 0) {
                // Counter hit zero — clear all effects
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) {
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    cancelSway(uuid);
                    p.sendMessage("§7☁ §7The fog of the Soulfur slowly lifts...");
                }
                nextDamageTime.remove(uuid);
                return true; // remove from sipCount map
            }

            entry.setValue(newSips);
            return false;
        });
    }

    // ── Curse tick ────────────────────────────────────────────────────────────

    private void tickCurseEffects() {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!manager.isCursed(uuid) || player.isDead()) continue;

            // Keep SLOWNESS active (refresh every second, duration 2s)
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true), true);

            // Sunlight burn: ½ heart per second
            if (isInSunlight(player)) {
                Long next = nextSunlightDmgTime.get(uuid);
                if (next == null || now >= next) {
                    player.damage(1.0);
                    nextSunlightDmgTime.put(uuid, now + 1_000L);
                }
            }
        }
    }

    // ── 50-sip death → respawn curse ──────────────────────────────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        Integer sips = sipCount.get(uuid);
        if (sips != null && sips >= 50) {
            pendingCurse.add(uuid);
        }
        // Clear sip state on death; they start the afterlife fresh (cursed)
        sipCount.remove(uuid);
        nextDamageTime.remove(uuid);
        cancelSway(uuid);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        if (!pendingCurse.remove(uuid)) return;

        manager.setCursed(uuid);

        // Apply curse effects 5 ticks after respawn (inventory not ready immediately)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, false, true));
            player.sendMessage("§4☠ §cYou are cursed by the Soulfur.");
            player.sendMessage("§8  Sunlight burns your skin. Your speed is halved.");
            player.sendMessage("§8  The curse expires in §f24 hours§8.");
        }, 5L);
    }

    // ── Rejoin curse restore ──────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        if (!manager.isCursed(uuid)) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, false, true));
            long hoursLeft = (manager.getCursedUntil(uuid) - System.currentTimeMillis())
                             / (1000L * 60 * 60);
            player.sendMessage("§4☠ §cSoulfur Curse active. §7(" + Math.max(0, hoursLeft) + "h remaining)");
        }, 10L);
    }

    // ── Cleansing ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!activeSways.containsKey(player.getUniqueId())) return;
        if (player.getLocation().getBlock().getType() == Material.WATER) {
            cleanse(player);
            player.sendMessage("§b✦ §7The cool water washes the madness from your mind.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!activeSways.containsKey(player.getUniqueId())) return;
        cleanse(player);
        player.sendMessage("§e✦ §7Rest soothes your troubled mind. The sway fades.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelSway(uuid);
        nextSunlightDmgTime.remove(uuid);
    }

    // ── Drunken Sway ─────────────────────────────────────────────────────────

    private void startDrunkenSway(Player player) {
        cancelSway(player.getUniqueId());

        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline() || player.isDead()) {
                    cleanse(player);
                    return;
                }
                if (!player.hasPotionEffect(PotionEffectType.NAUSEA)) {
                    cancelSway(player.getUniqueId());
                    return;
                }
                int   magnitude = 5 + random.nextInt(11); // 5–15
                float offset    = random.nextBoolean() ? magnitude : -magnitude;
                player.setRotation(
                    player.getLocation().getYaw() + offset,
                    player.getLocation().getPitch());
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeSways.put(player.getUniqueId(), task);
    }

    private void cleanse(Player player) {
        player.removePotionEffect(PotionEffectType.NAUSEA);
        cancelSway(player.getUniqueId());
    }

    private void cancelSway(UUID uuid) {
        BukkitTask task = activeSways.remove(uuid);
        if (task != null) task.cancel();
    }

    // ── Sunlight check ────────────────────────────────────────────────────────

    private boolean isInSunlight(Player player) {
        long time = player.getWorld().getTime();
        boolean isDay = (time < 12000 || time > 23800);
        if (!isDay) return false;
        return player.getLocation().getBlock().getLightFromSky() >= 12;
    }
}
