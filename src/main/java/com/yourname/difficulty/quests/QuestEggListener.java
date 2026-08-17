package com.yourname.difficulty.quests;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * QuestEggListener — right-click a block with a Quest NPC Egg (from the Registry,
 * pages 12–18) to spawn that quest's NPC at the clicked location.
 *
 * Mirrors {@code /questnpc spawn <id>} but reachable directly from the Registry GUI
 * without needing to know quest ids by heart. Reuses NpcQuestSpawner's persisted
 * position tracking (see {@link NpcQuestSpawner#spawnNpcById}), so eggs placed this
 * way survive server restarts exactly like admin-placed NPCs.
 */
public class QuestEggListener implements Listener {

    private final ItemFactory      itemFactory;
    private final NpcQuestSpawner  npcQuestSpawner;

    public QuestEggListener(ItemFactory itemFactory, NpcQuestSpawner npcQuestSpawner) {
        this.itemFactory     = itemFactory;
        this.npcQuestSpawner = npcQuestSpawner;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || !itemFactory.isQuestEgg(item)) return;

        if (!player.hasPermission("difficultyengine.cape.admin")) {
            player.sendMessage("§c✗ §7Placing Quest NPCs requires admin permission.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        int questId = itemFactory.getQuestEggId(item);
        NpcQuestDef def = NpcQuestRegistry.byId(questId);
        if (def == null) {
            player.sendMessage("§c✗ §7Invalid quest id on this egg: §e" + questId);
            return;
        }

        var loc = event.getClickedBlock().getLocation().add(0.5, 1, 0.5);
        boolean spawned = npcQuestSpawner.spawnNpcById(questId, loc);

        if (!spawned) {
            player.sendMessage("§e✗ §7An NPC for quest §e#" + questId
                    + " §7already exists. Use §e/questnpc remove " + questId + " §7first.");
            return;
        }

        player.sendMessage("§6✦ §7Spawned quest NPC §e#" + questId + " §8(" + def.npcName + ")§7!");

        // Consume one egg from the stack
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
