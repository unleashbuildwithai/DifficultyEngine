package net.yourserver.coreengine.market;

/**
 * Outcome codes returned by {@link MarketManager} operations so commands and
 * GUI handlers can map them to user-facing messages.
 */
public enum MarketResult {
    SUCCESS,
    NOT_FOUND,
    NOT_ACTIVE,
    OWN_ORDER,
    EMPTY_HAND,
    INVALID_MATERIAL,
    INVALID_AMOUNT,
    INVALID_PRICE,
    INSUFFICIENT_FUNDS,
    INVENTORY_FULL,
    INVENTORY_MISMATCH,
    CAP_REACHED,
    NOT_ACCEPTED,
    EXPIRED
}
