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

/** {@code /markon} - gives the region-selection stick and clears the selection. */
public class MarkOnCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public MarkOnCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /markon.");
            return true;
        }
        plugin.getRegionManager().clearSelection(player.getUniqueId());
        ItemStack stick = new ItemStack(Material.STICK);
        var meta = stick.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§aRegion Selector"));
        meta.lore(java.util.List.of(
                net.kyori.adventure.text.Component.text("§7Left-click: set corner 1"),
                net.kyori.adventure.text.Component.text("§7Right-click: set corner 2")));
        meta.getPersistentDataContainer().set(PDCKeys.regionStick(), PersistentDataType.BYTE, (byte) 1);
        stick.setItemMeta(meta);
        player.getInventory().addItem(stick);
        player.sendMessage("§aRegion selector given. Left-click = corner 1, right-click = corner 2, then /region create <name>.");
        return true;
    }
}
