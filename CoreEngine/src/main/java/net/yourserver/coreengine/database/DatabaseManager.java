package net.yourserver.coreengine.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Owns the HikariCP connection pool and schema for the CoreEngine plugin.
 * <p>
 * Module 1 (Dynamic Market Engine &amp; Order Book) extends the base schema with:
 * <ul>
 *     <li>{@code status} / matching columns on {@code market_orders}</li>
 *     <li>{@code market_escrow} - claimable inbox for expired sell listings
 *     and items delivered from fulfilled buy orders</li>
 *     <li>{@code market_transactions} - permanent buy/sell history log</li>
 * </ul>
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        HikariConfig config = new HikariConfig();
        File dbFile = new File(plugin.getDataFolder(), "core_engine.db");

        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setPoolName("CoreEngineHikariPool");

        this.dataSource = new HikariDataSource(config);
        applyPragmas();
        createTables();
    }

    private void applyPragmas() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // WAL mode allows concurrent readers while a writer commits, which
            // significantly reduces "database is locked" errors under load.
            stmt.execute("PRAGMA journal_mode=WAL;");
            stmt.execute("PRAGMA synchronous=NORMAL;");
            stmt.execute("PRAGMA foreign_keys=ON;");
            stmt.execute("PRAGMA busy_timeout=5000;");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to apply SQLite PRAGMAs: " + e.getMessage());
        }
    }

    private void createTables() {
        String queryMarketOrders = """
            CREATE TABLE IF NOT EXISTS market_orders (
                order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(32) NOT NULL,
                order_type VARCHAR(10) NOT NULL, -- 'SELL' or 'BUY'
                status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, CANCELLED, FULFILLED, EXPIRED
                item_material VARCHAR(64) NOT NULL,
                item_serialized TEXT NOT NULL,
                amount INTEGER NOT NULL,
                remaining_amount INTEGER NOT NULL,
                price_per_unit DOUBLE NOT NULL,
                total_price DOUBLE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                fulfilled_at TIMESTAMP,
                claimed BOOLEAN DEFAULT 0
            );
        """;

        String queryMarketEscrow = """
            CREATE TABLE IF NOT EXISTS market_escrow (
                escrow_id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid VARCHAR(36) NOT NULL,
                source_order_id INTEGER,
                reason VARCHAR(32) NOT NULL, -- SELL_EXPIRED, BUY_FULFILLED, INVENTORY_FULL_FALLBACK
                item_material VARCHAR(64) NOT NULL,
                item_serialized TEXT NOT NULL,
                amount INTEGER NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                claimed BOOLEAN DEFAULT 0,
                claimed_at TIMESTAMP,
                FOREIGN KEY (source_order_id) REFERENCES market_orders(order_id)
            );
        """;

        String queryMarketTransactions = """
            CREATE TABLE IF NOT EXISTS market_transactions (
                transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER,
                seller_uuid VARCHAR(36) NOT NULL,
                buyer_uuid VARCHAR(36) NOT NULL,
                transaction_type VARCHAR(16) NOT NULL, -- SELL_PURCHASE, BUY_FULFILL, QUICK_SELL
                item_material VARCHAR(64) NOT NULL,
                amount INTEGER NOT NULL,
                price_per_unit DOUBLE NOT NULL,
                total_price DOUBLE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (order_id) REFERENCES market_orders(order_id)
            );
        """;

        String queryPlayerHomes = """
            CREATE TABLE IF NOT EXISTS player_homes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                home_slot INTEGER NOT NULL,
                world_name VARCHAR(64) NOT NULL,
                x DOUBLE NOT NULL,
                y DOUBLE NOT NULL,
                z DOUBLE NOT NULL,
                yaw FLOAT NOT NULL,
                pitch FLOAT NOT NULL,
                name TEXT DEFAULT '',
                UNIQUE(player_uuid, home_slot)
            );
        """;

        String queryPlayerProfiles = """
            CREATE TABLE IF NOT EXISTS player_profiles (
                player_uuid VARCHAR(36) PRIMARY KEY,
                rank_tier INTEGER DEFAULT 0, -- 0=Default, 1=Member, 2=Member+, 3=Member++
                balance DOUBLE DEFAULT 0.0,
                shards INTEGER DEFAULT 0,
                kills INTEGER DEFAULT 0,
                deaths INTEGER DEFAULT 0,
                difficulty_mode VARCHAR(16) DEFAULT 'NORMAL',
                public_chat_mode VARCHAR(16) DEFAULT 'ON'
            );
        """;

        String indexOrdersStatus = "CREATE INDEX IF NOT EXISTS idx_orders_status ON market_orders(status, order_type);";
        String indexOrdersPlayer = "CREATE INDEX IF NOT EXISTS idx_orders_player ON market_orders(player_uuid);";
        String indexEscrowOwner = "CREATE INDEX IF NOT EXISTS idx_escrow_owner ON market_escrow(owner_uuid, claimed);";
        String indexTxSeller = "CREATE INDEX IF NOT EXISTS idx_tx_seller ON market_transactions(seller_uuid);";
        String indexTxBuyer = "CREATE INDEX IF NOT EXISTS idx_tx_buyer ON market_transactions(buyer_uuid);";

        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(queryMarketOrders);
            stmt.execute(queryMarketEscrow);
            stmt.execute(queryMarketTransactions);
            stmt.execute(queryPlayerHomes);
            // Migration for pre-rename installs: add the homes `name` column.
            // Ignored when the column already exists (fresh installs create it above).
            try {
                stmt.execute("ALTER TABLE player_homes ADD COLUMN name TEXT DEFAULT ''");
            } catch (SQLException ignored) {
                // Already present.
            }
            stmt.execute(queryPlayerProfiles);
            stmt.execute(indexOrdersStatus);
            stmt.execute(indexOrdersPlayer);
            stmt.execute(indexEscrowOwner);
            stmt.execute(indexTxSeller);
            stmt.execute(indexTxBuyer);
            plugin.getLogger().info("Database tables initialized successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize database tables!");
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
