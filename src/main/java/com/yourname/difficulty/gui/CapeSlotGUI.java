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
 * CapeSlotGUI — Cape Wardrobe GUI accessed via /cape.
 *
 * Layout (27 slots, 3 rows):
 *   Row 0: glass border + header at slot 4
 *   Row 1: [g][g][info][g][CAPE=13][g][info][g][g]
 *   Row 2: glass border
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  The CAPE SLOT (slot 13) mirrors the player's chestplate slot — capes are
 *  worn in the chestplate slot so they render visually on the player's back.
 *
 *  • If a cape is equipped → shown in slot 13.  Click with empty cursor to
 *    unequip it (goes to cursor, then player can drag to their inventory).
 *  • If empty → placeholder shown.  Click with a cape on cursor to equip.
 *
 *  Player inventory (bottom 3 rows) is fully interactive — items can be freely
 *  moved in and out.  Only the top GUI row is restricted.
 *
 * ── Both cape AND armour ───────────────────────────────────────────────────────
 *  Vanilla Minecraft uses the same slot for chestplates and elytra, so only
 *  ONE chestplate-slot item can be worn at a time.  Equipping a cape replaces
 *  whatever was in that slot (returned to inventory automatically).
 */
public class CapeSlotGUI {

    public static final String TITLE     = "§8✦ §5Cape Wardrobe §8✦";
    public static final int    SIZE      = 27;
    /** The interactive cape equip/unequip slot. */
    public static final int    CAPE_SLOT = 13;

    private final SkillCapeManager capeManager;
    private final CapeDataManager  capeDataManager;

    public CapeSlotGUI(SkillCapeManager capeManager, CapeDataManager capeDataManager) {
        this.capeManager     = capeManager;
        this.capeDataManager = capeDataManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);

        // Fill border with glass
        ItemStack glass = filler();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);

        // Header label (slot 4)
        inv.setItem(4, label());

        // Info labels flanking the cape slot
        inv.setItem(11, infoItem());
        inv.setItem(15, infoItem());

        // Cape slot — show current chestplate if it is a cape, else empty marker
        ItemStack chestplate = player.getInventory().getChestplate();
        if (chestplate != null && capeManager.isAnyCape(chestplate)) {
            inv.setItem(CAPE_SLOT, chestplate.clone());
        } else {
            inv.setItem(CAPE_SLOT, emptyCapeSlot());
        }

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
                "§7Place a §5skill cape §7in the centre slot.",
                "§7Your cape appears on §byour back §7in-game.",
                "§8" + "─".repeat(28),
                "§7Requires §aLevel 99 §7in the cape's skill.",
                "§7Your items (bottom rows) are freely movable."
            ));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack emptyCapeSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7[ Cape Slot ]");
            m.setLore(List.of(
                "§8Click a cape from your inventory,",
                "§8then click here to equip it."
            ));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack infoItem() {
        ItemStack it = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§5→ Cape Slot ←"); it.setItemMeta(m); }
        return it;
    }
}
