package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.protection.RegionManager.Region;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /region create|delete|list [name]} - manage protected regions. */
public class RegionCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public RegionCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /region <create|delete|list> [name]");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> create(sender, args);
            case "delete" -> delete(sender, args);
            case "list" -> list(sender);
            default -> sender.sendMessage("§cUsage: /region <create|delete|list> [name]");
        }
        return true;
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can create regions.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /region create <name>");
            return;
        }
        Region region = plugin.getRegionManager().createRegion(args[1], player.getUniqueId(), false);
        if (region == null) {
            player.sendMessage("§cSelect both corners first with /markon (left + right click).");
            return;
        }
        player.sendMessage("§aRegion §e" + region.name() + "§a created - blocks are now protected and monsters disabled inside it.");
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /region delete <name>");
            return;
        }
        boolean deleted = plugin.getRegionManager().deleteRegion(args[1]);
        sender.sendMessage(deleted ? "§aRegion §e" + args[1] + "§a deleted." : "§cRegion not found.");
    }

    private void list(CommandSender sender) {
        var regions = plugin.getRegionManager().getRegions();
        if (regions.isEmpty()) {
            sender.sendMessage("§7No regions defined.");
            return;
        }
        sender.sendMessage("§aRegions:");
        for (Region r : regions) {
            sender.sendMessage("§7 - §e" + r.name() + " §7(" + r.world() + ")");
        }
    }
}
