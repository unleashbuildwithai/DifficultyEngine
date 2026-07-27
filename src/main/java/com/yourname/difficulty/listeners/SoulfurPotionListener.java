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
 * SoulfurPotionListener â€” Escalating sip-counter system for the Soulfur Potion.
 *
 * â”€â”€ Sip counter â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * Every consumption adds 1 to the player's sip counter. A decay task fires
 * every 60 seconds and decrements the counter by 1. When it reaches 0 all
 * visual/damage effects stop ("effects slowly negate").
 *
 * â”€â”€ Darkness progression (refreshed every second by main task) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  1â€“4   sips â†’ DARKNESS amplitude 0  (mild dimming)
 *  5â€“9   sips â†’ DARKNESS amplitude 1
 *  10â€“14 sips â†’ DARKNESS amplitude 2
 *  15+   sips â†’ BLINDNESS â€” pitch black
 *
 * â”€â”€ Damage progression â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  25â€“34 sips â†’ 1 HP (Â½ heart)   every 30 seconds
 *  35â€“49 sips â†’ 2 HP (1 heart)   every 60 seconds
 *  50+   sips â†’ 6 HP (3 hearts)  every second (lethal)
 *
 * â”€â”€ Drunken Sway â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * Repeating task (every 20 ticks) that rotates yaw Â±5â€“15Â°.
 * Cleansed by: entering WATER block or entering a bed.
 *
 * â”€â”€ 50-sip death curse â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 * If a player dies with 50+ sips they receive a 24-hour Soulfur Curse on respawn:
 *   â€¢ SLOWNESS amplifier 1 (permanent until curse expires)
 *   â€¢ Sunlight deals Â½ heart per second while exposed
 * Curse timestamp is persisted in PlayerDifficultyManager â†’ player_data.yml.
 */
public class SoulfurPotionListener implements Listener {

    // â”€â”€ Dependencies â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private final JavaPlugin              plugin;
    private final ItemFactory             itemFactory;
    private final PlayerDifficultyManager manager;

    // â”€â”€ Per-player state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    /** Total sips consumed (current session, decays over time). */
    private final Map<UUID, Integer>    sipCount            = new HashMap<>();
    /** Active drunken-sway BukkitTasks. */
    private final Map<UUID, BukkitTask> activeSways         = new HashMap<>();
    /**
     * Per-player blindness refresh tasks.
     * A task is created when sips cross the BLINDNESS threshold (15+).
     * It re-applies BLINDNESS every second and auto-cancels when sips fall below 15.
     * cleanse() also cancels it so going in water/bed fully clears vision.
     */
    private final Map<UUID, BukkitTask> blindnessTasks      = new HashMap<>();
    /** Epoch-ms timestamp for when next sip-damage fires. */
    private final Map<UUID, Long>       nextDamageTime      = new HashMap<>();
    /** Epoch-ms timestamp for when next sunlight-curse damage fires. */
    private final Map<UUID, Long>       nextSunlightDmgTime = new HashMap<>();
    /** Players who died with 50+ sips â€” awaiting PlayerRespawnEvent. */
    private final Set<UUID>             pendingCurse        = new HashSet<>();

    private final Random random = new Random();

    // â”€â”€ Constructor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public SoulfurPotionListener(JavaPlugin plugin,
                                  ItemFactory itemFactory,
                                  PlayerDifficultyManager manager) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
        this.manager     = manager;
        startTasks();
    }

    // â”€â”€ Scheduled tasks â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void startTasks() {
        // Main task â€” every 20 ticks (1 second): darkness, damage, curse
        new BukkitRunnable() {
            @Override public void run() {
                tickSipEffects();
                tickCurseEffects();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        // Decay task â€” every 1200 ticks (60 seconds): decrement sip counter
        new BukkitRunnable() {
            @Override public void run() { tickDecay(); }
        }.runTaskTimer(plugin, 1200L, 1200L);
    }

    // â”€â”€ Consumption â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            case 1  -> "Â§5â˜  Â§7Your vision warps. The world tilts around you...";
            case 5  -> "Â§5â˜  Â§7The shadows grow deeper...";
            case 10 -> "Â§4â˜  Â§cYou can barely see.";
            case 15 -> "Â§4â˜  Â§4The world goes dark. Â§cYou are blind.";
            case 25 -> "Â§4â˜  Â§cYour body begins to fail. The poison damages you.";
            case 35 -> "Â§4â˜  Â§4The rot accelerates. Â§cDeath draws near.";
            case 50 -> "Â§4â˜  Â§4â˜  Â§4You have consumed too much. Death will mark you. Â§4â˜  Â§4â˜ ";
            default -> "Â§5â˜  Â§7The madness deepens... Â§8(" + sips + " sips)";
        };
        player.sendMessage(msg);
    }

    // â”€â”€ Main-task effects â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void tickSipEffects() {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID    uuid = player.getUniqueId();
            Integer sips = sipCount.get(uuid);
            if (sips == null || sips <= 0 || player.isDead()) continue;

            // â”€â”€ Darkness (refreshed every second, 2-second duration) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (sips >= 15) {
                // Pitch black â€” BLINDNESS
                player.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false), true);
            } else if (sips >= 10) {
                applyDarkness(player, 2);
            } else if (sips >= 5) {
                applyDarkness(player, 1);
            } else {
                applyDarkness(player, 0);
            }

            // â”€â”€ Damage â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            if (sips >= 50) {
                // 3 hearts per second â€” lethal
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

    // â”€â”€ Decay task â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void tickDecay() {
        sipCount.entrySet().removeIf(entry -> {
            UUID uuid   = entry.getKey();
            int newSips = entry.getValue() - 1;

            if (newSips <= 0) {
                // Counter hit zero â€” clear all effects
                Player p = plugin.getServer().getPlayer(uuid);
                if (p != null) {
                    p.removePotionEffect(PotionEffectType.BLINDNESS);
                    p.removePotionEffect(PotionEffectType.DARKNESS);
                    cancelSway(uuid);
                    p.sendMessage("Â§7â˜ Â§7The fog of the Soulfur slowly lifts...");
                }
                nextDamageTime.remove(uuid);
                return true; // remove from sipCount map
            }

            entry.setValue(newSips);
            return false;
        });
    }

    // â”€â”€ Curse tick â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void tickCurseEffects() {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (!manager.isCursed(uuid) || player.isDead()) continue;

            // Keep SLOWNESS active (refresh every second, duration 2s)
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false, true), true);

            // Sunlight burn: Â½ heart per second
            if (isInSunlight(player)) {
                Long next = nextSunlightDmgTime.get(uuid);
                if (next == null || now >= next) {
                    player.damage(1.0);
                    nextSunlightDmgTime.put(uuid, now + 1_000L);
                }
            }
        }
    }

    // â”€â”€ 50-sip death â†’ respawn curse â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        // â”€â”€ 1HP Respawn Bug Fix â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // When a player respawns, immediately strip any inflated max-health modifiers
        // (like the Defence HP bonus) before the server recalculates their health.
        // This ensures they spawn with the standard full 20 HP instead of 1 HP out of 40.
        org.bukkit.attribute.AttributeInstance hpAttr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(20.0);
            player.setHealth(20.0);
        }

        if (!pendingCurse.remove(uuid)) return;

        manager.setCursed(uuid);

        // Apply curse effects 5 ticks after respawn (inventory not ready immediately)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.addPotionEffect(
                new PotionEffect(PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 1, false, false, true));
            player.sendMessage("Â§4â˜  Â§cYou are cursed by the Soulfur.");
            player.sendMessage("Â§8  Sunlight burns your skin. Your speed is halved.");
            player.sendMessage("Â§8  The curse expires in Â§f24 hoursÂ§8.");
        }, 5L);
    }

    // â”€â”€ Cleansing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!activeSways.containsKey(player.getUniqueId())) return;
        if (player.getLocation().getBlock().getType() == Material.WATER) {
            cleanse(player);
            player.sendMessage("Â§bâœ¦ Â§7The cool water washes the madness from your mind.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!activeSways.containsKey(player.getUniqueId())) return;
        cleanse(player);
        player.sendMessage("Â§eâœ¦ Â§7Rest soothes your troubled mind. The sway fades.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelSway(uuid);
        nextSunlightDmgTime.remove(uuid);
    }

    // â”€â”€ Drunken Sway â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
                int   magnitude = 5 + random.nextInt(11); // 5â€“15
                float offset    = random.nextBoolean() ? magnitude : -magnitude;
                player.setRotation(
                    player.getLocation().getYaw() + offset,
                    player.getLocation().getPitch());
            }
        }.runTaskTimer(plugin, 20L, 20L);

        activeSways.put(player.getUniqueId(), task);
    }

    /**
     * Full cleanse: removes NAUSEA, BLINDNESS, DARKNESS, cancels sway,
     * and â€” critically â€” CLEARS the sip counter so tickSipEffects() stops
     * re-applying blindness after the player goes in water or sleeps.
     */
    private void cleanse(Player player) {
        UUID uuid = player.getUniqueId();
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        sipCount.remove(uuid);
        nextDamageTime.remove(uuid);
        cancelBlindnessTask(uuid);
        cancelSway(uuid);
    }

    private void cancelSway(UUID uuid) {
        BukkitTask task = activeSways.remove(uuid);
        if (task != null) task.cancel();
    }

    private void cancelBlindnessTask(UUID uuid) {
        BukkitTask task = blindnessTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    // â”€â”€ Sunlight check â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private boolean isInSunlight(Player player) {
        long time = player.getWorld().getTime();
        boolean isDay = (time < 12000 || time > 23800);
        if (!isDay) return false;
        return player.getLocation().getBlock().getLightFromSky() >= 12;
    }
}
