package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/** {@code /markoff} - clears the region selection and removes the selector stick. */
public class MarkOffCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public MarkOffCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /markoff.");
            return true;
        }
        plugin.getRegionManager().clearSelection(player.getUniqueId());
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer()
                            .has(PDCKeys.regionStick(), PersistentDataType.BYTE)) {
                player.getInventory().remove(item);
            }
        }
        player.sendMessage("§aRegion selection cleared.");
        return true;
    }
}
