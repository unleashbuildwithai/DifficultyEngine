package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /tpauto} - toggles auto-accept of teleport requests. */
public class TpAutoCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpAutoCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tpauto.");
            return true;
        }
        boolean on = plugin.getPlayerSettingsManager().toggleTpAuto(player.getUniqueId());
        player.sendMessage(on
                ? "§aTeleport auto-accept §2ENABLED§a - players can teleport to you."
                : "§cTeleport auto-accept §4DISABLED§c - teleports now require a request.");
        return true;
    }
}
