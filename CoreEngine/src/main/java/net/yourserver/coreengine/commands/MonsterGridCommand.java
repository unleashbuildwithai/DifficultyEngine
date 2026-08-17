package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /monstergrid} — admin controls for the Market safe-zone anchor.
 *
 * <pre>
 *  /monstergrid            → status
 *  /monstergrid on|off     → enable / disable the monster-exclusion grid
 *  /monstergrid radius &lt;n&gt; → set the exclusion radius around the Market NPC
 *  /monstergrid reload     → re-read config.yml
 * </pre>
 */
public class MonsterGridCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public MonsterGridCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("coreengine.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6🛡 §7MonsterGrid — Market safe-zone anchor");
            sender.sendMessage("§7  Enabled: §e" + plugin.getConfigManager().isMonsterGridEnabled());
            sender.sendMessage("§7  Radius:  §e" + plugin.getConfigManager().getMonsterGridRadius() + " §7blocks");
            sender.sendMessage("§7  Anchor:  §7Market NPC §8(§7"
                    + plugin.getConfigManager().getMarketNpcLocation().getWorld().getName() + "§8)");
            sender.sendMessage("§7Usage: §e/monstergrid <on|off|radius <n>|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                plugin.getConfigManager().setMonsterGridEnabled(true);
                sender.sendMessage("§a✓ §7MonsterGrid §aenabled§7 — monsters can no longer spawn near the Market.");
            }
            case "off" -> {
                plugin.getConfigManager().setMonsterGridEnabled(false);
                sender.sendMessage("§c✗ §7MonsterGrid §cdisabled§7 — natural monsters may spawn near the Market.");
            }
            case "radius" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c✗ §7Usage: §e/monstergrid radius <blocks>");
                    return true;
                }
                try {
                    int r = Integer.parseInt(args[1]);
                    if (r < 1 || r > 500) {
                        sender.sendMessage("§c✗ §7Radius must be §e1-500 §7blocks.");
                        return true;
                    }
                    plugin.getConfigManager().setMonsterGridRadius(r);
                    sender.sendMessage("§a✓ §7MonsterGrid radius set to §e" + r + " §7blocks.");
                } catch (NumberFormatException ex) {
                    sender.sendMessage("§c✗ §7'" + args[1] + "' is not a number.");
                }
            }
            case "reload" -> {
                plugin.getConfigManager().reload();
                sender.sendMessage("§a✓ §7Config reloaded.");
            }
            default -> sender.sendMessage("§c✗ §7Unknown §e" + args[0]
                    + " §7(§eon§7|§eoff§7|§eradius <n>§7|§ereload§7)");
        }
        return true;
    }
}