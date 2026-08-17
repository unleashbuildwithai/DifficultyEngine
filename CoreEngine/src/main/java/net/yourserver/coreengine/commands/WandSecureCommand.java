package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.protection.RegionManager.Region;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /wandsecure [on|off]} - owner-only secure areas.
 * <ul>
 *   <li>{@code /wandsecure} (no args): create a secure region from the wand selection.</li>
 *   <li>{@code /wandsecure off}: unsecure the area you are standing in (else "no area found").</li>
 *   <li>{@code /wandsecure on}: re-secure the area you are standing in.</li>
 * </ul>
 * A secured area is unbreakable by EVERYONE (including OP/admins) until toggled off.
 */
public class WandSecureCommand implements CommandExecutor {

    /** xxfatalg0dz */
    private static final UUID OWNER = UUID.fromString("aa1690e5-a819-4028-a801-2fd3d482c533");

    private final CoreEngine plugin;

    public WandSecureCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /wandsecure.");
            return true;
        }
        if (!player.getUniqueId().equals(OWNER)) {
            player.sendMessage("§cYou are not allowed to use /wandsecure.");
            return true;
        }

        if (args.length == 0) {
            Region region = plugin.getRegionManager().createRegion(
                    "wand_" + System.currentTimeMillis(), player.getUniqueId(), true);
            if (region == null) {
                player.sendMessage("§cSelect both corners first with the wand (left + right click).");
                return true;
            }
            player.sendMessage("§aArea secured - blocks are now unbreakable by EVERYONE (including admins).");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                Region region = plugin.getRegionManager().getWandRegionAt(
                        player.getWorld().getName(),
                        player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                if (region == null) {
                    player.sendMessage("§cNo area found where you are standing.");
                    return true;
                }
                plugin.getRegionManager().setSecure(region.name(), true);
                player.sendMessage("§aArea §e" + region.name() + "§a is now secured again.");
            }
            case "off" -> {
                Region region = plugin.getRegionManager().getWandRegionAt(
                        player.getWorld().getName(),
                        player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
                if (region == null) {
                    player.sendMessage("§cNo area found where you are standing.");
                    return true;
                }
                plugin.getRegionManager().setSecure(region.name(), false);
                player.sendMessage("§cArea §e" + region.name() + "§c is no longer secured (blocks breakable again).");
            }
            default -> player.sendMessage("§cUsage: /wandsecure [on|off]");
        }
        return true;
    }
}