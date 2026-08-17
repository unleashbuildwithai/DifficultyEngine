package net.yourserver.coreengine.gui;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.market.MarketResult;
import net.yourserver.coreengine.util.MoneyFormat;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.World;
import org.bukkit.Bukkit;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.database.dao.HomeDao.HomeEntry;
import java.util.Optional;

public class GUIListener implements Listener {

    private final CoreEngine plugin;

    public GUIListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CustomGUIHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        MarketSession session = holder.getSession();
        if (session != null && session.getView() == MarketSession.View.QUICK_SELL_FLOOR
                && event.getRawSlot() == 22) {
            // Allow the quick-sell input slot to accept items (and let players
            // move them back out to cancel); selling happens via the Sell button
            // or auto-sell on close.
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        switch (holder.getGuiType()) {
            case "MARKET_MAIN" -> handleMarketClick(player, session, event);
            case "CREATE_A_VILLE_SETTINGS" -> handleSettingsClick(player, event);
            case "SPAWNER_MENU" -> handleSpawnerClick(player, event.getRawSlot());
            case "HOMES_MENU" -> handleHomesClick(player, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CustomGUIHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CustomGUIHolder holder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        MarketSession session = holder.getSession();
        if (session == null || session.getView() != MarketSession.View.QUICK_SELL_FLOOR) return;
        ItemStack offered = event.getInventory().getItem(22);
        if (offered == null || offered.getType() == Material.AIR || offered.getAmount() <= 0) return;
        MarketManager market = plugin.getMarketManager();
        if (market == null) return;
        MarketResult r = market.quickSellFromGui(player, offered);
        if (r == MarketResult.SUCCESS) {
            double unit = plugin.getConfigManager().getBuybackPrice(offered.getType().name());
            player.sendMessage("§aAuto-sold §2" + offered.getAmount() + "x §e" + offered.getType().name()
                    + " §afor §2$" + MoneyFormat.format(offered.getAmount() * unit) + "§a.");
        } else {
            var leftover = player.getInventory().addItem(offered);
            leftover.values().forEach(dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped));
        }
    }

    private void handleMarketClick(Player player, MarketSession session, InventoryClickEvent event) {
        MarketGUIManager gui = plugin.getMarketGuiManager();
        MarketManager market = plugin.getMarketManager();
        if (gui == null || market == null || session == null) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        String action = null; Long orderId = null; Long escrowId = null;
        if (clicked != null && clicked.hasItemMeta()) {
            var pdc = clicked.getItemMeta().getPersistentDataContainer();
            action = pdc.get(PDCKeys.guiAction(), PersistentDataType.STRING);
            orderId = pdc.get(PDCKeys.orderId(), PersistentDataType.LONG);
            escrowId = pdc.get(PDCKeys.escrowId(), PersistentDataType.LONG);
        }

        // Clicking an EMPTY grid slot while on the Sell Listings tab opens the
        // sell-create dialog ("click empty slot -> sell menu").
        if (action == null && session.getView() == MarketSession.View.MAIN_GRID
                && session.getBrowseTab() == MarketSession.BrowseTab.SELL_LISTINGS
                && slot >= 18 && slot <= 44) {
            gui.openSellCreate(player);
            return;
        }

        if (action != null) {
            switch (action) {
                case MarketGUIManager.ACTION_SEARCH -> {
                    session.setSearchMode(true);
                    player.sendMessage("§eMarket search: type an item name (or 'clear' to reset).");
                    player.closeInventory();
                    return;
                }
                case MarketGUIManager.ACTION_SELL_LISTINGS -> {
                    session.setBrowseTab(MarketSession.BrowseTab.SELL_LISTINGS); gui.openMain(player); return;
                }
                case MarketGUIManager.ACTION_BUY_ORDERS -> {
                    session.setBrowseTab(MarketSession.BrowseTab.BUY_ORDERS); gui.openMain(player); return;
                }
                case MarketGUIManager.ACTION_QUICK_SELL -> {
                    if (session.getView() == MarketSession.View.QUICK_SELL_FLOOR) {
                        confirmQuickSell(player, gui, market, event);
                    } else { gui.openQuickSell(player); }
                    return;
                }
                case MarketGUIManager.ACTION_SELL_CONFIRM -> {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand == null || hand.getType() == Material.AIR) {
                        player.sendMessage("§cNothing in your main hand to sell.");
                        gui.openMain(player);
                        return;
                    }
                    session.setAwaitingSellPrice(true);
                    session.setView(MarketSession.View.MAIN_GRID);
                    player.closeInventory();
                    player.sendMessage("§6┌ §eSell Listing §6┐");
                    player.sendMessage("§7Item: §f" + hand.getType().name() + " §7x§f" + hand.getAmount());
                    player.sendMessage("§7Type the §eTOTAL price§7 in chat §8(§7e.g. §e2500§8)§7 — or §ccancel§7.");
                    return;
                }
                case MarketGUIManager.ACTION_SELL_CANCEL -> {
                    session.setAwaitingSellPrice(false);
                    gui.openMain(player);
                    return;
                }
                case MarketGUIManager.ACTION_TELEPORT_NPC -> { teleportToMarketNpc(player); return; }
                case MarketGUIManager.ACTION_MY_SELLS -> { gui.openMySells(player); return; }
                case MarketGUIManager.ACTION_MY_BUYS -> { gui.openMyBuys(player); return; }
                case MarketGUIManager.ACTION_HISTORY -> { gui.openHistory(player); return; }
                case MarketGUIManager.ACTION_BUY_HISTORY -> {
                    session.setBrowseTab(MarketSession.BrowseTab.BUY_HISTORY); gui.openHistory(player); return;
                }
                case MarketGUIManager.ACTION_SELL_HISTORY -> {
                    session.setBrowseTab(MarketSession.BrowseTab.SELL_HISTORY); gui.openHistory(player); return;
                }
                case MarketGUIManager.ACTION_PREV_PAGE -> {
                    session.setPage(session.getPage() - 1); gui.refresh(player); return;
                }
                case MarketGUIManager.ACTION_NEXT_PAGE -> {
                    session.setPage(session.getPage() + 1); gui.refresh(player); return;
                }
                case MarketGUIManager.ACTION_CONFIRM_YES -> { teleportToMarketNpc(player); return; }
                case MarketGUIManager.ACTION_CONFIRM_NO -> { gui.openMain(player); return; }
                case MarketGUIManager.ACTION_CONFIRM_BUY_YES -> {
                    Long pending = session.getPendingBuyOrderId();
                    session.setPendingBuyOrderId(null);
                    if (pending != null) buyNow(player, gui, market, pending);
                    else gui.openMain(player);
                    return;
                }
                case MarketGUIManager.ACTION_CONFIRM_BUY_NO -> {
                    session.setPendingBuyOrderId(null);
                    gui.openMain(player);
                    return;
                }
                case MarketGUIManager.ACTION_CANCEL_ORDER -> {
                    if (orderId != null) {
                        player.sendMessage(feedback("Cancel", market.cancelOrder(player, orderId)));
                        gui.refresh(player);
                    }
                    return;
                }
                case MarketGUIManager.ACTION_CLAIM -> {
                    if (escrowId != null) {
                        MarketResult r = market.claimEscrowItem(player, escrowId);
                        if (r == MarketResult.SUCCESS) player.sendMessage("§aItem claimed!");
                        else if (r == MarketResult.INVENTORY_FULL) player.sendMessage("§cMake room in your inventory first.");
                        else player.sendMessage("§cThat item can no longer be claimed.");
                        gui.refresh(player);
                    }
                    return;
                }
                default -> {}
            }
        }

        if (orderId != null && session.getView() == MarketSession.View.MAIN_GRID) {
            if (session.getBrowseTab() == MarketSession.BrowseTab.SELL_LISTINGS) {
                if (plugin.getPlayerSettingsManager().get(player.getUniqueId()).quickBuy) {
                    buyNow(player, gui, market, orderId);
                } else {
                    gui.openConfirmBuy(player, orderId);
                }
            } else {
                MarketResult r = market.fulfillBuyOrder(player, orderId);
                if (r == MarketResult.SUCCESS) player.sendMessage("§aBuy order fulfilled!");
                else if (r == MarketResult.INVENTORY_MISMATCH) player.sendMessage("§cYou don't have enough of that item.");
                else if (r == MarketResult.OWN_ORDER) player.sendMessage("§cCannot fulfill own order.");
                else player.sendMessage("§cBuy order no longer active.");
                gui.refresh(player);
            }
        }
    }

    private void confirmQuickSell(Player player, MarketGUIManager gui, MarketManager market,
                                  InventoryClickEvent event) {
        Inventory inv = event.getView().getTopInventory();
        ItemStack offered = inv.getItem(22);
        if (offered == null || offered.getType() == Material.AIR || offered.getAmount() <= 0) {
            player.sendMessage("§cPlace an item in the input slot first.");
            return;
        }
        MarketResult r = market.quickSellFromGui(player, offered);
        if (r == MarketResult.SUCCESS) {
            double unit = plugin.getConfigManager().getBuybackPrice(offered.getType().name());
            player.sendMessage("§aQuick-sold §2" + offered.getAmount() + "x §e"
                    + offered.getType().name() + " §afor §2$" + MoneyFormat.format(offered.getAmount() * unit) + "§a.");
            inv.setItem(22, null);
            gui.refresh(player);
        } else if (r == MarketResult.NOT_ACCEPTED) {
            player.sendMessage("§cThe server does not buy that item.");
        } else {
            player.sendMessage("§cUnable to quick-sell: " + r);
        }
    }

    private void buyNow(Player player, MarketGUIManager gui, MarketManager market, long orderId) {
        MarketResult r = market.buyFromSellListing(player, orderId);
        if (r == MarketResult.SUCCESS) player.sendMessage("§aPurchase complete!");
        else if (r == MarketResult.INSUFFICIENT_FUNDS) player.sendMessage("§cNot enough money.");
        else if (r == MarketResult.OWN_ORDER) player.sendMessage("§cCannot buy your own listing.");
        else if (r == MarketResult.INVENTORY_FULL) player.sendMessage("§cInventory full - in your claim inbox.");
        else player.sendMessage("§cThat listing is no longer available.");
        gui.refresh(player);
    }

    private void teleportToMarketNpc(Player player) {
        Location loc = plugin.getConfigManager().getMarketNpcLocation();
        if (loc.getWorld() == null) {
            player.sendMessage("§cMarket NPC world not configured.");
            return;
        }
        player.closeInventory();
        player.teleport(loc);
        player.sendMessage("§aTeleported to the Market NPC!");
    }

    private String feedback(String prefix, MarketResult r) {
        return switch (r) {
            case SUCCESS -> "§a" + prefix + " successful.";
            case NOT_ACTIVE -> "§cThat entry is no longer active.";
            case NOT_FOUND -> "§cEntry not found.";
            default -> "§c" + prefix + " failed: " + r;
        };
    }

    private void handleSettingsClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.guiAction(), PersistentDataType.STRING);
        if (action == null) return;
        SettingsGUIManager gui = plugin.getSettingsGuiManager();
        PlayerSettingsManager settings = plugin.getPlayerSettingsManager();
        switch (action) {
            case SettingsGUIManager.ACTION_HOMES -> gui.openHomes(player);
            case SettingsGUIManager.ACTION_MARKET -> plugin.getMarketGuiManager().openMain(player);
            case SettingsGUIManager.ACTION_TELEPORT -> {
                player.closeInventory();
                player.sendMessage("§eUse /tp <player> to teleport to someone.");
            }
            case SettingsGUIManager.ACTION_PAY -> {
                player.closeInventory();
                player.sendMessage("§eUse /pay <player> <amount> to pay someone.");
            }
            case SettingsGUIManager.ACTION_BALANCE -> {
                player.closeInventory();
                player.sendMessage("§6Balance: §a" + MoneyFormat.formatWithSymbol(
                        plugin.getEconomyManager().getBalance(player.getUniqueId())));
            }
            case SettingsGUIManager.ACTION_PRIVACY -> {
                boolean on = settings.toggleGhost(player.getUniqueId());
                applyGhost(player, on);
                player.sendMessage(on ? "§aGhost mode ON." : "§cGhost mode OFF.");
                gui.openHub(player);
            }
            case SettingsGUIManager.ACTION_NIGHTVISION -> {
                boolean on = settings.toggleNightvision(player.getUniqueId());
                applyNightvision(player, on);
                player.sendMessage(on ? "§aNightvision ON." : "§cNightvision OFF.");
                gui.openHub(player);
            }
            case SettingsGUIManager.ACTION_REMOVE_MONSTERS -> {
                boolean on = settings.toggleRemoveMonsters(player.getUniqueId());
                player.sendMessage(on ? "§aRemove Monsters ON." : "§cRemove Monsters OFF.");
                gui.openHub(player);
            }
            case SettingsGUIManager.ACTION_HUD_STATS -> {
                boolean on = settings.toggleHudStats(player.getUniqueId());
                player.sendMessage(on ? "§aHUD Stats ON." : "§cHUD Stats OFF.");
                gui.openHub(player);
            }
            case SettingsGUIManager.ACTION_TP_PRIVACY -> {
                PlayerSettingsManager.TpPrivacy p = settings.cycleTpPrivacy(player.getUniqueId());
                player.sendMessage("§aTP privacy set to: §e" + p.name());
                gui.openHub(player);
            }
        }
    }

    private void handleSpawnerClick(Player player, int slot) {}

    private void handleHomesClick(Player player, InventoryClickEvent event) {
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.guiAction(), PersistentDataType.STRING);
        if (action == null) return;
        Integer slot = clicked.getItemMeta().getPersistentDataContainer()
                .get(PDCKeys.homeSlot(), PersistentDataType.INTEGER);

        switch (action) {
            case SettingsGUIManager.ACTION_HOME_BACK -> plugin.getSettingsGuiManager().openHub(player);
            case SettingsGUIManager.ACTION_HOME_LOCKED ->
                    player.sendMessage("§cThat home slot is locked - requires a higher member rank.");
            case SettingsGUIManager.ACTION_HOME_SAVE -> {
                if (slot == null) return;
                Location loc = player.getLocation();
                plugin.getHomeDao().setHome(player.getUniqueId(), slot, loc.getWorld().getName(),
                        loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                player.sendMessage("§aSaved Home §e" + slot + "§a at your location.");
                plugin.getSettingsGuiManager().openHomes(player);
            }
            case SettingsGUIManager.ACTION_HOME_OPTIONS -> {
                if (slot == null) return;
                plugin.getSettingsGuiManager().openHomeOptions(player, slot);
            }
            case SettingsGUIManager.ACTION_HOME_TELEPORT -> {
                if (slot == null) return;
                teleportToHome(player, slot);
            }
            case SettingsGUIManager.ACTION_HOME_DELETE -> {
                if (slot == null) return;
                boolean deleted = plugin.getHomeDao().deleteHome(player.getUniqueId(), slot);
                player.sendMessage(deleted ? "§aDeleted Home §e" + slot + "§a." : "§cNo home in that slot.");
                plugin.getSettingsGuiManager().openHomes(player);
            }
            case SettingsGUIManager.ACTION_HOME_RENAME -> {
                if (slot == null) return;
                plugin.requestHomeRename(player.getUniqueId(), slot);
                player.closeInventory();
                player.sendMessage("§d✎ §7Type the §eNEW NAME §7for §eHome " + slot + " §7in chat §8(§7or 'cancel'§8).");
                player.sendMessage("§7Name preview: §a" + player.getName() + "'s " + plugin.getHomeDao().getHome(player.getUniqueId(), slot)
                        .map(h -> h.name() == null || h.name().isEmpty() ? "Home " + slot : h.name()).orElse("Home " + slot));
            }
        }
    }

    private void teleportToHome(Player player, int slot) {
        Optional<HomeEntry> home = plugin.getHomeDao().getHome(player.getUniqueId(), slot);
        if (home.isEmpty()) {
            player.sendMessage("§cNo home in that slot.");
            return;
        }
        HomeEntry entry = home.get();
        World world = Bukkit.getWorld(entry.worldName());
        if (world == null) {
            player.sendMessage("§cThat home's world is not loaded.");
            return;
        }
        player.closeInventory();
        player.teleport(new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch()));
        player.sendMessage("§aTeleported to home §e" + slot + "§a.");
    }

    private void applyGhost(Player player, boolean on) {
        if (on) {
            player.setInvisible(true);
            for (Player other : Bukkit.getOnlinePlayers()) other.hidePlayer(plugin, player);
        } else {
            player.setInvisible(false);
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin, player);
        }
    }

    private void applyNightvision(Player player, boolean on) {
        if (on) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
        } else {
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
}
