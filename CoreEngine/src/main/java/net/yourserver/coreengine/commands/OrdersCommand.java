package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /orders} - open your active market sell/buy orders. */
public class OrdersCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public OrdersCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /orders.");
            return true;
        }
        if (plugin.getMarketGuiManager() == null) {
            player.sendMessage("§cThe market is not available yet.");
            return true;
        }
        plugin.getMarketGuiManager().openMySells(player);
        return true;
    }
}
