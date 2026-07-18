package com.yourname.difficulty.quests;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * QuestGUI — /questbook opens a 54-slot chest GUI showing all quests.
 *
 * Per-quest slot colours:
 *   LIME_STAINED_GLASS_PANE  = permanent quest done
 *   YELLOW_STAINED_GLASS_PANE = repeatable quest done (shows completions count)
 *   EMERALD_BLOCK            = ready to claim (click to collect reward)
 *   Quest icon               = in progress (shows progress bar)
 */
public class QuestGUI implements Listener {

    private static final String TITLE = "§8[ §6Quest Book §8]";

    private final QuestManager questManager;

    public QuestGUI(QuestManager questManager) {
        this.questManager = questManager;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        UUID uuid = player.getUniqueId();

        QuestType[] quests = QuestType.values();
        for (int i = 0; i < quests.length && i < 45; i++) {
            inv.setItem(i, buildQuestItem(uuid, quests[i]));
        }

        // Bottom row: info panel
        inv.setItem(49, infoItem());
        player.openInventory(inv);
    }

    // ── Quest item builder ────────────────────────────────────────────────────

    private ItemStack buildQuestItem(UUID uuid, QuestType q) {
        int  progress    = questManager.getProgress(uuid, q);
        int  completions = questManager.getCompletions(uuid, q);
        boolean permaDone  = questManager.isPermaDone(uuid, q);
        boolean claimable  = questManager.isClaimable(uuid, q);

        Material mat;
        String   nameColor;

        if (claimable) {
            mat = Material.EMERALD_BLOCK;
            nameColor = "§a§l";
        } else if (permaDone) {
            mat = Material.LIME_STAINED_GLASS_PANE;
            nameColor = "§7";
        } else if (!q.repeatable && completions >= 1) {
            mat = Material.YELLOW_STAINED_GLASS_PANE;
            nameColor = "§e";
        } else {
            mat = q.icon;
            nameColor = q.repeatable ? "§e" : "§6";
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        String tag = q.repeatable ? " §8[Repeatable]" : " §8[One-time]";
        meta.setDisplayName(nameColor + q.displayName + tag);

        List<String> lore = new ArrayList<>();
        lore.add(q.description);
        lore.add("§8" + "─".repeat(22));

        if (claimable) {
            lore.add("§a§lREADY TO CLAIM! Click to collect your reward.");
        } else if (permaDone) {
            lore.add("§7[DONE] Quest completed permanently.");
        } else {
            // Progress bar
            int pct    = q.targetCount > 0 ? Math.min(progress, q.targetCount) : 0;
            int filled = (int)((pct / (double) q.targetCount) * 14);
            String bar = "§a" + "|".repeat(filled) + "§8" + "|".repeat(14 - filled);
            lore.add("§7Progress: " + bar + " §e" + pct + "§8/§e" + q.targetCount);
        }

        if (completions > 0 && q.repeatable) {
            lore.add("§8Completed " + completions + "x so far.");
        }

        lore.add("§8" + "─".repeat(22));
        lore.add("§6Rewards: §7" + q.rewardSpec.replace(",", " + ").replace(":", " x"));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // ── GUI click — claim reward ──────────────────────────────────────────────

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= QuestType.values().length) return;

        QuestType q = QuestType.values()[slot];
        if (!questManager.isClaimable(player.getUniqueId(), q)) {
            player.sendMessage("§7Quest not ready to claim yet.");
            return;
        }

        questManager.claimReward(player, q);
        player.closeInventory();
        // Re-open to show updated state
        open(player);
    }

    // ── Info item ─────────────────────────────────────────────────────────────

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Quest Book");
            meta.setLore(List.of(
                "§7Kill specific mobs to complete quests.",
                "§7Click §agreen §7quests to claim rewards.",
                "§8Repeatable = can be done again. One-time = done once."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
