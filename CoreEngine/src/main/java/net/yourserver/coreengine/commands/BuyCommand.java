package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.market.MarketResult;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /buy <item_type> <amount> <price_per_item>} - creates a player BUY
 * order, instantly deducting {@code amount * price_per_item} from the
 * player's balance into market escrow.
 */
public class BuyCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public BuyCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /buy.");
            return true;
        }
        if (args.length != 3) {
            player.sendMessage("§cUsage: /buy <item_type> <amount> <price_per_item>");
            player.sendMessage("§7Example: /buy diamond 64 100");
            return true;
        }

        // 1. Item type - accept "diamond", "minecraft:diamond", "DIAMOND", etc.
        Material material = Material.matchMaterial(args[0]);
        if (material == null || material == Material.AIR) {
            player.sendMessage("§cUnknown item type: §e" + args[0]);
            return true;
        }

        // 2. Amount.
        int amount;
        try {
            amount = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount: §e" + args[1]);
            return true;
        }
        if (amount <= 0 || amount > 64 * 9) {
            player.sendMessage("§cAmount must be between 1 and 576.");
            return true;
        }

        // 3. Price per item.
        double pricePerItem;
        try {
            pricePerItem = Double.parseDouble(args[2].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid price: §e" + args[2]);
            return true;
        }
        if (pricePerItem <= 0) {
            player.sendMessage("§cPrice per item must be greater than 0.");
            return true;
        }

        MarketManager market = plugin.getMarketManager();
        if (market == null) {
            player.sendMessage("§cThe market is not available yet.");
            return true;
        }
        MarketResult result = market.placeBuyOrder(player, material, amount, pricePerItem);
        switch (result) {
            case SUCCESS -> {
                // placeBuyOrder already reports the outcome (instant fill vs placed).
            }
            case INSUFFICIENT_FUNDS -> player.sendMessage("§cYou don't have enough money for that buy order (need §2$"
                    + net.yourserver.coreengine.util.MoneyFormat.format(amount * pricePerItem) + "§c).");
            case CAP_REACHED -> player.sendMessage("§cYou have reached your maximum number of active listings for your rank.");
            default -> player.sendMessage("§cUnable to place that buy order: " + result);
        }
        return true;
    }
}
