package net.yourserver.coreengine.market;

/**
 * Lifecycle state of a {@link MarketOrder}.
 */
public enum OrderStatus {
    /** Currently live and matchable/purchasable. */
    ACTIVE,
    /** Cancelled by the owning player before completion. */
    CANCELLED,
    /** Fully bought out (SELL) or fully matched (BUY). */
    FULFILLED,
    /** Passed its expiration timestamp without being fulfilled/cancelled. */
    EXPIRED;

    public static OrderStatus fromDb(String value) {
        return OrderStatus.valueOf(value.trim().toUpperCase());
    }
}
