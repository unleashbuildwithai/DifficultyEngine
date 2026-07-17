package com.yourname.difficulty;

import com.yourname.difficulty.listeners.SitListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /sit [on|off] — Toggles right-click-to-sit on slabs and stairs.
 *
 * Usage:
 *   /sit        → toggles sit mode
 *   /sit on     → enables sit mode
 *   /sit off    → disables sit mode
 *
 * When ON, right-clicking a slab or stair mounts the player on an
 * invisible ArmorStand seat at the block's surface. Sneak to stand up.
 * Full blocks are not sittable — only half-block surfaces qualify.
 *
 * Permission: difficultyengine.use (default: true)
 */
public class SitCommand implements CommandExecutor {

    private final SitListener sitListener;

    public SitCommand(SitListener sitListener) {
        this.sitListener = sitListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        boolean enable;

        if (args.length >= 1) {
            enable = switch (args[0].toLowerCase()) {
                case "on"  -> true;
                case "off" -> false;
                default -> {
                    player.sendMessage("§cUsage: §f/sit [on|off]");
                    yield !sitListener.isSitEnabled(player.getUniqueId()); // no change
                }
            };
            // If the arg was invalid the above toggle is a no-op; send usage and exit
            if (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) {
                return true;
            }
        } else {
            // No args — toggle
            enable = !sitListener.isSitEnabled(player.getUniqueId());
        }

        sitListener.setSitEnabled(player.getUniqueId(), enable);

        if (enable) {
            player.sendMessage("§8[§6DifficultyEngine§8] §aSit mode §2ON " +
                    "§7— right-click any §fslab §7or §fstair §7to sit on the ledge.");
        } else {
            player.sendMessage("§8[§6DifficultyEngine§8] §cSit mode §4OFF§7.");
        }

        return true;
    }
}
