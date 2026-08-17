package net.yourserver.coreengine.database.dao;

import net.yourserver.coreengine.database.DatabaseManager;
import net.yourserver.coreengine.market.EscrowEntry;
import net.yourserver.coreengine.market.MarketOrder;
import net.yourserver.coreengine.market.OrderStatus;
import net.yourserver.coreengine.market.OrderType;
import net.yourserver.coreengine.market.TransactionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data-access layer for every Module 1 table: {@code market_orders},
 * {@code market_escrow}, {@code market_transactions} and the balance column
 * of {@code player_profiles}.
 * <p>
 * All methods open their own short-lived connections from the shared
 * {@link DatabaseManager} pool. Higher-level orchestration (who may act,
 * what the money movement means) lives in {@code MarketManager} /
 * {@code EconomyManager} - this class is deliberately pure SQL.
 */
public class MarketDao {

    /** Lightweight view of a listing row used by paginated browsing grids. */
    public record OrderListing(long orderId, String sellerName, String itemMaterial,
                               int amount, double pricePerUnit, Instant expiresAt) {
    }

    /** Lightweight view of an escrow inbox row used by claim GUIs. */
    public record EscrowListing(long escrowId, Long sourceOrderId, EscrowEntry.Reason reason,
                                String itemMaterial, int amount, Instant expiresAt) {
    }

    /** Result of inserting a new order row. */
    public record CreateOrderResult(long orderId, Instant createdAt) {
    }

    /** Result of inserting a new transaction-history row. */
    public record CreateTransactionResult(long transactionId, Instant createdAt) {
    }

    private final DatabaseManager databaseManager;
    private final Logger logger;

    public MarketDao(DatabaseManager databaseManager, Logger logger) {
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    // ==================================================================
    // market_orders - creation & reads
    // ==================================================================

    /**
     * Inserts a new order row. The caller is responsible for having already
     * removed the item from the world (SELL) or escrowed the funds (BUY).
     */
    public CreateOrderResult createOrder(UUID playerUuid, String playerName, OrderType orderType,
                                         String itemMaterial, String itemSerialized, int amount,
                                         int remainingAmount, double pricePerUnit, double totalPrice,
                                         Instant expiresAt) {
        String sql = """
            INSERT INTO market_orders
                (player_uuid, player_name, order_type, status, item_material, item_serialized,
                 amount, remaining_amount, price_per_unit, total_price, created_at, expires_at)
            VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Instant now = Instant.now();
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, orderType.name());
            stmt.setString(4, itemMaterial);
            stmt.setString(5, itemSerialized);
            stmt.setInt(6, amount);
            stmt.setInt(7, remainingAmount);
            stmt.setDouble(8, pricePerUnit);
            stmt.setDouble(9, totalPrice);
            stmt.setLong(10, now.toEpochMilli());
            stmt.setLong(11, expiresAt.toEpochMilli());
            stmt.executeUpdate();
            long orderId = -1;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    orderId = keys.getLong(1);
                }
            }
            return new CreateOrderResult(orderId, now);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create " + orderType + " order", e);
            throw new IllegalStateException("Order insert failed", e);
        }
    }

    /** Full single-order fetch by primary key. */
    public Optional<MarketOrder> findOrderById(long orderId) {
        String sql = "SELECT * FROM market_orders WHERE order_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapOrder(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to find order " + orderId, e);
        }
        return Optional.empty();
    }

    /** Loads active listings of one side, cheapest-first, with search + pagination. */
    public List<OrderListing> findActiveListings(OrderType orderType, int limit, int offset,
                                                 String materialSearch) {
        String sql = """
            SELECT order_id, player_name, item_material, amount, price_per_unit, expires_at
            FROM market_orders
            WHERE status = 'ACTIVE' AND order_type = ? AND remaining_amount > 0
                AND (? IS NULL OR item_material LIKE ?)
            ORDER BY price_per_unit ASC, order_id ASC
            LIMIT ? OFFSET ?
            """;
        String search = (materialSearch == null || materialSearch.isBlank())
                ? null : materialSearch.trim().toUpperCase(Locale.ROOT);
        List<OrderListing> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, orderType.name());
            if (search == null) {
                stmt.setNull(2, java.sql.Types.VARCHAR);
                stmt.setNull(3, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(2, search);
                stmt.setString(3, "%" + search + "%");
            }
            stmt.setInt(4, limit);
            stmt.setInt(5, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrderListing(
                            rs.getLong("order_id"),
                            rs.getString("player_name"),
                            rs.getString("item_material"),
                            rs.getInt("amount"),
                            rs.getDouble("price_per_unit"),
                            parseInstant(rs, "expires_at")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load active " + orderType + " listings", e);
        }
        return result;
    }

    /** All full orders owned by a player (any status), newest first. */
    public List<MarketOrder> findOrdersByPlayer(UUID playerUuid, OrderType orderType, OrderStatus status) {
        String sql = """
            SELECT * FROM market_orders
            WHERE player_uuid = ? AND order_type = ?
                AND (? IS NULL OR status = ?)
            ORDER BY created_at DESC
            """;
        List<MarketOrder> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, orderType.name());
            if (status == null) {
                stmt.setNull(3, java.sql.Types.VARCHAR);
                stmt.setNull(4, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(3, status.name());
                stmt.setString(4, status.name());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load orders for player " + playerUuid, e);
        }
        return result;
    }

    /** Count of a player's ACTIVE orders (optionally restricted to one side). */
    public int countActiveOrdersByPlayer(UUID playerUuid, OrderType orderType) {
        String sql = "SELECT COUNT(*) FROM market_orders WHERE player_uuid = ? AND status = 'ACTIVE'";
        if (orderType != null) {
            sql += " AND order_type = ?";
        }
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            if (orderType != null) {
                stmt.setString(2, orderType.name());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to count active orders for " + playerUuid, e);
        }
        return 0;
    }

    /** Lowest active SELL unit price (or empty). Feeds the Dynamic Price Engine. */
    public OptionalDouble getLowestActiveSellPrice() {
        String sql = """
            SELECT MIN(price_per_unit) FROM market_orders
            WHERE status = 'ACTIVE' AND order_type = 'SELL' AND remaining_amount > 0
            """;
        return querySinglePrice(sql);
    }

    /** Highest active BUY unit price (or empty). */
    public OptionalDouble getHighestActiveBuyPrice() {
        String sql = """
            SELECT MAX(price_per_unit) FROM market_orders
            WHERE status = 'ACTIVE' AND order_type = 'BUY' AND remaining_amount > 0
            """;
        return querySinglePrice(sql);
    }

    // ==================================================================
    // Order crossing (auto-match) + robust price sampling
    // ==================================================================

    /**
     * Active BUY orders for a material whose bid is >= the given sell unit
     * price, highest bid first (for auto-filling a newly placed sell).
     */
    public List<MarketOrder> findCrossableBuyOrders(String material, double sellUnit, int limit) {
        String sql = """
            SELECT * FROM market_orders
            WHERE status = 'ACTIVE' AND order_type = 'BUY' AND item_material = ?
                AND remaining_amount > 0 AND price_per_unit >= ?
            ORDER BY price_per_unit DESC, order_id ASC
            LIMIT ?
            """;
        List<MarketOrder> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, material);
            stmt.setDouble(2, sellUnit);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to find crossable buy orders for " + material, e);
        }
        return result;
    }

    /**
     * Active SELL listings for a material whose ask is <= the given buy unit
     * price, cheapest first (for auto-filling a newly placed buy order).
     */
    public List<MarketOrder> findCrossableSellListings(String material, double buyUnit, int limit) {
        String sql = """
            SELECT * FROM market_orders
            WHERE status = 'ACTIVE' AND order_type = 'SELL' AND item_material = ?
                AND remaining_amount > 0 AND price_per_unit <= ?
            ORDER BY price_per_unit ASC, order_id ASC
            LIMIT ?
            """;
        List<MarketOrder> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, material);
            stmt.setDouble(2, buyUnit);
            stmt.setInt(3, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to find crossable sell listings for " + material, e);
        }
        return result;
    }

    /**
     * Reduces an ACTIVE order's remaining amount by {@code units}, marking it
     * FULFILLED when the remaining hits zero. Used by the crossing engine for
     * partial fills.
     *
     * @return true if the reduction was applied.
     */
    public boolean reduceOrderRemaining(long orderId, int units) {
        String reduce = """
            UPDATE market_orders SET remaining_amount = remaining_amount - ?
            WHERE order_id = ? AND status = 'ACTIVE' AND remaining_amount >= ?
            """;
        String fulfill = """
            UPDATE market_orders SET status = 'FULFILLED', fulfilled_at = ?
            WHERE order_id = ? AND status = 'ACTIVE' AND remaining_amount <= 0
            """;
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement s1 = conn.prepareStatement(reduce)) {
                s1.setInt(1, units);
                s1.setLong(2, orderId);
                s1.setInt(3, units);
                int changed = s1.executeUpdate();
                if (changed == 0) {
                    conn.rollback();
                    return false;
                }
            }
            try (PreparedStatement s2 = conn.prepareStatement(fulfill)) {
                s2.setLong(1, Instant.now().toEpochMilli());
                s2.setLong(2, orderId);
                s2.executeUpdate();
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to reduce remaining for order " + orderId, e);
            return false;
        }
    }

    /**
     * Unit prices of the most recent ORGANIC completed trades (SELL_PURCHASE
     * and BUY_FULFILL only — QUICK_SELL excluded to avoid a feedback spiral
     * and manipulation). Optionally restricted to one material (null = all).
     * Ordered newest first, limited to {@code sampleSize}.
     */
    public List<Double> getOrganicTradeUnitPrices(String material, int sampleSize) {
        String sql = """
            SELECT price_per_unit FROM market_transactions
            WHERE transaction_type IN ('SELL_PURCHASE', 'BUY_FULFILL')
                AND (? IS NULL OR item_material = ?)
            ORDER BY created_at DESC
            LIMIT ?
            """;
        List<Double> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (material == null) {
                stmt.setNull(1, java.sql.Types.VARCHAR);
                stmt.setNull(2, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(1, material);
                stmt.setString(2, material);
            }
            stmt.setInt(3, sampleSize);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getDouble("price_per_unit"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to sample organic trade prices", e);
        }
        return result;
    }


    // ==================================================================
    // market_orders - status updates
    // ==================================================================

    /** Transitions lifecycle status; optionally stamps the fulfillment time. */
    public void updateOrderStatus(long orderId, OrderStatus status, boolean stampFulfilledAt) {
        String sql = "UPDATE market_orders SET status = ?";
        if (stampFulfilledAt) {
            sql += ", fulfilled_at = ?";
        }
        sql += " WHERE order_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status.name());
            if (stampFulfilledAt) {
                stmt.setLong(2, Instant.now().toEpochMilli());
                stmt.setLong(3, orderId);
            } else {
                stmt.setLong(2, orderId);
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update status of order " + orderId, e);
        }
    }

    /** Marks a SELL order bought out; true only if it was still ACTIVE. */
    public boolean fulfillSellOrderFully(long orderId) {
        String sql = """
            UPDATE market_orders
            SET status = 'FULFILLED', remaining_amount = 0, fulfilled_at = ?
            WHERE order_id = ? AND status = 'ACTIVE'
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fulfill sell order " + orderId, e);
            return false;
        }
    }

    /** Marks a BUY order matched; true only if it was still ACTIVE. */
    public boolean fulfillBuyOrderFully(long orderId) {
        String sql = """
            UPDATE market_orders
            SET status = 'FULFILLED', remaining_amount = 0, fulfilled_at = ?
            WHERE order_id = ? AND status = 'ACTIVE'
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            stmt.setLong(2, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to fulfill buy order " + orderId, e);
            return false;
        }
    }

    /** Marks an order CANCELLED (only if still ACTIVE). */
    public boolean cancelOrder(long orderId) {
        String sql = "UPDATE market_orders SET status = 'CANCELLED' WHERE order_id = ? AND status = 'ACTIVE'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to cancel order " + orderId, e);
            return false;
        }
    }

    /** Marks an order EXPIRED (only if still ACTIVE). */
    public boolean expireOrder(long orderId) {
        String sql = "UPDATE market_orders SET status = 'EXPIRED' WHERE order_id = ? AND status = 'ACTIVE'";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, orderId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to expire order " + orderId, e);
            return false;
        }
    }

    // ==================================================================
    // market_escrow - claimable inbox
    // ==================================================================

    /** Inserts a claimable escrow entry; returns the generated id (or -1). */
    public long insertEscrow(UUID ownerUuid, Long sourceOrderId, EscrowEntry.Reason reason,
                             String itemMaterial, String itemSerialized, int amount,
                             Instant expiresAt) {
        String sql = """
            INSERT INTO market_escrow
                (owner_uuid, source_order_id, reason, item_material, item_serialized,
                 amount, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, ownerUuid.toString());
            if (sourceOrderId != null) {
                stmt.setLong(2, sourceOrderId);
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT);
            }
            stmt.setString(3, reason.name());
            stmt.setString(4, itemMaterial);
            stmt.setString(5, itemSerialized);
            stmt.setInt(6, amount);
            stmt.setLong(7, Instant.now().toEpochMilli());
            stmt.setLong(8, expiresAt.toEpochMilli());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to insert escrow entry", e);
        }
        return -1;
    }

    /** Full single-escrow fetch by primary key. */
    public Optional<EscrowEntry> findEscrowById(long escrowId) {
        String sql = "SELECT * FROM market_escrow WHERE escrow_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, escrowId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapEscrow(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to find escrow " + escrowId, e);
        }
        return Optional.empty();
    }

    /** Claimable escrow rows owned by a player, optionally filtered by reason. */
    public List<EscrowListing> findUnclaimedEscrowByPlayer(UUID ownerUuid, EscrowEntry.Reason reason) {
        String sql = """
            SELECT escrow_id, source_order_id, reason, item_material, amount, expires_at
            FROM market_escrow
            WHERE owner_uuid = ? AND claimed = 0 AND (? IS NULL OR reason = ?)
            ORDER BY created_at DESC
            """;
        List<EscrowListing> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, ownerUuid.toString());
            if (reason == null) {
                stmt.setNull(2, java.sql.Types.VARCHAR);
                stmt.setNull(3, java.sql.Types.VARCHAR);
            } else {
                stmt.setString(2, reason.name());
                stmt.setString(3, reason.name());
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long sourceId = rs.getLong("source_order_id");
                    result.add(new EscrowListing(
                            rs.getLong("escrow_id"),
                            rs.wasNull() ? null : sourceId,
                            EscrowEntry.Reason.valueOf(rs.getString("reason")),
                            rs.getString("item_material"),
                            rs.getInt("amount"),
                            parseInstant(rs, "expires_at")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load escrow for player " + ownerUuid, e);
        }
        return result;
    }

    /** Marks an escrow row claimed; returns false if it was already claimed. */
    public boolean claimEscrow(long escrowId) {
        String sql = "UPDATE market_escrow SET claimed = 1, claimed_at = ? WHERE escrow_id = ? AND claimed = 0";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, Instant.now().toEpochMilli());
            stmt.setLong(2, escrowId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to claim escrow " + escrowId, e);
            return false;
        }
    }

    // ==================================================================
    // market_transactions - permanent history log
    // ==================================================================

    /** Records a completed trade / quick-sell into the permanent history. */
    public CreateTransactionResult insertTransaction(Long orderId, UUID sellerUuid, UUID buyerUuid,
                                                      TransactionRecord.Type type, String itemMaterial,
                                                      int amount, double pricePerUnit, double totalPrice) {
        String sql = """
            INSERT INTO market_transactions
                (order_id, seller_uuid, buyer_uuid, transaction_type, item_material,
                 amount, price_per_unit, total_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Instant now = Instant.now();
            if (orderId != null) {
                stmt.setLong(1, orderId);
            } else {
                stmt.setNull(1, java.sql.Types.BIGINT);
            }
            stmt.setString(2, sellerUuid.toString());
            stmt.setString(3, buyerUuid.toString());
            stmt.setString(4, type.name());
            stmt.setString(5, itemMaterial);
            stmt.setInt(6, amount);
            stmt.setDouble(7, pricePerUnit);
            stmt.setDouble(8, totalPrice);
            stmt.executeUpdate();
            long txId = -1;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    txId = keys.getLong(1);
                }
            }
            return new CreateTransactionResult(txId, now);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to record market transaction", e);
            throw new IllegalStateException("Transaction insert failed", e);
        }
    }

    /** History rows involving a player (buyer or seller), newest first. */
    public List<TransactionRecord> findTransactionsForPlayer(UUID playerUuid, int limit, int offset) {
        String sql = """
            SELECT * FROM market_transactions
            WHERE seller_uuid = ? OR buyer_uuid = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;
        List<TransactionRecord> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setInt(3, limit);
            stmt.setInt(4, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapTransaction(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load transactions for " + playerUuid, e);
        }
        return result;
    }

    // ==================================================================
    // player_profiles - balance column
    // ==================================================================

    /** Creates a default profile row if it does not exist yet. */
    public void ensureProfile(UUID playerUuid) {
        String sql = "INSERT OR IGNORE INTO player_profiles (player_uuid) VALUES (?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to ensure player_profiles row for " + playerUuid, e);
        }
    }

    /** Reads a player's balance; 0.0 if the profile does not exist yet. */
    public double getBalance(UUID playerUuid) {
        String sql = "SELECT balance FROM player_profiles WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("balance");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read balance for " + playerUuid, e);
        }
        return 0.0;
    }

    /** Kills / deaths / shards for a player (0s if no profile row yet). */
    public PlayerStats getPlayerStats(UUID playerUuid) {
        String sql = "SELECT kills, deaths, shards FROM player_profiles WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(rs.getInt("kills"), rs.getInt("deaths"), rs.getInt("shards"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to read stats for " + playerUuid, e);
        }
        return new PlayerStats(0, 0, 0);
    }

    /** Kills / deaths / shards snapshot for the HUD. */
    public record PlayerStats(int kills, int deaths, int shards) {
    }

    /**
     * Atomically applies a signed delta to a player's balance, never letting
     * it go below zero. Returns the new balance, or empty if insufficient.
     */
    public OptionalDouble adjustBalance(UUID playerUuid, double delta) {
        ensureProfile(playerUuid);
        String sql = "UPDATE player_profiles SET balance = balance + ? WHERE player_uuid = ? AND balance + ? >= 0";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, delta);
            stmt.setString(2, playerUuid.toString());
            stmt.setDouble(3, delta);
            int updated = stmt.executeUpdate();
            if (updated == 0) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(getBalance(playerUuid));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to adjust balance for " + playerUuid, e);
            return OptionalDouble.empty();
        }
    }

    // ==================================================================
    // expiration sweeps
    // ==================================================================

    /** ACTIVE orders whose expiration timestamp is in the past. */
    public List<MarketOrder> findActiveExpiredOrders(Instant now) {
        String sql = "SELECT * FROM market_orders WHERE status = 'ACTIVE' AND expires_at < ?";
        List<MarketOrder> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, now.toEpochMilli());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapOrder(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load expired active orders", e);
        }
        return result;
    }

    /** Unclaimed escrow rows past their claim window (safe to delete). */
    public List<EscrowEntry> findExpiredUnclaimedEscrow(Instant now) {
        String sql = "SELECT * FROM market_escrow WHERE claimed = 0 AND expires_at < ?";
        List<EscrowEntry> result = new ArrayList<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, now.toEpochMilli());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(mapEscrow(rs));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to load expired unclaimed escrow", e);
        }
        return result;
    }

    /** Permanently deletes a list of escrow rows (post-expiry cleanup). */
    public void deleteEscrowEntries(List<Long> escrowIds) {
        if (escrowIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(escrowIds.size(), "?"));
        String sql = "DELETE FROM market_escrow WHERE escrow_id IN (" + placeholders + ")";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < escrowIds.size(); i++) {
                stmt.setLong(i + 1, escrowIds.get(i));
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete escrow entries", e);
        }
    }

    // ==================================================================
    // mappers & helpers
    // ==================================================================

    private MarketOrder mapOrder(ResultSet rs) throws SQLException {
        return new MarketOrder(
                rs.getLong("order_id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                OrderType.valueOf(rs.getString("order_type")),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("item_material"),
                rs.getString("item_serialized"),
                rs.getInt("amount"),
                rs.getInt("remaining_amount"),
                rs.getDouble("price_per_unit"),
                rs.getDouble("total_price"),
                parseInstant(rs, "created_at"),
                parseInstant(rs, "expires_at"),
                parseInstantNullable(rs, "fulfilled_at"),
                rs.getBoolean("claimed"));
    }

    private EscrowEntry mapEscrow(ResultSet rs) throws SQLException {
        long sourceOrderId = rs.getLong("source_order_id");
        return new EscrowEntry(
                rs.getLong("escrow_id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.wasNull() ? null : sourceOrderId,
                EscrowEntry.Reason.valueOf(rs.getString("reason")),
                rs.getString("item_material"),
                rs.getString("item_serialized"),
                rs.getInt("amount"),
                parseInstant(rs, "created_at"),
                parseInstant(rs, "expires_at"),
                rs.getBoolean("claimed"),
                parseInstantNullable(rs, "claimed_at"));
    }

    private TransactionRecord mapTransaction(ResultSet rs) throws SQLException {
        long orderId = rs.getLong("order_id");
        return new TransactionRecord(
                rs.getLong("transaction_id"),
                rs.wasNull() ? null : orderId,
                UUID.fromString(rs.getString("seller_uuid")),
                UUID.fromString(rs.getString("buyer_uuid")),
                TransactionRecord.Type.valueOf(rs.getString("transaction_type")),
                rs.getString("item_material"),
                rs.getInt("amount"),
                rs.getDouble("price_per_unit"),
                rs.getDouble("total_price"),
                parseInstant(rs, "created_at"));
    }

    private OptionalDouble querySinglePrice(String sql) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                double value = rs.getDouble(1);
                // wasNull() must be called AFTER reading the column; calling it
                // before getDouble() throws "column -1 out of bounds" on SQLite.
                if (!rs.wasNull()) {
                    return OptionalDouble.of(value);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to query market price", e);
        }
        return OptionalDouble.empty();
    }

    private static final DateTimeFormatter SQLITE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Instant parseInstant(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) {
            return Instant.now();
        }
        return toInstant(raw.toString());
    }

    private static Instant parseInstantNullable(ResultSet rs, String column) throws SQLException {
        Object raw = rs.getObject(column);
        if (raw == null) {
            return null;
        }
        String s = raw.toString();
        if (s.isEmpty()) {
            return null;
        }
        return toInstant(s);
    }

    private static Instant toInstant(String raw) {
        try {
            return Instant.ofEpochMilli(Long.parseLong(raw));
        } catch (NumberFormatException ignored) {
            // fall through to date parsing
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            // fall through to SQLite format
        }
        try {
            return LocalDateTime.parse(raw, SQLITE_FORMAT).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    /** Stores the serialized (PDC-tagged) item payload for an order. */
    public void updateOrderSerialized(long orderId, String serialized) {
        String sql = "UPDATE market_orders SET item_serialized = ? WHERE order_id = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, serialized);
            stmt.setLong(2, orderId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to update serialized payload for order " + orderId, e);
        }
    }
}
