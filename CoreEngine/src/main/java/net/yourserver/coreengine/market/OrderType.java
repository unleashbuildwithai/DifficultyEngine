package net.yourserver.coreengine.market;

/**
 * The two sides of the Market's order book.
 */
public enum OrderType {
    /** Player is offering an item stack for sale; item is held in escrow. */
    SELL,
    /** Player wants to buy an item; money is held in escrow. */
    BUY;

    /**
     * Parses the value stored in the {@code order_type} database column back
     * into an {@link OrderType}.
     */
    public static OrderType fromDb(String value) {
        return OrderType.valueOf(value.trim().toUpperCase());
    }
}
