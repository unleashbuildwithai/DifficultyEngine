package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.database.dao.HomeDao.HomeEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** {@code /home [slot]} - open the homes GUI (no args) or teleport to a saved slot. */
public class HomeCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public HomeCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /home.");
            return true;
        }
        if (args.length == 0) {
            plugin.getSettingsGuiManager().openHomes(player);
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cUsage: /home <slot> | /home");
            return true;
        }
        Optional<HomeEntry> home = plugin.getHomeDao().getHome(player.getUniqueId(), slot);
        if (home.isEmpty()) {
            player.sendMessage("§cNo home saved in slot §e" + slot + "§c. Use /sethome " + slot + ".");
            return true;
        }
        HomeEntry entry = home.get();
        World world = Bukkit.getWorld(entry.worldName());
        if (world == null) {
            player.sendMessage("§cThat home's world is not loaded.");
            return true;
        }
        player.teleport(new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch()));
        player.sendMessage("§aTeleported to home slot §e" + slot + "§a.");
        return true;
    }
}
