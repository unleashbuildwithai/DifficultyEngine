package net.yourserver.coreengine.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.teleport.TeleportRequestManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /tphere <player>} - request that another player teleport to you.
 * Auto-accepts instantly when they have /tpauto on.
 */
public class TpHereCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpHereCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tphere.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /tphere <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cThat is you.");
            return true;
        }
        TeleportRequestManager mgr = plugin.getTeleportRequestManager();
        if (mgr.isInCombat(player.getUniqueId()) || mgr.isInCombat(target.getUniqueId())) {
            player.sendMessage("§cYou cannot teleport someone while you or they are in combat.");
            return true;
        }
        PlayerSettingsManager.PlayerSettings targetSettings =
                plugin.getPlayerSettingsManager().get(target.getUniqueId());
        if (targetSettings.tpAuto) {
            target.teleport(player);
            player.sendMessage("§aTeleported §e" + target.getName() + "§a to you (auto).");
            target.sendMessage("§e" + player.getName() + "§a teleported you to them.");
            return true;
        }
        mgr.request(player, target, TeleportRequestManager.RequestType.TPHERE);
        player.sendMessage("§aSending tphere request to §e" + target.getName() + "§a.");
        target.sendMessage("§e" + player.getName() + "§a wants to teleport you to them.");
        target.sendMessage(Component.text("   ▶ Yes ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .append(Component.text("▶ No").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/tpdeny")))
                .append(Component.text("   §7(chat Yes/No, or /tpaccept)").color(NamedTextColor.GRAY)));
        return true;
    }
}