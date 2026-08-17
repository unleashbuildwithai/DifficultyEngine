package net.yourserver.coreengine.util;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.NamespacedKey;

/**
 * Central registry of {@link NamespacedKey}s used to tag GUI-control items
 * (buttons, page arrows, listing icons, etc.) via
 * {@link org.bukkit.persistence.PersistentDataContainer} so click handlers
 * can identify what was clicked without relying on fragile display-name/lore
 * string matching.
 */
public final class PDCKeys {

    private PDCKeys() {
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey(CoreEngine.getInstance(), name);
    }

    /** Marks an ItemStack as a GUI control button, value = control id string. */
    public static NamespacedKey guiAction() {
        return key("gui_action");
    }

    /** The market_orders.order_id a listing icon represents (stored as LONG). */
    public static NamespacedKey orderId() {
        return key("market_order_id");
    }

    /** The market_escrow.escrow_id an escrow icon represents (stored as LONG). */
    public static NamespacedKey escrowId() {
        return key("market_escrow_id");
    }

    /** The market_transactions.transaction_id a history row icon represents. */
    public static NamespacedKey transactionId() {
        return key("market_transaction_id");
    }

    /** Marks a currently-displayed page number on pagination buttons (INTEGER). */
    public static NamespacedKey pageNumber() {
        return key("market_page_number");
    }

    /** The player_homes.home_slot a homes-GUI icon represents (stored as INTEGER). */
    public static NamespacedKey homeSlot() {
        return key("home_slot");
    }

    /** Marks the region-selection stick (stored as BYTE/BOOLEAN). */
    public static NamespacedKey regionStick() {
        return key("region_stick");
    }

    /** The /settings toggle tile a click represents (stored as STRING = Setting name). */
    public static NamespacedKey settingKey() {
        return key("setting_key");
    }

    /** The /settings page index a pagination button represents (stored as INTEGER). */
    public static NamespacedKey settingsPage() {
        return key("settings_page");
    }

    /** Marks the admin Market NPC spawn egg (stored as BYTE/BOOLEAN). */
    public static NamespacedKey marketNpcEgg() {
        return key("market_npc_egg");
    }

    /** Marks a spawned Market NPC (stored as BYTE/BOOLEAN). */
    public static NamespacedKey marketNpc() {
        return key("market_npc");
    }
}
