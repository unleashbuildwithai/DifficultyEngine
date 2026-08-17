package net.yourserver.coreengine.market;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Converts {@link ItemStack}s to/from a Base64-encoded string suitable for
 * storage in a SQLite {@code TEXT} column.
 * <p>
 * Uses {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}
 * which round-trip the item's full NBT tag (including any custom
 * {@link org.bukkit.persistence.PersistentDataContainer} entries, enchants,
 * custom model data, lore, etc.) byte-for-byte. This is the anti-duplication
 * backbone of the market: an item is serialized here and physically removed
 * from the world the instant it is listed, and only reconstructed (via
 * {@link #deserialize(String)}) at the moment of delivery.
 */
public final class ItemSerialization {

    private ItemSerialization() {
    }

    /**
     * Serializes a single {@link ItemStack} (amount included) to a Base64
     * string.
     */
    public static String serialize(ItemStack itemStack) {
        if (itemStack == null) {
            throw new IllegalArgumentException("Cannot serialize a null ItemStack");
        }
        byte[] bytes = itemStack.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Reconstructs the exact {@link ItemStack} (including amount and all
     * NBT/PDC data) from a Base64 string previously produced by
     * {@link #serialize(ItemStack)}.
     */
    public static ItemStack deserialize(String base64) {
        if (base64 == null || base64.isEmpty()) {
            throw new IllegalArgumentException("Cannot deserialize a null/empty item string");
        }
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ItemStack.deserializeBytes(bytes);
    }

    /**
     * Convenience helper that produces a single-unit copy of the given
     * ItemStack (amount forced to 1) - used for hover-lore "unit price"
     * display and for building representative GUI icons for a stacked
     * listing without mutating the original.
     */
    public static ItemStack singleUnitCopy(ItemStack itemStack) {
        ItemStack copy = itemStack.clone();
        copy.setAmount(1);
        return copy;
    }
}
