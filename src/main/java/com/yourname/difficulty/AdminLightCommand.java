package com.yourname.difficulty;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AdminLightCommand — /adminlight
 *
 * Toggles permanent Night Vision for an admin so they can see in the dark
 * without placing torches. The effect is personal — only the admin sees it.
 * Uses a repeating task that re-applies Night Vision every 4 seconds so it
 * never expires while the toggle is active.
 *
 * Usage:
 *   /adminlight        — toggle on/off
 *   /adminlight on     — force enable
 *   /adminlight off    — force disable
 *
 * Permission: difficultyengine.adminlight (default: op)
 */
public class AdminLightCommand implements CommandExecutor {

    private final JavaPlugin              plugin;
    /** Active refresh tasks per player UUID. */
    private final Map<UUID, BukkitTask>  activeTasks = new HashMap<>();

    // Night Vision duration in ticks — 5 seconds refresh, 6s effect = always active
    private static final int EFFECT_DURATION = 120; // 6 seconds (in ticks)
    private static final int REFRESH_PERIOD  = 80;  // refresh every 4 seconds

    public AdminLightCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /adminlight.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        // Parse explicit on/off argument
        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "on"  -> { enable(player, uuid);  return true; }
                case "off" -> { disable(player, uuid); return true; }
                default    -> { sender.sendMessage("§cUsage: /adminlight [on|off]"); return true; }
            }
        }

        // Toggle
        if (activeTasks.containsKey(uuid)) {
            disable(player, uuid);
        } else {
            enable(player, uuid);
        }
        return true;
    }

    // ── Enable / disable ──────────────────────────────────────────────────────

    private void enable(Player player, UUID uuid) {
        if (activeTasks.containsKey(uuid)) {
            player.sendMessage("§e☀ §7Admin light is already §aON§7.");
            return;
        }

        // Apply immediately
        applyNightVision(player);

        // Refresh every REFRESH_PERIOD ticks
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (!player.isOnline()) {
                    activeTasks.remove(uuid);
                    cancel();
                    return;
                }
                applyNightVision(player);
            }
        }.runTaskTimer(plugin, REFRESH_PERIOD, REFRESH_PERIOD);

        activeTasks.put(uuid, task);
        player.sendMessage("§e☀ §aAdmin light §7enabled. §8(Night Vision active — only you see this)");
    }

    private void disable(Player player, UUID uuid) {
        BukkitTask task = activeTasks.remove(uuid);
        if (task == null) {
            player.sendMessage("§e☀ §7Admin light is already §cOFF§7.");
            return;
        }
        task.cancel();
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.sendMessage("§e☀ §cAdmin light §7disabled.");
    }

    private void applyNightVision(Player player) {
        player.addPotionEffect(
            new PotionEffect(PotionEffectType.NIGHT_VISION,
                             EFFECT_DURATION, 0,
                             false,  // ambient (no particles)
                             false,  // particles
                             false), // icon
            true);
    }

    // ── Cleanup on plugin disable ─────────────────────────────────────────────

    public void disableAll() {
        for (Map.Entry<UUID, BukkitTask> entry : activeTasks.entrySet()) {
            entry.getValue().cancel();
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p != null) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        activeTasks.clear();
    }

    /** Returns true if admin light is currently active for this player. */
    public boolean isActive(UUID uuid) {
        return activeTasks.containsKey(uuid);
    }
}
