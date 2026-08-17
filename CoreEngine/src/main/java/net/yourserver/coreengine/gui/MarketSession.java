package net.yourserver.coreengine.gui;

import java.util.UUID;

/**
 * Per-player, in-memory state that drives how the Market GUI renders and how
 * clicks are interpreted. Because every market inventory uses the same
 * {@code "MARKET_MAIN"} guiType, the current {@link View} + page + search
 * filter stored here tell the click listener what a slot click means.
 * <p>
 * This object is transient - it is not persisted and is recreated whenever a
 * player first opens the market. It is keyed by player UUID in
 * {@link MarketGUIManager#sessions}.
 */
public class MarketSession {

    /** The distinct screens the Market GUI can show. */
    public enum View {
        MAIN_GRID,
        CONFIRM_TELEPORT,
        CONFIRM_BUY,
        SELL_CREATE,
        MY_SELLS,
        MY_BUYS,
        HISTORY,
        QUICK_SELL_FLOOR
    }

    /** Which order side a browse view is showing / which history tab. */
    public enum BrowseTab {
        SELL_LISTINGS,
        BUY_ORDERS,
        BUY_HISTORY,
        SELL_HISTORY
    }

    private final UUID playerUuid;
    private View view = View.MAIN_GRID;
    private BrowseTab browseTab = BrowseTab.SELL_LISTINGS;
    private int page;
    private String searchFilter;

    /** For the confirm-teleport dialog: whether the GUI is the yes/no prompt. */
    private boolean awaitingConfirm;

    /** When true, the player's next chat message is captured as a search term. */
    private boolean searchMode;

    /** Order id awaiting a buy confirmation (CONFIRM_BUY view). */
    private Long pendingBuyOrderId;

    /** When true, the player's next chat message is captured as a total sell price. */
    private boolean awaitingSellPrice;

    public boolean isAwaitingSellPrice() {
        return awaitingSellPrice;
    }

    public void setAwaitingSellPrice(boolean awaitingSellPrice) {
        this.awaitingSellPrice = awaitingSellPrice;
    }

    public MarketSession(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public View getView() {
        return view;
    }

    public void setView(View view) {
        this.view = view;
        this.page = 0;
    }

    public BrowseTab getBrowseTab() {
        return browseTab;
    }

    public void setBrowseTab(BrowseTab browseTab) {
        this.browseTab = browseTab;
        this.page = 0;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public String getSearchFilter() {
        return searchFilter;
    }

    public void setSearchFilter(String searchFilter) {
        this.searchFilter = searchFilter == null || searchFilter.isBlank()
                ? null : searchFilter.trim();
        this.page = 0;
    }

    public boolean isAwaitingConfirm() {
        return awaitingConfirm;
    }

    public void setAwaitingConfirm(boolean awaitingConfirm) {
        this.awaitingConfirm = awaitingConfirm;
    }

    public boolean isSearchMode() {
        return searchMode;
    }

    public void setSearchMode(boolean searchMode) {
        this.searchMode = searchMode;
    }

    public Long getPendingBuyOrderId() {
        return pendingBuyOrderId;
    }

    public void setPendingBuyOrderId(Long pendingBuyOrderId) {
        this.pendingBuyOrderId = pendingBuyOrderId;
    }
}
