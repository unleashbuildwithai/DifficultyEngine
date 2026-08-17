package net.yourserver.coreengine.commands;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

/** {@code /marketegg} - give yourself the Market NPC spawn egg (admin). */
public class GiveMarketNpcEggCommand implements CommandExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final CoreEngine plugin;

    public GiveMarketNpcEggCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /marketegg.");
            return true;
        }
        if (!player.hasPermission("coreengine.admin")) {
            player.sendMessage("§cYou do not have permission to use that command.");
            return true;
        }
        ItemStack egg = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        var meta = egg.getItemMeta();
        meta.displayName(LEGACY.deserialize("§6§lMarket NPC"));
        var lore = meta.getLore() == null ? new java.util.ArrayList<net.kyori.adventure.text.Component>() : meta.getLore();
        lore.add(LEGACY.deserialize("§7Right-click to place the Market NPC."));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.marketNpcEgg(), PersistentDataType.BYTE, (byte) 1);
        egg.setItemMeta(meta);
        player.getInventory().addItem(egg);
        player.sendMessage("§aYou received the Market NPC egg. Place it where you want the market.");
        return true;
    }
}
