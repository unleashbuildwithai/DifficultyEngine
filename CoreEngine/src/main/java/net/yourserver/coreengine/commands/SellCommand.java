package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.market.MarketResult;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /sell worth <price>} or {@code /sell <price>} - places the entire
 * stack held in the player's main hand up for sale at the given TOTAL price.
 */
public class SellCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public SellCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /sell.");
            return true;
        }
        // Supported syntaxes:
        //   /sell worth <price>
        //   /sell <price>
        double totalPrice;
        if (args.length >= 2 && args[0].equalsIgnoreCase("worth")) {
            totalPrice = parsePrice(args[1]);
        } else if (args.length == 1) {
            totalPrice = parsePrice(args[0]);
        } else {
            player.sendMessage("§cUsage: /sell worth <price> | /sell <price>");
            return true;
        }
        if (Double.isNaN(totalPrice)) {
            player.sendMessage("§cThat is not a valid price.");
            return true;
        }

        MarketManager market = plugin.getMarketManager();
        if (market == null) {
            player.sendMessage("§cThe market is not available yet.");
            return true;
        }
        MarketResult result = market.placeSellListing(player, totalPrice);
        switch (result) {
            case SUCCESS -> {
                // placeSellListing already reports the outcome (instant sale vs listed).
            }
            case EMPTY_HAND -> player.sendMessage("§cYou must hold the item you want to sell in your main hand.");
            case CAP_REACHED -> player.sendMessage("§cYou have reached your maximum number of active listings for your rank.");
            case INVENTORY_MISMATCH -> player.sendMessage("§cInventory changed mid-transaction - please try again.");
            default -> player.sendMessage("§cUnable to place that listing: " + result);
        }
        return true;
    }

    private double parsePrice(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
