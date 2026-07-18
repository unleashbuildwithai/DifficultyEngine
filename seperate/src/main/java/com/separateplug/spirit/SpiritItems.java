package com.separateplug.spirit;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * SpiritItems — Factory for all custom items in the SeparatePlug.
 *
 * ── Spirit Staves ────────────────────────────────────────────────────────────
 *  Each staff uses the base Material listed in SpiritType with a CustomModelData
 *  integer that your resource pack must map to the ghost-sword model.
 *  Items are identified via PersistentDataContainer — NOT by display name —
 *  so name changes will not confuse them.
 *
 * ── Ghost Sword (Stun Sword) ─────────────────────────────────────────────────
 *  IRON_SWORD with CustomModelData 1001.
 *  The resource pack replaces the IRON_SWORD model entirely when CMD = 1001:
 *    - Blade  = large grey ghost body (see reference picture)
 *    - Handle = dark hilt held at the ghost's base
 *  On hit: 3-second stun (Slowness 255 + flashing Blindness).
 *  Recipe: NETHER_STAR + QUARTZ_BLOCK (shapeless, crafting table).
 */
public class SpiritItems {

    // ── PDC key constants ─────────────────────────────────────────────────────
    /** PDC key stored on spirit staves: value = SpiritType.name() */
    public static final String PDC_SPIRIT_STAFF = "spirit_staff_type";
    /** PDC key stored on the stun sword: value = "ghost_sword" */
    public static final String PDC_STUN_SWORD   = "stun_sword";

    private final NamespacedKey staffKey;
    private final NamespacedKey stunKey;

    public SpiritItems(JavaPlugin plugin) {
        this.staffKey = new NamespacedKey(plugin, PDC_SPIRIT_STAFF);
        this.stunKey  = new NamespacedKey(plugin, PDC_STUN_SWORD);
    }

    // ── Spirit Staff ──────────────────────────────────────────────────────────

    /**
     * Creates a spirit staff ItemStack for the given SpiritType.
     * The item's actual appearance depends on the resource pack CustomModelData.
     */
    public ItemStack buildSpiritStaff(SpiritType type) {
        ItemStack item = new ItemStack(type.baseMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(type.displayName);
        meta.setLore(List.of(
            type.color + "\"" + type.description + "\"",
            "§8──────────────────────",
            "§7Right-click §8→ §" + type.color.charAt(1) + "Cast " + type.name().charAt(0)
                + type.name().substring(1).toLowerCase() + " spell",
            "§8Bound spirit — no runes required."
        ));
        meta.setCustomModelData(type.customModelData);
        meta.setUnbreakable(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        // Tag with PDC
        meta.getPersistentDataContainer().set(staffKey, PersistentDataType.STRING, type.name());

        item.setItemMeta(meta);
        return item;
    }

    /** @return true if the given ItemStack is a spirit staff of any type. */
    public boolean isSpiritStaff(ItemStack item) {
        return getSpiritTypeFromItem(item) != null;
    }

    /** @return the SpiritType of a spirit staff, or null if not a staff. */
    public SpiritType getSpiritTypeFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(staffKey, PersistentDataType.STRING)) return null;
        String raw = pdc.get(staffKey, PersistentDataType.STRING);
        try { return SpiritType.valueOf(raw); }
        catch (IllegalArgumentException e) { return null; }
    }

    // ── Ghost / Stun Sword ────────────────────────────────────────────────────

    /**
     * Creates the Ghost Sword (Stun Sword).
     *
     * In-game it appears as an IRON_SWORD but the resource pack replaces its
     * model entirely (CustomModelData 1001) with the ghost-sword design.
     *
     * Craft: NETHER_STAR + QUARTZ_BLOCK (shapeless, any crafting table).
     */
    public ItemStack buildStunSword() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName("§f§lGhost Sword");
        meta.setLore(List.of(
            "§8\"A spectral blade forged from the",
            "§8 essence of a wandering ghost.\"",
            "§8──────────────────────",
            "§7On hit: §fStun §83s §7(§8Slowness + §fBlinding flash§8)",
            "§7Combat bar full: §c§lBONUS STUN DAMAGE",
            "§8Craft: NETHER_STAR + QUARTZ_BLOCK"
        ));
        meta.setCustomModelData(1001);
        meta.setUnbreakable(true);
        meta.addItemFlags(
            org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE,
            org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES
        );

        // Tag with PDC
        meta.getPersistentDataContainer().set(stunKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** @return true if the ItemStack is the Ghost / Stun Sword. */
    public boolean isStunSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null
            && meta.getPersistentDataContainer().has(stunKey, PersistentDataType.BYTE);
    }
}
