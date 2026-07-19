package com.yourname.difficulty.gui;

import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CapeSlotGUIListener — Handles the Cape Wardrobe GUI.
 *
 * ── Key rules ─────────────────────────────────────────────────────────────────
 *  • Bottom rows (player inventory): fully interactive — items CAN be moved in
 *    and out normally.  NO clicks are cancelled down there.
 *  • Top row (GUI): only slot 13 (CAPE_SLOT) is interactive.  Everything else
 *    in the top row is cancelled.
 *
 * ── Cape slot (slot 13) logic ─────────────────────────────────────────────────
 *  Click cape onto slot (cursor has cape):
 *    → level check enforced
 *    → old chestplate (if any) returned to player inventory
 *    → new cape put into player's chestplate slot + tracked in CapeDataManager
 *
 *  Click empty cursor on filled slot (cape already equipped):
 *    → cape removed from chestplate slot
 *    → cape put on cursor (player drags it to their inventory)
 *    → CapeDataManager cleared
 */
public class CapeSlotGUIListener implements Listener {

    private final SkillCapeManager capeManager;
    private final SkillManager     skillManager;
    private final CapeDataManager  capeDataManager;

    public CapeSlotGUIListener(SkillCapeManager capeManager,
                                SkillManager skillManager,
                                CapeDataManager capeDataManager) {
        this.capeManager     = capeManager;
        this.skillManager    = skillManager;
        this.capeDataManager = capeDataManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!CapeSlotGUI.TITLE.equals(event.getView().getTitle())) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;

        boolean isTopInventory = event.getClickedInventory()
                .equals(event.getView().getTopInventory());

        if (!isTopInventory) {
            // ── Player's own inventory (bottom rows) ─────────────────────────
            // Normal left/right clicks: always allowed (player manages their items).
            // Shift-clicks: if the item is a cape, auto-equip it. Otherwise cancel
            // to prevent items from shift-jumping into glass pane slots.
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && capeManager.isAnyCape(clicked)) {
                    event.setCancelled(true);
                    // Simulate equipping the cape directly
                    if (canEquip(player, clicked)) {
                        if (player.hasPermission("difficultyengine.cape.admin")) grantAdminPerk(player, clicked);
                        // Store cape in CapeDataManager — chestplate stays on
                        ItemStack oldCape = capeDataManager.equipCape(player.getUniqueId(), clicked);
                        if (oldCape != null) player.getInventory().addItem(oldCape);
                        // Remove the cape from the player's inventory slot
                        event.setCurrentItem(new ItemStack(Material.AIR));
                        // Refresh the GUI cape slot
                        event.getView().getTopInventory().setItem(
                            CapeSlotGUI.CAPE_SLOT, clicked.clone());
                        player.sendMessage("§5✦ §7Cape equipped via shift-click! §5✦");
                    }
                } else {
                    // Non-cape item — cancel the shift so it can't go into glass slots
                    event.setCancelled(true);
                }
            }
            return;
        }

        // ── Top (GUI) inventory ───────────────────────────────────────────────
        // Only slot 13 (CAPE_SLOT) is interactive; cancel everything else.
        event.setCancelled(true);

        if (event.getSlot() != CapeSlotGUI.CAPE_SLOT) return;

        handleCapeSlot(player, event);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!CapeSlotGUI.TITLE.equals(event.getView().getTitle())) return;
        // Cancel drags that touch top-inventory slots (glass / non-interactive)
        for (int slot : event.getRawSlots()) {
            if (slot < CapeSlotGUI.SIZE) { // 0-26 = top inventory
                event.setCancelled(true);
                return;
            }
        }
        // Drags entirely in the player inventory (slots ≥ 27) are allowed.
    }

    // ── Cape slot logic ───────────────────────────────────────────────────────

    private void handleCapeSlot(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack inSlot = event.getCurrentItem();

        boolean cursorHasCape = cursor != null && capeManager.isAnyCape(cursor);
        boolean slotHasCape   = inSlot  != null && capeManager.isAnyCape(inSlot);

        if (cursorHasCape && !slotHasCape) {
            // ── EQUIP a cape from cursor ──────────────────────────────────────
            if (!canEquip(player, cursor)) return;

            // Grant admin perk
            if (player.hasPermission("difficultyengine.cape.admin")) {
                grantAdminPerk(player, cursor);
            }

            // Store cape in CapeDataManager only — chestplate slot is untouched
            ItemStack oldCape = capeDataManager.equipCape(player.getUniqueId(), cursor);
            if (oldCape != null) player.getInventory().addItem(oldCape);

            // Clear cursor and update GUI slot
            event.getView().setCursor(new ItemStack(Material.AIR));
            event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, cursor.clone());

            player.sendMessage("§5✦ §7Cape equipped! (Chestplate slot remains free) §5✦");

        } else if (slotHasCape && (cursor == null || cursor.getType().isAir())) {
            // ── UNEQUIP the cape ──────────────────────────────────────────────
            capeDataManager.unequipCape(player.getUniqueId());

            // Put cape on cursor so player can drag it to their inventory
            event.getView().setCursor(inSlot.clone());
            event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, emptyCapeSlot());

            player.sendMessage("§7Cape unequipped — drag it to your inventory.");

        } else if (cursorHasCape && slotHasCape) {
            // ── SWAP cape ─────────────────────────────────────────────────────
            if (!canEquip(player, cursor)) return;
            if (player.hasPermission("difficultyengine.cape.admin")) grantAdminPerk(player, cursor);

            // Swap via CapeDataManager — chestplate slot untouched
            ItemStack oldCape = capeDataManager.equipCape(player.getUniqueId(), cursor);
            event.getView().setCursor(oldCape != null ? oldCape.clone() : new ItemStack(Material.AIR));
            event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, cursor.clone());
            player.sendMessage("§5✦ §7Cape swapped! §5✦");
        }
    }

    // ── Level check ───────────────────────────────────────────────────────────

    private boolean canEquip(Player player, ItemStack cape) {
        if (player.hasPermission("difficultyengine.cape.admin")) return true;
        if (capeManager.isBossCape(cape)) return true;
        if (capeManager.isMaxCape(cape)) {
            if (!skillManager.isMaxed(player.getUniqueId())) {
                player.sendMessage("§c✗ §7Need §aLevel 99 §7in §fevery skill §7for the §5★ Max Cape§7.");
                return false;
            }
            return true;
        }
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill != null) {
            int lvl = skillManager.getLevel(player.getUniqueId(), skill);
            if (lvl < SkillLevel.MAX_LEVEL) {
                player.sendMessage("§c✗ §7Need §aLevel 99 §7in §"
                    + skill.getColorCode().charAt(1) + skill.getDisplayName()
                    + " §7for this cape. §8(You: §e" + lvl + "§8/§a99§8)");
                return false;
            }
        }
        return true;
    }

    // ── Admin perk ────────────────────────────────────────────────────────────

    private void grantAdminPerk(Player player, ItemStack cape) {
        if (capeManager.isBossCape(cape)) return;
        if (capeManager.isMaxCape(cape)) {
            skillManager.setAllToMax(player.getUniqueId());
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill != null) skillManager.setToMax(player.getUniqueId(), skill);
        }
    }

    // ── Placeholder builder ───────────────────────────────────────────────────

    private ItemStack emptyCapeSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName("§7[ Cape Slot ]");
            it.setItemMeta(m);
        }
        return it;
    }
}
