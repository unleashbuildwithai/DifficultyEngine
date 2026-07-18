package com.yourname.difficulty.gui;

import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.skills.SkillCapeManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * CapeSlotGUI — A 27-slot "Cape Wardrobe" GUI accessed via /mycape.
 *
 * Layout (3 rows × 9):
 *   Row 0: glass border with header label at slot 4
 *   Row 1: [g][armor_lbl][ARMOR=11][g][SEP][g][CAPE=15][cape_lbl][g]
 *   Row 2: glass border
 *
 * ── TWO independent slots ────────────────────────────────────────────────────
 *  ARMOR SLOT (slot 11) mirrors the player's chestplate armour.
 *    Click with item in cursor  → equips item as chestplate (old one returned)
 *    Click with empty cursor    → takes chestplate to cursor
 *
 *  CAPE SLOT (slot 15) reads from CapeDataManager (NOT the chestplate slot).
 *    Players can now wear BOTH a chestplate and a skill cape simultaneously.
 *    The cape's visual (particles + back label) is still shown by CapeVisualTask.
 */
public class CapeSlotGUI {

    public static final String TITLE      = "§8✦ §5Cape Wardrobe §8✦";
    public static final int    SIZE       = 27;
    /** Slot for the player's chestplate armour (independent of the cape). */
    public static final int    ARMOR_SLOT = 11;
    /** Slot for the equipped skill cape (stored in CapeDataManager). */
    public static final int    CAPE_SLOT  = 15;

    private final SkillCapeManager capeManager;
    private final CapeDataManager  capeDataManager;

    public CapeSlotGUI(SkillCapeManager capeManager, CapeDataManager capeDataManager) {
        this.capeManager     = capeManager;
        this.capeDataManager = capeDataManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fill all with glass
        ItemStack glass = filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        // Header label (slot 4)
        inv.setItem(4, label());

        // Armor slot — show current chestplate or empty marker
        ItemStack chestplate = player.getInventory().getChestplate();
        inv.setItem(ARMOR_SLOT, (chestplate != null && !chestplate.getType().isAir())
            ? chestplate.clone() : emptyArmorSlot());

        // Separator
        inv.setItem(13, separator());

        // Cape slot — show cape from CapeDataManager or empty marker
        ItemStack cape = capeDataManager.getEquippedCape(player.getUniqueId());
        inv.setItem(CAPE_SLOT, (cape != null) ? cape.clone() : emptyCapeSlot());

        // Labels flanking each slot
        inv.setItem(10, armorInfoItem());
        inv.setItem(12, armorInfoItem());
        inv.setItem(14, capeInfoItem());
        inv.setItem(16, capeInfoItem());

        player.openInventory(inv);
    }

    // ── Item builders ─────────────────────────────────────────────────────────

    private ItemStack filler() {
        ItemStack g = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = g.getItemMeta();
        if (m != null) { m.setDisplayName("§8"); g.setItemMeta(m); }
        return g;
    }

    private ItemStack label() {
        ItemStack it = new ItemStack(Material.FEATHER);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§5✦ §dCape Wardrobe §5✦");
            m.setLore(List.of(
                "§7Wear §barmour §7AND a §5cape §7at the same time!",
                "§8" + "─".repeat(28),
                "§e◀ Armour slot  §8|  §5Cape slot ▶",
                "§8Each slot is independent."
            ));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack separator() {
        ItemStack it = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§8│"); it.setItemMeta(m); }
        return it;
    }

    private ItemStack emptyArmorSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7[ Armour Slot ]");
            m.setLore(List.of("§8Click to place a chestplate here.", "§8Any chestplate is accepted."));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack emptyCapeSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7[ Cape Slot ]");
            m.setLore(List.of("§8Drag a skill cape here to equip it.", "§8Requires Level 99 in the cape's skill."));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack armorInfoItem() {
        ItemStack it = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§e→ Armour Slot ←"); it.setItemMeta(m); }
        return it;
    }

    private ItemStack capeInfoItem() {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§5→ Cape Slot ←"); it.setItemMeta(m); }
        return it;
    }
}
