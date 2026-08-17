package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.gui.MarketGUIManager;
import net.yourserver.coreengine.gui.MarketSession;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.market.MarketResult;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MarketChatListener implements Listener {

    private final CoreEngine plugin;

    public MarketChatListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Home-rename prompt.
        Integer renameSlot = plugin.consumeHomeRename(player.getUniqueId());
        if (renameSlot != null) {
            event.setCancelled(true);
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§cHome rename cancelled.");
            } else {
                String name = message.replace('§', '\'').trim();
                if (name.length() > 24) name = name.substring(0, 24);
                if (name.isEmpty()) {
                    player.sendMessage("§cThat is not a valid name.");
                } else {
                    boolean ok = plugin.getHomeDao().renameHome(player.getUniqueId(), renameSlot, name);
                    player.sendMessage(ok ? "§aHome renamed to §e" + name + "§a." : "§cNo home in that slot.");
                }
            }
            reopenHomes(player);
            return;
        }

        MarketGUIManager gui = plugin.getMarketGuiManager();
        if (gui == null) return;
        MarketSession session = gui.peekSession(player.getUniqueId());

        // Sell-listing price prompt.
        if (session != null && session.isAwaitingSellPrice()) {
            event.setCancelled(true);
            session.setAwaitingSellPrice(false);
            if (message.equalsIgnoreCase("cancel") || message.equalsIgnoreCase("clear")) {
                player.sendMessage("§cSell listing cancelled.");
                reopenMarket(gui, player);
                return;
            }
            double price;
            try {
                price = Double.parseDouble(message);
            } catch (NumberFormatException e) {
                price = Double.NaN;
            }
            if (Double.isNaN(price) || price < 0.01 || price > 100_000_000_000D) {
                session.setAwaitingSellPrice(true);
                player.sendMessage("§cInvalid price - type a number like §e2500§c or §ecancel§7.");
                return;
            }
            MarketManager market = plugin.getMarketManager();
            if (market == null) return;
            MarketResult result = market.placeSellListing(player, price);
            switch (result) {
                case SUCCESS -> player.sendMessage("§aListing placed!");
                case EMPTY_HAND -> player.sendMessage("§cYou must hold the item you want to sell in your main hand.");
                case CAP_REACHED -> player.sendMessage("§cYou have reached your maximum active listings for your rank.");
                case INVENTORY_MISMATCH -> player.sendMessage("§cInventory changed mid-transaction - try again.");
                default -> player.sendMessage("§cUnable to list that item: " + result);
            }
            reopenMarket(gui, player);
            return;
        }

        // Market search capture.
        if (session != null && session.isSearchMode()) {
            event.setCancelled(true);
            session.setSearchMode(false);
            if (message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("cancel")) {
                session.setSearchFilter(null);
                player.sendMessage("§aSearch filter cleared.");
            } else {
                session.setSearchFilter(message);
                player.sendMessage("§aSearching for: §e" + message);
            }
            reopenMarket(gui, player);
        }
    }

    private void reopenMarket(MarketGUIManager gui, Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> gui.openMain(player));
    }

    private void reopenHomes(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getSettingsGuiManager().openHomes(player));
    }
}
