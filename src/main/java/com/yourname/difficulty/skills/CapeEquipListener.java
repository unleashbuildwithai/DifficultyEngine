package com.yourname.difficulty.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * CapeEquipListener — Enforces level requirements for skill capes.
 *
 * Rules:
 *   • Admin (difficultyengine.cape.admin) → always allowed + grants Level 99
 *   • Player with Level 99 in the skill   → allowed to wear the cape
 *   • Anyone else                         → equip is CANCELLED with an error message
 *
 * Max Cape requirement: ALL skills must be Level 99 (or admin bypass).
 *
 * Detection covers two equip paths:
 *   1. Direct drag/click into the chestplate slot (slot 38)
 *   2. Shift-click on a cape item anywhere in the player inventory
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

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null) return;
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return;

        ItemStack cursor  = event.getCursor();
        ItemStack clicked = event.getCurrentItem();

        ItemStack capeCandidate = null;

        // Case 1: Direct place into chestplate slot
        if (event.getSlot() == CHESTPLATE_SLOT
                && cursor != null && !cursor.getType().isAir()) {
            capeCandidate = cursor;
        }

        // Case 2: Shift-click a cape item
        if (event.isShiftClick()
                && clicked != null && !clicked.getType().isAir()
                && capeManager.isAnyCape(clicked)) {
            capeCandidate = clicked;
        }

        if (capeCandidate == null || !capeManager.isAnyCape(capeCandidate)) return;

        // ── Admin bypass ──────────────────────────────────────────────────────
        if (player.hasPermission("difficultyengine.cape.admin")) {
            applyAdminPerk(player, capeCandidate);
            return; // allow equip
        }

        // ── Level check ───────────────────────────────────────────────────────
        if (!meetsRequirement(player, capeCandidate)) {
            event.setCancelled(true);
            sendRequirementMessage(player, capeCandidate);
        }
        // If requirement met, allow equip silently (no admin perk granted)
    }

    // ── Requirement check ─────────────────────────────────────────────────────

    private boolean meetsRequirement(Player player, ItemStack cape) {
        // Boss Cape: earned via gameplay — no level requirement
        if (capeManager.isBossCape(cape)) return true;
        if (capeManager.isMaxCape(cape)) {
            // Max Cape: ALL skills must be 99
            return skillManager.isMaxed(player.getUniqueId());
        }
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return true; // unknown cape — don't block
        return skillManager.getLevel(player.getUniqueId(), skill) >= SkillLevel.MAX_LEVEL;
    }

    private void sendRequirementMessage(Player player, ItemStack cape) {
        if (capeManager.isBossCape(cape)) return; // no requirement, never shown
        if (capeManager.isMaxCape(cape)) {
            player.sendMessage("§c✗ §7You need §aLevel 99 §7in §fevery skill §7to wear the §5★ Max Cape§7.");
            player.sendMessage("§7Use §e/mystats §7to check your progress.");
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill == null) return;
            int current = skillManager.getLevel(player.getUniqueId(), skill);
            player.sendMessage("§c✗ §7You need §aLevel 99 §7in §"
                    + skill.getColorCode().charAt(1)
                    + SkillCapeManager.symbol(skill) + " " + skill.getDisplayName()
                    + " §7to wear this cape.");
            player.sendMessage("  §7Your level: §e" + current + " §8/ §a99");
            player.sendMessage("  §7Use §e/mystats §7to view your skill progress.");
        }
    }

    // ── Admin perk ────────────────────────────────────────────────────────────

    /** Silently sets skill(s) to Level 99 — no announcement shown. */
    private void applyAdminPerk(Player player, ItemStack cape) {
        if (capeManager.isBossCape(cape)) return; // Boss cape has no skill to set
        if (capeManager.isMaxCape(cape)) {
            skillManager.setAllToMax(player.getUniqueId());
        } else {
            SkillType skill = capeManager.getCapeSkill(cape);
            if (skill != null) skillManager.setToMax(player.getUniqueId(), skill);
        }
    }
}
