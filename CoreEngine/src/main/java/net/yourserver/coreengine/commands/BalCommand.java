package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.util.MoneyFormat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /bal} - shows the player's balance (Vault/EssentialsX or internal). */
public class BalCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public BalCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /bal.");
            return true;
        }
        double balance = plugin.getEconomyManager().getBalance(player.getUniqueId());
        player.sendMessage("§6Balance: §a" + MoneyFormat.formatWithSymbol(balance));
        return true;
    }
}
