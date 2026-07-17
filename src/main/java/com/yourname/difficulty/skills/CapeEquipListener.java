package com.yourname.difficulty.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * CapeEquipListener — Grants Level 99 to admins when they equip a skill cape.
 *
 * Detection: Listens for InventoryClickEvent where a player shifts or directly
 * places a cape item into their chestplate slot (slot 38 in the player inventory).
 *
 * Permission required: difficultyengine.cape.admin
 * Without the permission, the cape still works as a cosmetic item — it just
 * doesn't grant level 99.
 *
 * Effect:
 *   Skill Cape → Sets that specific skill to Level 99 (13,034,431 XP)
 *   Max Cape   → Sets ALL skills to Level 99
 */
public class CapeEquipListener implements Listener {

    /** Slot index in the player's inventory that corresponds to the chestplate. */
    private static final int CHESTPLATE_SLOT = 38;

    private final SkillManager     skillManager;
    private final SkillCapeManager capeManager;

    public CapeEquipListener(SkillManager skillManager, SkillCapeManager capeManager) {
        this.skillManager = skillManager;
        this.capeManager  = capeManager;
    }

    /**
     * Fires when a player clicks inside their own inventory.
     * We catch two cases:
     *  1. Direct click into the chestplate slot (slot 38)
     *  2. Shift-click a cape from anywhere → it auto-goes to the armor slot
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only process clicks inside the player's own inventory
        if (event.getClickedInventory() == null) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        ItemStack capeCandidate = null;

        // Case 1: Direct place into chestplate slot
        if (event.getSlot() == CHESTPLATE_SLOT
                && cursor != null && !cursor.getType().isAir()) {
            capeCandidate = cursor;
        }

        // Case 2: Shift-click a cape item from inventory
        if (event.isShiftClick()
                && clicked != null && !clicked.getType().isAir()
                && capeManager.isAnyCape(clicked)) {
            capeCandidate = clicked;
        }

        if (capeCandidate == null) return;
        if (!capeManager.isAnyCape(capeCandidate)) return;

        applyCapePerk(player, capeCandidate);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void applyCapePerk(Player player, ItemStack cape) {
        // Only admins with the cape perk get instant level 99
        if (!player.hasPermission("difficultyengine.cape.admin")) return;

        if (capeManager.isMaxCape(cape)) {
            // Max Cape → all skills to 99
            skillManager.setAllToMax(player.getUniqueId());
            player.sendMessage("");
            player.sendMessage("§5✦ §6ADMIN PERK §5✦");
            player.sendMessage("§7The §d★ Max Cape §7has set §aALL skills to Level 99§7!");
            player.sendMessage("§8(Permission: difficultyengine.cape.admin)");
            player.sendMessage("");
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill == null) return;

            skillManager.setToMax(player.getUniqueId(), skill);
            player.sendMessage("");
            player.sendMessage("§5✦ §6ADMIN PERK §5✦");
            player.sendMessage("§7The §" + skill.getColorCode().charAt(1)
                    + SkillCapeManager.symbol(skill) + " Cape of " + skill.getDisplayName()
                    + " §7has set your §" + skill.getColorCode().charAt(1)
                    + skill.getDisplayName() + " §7to §aLevel 99§7!");
            player.sendMessage("§8(Permission: difficultyengine.cape.admin)");
            player.sendMessage("");
        }
    }
}
