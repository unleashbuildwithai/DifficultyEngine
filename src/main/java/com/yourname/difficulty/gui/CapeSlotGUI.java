package com.yourname.difficulty.gui;

import com.yourname.difficulty.skills.SkillCapeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * CapeSlotGUI — A 27-slot "Cape Wardrobe" GUI accessed via /cape.
 *
 * Layout:
 *   Row 0: glass border + header label in centre
 *   Row 1: glass, glass, [info], glass, [CAPE SLOT], glass, [info], glass, glass
 *   Row 2: glass border
 *
 * The CAPE SLOT (slot 13) mirrors the player's elytra/chest slot.
 *   • If the player has a cape equipped → shown in slot 13
 *   • If empty → a placeholder "drag cape here" glass is shown
 *
 * All click logic is handled by {@link CapeSlotGUIListener}.
 */
public class CapeSlotGUI {

    public static final String TITLE       = "§8✦ §5Cape Wardrobe §8✦";
    public static final int    SIZE        = 27;
    /** The inventory slot that acts as the cape equip slot. */
    public static final int    CAPE_SLOT   = 13;

    private final SkillCapeManager capeManager;

    public CapeSlotGUI(SkillCapeManager capeManager) {
        this.capeManager = capeManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fill all with glass
        ItemStack glass = filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        // Header label (slot 4)
        inv.setItem(4, label());

        // Info items (slots 11 and 15)
        inv.setItem(11, infoItem());
        inv.setItem(15, infoItem());

        // Cape slot — show current elytra if it's a cape, else empty marker
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && capeManager.isAnyCape(chestplate)) {
            inv.setItem(CAPE_SLOT, chestplate.clone());
        } else {
            inv.setItem(CAPE_SLOT, emptySlot());
        }

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) { meta.setDisplayName("§8"); glass.setItemMeta(meta); }
        return glass;
    }

    private ItemStack label() {
        ItemStack item = new ItemStack(Material.FEATHER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ §dCape Wardrobe §5✦");
            meta.setLore(List.of(
                "§7Place a skill cape in the",
                "§7centre slot to equip it.",
                "§8" + "─".repeat(22),
                "§7Capes are §bElytra §7— they appear",
                "§7on your §bback §7like a real cape.",
                "§8Use a resource pack for",
                "§8custom cape textures."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack emptySlot() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7[ Cape Slot ]");
            meta.setLore(List.of(
                "§8Drag a skill cape here",
                "§8to equip it on your back."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5→ Cape Slot ←");
            item.setItemMeta(meta);
        }
        return item;
    }
}
