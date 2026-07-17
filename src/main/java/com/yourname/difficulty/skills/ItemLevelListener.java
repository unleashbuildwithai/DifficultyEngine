package com.yourname.difficulty.skills;

import com.yourname.difficulty.skills.ItemLevelRequirements.LevelRequirement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * ItemLevelListener — Enforces skill level requirements on items.
 *
 * ── Enforcement points ────────────────────────────────────────────────────
 *
 *  1. CraftItemEvent        — Blocks crafting a weapon/tool the player can't use yet.
 *
 *  2. InventoryClickEvent   — Blocks placing restricted items into hotbar slots 0–8.
 *                             Covers: direct click, shift-click into hotbar.
 *
 *  3. PlayerItemHeldEvent   — If a restricted item somehow ends up in a hotbar slot
 *                             (e.g. received via command), auto-moves it to inventory
 *                             or drops it if inventory is full.
 *
 *  4. BlockPlaceEvent       — Blocks planting seeds/crops below the required
 *                             Farming level.
 *
 *  5. PlayerFishEvent       — Blocks casting an enchanted fishing rod that requires
 *                             a higher Fishing level than the player has.
 *
 * ── Admin bypass ─────────────────────────────────────────────────────────
 *  Players with difficultyengine.itemlevel.bypass skip ALL checks.
 *  This permission defaults to op.
 */
public class ItemLevelListener implements Listener {

    /** First 9 slots in the player inventory = hotbar. */
    private static final int HOTBAR_SIZE = 9;

    private final SkillManager skillManager;

    public ItemLevelListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // ── 1. Crafting block ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        ItemStack result = event.getRecipe().getResult();
        LevelRequirement req = ItemLevelRequirements.getRequirement(result);
        if (req == null) return;

        int playerLevel = skillManager.getLevel(player.getUniqueId(), req.skill());
        if (playerLevel < req.requiredLevel()) {
            event.setCancelled(true);
            player.sendMessage("§c✗ §7You need " + req.formatRequirement()
                    + " §7to craft this.");
            player.sendMessage("  §7Your level: §e" + playerLevel
                    + " §8/ §a" + req.requiredLevel());
        }
    }

    // ── 2. Hotbar placement block ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (hasBypass(player)) return;

        ItemStack moving = getItemBeingMovedToHotbar(event);
        if (moving == null) return;

        LevelRequirement req = ItemLevelRequirements.getRequirement(moving);
        if (req == null) return;

        int playerLevel = skillManager.getLevel(player.getUniqueId(), req.skill());
        if (playerLevel < req.requiredLevel()) {
            event.setCancelled(true);
            player.sendMessage("§c✗ §7You need " + req.formatRequirement()
                    + " §7to equip this in your hotbar.");
            player.sendMessage("  §7Your level: §e" + playerLevel
                    + " §8/ §a" + req.requiredLevel());
        }
    }

    /**
     * Returns the item that would land in a hotbar slot (0–8) from this click,
     * or null if this click doesn't place anything restricted into the hotbar.
     */
    private ItemStack getItemBeingMovedToHotbar(InventoryClickEvent event) {
        // Only care about the player's own inventory
        if (!(event.getClickedInventory() instanceof PlayerInventory)) return null;

        int slot = event.getSlot();

        // Case A: Direct click into a hotbar slot with cursor holding an item
        if (slot < HOTBAR_SIZE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) return cursor;
        }

        // Case B: Shift-click on an item — estimate if it would go to hotbar
        // (only block if the item is restricted; the exact destination is uncertain,
        //  but we err on the side of blocking it if the hotbar might receive it)
        if (event.isShiftClick()) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                // Only block shift-clicks from non-hotbar slots going TO hotbar
                if (slot >= HOTBAR_SIZE) {
                    LevelRequirement req = ItemLevelRequirements.getRequirement(clicked);
                    if (req != null) return clicked; // will be blocked by caller
                }
            }
        }

        return null;
    }

    // ── 3. Auto-remove restricted items from hotbar on slot change ────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        // Check the slot the player is switching TO
        ItemStack item = player.getInventory().getItem(event.getNewSlot());
        if (item == null || item.getType().isAir()) return;

        LevelRequirement req = ItemLevelRequirements.getRequirement(item);
        if (req == null) return;

        UUID uuid = player.getUniqueId();
        int playerLevel = skillManager.getLevel(uuid, req.skill());
        if (playerLevel >= req.requiredLevel()) return;

        // Player can't use this item — remove it from the hotbar slot
        player.getInventory().setItem(event.getNewSlot(), null);

        // Try to put it in a non-hotbar inventory slot
        boolean added = false;
        PlayerInventory inv = player.getInventory();
        for (int slot = HOTBAR_SIZE; slot < inv.getSize(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType().isAir()) {
                inv.setItem(slot, item);
                added = true;
                break;
            }
        }

        if (!added) {
            // Inventory full — drop on ground
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }

        player.sendMessage("§c✗ §7Requires " + req.formatRequirement()
                + " §7— moved to inventory.");
        player.sendMessage("  §7Your level: §e" + playerLevel
                + " §8/ §a" + req.requiredLevel());
    }

    // ── 4. Seed planting block ────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        ItemStack inHand = event.getItemInHand();
        int seedReq = ItemLevelRequirements.getSeedRequirement(inHand.getType());
        if (seedReq == 0) return;

        int playerLevel = skillManager.getLevel(player.getUniqueId(), SkillType.FARMING);
        if (playerLevel < seedReq) {
            event.setCancelled(true);
            player.sendMessage("§c✗ §7You need §2✿ Farming §7Level §a" + seedReq
                    + " §7to plant §f" + formatName(inHand.getType()) + "§7.");
            player.sendMessage("  §7Your Farming level: §e" + playerLevel
                    + " §8/ §a" + seedReq);
        }
    }

    // ── 5. Fishing rod cast block ─────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.FISHING) return;

        Player player = event.getPlayer();
        if (hasBypass(player)) return;

        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod.getType() != org.bukkit.Material.FISHING_ROD) {
            rod = player.getInventory().getItemInOffHand();
        }
        if (rod.getType() != org.bukkit.Material.FISHING_ROD) return;

        int rodReq = ItemLevelRequirements.getFishingRodRequirement(rod);
        if (rodReq <= 1) return; // unenchanted rods always usable

        int playerLevel = skillManager.getLevel(player.getUniqueId(), SkillType.FISHING);
        if (playerLevel < rodReq) {
            event.setCancelled(true);
            player.sendMessage("§c✗ §7Your fishing rod's enchantments require §bFishing §7Level §a"
                    + rodReq + "§7.");
            player.sendMessage("  §7Your Fishing level: §e" + playerLevel
                    + " §8/ §a" + rodReq);
            player.sendMessage("  §7Remove enchantments or level up your §bFishing §7skill.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasBypass(Player player) {
        return player.hasPermission("difficultyengine.itemlevel.bypass");
    }

    private static String formatName(org.bukkit.Material mat) {
        String name = mat.name().replace('_', ' ').toLowerCase();
        // Capitalize first letter of each word
        String[] words = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
