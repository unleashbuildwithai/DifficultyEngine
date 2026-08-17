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

/** {@code /tp <player>} - request to teleport to another player (request-based). */
public class TpCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public TpCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /tp.");
            return true;
        }
        if (args.length != 1) {
            player.sendMessage("§cUsage: /tp <player>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cPlayer §e" + args[0] + "§c not found.");
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage("§cYou are already there.");
            return true;
        }
        if (!plugin.getTeleportManager().canTeleportTo(target.getUniqueId(), player.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " has teleports disabled for you.");
            return true;
        }
        TeleportRequestManager mgr = plugin.getTeleportRequestManager();
        if (mgr.isInCombat(player.getUniqueId()) || mgr.isInCombat(target.getUniqueId())) {
            player.sendMessage("§cYou cannot teleport while you or the target are in combat.");
            return true;
        }
        PlayerSettingsManager.PlayerSettings targetSettings =
                plugin.getPlayerSettingsManager().get(target.getUniqueId());
        if (targetSettings.tpAuto) {
            player.teleport(target);
            player.sendMessage("§aTeleported to §e" + target.getName() + "§a (auto).");
            target.sendMessage("§e" + player.getName() + "§a teleported to you.");
            return true;
        }
        mgr.request(player, target, TeleportRequestManager.RequestType.TP);
        player.sendMessage("§aSending teleport request to §e" + target.getName() + "§a.");
        target.sendMessage("§e" + player.getName() + "§a wants to teleport to you.");
        target.sendMessage(Component.text("   ▶ Yes ").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .append(Component.text("▶ No").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/tpdeny")))
                .append(Component.text("   §7(chat Yes/No, or /tpaccept)").color(NamedTextColor.GRAY)));
        return true;
    }
}