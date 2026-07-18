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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CapeSlotGUIListener — Handles the redesigned two-slot Cape Wardrobe GUI.
 *
 * ── Armour slot (slot 11) ────────────────────────────────────────────────────
 *   • Click with any non-cape item on cursor → equips as chestplate.
 *     Returns old chestplate to cursor (player can put it in inventory later).
 *   • Click with empty cursor + slot occupied → takes chestplate to cursor.
 *   • Capes are rejected here (use the cape slot instead).
 *
 * ── Cape slot (slot 15) ──────────────────────────────────────────────────────
 *   • Click with cape on cursor → equips via CapeDataManager.
 *     Returns old cape (if any) to cursor.
 *   • Click with empty cursor + cape in slot → unequips cape, puts on cursor.
 *   • Level 99 requirement still enforced (admin bypass available).
 *
 * ── All other slots ──────────────────────────────────────────────────────────
 *   • Cancelled — the player's own inventory (bottom half) is still accessible.
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
        event.setCancelled(true); // cancel by default

        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only handle clicks in the top (GUI) inventory
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getSlot();

        if (slot == CapeSlotGUI.ARMOR_SLOT) {
            handleArmorSlotClick(player, event);
        } else if (slot == CapeSlotGUI.CAPE_SLOT) {
            handleCapeSlotClick(player, event);
        }
        // All other GUI slots stay cancelled
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (CapeSlotGUI.TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    // ── Armour slot logic ─────────────────────────────────────────────────────

    private void handleArmorSlotClick(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack inSlot = event.getCurrentItem();

        boolean cursorHasItem = cursor != null && !cursor.getType().isAir();
        boolean slotHasItem   = inSlot  != null && !inSlot.getType().isAir()
                                && !(inSlot.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE);

        if (cursorHasItem) {
            // Reject capes in armor slot
            if (capeManager.isAnyCape(cursor)) {
                player.sendMessage("§c✗ §7Capes go in the §5cape slot §7(right side)!");
                return;
            }
            // Equip cursor item as chestplate; return old chestplate to cursor
            ItemStack old = player.getInventory().getChestplate();
            player.getInventory().setChestplate(cursor.clone());
            event.getView().setCursor(
                (old != null && !old.getType().isAir()) ? old.clone() : new ItemStack(Material.AIR));
            event.getView().getTopInventory().setItem(CapeSlotGUI.ARMOR_SLOT, cursor.clone());
            player.sendMessage("§e⚔ §7Armour equipped!");

        } else if (slotHasItem) {
            // Take chestplate to cursor
            ItemStack chest = player.getInventory().getChestplate();
            if (chest != null && !chest.getType().isAir()) {
                player.getInventory().setChestplate(null);
                event.getView().setCursor(chest.clone());
                event.getView().getTopInventory().setItem(CapeSlotGUI.ARMOR_SLOT, emptyArmorSlot());
                player.sendMessage("§7Armour removed.");
            }
        }
    }

    // ── Cape slot logic ───────────────────────────────────────────────────────

    private void handleCapeSlotClick(Player player, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        ItemStack inSlot = event.getCurrentItem();

        boolean cursorHasCape = cursor != null && capeManager.isAnyCape(cursor);
        boolean slotHasCape   = inSlot  != null && capeManager.isAnyCape(inSlot);

        if (cursorHasCape && !slotHasCape) {
            // Player wants to EQUIP a cape
            if (!canEquip(player, cursor)) return;
            // Swap into CapeDataManager
            ItemStack old = capeDataManager.equipCape(player.getUniqueId(), cursor);
            event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, cursor.clone());
            event.getView().setCursor(
                (old != null && !old.getType().isAir()) ? old.clone() : new ItemStack(Material.AIR));
            if (player.hasPermission("difficultyengine.cape.admin")) grantAdminPerk(player, cursor);
            player.sendMessage("§5✦ §7Cape equipped! §5✦");

        } else if (slotHasCape && (cursor == null || cursor.getType().isAir())) {
            // Player wants to UNEQUIP the cape
            ItemStack old = capeDataManager.unequipCape(player.getUniqueId());
            event.getView().setCursor(old != null ? old.clone() : new ItemStack(Material.AIR));
            event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, emptyCapeSlot());
            player.sendMessage("§7Cape unequipped.");
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

    // ── Slot placeholder builders ─────────────────────────────────────────────

    private ItemStack emptyArmorSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§7[ Armour Slot ]"); it.setItemMeta(m); }
        return it;
    }

    private ItemStack emptyCapeSlot() {
        ItemStack it = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
        if (m != null) { m.setDisplayName("§7[ Cape Slot ]"); it.setItemMeta(m); }
        return it;
    }
}
