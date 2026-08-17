package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.gui.MarketGUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /market} - opens the 2-option prompt (Yes = teleport to the
 * configured Market NPC location, No = open the main Market GUI), per the
 * Module 1 spec.
 */
public class MarketCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public MarketCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /market.");
            return true;
        }
        MarketGUIManager gui = plugin.getMarketGuiManager();
        if (gui == null) {
            player.sendMessage("§cThe market is not available yet.");
            return true;
        }
        // 2-option prompt: Yes (teleport) / No (open main GUI).
        gui.openConfirmTeleport(player);
        return true;
    }
}
