package net.yourserver.coreengine.market;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory representation of a single row in the {@code market_orders}
 * table - either an active/completed SELL listing or BUY order.
 * <p>
 * {@code itemSerialized} stores the Base64-encoded, PDC/NBT-preserving byte
 * representation of the exact {@link org.bukkit.inventory.ItemStack} that was
 * removed from the world when the order was placed (see
 * {@link ItemSerialization}).
 */
public class MarketOrder {

    private long orderId;
    private final UUID playerUuid;
    private final String playerName;
    private final OrderType orderType;
    private OrderStatus status;
    private final String itemMaterial;
    private final String itemSerialized;
    private final int amount;
    private int remainingAmount;
    private final double pricePerUnit;
    private final double totalPrice;
    private final Instant createdAt;
    private Instant expiresAt;
    private Instant fulfilledAt;
    private boolean claimed;

    public MarketOrder(long orderId, UUID playerUuid, String playerName, OrderType orderType,
                        OrderStatus status, String itemMaterial, String itemSerialized, int amount,
                        int remainingAmount, double pricePerUnit, double totalPrice,
                        Instant createdAt, Instant expiresAt, Instant fulfilledAt, boolean claimed) {
        this.orderId = orderId;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.orderType = orderType;
        this.status = status;
        this.itemMaterial = itemMaterial;
        this.itemSerialized = itemSerialized;
        this.amount = amount;
        this.remainingAmount = remainingAmount;
        this.pricePerUnit = pricePerUnit;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.fulfilledAt = fulfilledAt;
        this.claimed = claimed;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getItemMaterial() {
        return itemMaterial;
    }

    public String getItemSerialized() {
        return itemSerialized;
    }

    public int getAmount() {
        return amount;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(int remainingAmount) {
        this.remainingAmount = remainingAmount;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public void setFulfilledAt(Instant fulfilledAt) {
        this.fulfilledAt = fulfilledAt;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public boolean isActive() {
        return status == OrderStatus.ACTIVE;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
