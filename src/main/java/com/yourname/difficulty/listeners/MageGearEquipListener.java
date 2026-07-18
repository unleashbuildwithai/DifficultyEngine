package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.items.MageGearTier;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * MageGearEquipListener — Enforces Magic level requirements for mage gear.
 *
 * Watches for players attempting to equip mage gear below the tier's required
 * Magic level. Handles both:
 *   • Direct placement into armour slots (slot 5-8 in the player inventory view)
 *   • Shift-click auto-equip from the inventory into an armour slot
 *
 * Level requirements:
 *   APPRENTICE : Magic Lv  1  (always equippable)
 *   MAGE       : Magic Lv 30
 *   ALCH       : Magic Lv 60
 *   MASTER     : Magic Lv 90
 *
 * Players who don't meet the requirement are told what level they need.
 * Admins with {@code difficultyengine.cape.admin} bypass all checks.
 */
public class MageGearEquipListener implements Listener {

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;

    /** Raw slot indices for the 4 armour slots in the player's own inventory view. */
    private static final int SLOT_HELMET     = 5;
    private static final int SLOT_CHESTPLATE = 6;
    private static final int SLOT_LEGGINGS   = 7;
    private static final int SLOT_BOOTS      = 8;

    public MageGearEquipListener(ItemFactory itemFactory, SkillManager skillManager) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only care about the player's own inventory
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING) return;

        ItemStack toEquip = null;

        if (event.getClickedInventory() != null
                && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            // Click directly on an armour slot with a cursor item
            int slot = event.getSlot();
            if (slot == SLOT_HELMET || slot == SLOT_CHESTPLATE
                    || slot == SLOT_LEGGINGS || slot == SLOT_BOOTS) {
                toEquip = event.getCursor();
            }
        } else if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            // Shift-click from main/hotbar inventory — item will auto-go to armour slot
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && isArmour(clicked)) {
                toEquip = clicked;
            }
        }

        if (toEquip == null || toEquip.getType().isAir()) return;

        MageGearTier tier = itemFactory.getMageGearTier(toEquip);
        if (tier == null) return; // not mage gear

        // Admin bypass
        if (player.hasPermission("difficultyengine.cape.admin")) return;

        int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        if (magicLevel < tier.levelRequired) {
            event.setCancelled(true);
            player.sendMessage("§c✗ §7Need §eMagic Level " + tier.levelRequired
                + " §7to equip " + tier.displayPrefix + " gear§7. §8(You: §e"
                + magicLevel + "§8/§a" + tier.levelRequired + "§8)");
        }
    }

    private boolean isArmour(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        return m.name().endsWith("_HELMET") || m.name().endsWith("_CHESTPLATE")
            || m.name().endsWith("_LEGGINGS") || m.name().endsWith("_BOOTS");
    }
}
