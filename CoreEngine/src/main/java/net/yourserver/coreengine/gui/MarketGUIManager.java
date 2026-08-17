package net.yourserver.coreengine.gui;

import org.bukkit.Bukkit;

import net.yourserver.coreengine.config.ConfigManager;
import net.yourserver.coreengine.database.dao.MarketDao;
import net.yourserver.coreengine.database.dao.MarketDao.EscrowListing;
import net.yourserver.coreengine.database.dao.MarketDao.OrderListing;
import net.yourserver.coreengine.economy.EconomyManager;
import net.yourserver.coreengine.market.EscrowEntry;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.market.MarketOrder;
import net.yourserver.coreengine.market.OrderType;
import net.yourserver.coreengine.market.TransactionRecord;
import net.yourserver.coreengine.util.MoneyFormat;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds every Module 1 market inventory (all guiType "MARKET_MAIN") and
 * tracks per-player {@link MarketSession}s. Click routing itself lives in
 * {@code GUIListener} (Phase 7); this class provides the items + PDC tags
 * that let the listener interpret clicks, plus the public open() entry
 * points used by commands.
 */
public class MarketGUIManager {

    // PDC gui-action identifiers (see PDCKeys.guiAction()).
    public static final String ACTION_OPEN_MAIN = "open_main";
    public static final String ACTION_SELL_LISTINGS = "sell_listings";
    public static final String ACTION_BUY_ORDERS = "buy_orders";
    public static final String ACTION_QUICK_SELL = "quick_sell";
    public static final String ACTION_TELEPORT_NPC = "teleport_npc";
    public static final String ACTION_PREV_PAGE = "prev_page";
    public static final String ACTION_NEXT_PAGE = "next_page";
    public static final String ACTION_MY_SELLS = "my_sells";
    public static final String ACTION_MY_BUYS = "my_buys";
    public static final String ACTION_HISTORY = "history";
    public static final String ACTION_CONFIRM_YES = "confirm_yes";
    public static final String ACTION_CONFIRM_NO = "confirm_no";
    public static final String ACTION_CONFIRM_BUY_YES = "confirm_buy_yes";
    public static final String ACTION_CONFIRM_BUY_NO = "confirm_buy_no";
    public static final String ACTION_SELL_CONFIRM = "sell_confirm";
    public static final String ACTION_SELL_CANCEL = "sell_cancel";
    public static final String ACTION_SEARCH = "search";
    public static final String ACTION_CANCEL_ORDER = "cancel_order";
    public static final String ACTION_CLAIM = "claim";
    public static final String ACTION_BUY_HISTORY = "buy_history";
    public static final String ACTION_SELL_HISTORY = "sell_history";
    public static final String ACTION_REFRESH = "refresh";

    private static final int INVENTORY_SIZE = 54;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    private final MarketManager market;
    private final EconomyManager economy;
    private final ConfigManager config;
    private final Map<UUID, MarketSession> sessions = new ConcurrentHashMap<>();

    public MarketGUIManager(MarketManager market, EconomyManager economy, ConfigManager config) {
        this.market = market;
        this.economy = economy;
        this.config = config;
    }

    // ==================================================================
    // Public open() entry points
    // ==================================================================

    /** Opens the 2-option prompt (Yes = teleport, No = main GUI) for /market. */
    public void openConfirmTeleport(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.CONFIRM_TELEPORT);
        session.setAwaitingConfirm(true);
        open(player, session);
    }

    /** Opens the main 54-slot market grid (defaults to Sell Listings). */
    public void openMain(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.MAIN_GRID);
        if (session.getBrowseTab() == null) {
            session.setBrowseTab(MarketSession.BrowseTab.SELL_LISTINGS);
        }
        open(player, session);
    }

    /** Refreshes whatever view the player currently has open (e.g. after a purchase). */
    public void refresh(Player player) {
        MarketSession session = session(player);
        open(player, session);
    }

    /** Opens the quick-sell floor dialog (item-in, buyback confirm). */
    public void openQuickSell(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.QUICK_SELL_FLOOR);
        open(player, session);
    }

    /** Opens the player's active sell orders view. */
    public void openMySells(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.MY_SELLS);
        open(player, session);
    }

    /** Opens the player's active buy orders + claimable inbox view. */
    public void openMyBuys(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.MY_BUYS);
        open(player, session);
    }

    /** Opens the transaction history view (Buy / Sell sub-tabs). */
    public void openHistory(Player player) {
        MarketSession session = session(player);
        session.setView(MarketSession.View.HISTORY);
        open(player, session);
    }

    public MarketSession getSession(UUID uuid) {
        return session(uuid);
    }

    /** Returns the existing session for the player, or null if none yet. */
    public MarketSession peekSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    private MarketSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), MarketSession::new);
    }

    private MarketSession session(UUID uuid) {
        return sessions.computeIfAbsent(uuid, MarketSession::new);
    }

    // ==================================================================
    // Build dispatch
    // ==================================================================

    /** Builds and opens the correct inventory for the playersu2019s current session view. */
    private void open(Player player, MarketSession session) {
        Inventory inv = Bukkit.createInventory(
                new CustomGUIHolder("MARKET_MAIN", session),
                INVENTORY_SIZE,
                switch (session.getView()) {
                    case CONFIRM_TELEPORT  -> "§8[§aM§7] §fTeleport to Market?";
                    case CONFIRM_BUY       -> "§8[§aM§7] §fConfirm Purchase";
                    case QUICK_SELL_FLOOR  -> "§8[§aM§7] §fQuick-Sell to Server";

                    case SELL_CREATE      -> "§8[§aM§7] §fSell an Item";                    case MY_SELLS          -> "§8[§aM§7] §fMy Active Sells";
                    case MY_BUYS           -> "§8[§aM§7] §fMy Buy Orders";
                    case HISTORY           -> "§8[§aM§7] §fTransaction History";
                    default               -> "§8[§aM§7] §fMarket";
                });

        switch (session.getView()) {
            case CONFIRM_TELEPORT -> buildConfirmTeleport(inv, session);
            case CONFIRM_BUY      -> buildConfirmBuy(inv, session);

            case SELL_CREATE      -> buildSellCreate(inv, session);            case QUICK_SELL_FLOOR -> buildQuickSellFloor(inv, session);
            case MY_SELLS         -> buildMyOrders(inv, session, OrderType.SELL);
            case MY_BUYS          -> buildMyBuyOrders(inv, session);
            case HISTORY          -> buildHistory(inv, session);
            default              -> buildMainGrid(inv, session);
        }

        player.openInventory(inv);
    }

    // ==================================================================
    // Confirm Teleport (27-slot dialog with Yes / No)
    // ==================================================================

    /**
     * Sell-create dialog: shows the item in your main hand, with Accept / Cancel.
     * On accept the player types a total price in chat.
     */     public void openSellCreate(Player player) {         MarketSession session = sessions.computeIfAbsent(player.getUniqueId(), MarketSession::new);         session.setView(MarketSession.View.SELL_CREATE);         open(player, session);     }      private void buildSellCreate(Inventory inv, MarketSession session) {         ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);         var bm = border.getItemMeta();         bm.displayName(net.kyori.adventure.text.Component.text(""));         border.setItemMeta(bm);         for (int i = 0; i < 9; i++) {             inv.setItem(i, border);             inv.setItem(i + 18, border);         }         for (int i = 9; i < 18; i++) {             inv.setItem(i, border);         }          Player player = Bukkit.getPlayer(session.getPlayerUuid());         ItemStack hand = (player == null) ? new ItemStack(Material.AIR) : player.getInventory().getItemInMainHand();         if (hand == null || hand.getType() == Material.AIR) {             ItemStack none = new ItemStack(Material.BARRIER);             var nm = none.getItemMeta();             nm.displayName(net.kyori.adventure.text.Component.text("§cNothing in main hand"));             nm.lore(List.of(net.kyori.adventure.text.Component.text("§7Hold the item you want to list, then reopen.")));             none.setItemMeta(nm);             inv.setItem(13, none);         } else {             ItemStack preview = hand.clone();             preview.setAmount(Math.min(hand.getAmount(), 64));             var pm = preview.getItemMeta();             var l = new java.util.ArrayList<net.kyori.adventure.text.Component>();             l.add(net.kyori.adventure.text.Component.text("§8This will be listed"));             l.add(net.kyori.adventure.text.Component.text("§7Hold §f" + hand.getType().name() + " §7x§f" + hand.getAmount()));             pm.lore(l);             preview.setItemMeta(pm);             inv.setItem(13, preview);         }          ItemStack yes = new ItemStack(Material.LIME_DYE);         var y = yes.getItemMeta();         y.displayName(net.kyori.adventure.text.Component.text("§a§lACCEPT"));         y.lore(List.of(net.kyori.adventure.text.Component.text("§7Choose the total price next.")));         y.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_SELL_CONFIRM);         yes.setItemMeta(y);          ItemStack no = new ItemStack(Material.RED_DYE);         var n2 = no.getItemMeta();         n2.displayName(net.kyori.adventure.text.Component.text("§c§lCANCEL"));         n2.lore(List.of(net.kyori.adventure.text.Component.text("§7Back to the market.")));         n2.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_SELL_CANCEL);         no.setItemMeta(n2);          inv.setItem(11, yes);         inv.setItem(15, no);     } 
    private void buildConfirmTeleport(Inventory inv, MarketSession session) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var borderMeta = border.getItemMeta();
        borderMeta.displayName(net.kyori.adventure.text.Component.text(""));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(i + 18, border);
        }
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, border);
        }

        ItemStack yes = new ItemStack(Material.LIME_DYE);
        var yesMeta = yes.getItemMeta();
        yesMeta.displayName(net.kyori.adventure.text.Component.text("§a§lYES"));
        yesMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Teleport to the Market NPC.")));
        yesMeta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CONFIRM_YES);
        yes.setItemMeta(yesMeta);

        ItemStack no = new ItemStack(Material.RED_DYE);
        var noMeta = no.getItemMeta();
        noMeta.displayName(net.kyori.adventure.text.Component.text("§c§lNO"));
        noMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Open the Main Market GUI.")));
        noMeta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CONFIRM_NO);
        no.setItemMeta(noMeta);

        inv.setItem(11, yes);
        inv.setItem(15, no);
    }

    // ==================================================================
    // Confirm Buy (27-slot dialog with Yes / No)
    // ==================================================================

    public void openConfirmBuy(Player player, long orderId) {
        MarketSession session = sessions.computeIfAbsent(player.getUniqueId(), MarketSession::new);
        session.setPendingBuyOrderId(orderId);
        session.setView(MarketSession.View.CONFIRM_BUY);
        open(player, session);
    }

    private void buildConfirmBuy(Inventory inv, MarketSession session) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var borderMeta = border.getItemMeta();
        borderMeta.displayName(net.kyori.adventure.text.Component.text(""));
        border.setItemMeta(borderMeta);
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, border);
            inv.setItem(i + 18, border);
        }
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, border);
        }

        ItemStack yes = new ItemStack(Material.LIME_DYE);
        var yesMeta = yes.getItemMeta();
        yesMeta.displayName(net.kyori.adventure.text.Component.text("§a§lYES"));
        yesMeta.lore(List.of(net.kyori.adventure.text.Component.text("§7Confirm purchase.")));
        yesMeta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CONFIRM_BUY_YES);
        yes.setItemMeta(yesMeta);

        ItemStack no = new ItemStack(Material.RED_DYE);
        var noMeta = no.getItemMeta();
        noMeta.displayName(net.kyori.adventure.text.Component.text("§c§lNO"));
        noMeta.lore(List.of(net.kyori.adventure.text.Component.text("§7Cancel.")));
        noMeta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CONFIRM_BUY_NO);
        no.setItemMeta(noMeta);

        inv.setItem(11, yes);
        inv.setItem(15, no);
    }

    // ==================================================================
    // Quick-Sell Floor
    // ==================================================================

    private void buildQuickSellFloor(Inventory inv, MarketSession session) {
        applyFooter(inv, session);

        ItemStack input = new ItemStack(Material.HOPPER);
        var inputMeta = input.getItemMeta();
        inputMeta.displayName(net.kyori.adventure.text.Component.text("§eInput Item"));
        inputMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Place your item here,"),
                net.kyori.adventure.text.Component.text("§7then click the confirm button below.")));
        input.setItemMeta(inputMeta);
        inv.setItem(22, input);

        ItemStack confirm = new ItemStack(Material.NETHERITE_INGOT);
        var confMeta = confirm.getItemMeta();
        confMeta.displayName(net.kyori.adventure.text.Component.text("§a§lQuick-Sell"));
        confMeta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Sell the item in the slot above"),
                net.kyori.adventure.text.Component.text("§7to the server §c(permanently deleted)§7.")));
        confMeta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_QUICK_SELL);
        confirm.setItemMeta(confMeta);
        inv.setItem(40, confirm);
    }

    // ==================================================================
    // Shared footer (slots 45-53) + header (0-8)
    // ==================================================================

    private void applyHeader(Inventory inv, MarketSession session) {
        // Slot 0 - Search filter.
        ItemStack search = new ItemStack(Material.COMPASS);
        var sm = search.getItemMeta();
        sm.displayName(net.kyori.adventure.text.Component.text("§e§lSearch Filter"));
        if (session.getSearchFilter() != null) {
            sm.lore(List.of(net.kyori.adventure.text.Component.text("§6Filter: §f" + session.getSearchFilter()),
                            net.kyori.adventure.text.Component.text("§7Click to change.")));
        } else {
            sm.lore(List.of(net.kyori.adventure.text.Component.text("§7Click to search an item type.")));
        }
        sm.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_SEARCH);
        search.setItemMeta(sm);
        inv.setItem(0, search);

        // Slots 2 / 4 / 6 / 8 - main market buttons.
        inv.setItem(2, mainButton(Material.BOOK, "§bGlobal Sell Listings",
                ACTION_SELL_LISTINGS, "§7Browse and buy player listings."));
        inv.setItem(4, mainButton(Material.WRITABLE_BOOK, "§dGlobal Buy Orders",
                ACTION_BUY_ORDERS, "§7Browse and fulfill buy orders."));
        inv.setItem(6, mainButton(Material.NETHERITE_INGOT, "§6Server Quick-Sell Floor",
                ACTION_QUICK_SELL, "§7Sell directly to the server."));
        inv.setItem(8, mainButton(Material.ENDER_PEARL, "§5Market NPC Teleport",
                ACTION_TELEPORT_NPC, "§7Teleport to the Market NPC."));

        // Filler panes.
        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot : new int[]{1, 3, 5, 7}) {
            inv.setItem(slot, filler);
        }
    }

    /** Builds the pagination + navigation footer shared by most views. */
    private void applyFooter(Inventory inv, MarketSession session) {
        applyFooter(inv, session, true);
    }

    private void applyFooter(Inventory inv, MarketSession session, boolean includeHistory) {
        ItemStack prev = new ItemStack(Material.ARROW);
        var pm = prev.getItemMeta();
        pm.displayName(net.kyori.adventure.text.Component.text("§fPrevious Page"));
        pm.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_PREV_PAGE);
        prev.setItemMeta(pm);

        ItemStack next = new ItemStack(Material.ARROW);
        var nm = next.getItemMeta();
        nm.displayName(net.kyori.adventure.text.Component.text("§fNext Page"));
        nm.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_NEXT_PAGE);
        next.setItemMeta(nm);

        inv.setItem(45, prev);

        inv.setItem(47, mainButton(Material.CHEST, "§fMy Active Sell Orders",
                ACTION_MY_SELLS, "§7View / cancel your sell listings."));

        if (includeHistory) {
            inv.setItem(49, mainButton(Material.WRITABLE_BOOK, "§fTransaction History",
                    ACTION_HISTORY, "§7Buy / sell history."));
        }

        inv.setItem(51, mainButton(Material.ENDER_CHEST, "§fMy Active Buy Orders",
                ACTION_MY_BUYS, "§7View / cancel buy orders & claim items."));
        inv.setItem(53, next);
    }

    private ItemStack mainButton(Material material, String name, String action, String lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name));
        meta.lore(List.of(net.kyori.adventure.text.Component.text(lore)));
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(""));
        item.setItemMeta(meta);
        return item;
    }

    // ==================================================================
    // Main grid (slots 18-44 = 27-slot active display grid)
    // ==================================================================

    private void buildMainGrid(Inventory inv, MarketSession session) {
        applyHeader(inv, session);
        applyFooter(inv, session);

        boolean sell = session.getBrowseTab() == MarketSession.BrowseTab.SELL_LISTINGS;
        OrderType type = sell ? OrderType.SELL : OrderType.BUY;
        List<OrderListing> listings =
                market.getActiveListings(type, session.getPage(), session.getSearchFilter());
        int perPage = config.getListingsPerPage();

        int slot = 18;
        for (OrderListing listing : listings) {
            if (slot > 44) {
                break;
            }
            inv.setItem(slot, buildBrowseListingItem(listing, type));
            slot++;
        }
        if (listings.isEmpty()) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            var em = empty.getItemMeta();
            em.displayName(net.kyori.adventure.text.Component.text("§7No " + (sell ? "sell" : "buy")
                    + " listings here."));
            empty.setItemMeta(em);
            inv.setItem(31, empty);
        }

        // Stats panel on the nav row (slots 46 / 48 / 50 / 52).
        buildStatsRow(inv, session);
    }

    /** Icon + hover lore for a browse-grid listing (unit price + total value). */
    private ItemStack buildBrowseListingItem(OrderListing listing, OrderType type) {
        Material mat = Material.matchMaterial(listing.itemMaterial());
        if (mat == null) {
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat, Math.min(listing.amount(), 64));
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§e" + prettyName(mat.name())));

        double unit = listing.pricePerUnit();
        double total = unit * listing.amount();
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        if (type == OrderType.SELL) {
            lore.add(net.kyori.adventure.text.Component.text("§8Seller: §f" + listing.sellerName()));
        } else {
            lore.add(net.kyori.adventure.text.Component.text("§8Buyer wants: §f" + listing.amount() + "x"));
        }
        lore.add(net.kyori.adventure.text.Component.text("§a" + MoneyFormat.formatWithSymbol(unit)
                + " §7per unit"));
        lore.add(net.kyori.adventure.text.Component.text("§a" + MoneyFormat.formatWithSymbol(total)
                + " §2Total"));
        if (listing.expiresAt() != null) {
            lore.add(net.kyori.adventure.text.Component.text("§8Expires: §7"
                    + TIME_FORMAT.format(listing.expiresAt())));
        }
        lore.add(net.kyori.adventure.text.Component.text("§8Click to " + (type == OrderType.SELL
                ? "buy instantly" : "fulfill buy order")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.orderId(), PersistentDataType.LONG, listing.orderId());
        item.setItemMeta(meta);
        return item;
    }

    /** Builds the stats row: dynamic market price, balance, listing slots. */
    private void buildStatsRow(Inventory inv, MarketSession session) {
        Player player = Bukkit.getPlayer(session.getPlayerUuid());
        double price = market.getAverageMarketPrice().orElse(0.0);
        double balance = economy.getBalance(session.getPlayerUuid());
        int slots = player != null ? market.getRemainingListingSlots(player) : 0;

        inv.setItem(46, statItem(Material.SUNFLOWER, "§6Market Price",
                "§f" + MoneyFormat.formatWithSymbol(price) + " §7per unit",
                "§8Supply & demand (completed trades)"));
        inv.setItem(48, statItem(Material.GOLD_NUGGET, "§6Your Balance",
                "§f" + MoneyFormat.formatWithSymbol(balance)));
        inv.setItem(50, statItem(Material.PAPER, "§6Listing Slots",
                "§f" + slots + " §7remaining"));
        inv.setItem(52, statItem(Material.CLOCK, "§7Page " + (session.getPage() + 1), ""));
    }

    /** A small labeled stat icon for the footer stats row. */
    private ItemStack statItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(net.kyori.adventure.text.Component.text(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ==================================================================
    // My Sell Orders / My Buy Orders
    // ==================================================================

    private void buildMyOrders(Inventory inv, MarketSession session, OrderType type) {
        applyHeader(inv, session);
        applyFooter(inv, session, true);
        List<MarketOrder> orders = market.getMyOrders(session.getPlayerUuid(), type);
        int slot = 18;
        for (MarketOrder order : orders) {
            if (order.getStatus() != net.yourserver.coreengine.market.OrderStatus.ACTIVE) {
                continue;
            }
            if (slot > 44) {
                break;
            }
            inv.setItem(slot, buildOwnOrderItem(order));
            slot++;
        }
        if (slot == 18) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            var em = empty.getItemMeta();
            em.displayName(net.kyori.adventure.text.Component.text("§7No active "
                    + (type == OrderType.SELL ? "sell" : "buy") + " orders."));
            empty.setItemMeta(em);
            inv.setItem(31, empty);
        }
    }

    /** Icon + controls for an order the viewing player owns (cancel button). */
    private ItemStack buildOwnOrderItem(MarketOrder order) {
        Material mat = Material.matchMaterial(order.getItemMaterial());
        if (mat == null) {
            mat = Material.STONE;
        }
        ItemStack item = new ItemStack(mat, Math.min(order.getRemainingAmount(), 64));
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§e" + prettyName(mat.name())));
        double unit = order.getPricePerUnit();
        double total = unit * order.getAmount();
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§a" + MoneyFormat.formatWithSymbol(unit)
                        + " §7per unit"),
                net.kyori.adventure.text.Component.text("§a" + MoneyFormat.formatWithSymbol(total)
                        + " §2Total"),
                net.kyori.adventure.text.Component.text("§cShift+Click to cancel")));
        meta.getPersistentDataContainer().set(PDCKeys.orderId(), PersistentDataType.LONG, order.getOrderId());
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CANCEL_ORDER);
        item.setItemMeta(meta);
        return item;
    }

    private String prettyName(String enumName) {
        String lower = enumName.toLowerCase().replace('_', ' ');
        if (lower.isEmpty()) {
            return enumName;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    // ==================================================================
    // My Buy Orders + claimable inbox
    // ==================================================================

    private void buildMyBuyOrders(Inventory inv, MarketSession session) {
        applyHeader(inv, session);
        applyFooter(inv, session, true);

        // Section header
        List<EscrowListing> claimable =
                market.getClaimableItems(session.getPlayerUuid(), null);

        int slot = 18;
        // Active (pending) buy orders first.
        List<MarketOrder> buyOrders = market.getMyOrders(session.getPlayerUuid(), OrderType.BUY);
        for (MarketOrder order : buyOrders) {
            if (order.getStatus() != net.yourserver.coreengine.market.OrderStatus.ACTIVE) {
                continue;
            }
            if (slot > 44) {
                break;
            }
            inv.setItem(slot, buildOwnOrderItem(order));
            slot++;
        }
        // Then claimable (fulfilled) escrow items.
        for (EscrowListing entry : claimable) {
            if (slot > 44) {
                break;
            }
            inv.setItem(slot, buildClaimableItem(entry));
            slot++;
        }

        if (slot == 18) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            var em = empty.getItemMeta();
            em.displayName(net.kyori.adventure.text.Component.text("§7No active buy orders or items to claim."));
            empty.setItemMeta(em);
            inv.setItem(31, empty);
        }
    }

    /** Icon + controls for a claimable escrow inbox entry. */
    private ItemStack buildClaimableItem(EscrowListing entry) {
        Material mat = Material.matchMaterial(entry.itemMaterial());
        if (mat == null) {
            mat = Material.CHEST;
        }
        ItemStack item = new ItemStack(mat, Math.min(entry.amount(), 64));
        var meta = item.getItemMeta();
        String kind = switch (entry.reason()) {
            case BUY_FULFILLED -> "§6Purchased §7(claim)";
            case SELL_EXPIRED -> "§bExpired listing §7(claim)";
            default -> "§fClaim";
        };
        meta.displayName(net.kyori.adventure.text.Component.text(kind + ": §e" + prettyName(mat.name())));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Amount: §f" + entry.amount()),
                net.kyori.adventure.text.Component.text("§cClick to claim")));
        meta.getPersistentDataContainer().set(PDCKeys.escrowId(), PersistentDataType.LONG, entry.escrowId());
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_CLAIM);
        item.setItemMeta(meta);
        return item;
    }

    // ==================================================================
    // Transaction History (Buy / Sell sub-tabs)
    // ==================================================================

    private void buildHistory(Inventory inv, MarketSession session) {
        applyHeader(inv, session);
        applyFooter(inv, session, false);

        // Sub-tab switcher on the nav row.
        inv.setItem(47, mainButton(Material.GREEN_DYE, "§aBuy History",
                ACTION_BUY_HISTORY, "§7Show purchases you made / fulfilled."));
        inv.setItem(51, mainButton(Material.RED_DYE, "§cSell History",
                ACTION_SELL_HISTORY, "§7Show items you sold / quick-sold."));

        boolean buyTab = session.getBrowseTab() == MarketSession.BrowseTab.BUY_HISTORY;
        List<TransactionRecord> history =
                market.getTransactionHistory(session.getPlayerUuid(), session.getPage());

        // In a full impl the DAO filters by buyer/seller; here we filter the
        // already-relevant rows in-memory for the requested tab.
        int slot = 18;
        int shown = 0;
        int perPage = config.getListingsPerPage();
        for (TransactionRecord tx : history) {
            boolean isBuy = isBuyForPlayer(tx, session.getPlayerUuid());
            if (isBuy != buyTab) {
                continue;
            }
            if (shown >= perPage) {
                break;
            }
            if (slot > 44) {
                break;
            }
            inv.setItem(slot, buildHistoryItem(tx));
            slot++;
            shown++;
        }
        if (shown == 0) {
            ItemStack empty = new ItemStack(Material.BARRIER);
            var em = empty.getItemMeta();
            em.displayName(net.kyori.adventure.text.Component.text("§7No " + (buyTab ? "buy" : "sell")
                    + " history."));
            empty.setItemMeta(em);
            inv.setItem(31, empty);
        }
    }

    private boolean isBuyForPlayer(TransactionRecord tx, UUID p) {
        // QUICK_SELL and SELL_PURCHASE hit the player as buyer when they are
        // the buyerUuid; everything else is a "sell" from their perspective.
        return tx.getType() == TransactionRecord.Type.SELL_PURCHASE
                && tx.getBuyerUuid() != null && tx.getBuyerUuid().equals(p);
    }

    /** History icon with amount / material / price. */
    private ItemStack buildHistoryItem(TransactionRecord tx) {
        Material mat = Material.matchMaterial(tx.getItemMaterial());
        if (mat == null) {
            mat = Material.PAPER;
        }
        ItemStack item = new ItemStack(mat, Math.min(tx.getAmount(), 64));
        var meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§e" + prettyName(mat.name())));
        meta.lore(List.of(
                net.kyori.adventure.text.Component.text("§7Amount: §f" + tx.getAmount()),
                net.kyori.adventure.text.Component.text("§7Total: §f"
                        + MoneyFormat.formatWithSymbol(tx.getTotalPrice())),
                net.kyori.adventure.text.Component.text("§8" + tx.getType().name().replace('_', ' '))));
        meta.getPersistentDataContainer().set(PDCKeys.transactionId(), PersistentDataType.LONG, tx.getTransactionId());
        item.setItemMeta(meta);
        return item;
    }
}
