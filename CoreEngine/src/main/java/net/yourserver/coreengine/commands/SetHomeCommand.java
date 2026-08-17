package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /sethome <slot>} - save a home at your current location. */
public class SetHomeCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public SetHomeCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /sethome.");
            return true;
        }
        if (args.length != 1 && args.length != 2) {
            player.sendMessage("§cUsage: /sethome <slot> [name]");
            return true;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[0].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cUsage: /sethome <slot>");
            return true;
        }
        if (slot < 1 || slot > 50) {
            player.sendMessage("§cSlot must be between 1 and 50.");
            return true;
        }
        int maxHomes = plugin.getRankManager().getRank(player.getUniqueId()).getMaxHomes();
        if (slot > maxHomes) {
            player.sendMessage("§cYour rank only allows up to §e" + maxHomes + "§c home slots. Upgrade your member rank for more.");
            return true;
        }
        Location loc = player.getLocation();
        String name = null;
        if (args.length == 2) {
            name = args[1].trim().replace('§', '\'').substring(0, Math.min(args[1].trim().length(), 24));
            if (name.isEmpty()) name = null;
        }
        if (name != null) {
            plugin.getHomeDao().setHome(player.getUniqueId(), slot, name, loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            player.sendMessage("§aSaved home slot §e" + slot + "§a as §e" + name + "§a.");
        } else {
            plugin.getHomeDao().setHome(player.getUniqueId(), slot, loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            player.sendMessage("§aSaved home slot §e" + slot + "§a.");
        }
        return true;
    }
}
