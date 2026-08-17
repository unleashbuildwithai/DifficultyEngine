package net.yourserver.coreengine.market;

import java.time.Instant;
import java.util.UUID;

/**
 * A single claimable inbox entry in the {@code market_escrow} table.
 * <p>
 * Created in two scenarios:
 * <ol>
 *     <li>{@link Reason#SELL_EXPIRED} - a SELL listing hit its 24h expiration
 *     without selling; the item is returned here for the seller to claim.</li>
 *     <li>{@link Reason#BUY_FULFILLED} - a BUY order was fulfilled by another
 *     player; the purchased item lands here for up to 90 days.</li>
 *     <li>{@link Reason#INVENTORY_FULL_FALLBACK} - an instant-delivery
 *     purchase (Sell Listing buyout) could not be placed directly into the
 *     buyer's inventory (full inventory, or buyer offline/disconnected mid
 *     transaction), so it safely falls back to escrow instead of being lost
 *     or duplicated.</li>
 * </ol>
 */
public class EscrowEntry {

    public enum Reason {
        SELL_EXPIRED,
        BUY_FULFILLED,
        INVENTORY_FULL_FALLBACK
    }

    private long escrowId;
    private final UUID ownerUuid;
    private final Long sourceOrderId;
    private final Reason reason;
    private final String itemMaterial;
    private final String itemSerialized;
    private final int amount;
    private final Instant createdAt;
    private Instant expiresAt;
    private boolean claimed;
    private Instant claimedAt;

    public EscrowEntry(long escrowId, UUID ownerUuid, Long sourceOrderId, Reason reason,
                        String itemMaterial, String itemSerialized, int amount,
                        Instant createdAt, Instant expiresAt, boolean claimed, Instant claimedAt) {
        this.escrowId = escrowId;
        this.ownerUuid = ownerUuid;
        this.sourceOrderId = sourceOrderId;
        this.reason = reason;
        this.itemMaterial = itemMaterial;
        this.itemSerialized = itemSerialized;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.claimed = claimed;
        this.claimedAt = claimedAt;
    }

    public long getEscrowId() {
        return escrowId;
    }

    public void setEscrowId(long escrowId) {
        this.escrowId = escrowId;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Long getSourceOrderId() {
        return sourceOrderId;
    }

    public Reason getReason() {
        return reason;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
