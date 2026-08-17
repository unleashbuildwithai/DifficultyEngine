package net.yourserver.coreengine.market;

import net.yourserver.coreengine.config.ConfigManager;
import net.yourserver.coreengine.database.dao.MarketDao;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Periodic maintenance task implementing the Market's expiration logic:
 * <ul>
 *   <li><b>SELL listings</b> still ACTIVE past their 24-hour window are
 *       expired and the item is migrated into the owner's Unclaimed Escrow
 *       inbox ({@link EscrowEntry.Reason#SELL_EXPIRED}).</li>
 *   <li><b>Escrow entries</b> past their 90-day claim window are permanently
 *       deleted (auto-cleanup).</li>
 *   <li><b>BUY orders</b> past their window are expired (their escrow funds
 *       remain claimable via the cancellation/refund path - the order row is
 *       marked EXPIRED and the owner can reclaim through My Active Buy
 *       Orders / escrow management).</li>
 * </ul>
 * Runs on the Bukkit main thread via the scheduler at the configured interval.
 */
public class MarketExpirationTask extends BukkitRunnable {

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final MarketDao dao;
    private final MarketManager marketManager;

    public MarketExpirationTask(JavaPlugin plugin, ConfigManager config, MarketDao dao,
                                MarketManager marketManager) {
        this.plugin = plugin;
        this.config = config;
        this.dao = dao;
        this.marketManager = marketManager;
    }

    @Override
    public void run() {
        try {
            sweepExpiredOrders();
            sweepExpiredEscrow();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Market expiration sweep failed", e);
        }
    }

    /** Migrates expired ACTIVE sell/buy orders to escrow or EXPIRED state. */
    private void sweepExpiredOrders() {
        Instant now = Instant.now();
        List<MarketOrder> expired = dao.findActiveExpiredOrders(now);
        for (MarketOrder order : expired) {
            if (order.getOrderType() == OrderType.SELL) {
                // Item goes to the seller's Unclaimed Escrow inbox.
                dao.insertEscrow(order.getPlayerUuid(), order.getOrderId(),
                        EscrowEntry.Reason.SELL_EXPIRED, order.getItemMaterial(),
                        order.getItemSerialized(), order.getRemainingAmount(),
                        now.plus(config.getEscrowClaimExpirationDays(), ChronoUnit.DAYS));
                if (dao.expireOrder(order.getOrderId())) {
                    plugin.getLogger().info("Expired SELL listing " + order.getOrderId()
                            + " for " + order.getPlayerName() + " -> unclaimed escrow.");
                }
            } else if (order.getOrderType() == OrderType.BUY) {
                // Escrow funds remain parked on the order; mark EXPIRED so the
                // owner can see it in their buy-order history and reclaim via
                // the escrow/refund path.
                if (dao.expireOrder(order.getOrderId())) {
                    plugin.getLogger().info("Expired BUY order " + order.getOrderId()
                            + " for " + order.getPlayerName() + " (escrow parked).");
                }
            }
        }
    }

    /** Permanently deletes unclaimed escrow entries past their claim window. */
    private void sweepExpiredEscrow() {
        Instant now = Instant.now();
        List<EscrowEntry> expired = dao.findExpiredUnclaimedEscrow(now);
        if (expired.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        for (EscrowEntry entry : expired) {
            ids.add(entry.getEscrowId());
        }
        dao.deleteEscrowEntries(ids);
        plugin.getLogger().info("Purged " + ids.size()
                + " expired escrow entries (90-day claim window passed).");
    }

    /** Starts the repeating task; returns this task for tracking. */
    public MarketExpirationTask start() {
        long intervalTicks = config.getExpirationTaskIntervalMinutes() * 60L * 20L;
        this.runTaskTimer(plugin, intervalTicks, intervalTicks);
        return this;
    }
}
