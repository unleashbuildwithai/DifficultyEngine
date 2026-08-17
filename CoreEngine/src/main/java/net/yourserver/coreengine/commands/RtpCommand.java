package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/** {@code /rtp} - teleport to a random location, away from spawn. */
public class RtpCommand implements CommandExecutor {

    private static final int MIN_DISTANCE = 500;
    private static final int MAX_DISTANCE = 5000;
    private static final int MAX_ATTEMPTS = 25;

    private final CoreEngine plugin;
    private final Random random = new Random();

    public RtpCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /rtp.");
            return true;
        }

        World world = player.getWorld();
        Location spawn = world.getSpawnLocation();
        player.sendMessage("§7Finding a safe location...");

        // Generate/load chunks and resolve heights OFF the main thread. Calling
        // getHighestBlockYAt() synchronously on the main thread blocks the server
        // (and trips the Paper Watchdog) whenever the target chunk is ungenerated.
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double dist = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
                int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * dist);
                int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * dist);

                try {
                    // Paper async chunk API: loads/generates the chunk without
                    // blocking the main thread, then lets us read its height map.
                    org.bukkit.Chunk chunk = world.getChunkAtAsync(x >> 4, z >> 4).join();
                    int y = world.getHighestBlockYAt(x, z);
                    Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);

                    if (isSafe(loc)) {
                        teleportOnMainThread(player, loc, x, y, z);
                        return;
                    }
                } catch (Exception ignored) {
                    // Try the next random candidate.
                }
            }

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cCouldn't find a safe random location - try again."));
        });

        return true;
    }

    /** Teleportation must happen on the main thread. */
    private void teleportOnMainThread(Player player, Location loc, int x, int y, int z) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.teleport(loc);
            player.sendMessage("§aTeleported to a random location (§2" + x + ", " + y + ", " + z + "§a).");
        });
    }

    private boolean isSafe(Location loc) {
        Material m = loc.getBlock().getType();
        return m == Material.AIR || m == Material.CAVE_AIR || m == Material.VOID_AIR;
    }
}
