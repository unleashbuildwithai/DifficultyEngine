package net.yourserver.coreengine.market;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable, permanent record of a completed market transaction, stored in
 * {@code market_transactions} and shown in the Transaction History GUI
 * (Slot 49) - split by sub-page into Buy History and Sell History depending
 * on whether the viewing player is the {@code buyerUuid} or {@code sellerUuid}.
 */
public class TransactionRecord {

    public enum Type {
        /** A Sell Listing was instantly bought out by another player. */
        SELL_PURCHASE,
        /** A Buy Order was fulfilled by another player supplying the item. */
        BUY_FULFILL,
        /** Item permanently destroyed via the Server Quick-Sell Floor. */
        QUICK_SELL
    }

    private final long transactionId;
    private final Long orderId;
    private final UUID sellerUuid;
    private final UUID buyerUuid;
    private final Type type;
    private final String itemMaterial;
    private final int amount;
    private final double pricePerUnit;
    private final double totalPrice;
    private final Instant createdAt;

    public TransactionRecord(long transactionId, Long orderId, UUID sellerUuid, UUID buyerUuid,
                              Type type, String itemMaterial, int amount, double pricePerUnit,
                              double totalPrice, Instant createdAt) {
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.sellerUuid = sellerUuid;
        this.buyerUuid = buyerUuid;
        this.type = type;
        this.itemMaterial = itemMaterial;
        this.amount = amount;
        this.pricePerUnit = pricePerUnit;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public UUID getSellerUuid() {
        return sellerUuid;
    }

    public UUID getBuyerUuid() {
        return buyerUuid;
    }

    public Type getType() {
        return type;
    }

    public String getItemMaterial() {
        return itemMaterial;
    }

    public int getAmount() {
        return amount;
    }

    public double getPricePerUnit() {
        return pricePerUnit;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
