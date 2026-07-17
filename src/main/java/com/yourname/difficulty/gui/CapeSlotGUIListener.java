package com.yourname.difficulty.gui;

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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * CapeSlotGUIListener — Handles interaction with the /cape wardrobe GUI.
 *
 * Logic:
 *   • Only slot 13 (CAPE_SLOT) is interactive.
 *   • Placing a cape in the slot → equips to the player's elytra/chest slot.
 *   • Taking a cape from the slot → removes from elytra slot, put on cursor.
 *   • Level 99 requirement enforced (admin bypass).
 *   • All other clicks are cancelled.
 *   • On GUI close, the GUI cape slot is synced back to inventory if needed.
 */
public class CapeSlotGUIListener implements Listener {

    private final SkillCapeManager capeManager;
    private final SkillManager     skillManager;

    public CapeSlotGUIListener(SkillCapeManager capeManager, SkillManager skillManager) {
        this.capeManager  = capeManager;
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!CapeSlotGUI.TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true); // cancel everything by default

        if (!(event.getWhoClicked() instanceof Player player)) return;
        // Only allow interaction with the top inventory (the GUI)
        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;
        // Only the cape slot is interactive
        if (event.getSlot() != CapeSlotGUI.CAPE_SLOT) return;

        ItemStack cursor  = event.getCursor();
        ItemStack inSlot  = event.getCurrentItem();

        boolean cursorHasCape = cursor != null && capeManager.isAnyCape(cursor);
        boolean slotHasCape   = inSlot != null  && capeManager.isAnyCape(inSlot);

        if (cursorHasCape && !slotHasCape) {
            // Player wants to EQUIP a cape from cursor
            if (!canEquip(player, cursor)) return; // sends its own error message
            equipCape(player, cursor, event);
        } else if (slotHasCape && (cursor == null || cursor.getType().isAir())) {
            // Player wants to UNEQUIP the cape
            unequipCape(player, inSlot, event);
        }
        // Any other click (cape-on-cape swap, non-cape item, etc.) stays cancelled
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (CapeSlotGUI.TITLE.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    // ── Equip ─────────────────────────────────────────────────────────────────

    private void equipCape(Player player, ItemStack cape, InventoryClickEvent event) {
        // If there's already a chestplate/elytra, return it to inventory
        ItemStack current = player.getInventory().getChestplate();
        if (current != null && !current.getType().isAir()) {
            player.getInventory().addItem(current);
        }

        // Apply admin perk
        if (player.hasPermission("difficultyengine.cape.admin")) {
            grantAdminPerk(player, cape);
        }

        // Equip the cape (put into chest/elytra slot)
        player.getInventory().setChestplate(cape.clone());

        // Clear cursor
        event.getView().setCursor(new ItemStack(Material.AIR));

        // Update the GUI slot to show the cape
        event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, cape.clone());

        player.sendMessage("§5✦ §7Cape equipped! §5✦");
    }

    // ── Unequip ───────────────────────────────────────────────────────────────

    private void unequipCape(Player player, ItemStack cape, InventoryClickEvent event) {
        // Remove from elytra slot
        player.getInventory().setChestplate(null);

        // Put on cursor so player can move it
        event.getView().setCursor(cape.clone());

        // Replace GUI slot with empty marker
        CapeSlotGUI gui = new CapeSlotGUI(capeManager);
        // We rebuild the empty slot item inline
        org.bukkit.inventory.meta.ItemMeta meta;
        ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        meta = empty.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7[ Cape Slot ]");
            meta.setLore(java.util.List.of("§8Drag a skill cape here", "§8to equip it on your back."));
            empty.setItemMeta(meta);
        }
        event.getView().getTopInventory().setItem(CapeSlotGUI.CAPE_SLOT, empty);

        player.sendMessage("§7Cape unequipped.");
    }

    // ── Level check ───────────────────────────────────────────────────────────

    private boolean canEquip(Player player, ItemStack cape) {
        if (player.hasPermission("difficultyengine.cape.admin")) return true;

        if (capeManager.isMaxCape(cape)) {
            if (!skillManager.isMaxed(player.getUniqueId())) {
                player.sendMessage("§c✗ §7You need §aLevel 99 §7in §fevery skill §7to wear the §5★ Max Cape§7.");
                player.sendMessage("  §7Use §e/mystats §7to check your progress.");
                return false;
            }
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill != null) {
                int lvl = skillManager.getLevel(player.getUniqueId(), skill);
                if (lvl < SkillLevel.MAX_LEVEL) {
                    player.sendMessage("§c✗ §7You need §aLevel 99 §7in §"
                            + skill.getColorCode().charAt(1)
                            + SkillCapeManager.symbol(skill) + " " + skill.getDisplayName()
                            + " §7to wear this cape.");
                    player.sendMessage("  §7Your level: §e" + lvl + " §8/ §a99");
                    return false;
                }
            }
        }
        return true;
    }

    // ── Admin perk ────────────────────────────────────────────────────────────

    /** Silently sets the admin's skill(s) to Level 99 when they equip a cape. */
    private void grantAdminPerk(Player player, ItemStack cape) {
        if (capeManager.isMaxCape(cape)) {
            skillManager.setAllToMax(player.getUniqueId());
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill != null) {
                skillManager.setToMax(player.getUniqueId(), skill);
            }
        }
        // No announcement — stats are set silently every time.
    }
}
