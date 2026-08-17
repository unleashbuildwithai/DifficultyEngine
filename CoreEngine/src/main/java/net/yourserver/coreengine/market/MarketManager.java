package net.yourserver.coreengine.market;

import net.yourserver.coreengine.config.ConfigManager;
import net.yourserver.coreengine.database.dao.MarketDao;
import net.yourserver.coreengine.database.dao.MarketDao.EscrowListing;
import net.yourserver.coreengine.database.dao.MarketDao.OrderListing;
import net.yourserver.coreengine.economy.EconomyManager;
import net.yourserver.coreengine.rank.PlayerRank;
import net.yourserver.coreengine.rank.RankManager;
import net.yourserver.coreengine.util.MoneyFormat;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The heart of Module 1 - owns every market mutation and guarantees the
 * anti-duplication invariants:
 * <ul>
 *   <li>An item is serialized (Base64, full NBT/PDC) and physically removed
 *       from the world at the exact moment it is listed; it only ever exists
 *       in the DB until delivery.</li>
 *   <li>Per-player {@link java.util.concurrent.locks.ReentrantLock}s (from
 *       {@link MarketLockRegistry}) are held across every multi-step mutation,
 *       acquired in deterministic sorted-UUID order for two-party trades.</li>
 *   <li>Order status transitions are conditional SQL updates
 *       ({@code WHERE status = 'ACTIVE'}), so a consumed order can never be
 *       consumed twice.</li>
 *   <li>Pre/post inventory + item-count audits run around item-taking
 *       operations.</li>
 *   <li>DB writes happen first and are authoritative; item delivery falls
 *       back to the claimable escrow inbox if the recipient has no space or
 *       is not confirmably online.</li>
 * </ul>
 * <p>
 * All operations are synchronous and must be invoked from the Bukkit main
 * thread (they mutate inventories). Local SQLite keeps DB work fast enough
 * that this is fine at SMP scale.
 */
public class MarketManager {

    /** UUID used as the "buyer" for quick-sell transactions in history. */
    public static final UUID SERVER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final JavaPlugin plugin;
    private final Logger logger;
    private final ConfigManager config;
    private final MarketDao dao;
    private final EconomyManager economy;
    private final RankManager rankManager;
    private final MarketLockRegistry locks;

    /** Guards against double-processing of the same order across threads. */
    private final KeySetView<Long, Boolean> processingOrderIds = ConcurrentHashMap.newKeySet();

    public MarketManager(JavaPlugin plugin, ConfigManager config, MarketDao dao,
                         EconomyManager economy, RankManager rankManager,
                         MarketLockRegistry locks) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.config = config;
        this.dao = dao;
        this.economy = economy;
        this.rankManager = rankManager;
        this.locks = locks;
    }

    // ==================================================================
    // Place orders
    // ==================================================================

    /**
     * Lists the entire stack in the player's main hand as a SELL listing.
     * Order row is inserted first (to obtain the id), the item is then
     * serialized WITH the order-id PDC tag, removed from the world, and the
     * removal audited against the expected count. On audit failure the order
     * is cancelled and the item restored.
     */
    public MarketResult placeSellListing(Player player, double totalPrice) {
        if (totalPrice <= 0) {
            return MarketResult.INVALID_PRICE;
        }
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR || inHand.getAmount() <= 0) {
            return MarketResult.EMPTY_HAND;
        }
        if (!canPlaceMoreListings(player)) {
            return MarketResult.CAP_REACHED;
        }
        locks.lock(player.getUniqueId());
        try {
            int amount = inHand.getAmount();
            double unit = totalPrice / amount;
            Material material = inHand.getType();
            int before = countMatching(player.getInventory(), material);

            Instant expiresAt = Instant.now().plus(
                    config.getSellListingExpirationHours(), ChronoUnit.HOURS);
            MarketDao.CreateOrderResult created = dao.createOrder(
                    player.getUniqueId(), player.getName(), OrderType.SELL,
                    material.name(), "", amount, amount, unit, totalPrice, expiresAt);
            long orderId = created.orderId();

            ItemStack escrowed = inHand.clone();
            escrowed.editMeta(meta -> meta.getPersistentDataContainer().set(
                    PDCKeys.orderId(), PersistentDataType.LONG, orderId));
            String serialized = ItemSerialization.serialize(escrowed);

            // Now the item leaves the world permanently (until delivery).
            inHand.setAmount(0);
            player.getInventory().setItemInMainHand(inHand);
            player.updateInventory();

            int after = countMatching(player.getInventory(), material);
            if (before - after != amount) {
                logger.warning("[ANTI-DUPE] Sell listing removal audit failed for "
                        + player.getName() + " (before=" + before + ", after=" + after
                        + ", expected=" + amount + ") - rolling back order " + orderId);
                player.getInventory().addItem(escrowed);
                player.updateInventory();
                dao.updateOrderStatus(orderId, OrderStatus.CANCELLED, false);
                return MarketResult.INVENTORY_MISMATCH;
            }

            dao.updateOrderSerialized(orderId, serialized);

            // Auto-match: fill any resting buy orders bidding >= our ask.
            int crossed = crossSellAgainstBuyOrders(orderId, material.name(), amount, unit,
                    player.getUniqueId());
            if (crossed > 0) {
                dao.reduceOrderRemaining(orderId, crossed);
            }
            int leftover = amount - crossed;
            if (crossed >= amount) {
                player.sendMessage("§aYour items were instantly sold to existing buy orders!");
            } else if (crossed > 0) {
                player.sendMessage("§aSold §2" + crossed + "§a instantly to buy orders; §2"
                        + leftover + "§a listed on the market.");
            } else {
                player.sendMessage("§aYour item has been listed on the market for §2$"
                        + MoneyFormat.format(totalPrice) + "§a total.");
            }
            logger.fine("SELL listing " + orderId + " placed by " + player.getName()
                    + " (crossed=" + crossed + ", leftover=" + leftover + ")");
            return MarketResult.SUCCESS;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "placeSellListing failed for " + player.getName(), e);
            return MarketResult.NOT_ACTIVE;
        } finally {
            locks.unlock(player.getUniqueId());
        }
    }

    /**
     * Creates a player BUY order, instantly escrowing
     * {@code amount * pricePerUnit} from the player's balance.
     */
    public MarketResult placeBuyOrder(Player player, Material material, int amount, double pricePerUnit) {
        if (material == null || material == Material.AIR) {
            return MarketResult.INVALID_MATERIAL;
        }
        if (amount <= 0) {
            return MarketResult.INVALID_AMOUNT;
        }
        if (pricePerUnit <= 0) {
            return MarketResult.INVALID_PRICE;
        }
        if (!canPlaceMoreListings(player)) {
            return MarketResult.CAP_REACHED;
        }
        double total = amount * pricePerUnit;
        locks.lock(player.getUniqueId());
        try {
            OptionalDouble remaining = economy.withdraw(player.getUniqueId(), total);
            if (remaining.isEmpty()) {
                return MarketResult.INSUFFICIENT_FUNDS;
            }
            Instant expiresAt = Instant.now().plus(
                    config.getEscrowClaimExpirationDays(), ChronoUnit.DAYS);
            MarketDao.CreateOrderResult created = dao.createOrder(
                    player.getUniqueId(), player.getName(), OrderType.BUY,
                    material.name(), "", amount, amount, pricePerUnit, total, expiresAt);
            long orderId = created.orderId();

            // Auto-match: fill any resting sell listings asking <= our bid.
            int crossed = crossBuyAgainstSellListings(orderId, material.name(), amount,
                    pricePerUnit, player.getUniqueId(), player);
            if (crossed > 0) {
                dao.reduceOrderRemaining(orderId, crossed);
            }
            int leftover = amount - crossed;
            if (crossed >= amount) {
                player.sendMessage("§aYour buy order was instantly filled from existing sell listings!");
            } else if (crossed > 0) {
                player.sendMessage("§aBought §2" + crossed + "§a instantly; §2" + leftover
                        + "§a remains as an active buy order.");
            } else {
                player.sendMessage("§aBuy order placed for §2" + amount + "x §e" + material.name()
                        + " §a(escrowed §2$" + MoneyFormat.format(total) + "§a).");
            }
            return MarketResult.SUCCESS;
        } finally {
            locks.unlock(player.getUniqueId());
        }
    }

    // ==================================================================
    // Consume orders (two-party trades)
    // ==================================================================

    /**
     * Instantly buys out an ACTIVE sell listing: buyer pays seller, item is
     * delivered to the buyer (or into the buyer's escrow inbox if the
     * inventory has no room), and a permanent history row is written.
     */
    public MarketResult buyFromSellListing(Player buyer, long orderId) {
        if (!processingOrderIds.add(orderId)) {
            return MarketResult.NOT_ACTIVE; // already being processed
        }
        try {
            Optional<MarketOrder> opt = dao.findOrderById(orderId);
            if (opt.isEmpty()) {
                return MarketResult.NOT_FOUND;
            }
            MarketOrder order = opt.get();
            if (order.getOrderType() != OrderType.SELL || order.getStatus() != OrderStatus.ACTIVE) {
                return MarketResult.NOT_ACTIVE;
            }
            if (order.getPlayerUuid().equals(buyer.getUniqueId())) {
                return MarketResult.OWN_ORDER;
            }

            locks.lockAll(buyer.getUniqueId(), order.getPlayerUuid());
            try {
                // Re-check under the lock - the order may have been consumed.
                Optional<MarketOrder> fresh = dao.findOrderById(orderId);
                if (fresh.isEmpty() || fresh.get().getStatus() != OrderStatus.ACTIVE) {
                    return MarketResult.NOT_ACTIVE;
                }
                MarketOrder current = fresh.get();
                int qty = current.getRemainingAmount();
                double cost = qty * current.getPricePerUnit();
                if (!economy.has(buyer.getUniqueId(), cost)) {
                    return MarketResult.INSUFFICIENT_FUNDS;
                }
                if (!economy.transfer(buyer.getUniqueId(), current.getPlayerUuid(), cost)) {
                    return MarketResult.INSUFFICIENT_FUNDS;
                }
                if (!dao.fulfillSellOrderFully(orderId)) {
                    // Extremely unlikely: order became non-active between check and mark.
                    economy.deposit(buyer.getUniqueId(), cost);
                    return MarketResult.NOT_ACTIVE;
                }

                dao.insertTransaction(orderId, current.getPlayerUuid(), buyer.getUniqueId(),
                        TransactionRecord.Type.SELL_PURCHASE, current.getItemMaterial(),
                        qty, current.getPricePerUnit(), cost);

                ItemStack payload = ItemSerialization.deserialize(current.getItemSerialized());
                payload.setAmount(qty);
                deliverOrEscrow(buyer, payload, orderId, EscrowEntry.Reason.INVENTORY_FULL_FALLBACK);
                return MarketResult.SUCCESS;
            } finally {
                locks.unlockAll(buyer.getUniqueId(), order.getPlayerUuid());
            }
        } finally {
            processingOrderIds.remove(orderId);
        }
    }

    /**
     * A player holding the exact item fulfills an ACTIVE buy order: the
     * escrowed funds are paid to the fulfiller, the item is delivered into
     * the BUY ORDER OWNER's 90-day claimable inbox, and the order is marked
     * FULFILLED. A buyer may never fulfill their own order.
     */
    public MarketResult fulfillBuyOrder(Player fulfiller, long orderId) {
        if (!processingOrderIds.add(orderId)) {
            return MarketResult.NOT_ACTIVE;
        }
        try {
            Optional<MarketOrder> opt = dao.findOrderById(orderId);
            if (opt.isEmpty()) {
                return MarketResult.NOT_FOUND;
            }
            MarketOrder order = opt.get();
            if (order.getOrderType() != OrderType.BUY || order.getStatus() != OrderStatus.ACTIVE) {
                return MarketResult.NOT_ACTIVE;
            }
            if (order.getPlayerUuid().equals(fulfiller.getUniqueId())) {
                return MarketResult.OWN_ORDER;
            }

            locks.lockAll(order.getPlayerUuid(), fulfiller.getUniqueId());
            try {
                Optional<MarketOrder> fresh = dao.findOrderById(orderId);
                if (fresh.isEmpty() || fresh.get().getStatus() != OrderStatus.ACTIVE) {
                    return MarketResult.NOT_ACTIVE;
                }
                MarketOrder current = fresh.get();
                Material mat = Material.matchMaterial(current.getItemMaterial());
                if (mat == null) {
                    return MarketResult.INVALID_MATERIAL;
                }

                int needed = current.getRemainingAmount();
                int before = countMatching(fulfiller.getInventory(), mat);
                if (before < needed) {
                    return MarketResult.INVENTORY_MISMATCH;
                }
                if (!removeExact(fulfiller.getInventory(), mat, needed)) {
                    return MarketResult.INVENTORY_MISMATCH;
                }
                int after = countMatching(fulfiller.getInventory(), mat);
                if (before - after != needed) {
                    logger.warning("[ANTI-DUPE] Buy-order fulfill audit failed for "
                            + fulfiller.getName() + " (order " + orderId + ")");
                    return MarketResult.INVENTORY_MISMATCH;
                }

                double payout = current.getRemainingAmount() * current.getPricePerUnit();
                if (!economy.transfer(current.getPlayerUuid(), fulfiller.getUniqueId(), payout)) {
                    // Order escrow missing funds - refund the items to the fulfiller.
                    fulfiller.getInventory().addItem(new ItemStack(mat, needed));
                    fulfiller.updateInventory();
                    return MarketResult.INSUFFICIENT_FUNDS;
                }
                if (!dao.fulfillBuyOrderFully(orderId)) {
                    fulfiller.getInventory().addItem(new ItemStack(mat, needed));
                    fulfiller.updateInventory();
                    return MarketResult.NOT_ACTIVE;
                }

                dao.insertTransaction(orderId, fulfiller.getUniqueId(), current.getPlayerUuid(),
                        TransactionRecord.Type.BUY_FULFILL, current.getItemMaterial(),
                        current.getAmount(), current.getPricePerUnit(), payout);

                // Purchased item -> buy order owner's claimable inbox (90 days).
                ItemStack payload = new ItemStack(mat, needed);
                dao.insertEscrow(current.getPlayerUuid(), orderId, EscrowEntry.Reason.BUY_FULFILLED,
                        current.getItemMaterial(), ItemSerialization.serialize(payload), needed,
                        Instant.now().plus(config.getEscrowClaimExpirationDays(), ChronoUnit.DAYS));
                return MarketResult.SUCCESS;
            } finally {
                locks.unlockAll(order.getPlayerUuid(), fulfiller.getUniqueId());
            }
        } finally {
            processingOrderIds.remove(orderId);
        }
    }

    // ==================================================================
    // Cancel, claim, quick-sell
    // ==================================================================

    /**
     * Cancels an order owned by the player. SELL: item returns to inventory
     * (or claimable escrow if the inventory is full). BUY: the remaining
     * escrow is refunded to the player's balance.
     */
    public MarketResult cancelOrder(Player player, long orderId) {
        if (!processingOrderIds.add(orderId)) {
            return MarketResult.NOT_ACTIVE;
        }
        try {
            Optional<MarketOrder> opt = dao.findOrderById(orderId);
            if (opt.isEmpty()) {
                return MarketResult.NOT_FOUND;
            }
            MarketOrder order = opt.get();
            if (!order.getPlayerUuid().equals(player.getUniqueId())) {
                return MarketResult.OWN_ORDER;
            }
            if (order.getStatus() != OrderStatus.ACTIVE) {
                return MarketResult.NOT_ACTIVE;
            }

            locks.lock(player.getUniqueId());
            try {
                if (!dao.cancelOrder(orderId)) {
                    return MarketResult.NOT_ACTIVE;
                }
                if (order.getOrderType() == OrderType.SELL) {
                    ItemStack payload = ItemSerialization.deserialize(order.getItemSerialized());
                    payload.setAmount(order.getRemainingAmount());
                    deliverOrEscrow(player, payload, orderId, EscrowEntry.Reason.INVENTORY_FULL_FALLBACK);
                } else { // BUY -> refund remaining escrow.
                    double refund = order.getRemainingAmount() * order.getPricePerUnit();
                    if (refund > 0) {
                        economy.deposit(player.getUniqueId(), refund);
                    }
                }
                return MarketResult.SUCCESS;
            } finally {
                locks.unlock(player.getUniqueId());
            }
        } finally {
            processingOrderIds.remove(orderId);
        }
    }

    /**
     * Claims a single escrow inbox entry (expired sell listing return, or a
     * fulfilled buy order's purchased items). Claims are conditional in the
     * DB ({@code claimed = 0}) so an entry can never be claimed twice.
     */
    public MarketResult claimEscrowItem(Player player, long escrowId) {
        Optional<EscrowEntry> opt = dao.findEscrowById(escrowId);
        if (opt.isEmpty()) {
            return MarketResult.NOT_FOUND;
        }
        EscrowEntry entry = opt.get();
        if (!entry.getOwnerUuid().equals(player.getUniqueId())) {
            return MarketResult.OWN_ORDER;
        }
        if (entry.isClaimed()) {
            return MarketResult.NOT_ACTIVE;
        }
        if (entry.isExpired(Instant.now())) {
            return MarketResult.EXPIRED;
        }

        ItemStack payload = ItemSerialization.deserialize(entry.getItemSerialized());
        payload.setAmount(entry.getAmount());
        int before = countFreeSlots(player.getInventory());
        player.getInventory().addItem(payload);
        player.updateInventory();
        int after = countFreeSlots(player.getInventory());
        if (after == before) {
            // Nothing could be placed (full inventory).
            return MarketResult.INVENTORY_FULL;
        }
        if (!dao.claimEscrow(escrowId)) {
            // Another thread claimed it between check and give - roll back.
            player.getInventory().removeItem(payload);
            player.updateInventory();
            return MarketResult.NOT_ACTIVE;
        }
        return MarketResult.SUCCESS;
    }

    /**
     * Server Quick-Sell Floor: the item is permanently deleted from the
     * world economy and the player is paid {@code amount * buyback unit
     * price}. No listing is created.
     */
    public MarketResult quickSellToServer(Player player, ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR || itemStack.getAmount() <= 0) {
            return MarketResult.EMPTY_HAND;
        }
        double unitPrice = getQuickSellBasePrice(itemStack.getType().name());
        if (unitPrice < 0) {
            return MarketResult.NOT_ACCEPTED;
        }
        int amount = itemStack.getAmount();
        double total = amount * unitPrice;

        locks.lock(player.getUniqueId());
        try {
            // Audit the stack still exists in the given slot before deleting.
            ItemStack current = player.getInventory().getItemInMainHand();
            if (current == null || current.getType() != itemStack.getType()
                    || current.getAmount() < amount) {
                return MarketResult.INVENTORY_MISMATCH;
            }
            economy.deposit(player.getUniqueId(), total);
            current.setAmount(current.getAmount() - amount);
            player.getInventory().setItemInMainHand(current);
            player.updateInventory();
            dao.insertTransaction(null, player.getUniqueId(), SERVER_UUID,
                    TransactionRecord.Type.QUICK_SELL, itemStack.getType().name(),
                    amount, unitPrice, total);
            return MarketResult.SUCCESS;
        } finally {
            locks.unlock(player.getUniqueId());
        }
    }
/**
     * Quick-sell from the GUI quick-sell floor (no main-hand audit - the
     * item is destroyed directly from the GUI slot). Quick-selling feeds
     * into the market_transactions table & drives the VWAP down.
     */
    public MarketResult quickSellFromGui(Player player, ItemStack offered) {
        if (offered == null || offered.getType() == Material.AIR || offered.getAmount() <= 0) {
            return MarketResult.EMPTY_HAND;
        }
        double unitPrice = getQuickSellBasePrice(offered.getType().name());
        if (unitPrice < 0) {
            return MarketResult.NOT_ACCEPTED;
        }
        int amount = offered.getAmount();
        double total = amount * unitPrice;
        locks.lock(player.getUniqueId());
        try {
            economy.deposit(player.getUniqueId(), total);
            dao.insertTransaction(null, player.getUniqueId(), SERVER_UUID,
                    TransactionRecord.Type.QUICK_SELL, offered.getType().name(),
                    amount, unitPrice, total);
            return MarketResult.SUCCESS;
        } finally {
            locks.unlock(player.getUniqueId());
        }
    }

    // ==================================================================
    // Queries for the GUI + commands
    // ==================================================================

    /** Cheapest-first page of active listings for the browse grid. */
    public List<OrderListing> getActiveListings(OrderType orderType, int page, String search) {
        int perPage = config.getListingsPerPage();
        return dao.findActiveListings(orderType, perPage, page * perPage, search);
    }

    /** Claimable inbox entries for the player, optionally by reason. */
    public List<EscrowListing> getClaimableItems(UUID playerUuid, EscrowEntry.Reason reason) {
        return dao.findUnclaimedEscrowByPlayer(playerUuid, reason);
    }

    /** The player's own orders, newest first (for My-Sells / My-Buys). */
    public List<MarketOrder> getMyOrders(UUID playerUuid, OrderType orderType) {
        return dao.findOrdersByPlayer(playerUuid, orderType, null);
    }

    /** Recent transaction history for the player, newest first. */
    public List<TransactionRecord> getTransactionHistory(UUID playerUuid, int page) {
        int perPage = config.getListingsPerPage();
        return dao.findTransactionsForPlayer(playerUuid, perPage, page * perPage);
    }

    /**
     * Dynamic Price Engine - supply-and-demand driven, manipulation-resistant.
     * Uses the robust average (median by default) of confirmed ORGANIC trades
     * (SELL_PURCHASE + BUY_FULFILL; QUICK_SELL excluded to avoid a spiral).
     * Just placing a listing does NOT affect it. Falls back to the spec
     * (lowest sell + highest buy) / 2 midpoint on a cold start.
     */
    public OptionalDouble getAverageMarketPrice() {
        List<Double> prices = dao.getOrganicTradeUnitPrices(null, config.getBasePriceSampleSize());
        if (!prices.isEmpty()) {
            return OptionalDouble.of(centralMeasure(prices));
        }
        OptionalDouble lowestSell = dao.getLowestActiveSellPrice();
        OptionalDouble highestBuy = dao.getHighestActiveBuyPrice();
        if (lowestSell.isPresent() && highestBuy.isPresent()) {
            return OptionalDouble.of((lowestSell.getAsDouble() + highestBuy.getAsDouble()) / 2.0);
        }
        if (lowestSell.isPresent()) {
            return lowestSell;
        }
        return highestBuy;
    }

    /**
     * Quick-sell base price for a material: robust average of that material's
     * confirmed organic trades, falling back to the configured buyback floor,
     * then to -1 (not accepted).
     */
    public double getQuickSellBasePrice(String material) {
        List<Double> prices = dao.getOrganicTradeUnitPrices(material, config.getBasePriceSampleSize());
        if (!prices.isEmpty()) {
            return centralMeasure(prices);
        }
        return config.getBuybackPrice(material);
    }

    private double centralMeasure(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        if (config.useMedian()) {
            return median(values);
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** True if the player can place another active listing per their rank cap. */
    public boolean canPlaceMoreListings(Player player) {
        PlayerRank rank = rankManager.getRank(player.getUniqueId());
        int cap = config.getRankListingCap(rank);
        if (cap <= 0) {
            return false;
        }
        int active = dao.countActiveOrdersByPlayer(player.getUniqueId(), null);
        return active < cap;
    }

    /** Remaining listing capacity for a player (used by GUI lore). */
    public int getRemainingListingSlots(Player player) {
        PlayerRank rank = rankManager.getRank(player.getUniqueId());
        int cap = config.getRankListingCap(rank);
        int active = dao.countActiveOrdersByPlayer(player.getUniqueId(), null);
        return Math.max(0, cap - active);
    }

    // ==================================================================
    // Order crossing (auto-match) engine
    // ==================================================================

    /**
     * Crosses a newly-placed sell listing against resting buy orders whose
     * bid is >= the sell unit price (highest bid first). The buyer's escrow
     * pays the seller at the BUY order's price (maker price, so the seller
     * gets >= their ask), and the items are delivered to each buyer's
     * claimable inbox. Returns the number of units filled.
     */
    private int crossSellAgainstBuyOrders(long sellOrderId, String material, int sellAmount,
                                          double sellUnit, UUID sellerUuid) {
        int remaining = sellAmount;
        String serialized = dao.findOrderById(sellOrderId)
                .map(MarketOrder::getItemSerialized).orElse(null);
        if (serialized == null) {
            return 0;
        }
        ItemStack sellItem = ItemSerialization.deserialize(serialized);
        List<MarketOrder> bids = dao.findCrossableBuyOrders(material, sellUnit, 20);
        for (MarketOrder bid : bids) {
            if (remaining <= 0) break;
            if (bid.getPlayerUuid().equals(sellerUuid)) continue;
            int units = Math.min(remaining, bid.getRemainingAmount());
            if (units <= 0) continue;
            double fillUnit = bid.getPricePerUnit();
            double payout = units * fillUnit;
            locks.lock(bid.getPlayerUuid());
            try {
                if (!economy.transfer(bid.getPlayerUuid(), sellerUuid, payout)) {
                    continue;
                }
                ItemStack payload = sellItem.clone();
                payload.setAmount(units);
                dao.insertEscrow(bid.getPlayerUuid(), bid.getOrderId(),
                        EscrowEntry.Reason.BUY_FULFILLED, material,
                        ItemSerialization.serialize(payload), units,
                        Instant.now().plus(config.getEscrowClaimExpirationDays(), ChronoUnit.DAYS));
                dao.reduceOrderRemaining(bid.getOrderId(), units);
                dao.insertTransaction(bid.getOrderId(), sellerUuid, bid.getPlayerUuid(),
                        TransactionRecord.Type.BUY_FULFILL, material, units, fillUnit, payout);
                remaining -= units;
            } finally {
                locks.unlock(bid.getPlayerUuid());
            }
        }
        return sellAmount - remaining;
    }

    /**
     * Crosses a newly-placed buy order against resting sell listings whose
     * ask is <= the buy unit price (cheapest first). The buyer's escrow pays
     * the seller at the SELL listing's price (maker price, so the buyer pays
     * <= their bid), and the items are delivered to the buyer. Returns the
     * number of units filled.
     */
    private int crossBuyAgainstSellListings(long buyOrderId, String material, int buyAmount,
                                            double buyUnit, UUID buyerUuid, Player buyer) {
        int remaining = buyAmount;
        List<MarketOrder> asks = dao.findCrossableSellListings(material, buyUnit, 20);
        for (MarketOrder ask : asks) {
            if (remaining <= 0) break;
            if (ask.getPlayerUuid().equals(buyerUuid)) continue;
            int units = Math.min(remaining, ask.getRemainingAmount());
            if (units <= 0) continue;
            double fillUnit = ask.getPricePerUnit();
            double cost = units * fillUnit;
            locks.lock(ask.getPlayerUuid());
            try {
                if (!economy.transfer(buyerUuid, ask.getPlayerUuid(), cost)) {
                    continue;
                }
                ItemStack payload = ItemSerialization.deserialize(ask.getItemSerialized());
                payload.setAmount(units);
                deliverOrEscrow(buyer, payload, ask.getOrderId(),
                        EscrowEntry.Reason.INVENTORY_FULL_FALLBACK);
                dao.reduceOrderRemaining(ask.getOrderId(), units);
                dao.insertTransaction(ask.getOrderId(), ask.getPlayerUuid(), buyerUuid,
                        TransactionRecord.Type.SELL_PURCHASE, material, units, fillUnit, cost);
                remaining -= units;
            } finally {
                locks.unlock(ask.getPlayerUuid());
            }
        }
        return buyAmount - remaining;
    }

    // ==================================================================
    // Inventory / delivery helpers
    // ==================================================================

    /** Places an item, falling back to a claimable escrow entry if full. */
    private void deliverOrEscrow(Player target, ItemStack item, Long orderId, EscrowEntry.Reason fallbackReason) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return;
        }
        if (target.isOnline() && countFreeSlots(target.getInventory()) > 0) {
            target.getInventory().addItem(item);
            target.updateInventory();
            return;
        }
        dao.insertEscrow(target.getUniqueId(), orderId, fallbackReason,
                item.getType().name(), ItemSerialization.serialize(item), item.getAmount(),
                Instant.now().plus(config.getEscrowClaimExpirationDays(), ChronoUnit.DAYS));
    }

    /** Counts matching (exact-type) item stacks across a player inventory. */
    private int countMatching(PlayerInventory inv, Material material) {
        int total = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() == material) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} of the material; returns false if short. */
    private boolean removeExact(PlayerInventory inv, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                inv.setItem(i, item);
            }
        }
        return remaining == 0;
    }

    /** Counts fully empty inventory slots (not partial stacks). */
    private int countFreeSlots(PlayerInventory inv) {
        int free = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    public MarketDao getDao() {
        return dao;
    }
}
