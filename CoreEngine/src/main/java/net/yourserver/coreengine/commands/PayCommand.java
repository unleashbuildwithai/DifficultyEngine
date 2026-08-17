package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /pay <player> <amount>} - transfers money to another player. */
public class PayCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public PayCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /pay.");
            return true;
        }
        if (args.length != 2) {
            player.sendMessage("§cUsage: /pay <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cYou can't pay yourself.");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1].trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid amount.");
            return true;
        }
        if (amount <= 0) {
            player.sendMessage("§cAmount must be greater than 0.");
            return true;
        }
        boolean ok = plugin.getEconomyManager()
                .transferWithLocks(player.getUniqueId(), target.getUniqueId(), amount);
        if (ok) {
            player.sendMessage("§aPaid §2" + MoneyFormat.formatWithSymbol(amount) + "§a to §e" + target.getName() + "§a.");
            target.sendMessage("§aYou received §2" + MoneyFormat.formatWithSymbol(amount) + "§a from §e" + player.getName() + "§a.");
        } else {
            player.sendMessage("§cYou don't have enough money.");
        }
        return true;
    }
}
