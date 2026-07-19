package com.yourname.difficulty.quests;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/**
 * NpcQuestListener — handles right-clicking a quest NPC villager.
 *
 * Flow:
 *   1. Player right-clicks a villager.
 *   2. If that villager has a quest_npc_id PDC key, look up the quest.
 *   3. Cancel the default villager trade GUI.
 *   4. If already completed → tell the player.
 *   5. If secret + requireSneak → player must be sneaking.
 *   6. If requirements not met  → show progress message.
 *   7. If requirements met → delegate to NpcQuestManager.completeQuest().
 */
public class NpcQuestListener implements Listener {

    private final NpcQuestManager npcQuestManager;
    private final NpcQuestSpawner npcQuestSpawner;

    public NpcQuestListener(NpcQuestManager npcQuestManager, NpcQuestSpawner npcQuestSpawner) {
        this.npcQuestManager = npcQuestManager;
        this.npcQuestSpawner = npcQuestSpawner;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;

        int questId = npcQuestSpawner.getQuestId(villager);
        if (questId < 0) return; // not a quest NPC

        // Cancel vanilla trade GUI
        event.setCancelled(true);

        Player player = event.getPlayer();
        NpcQuestDef quest = NpcQuestRegistry.byId(questId);
        if (quest == null) return;

        // ── Already completed ─────────────────────────────────────────────────
        if (npcQuestManager.isCompleted(player.getUniqueId(), questId)) {
            player.sendMessage("§8[" + villager.getCustomName() + "§8]");
            player.sendMessage("§7\"" + completedDialogue(quest) + "\"");
            return;
        }

        // ── Secret sneak requirement ──────────────────────────────────────────
        if (quest.requireSneak && !player.isSneaking()) {
            player.sendMessage("§8[" + villager.getCustomName() + "§8]");
            player.sendMessage("§7\"...\"");
            player.sendActionBar("§8✦ §7Something feels off. Try approaching differently...");
            return;
        }

        // ── Greet player ──────────────────────────────────────────────────────
        player.sendMessage("§8[" + villager.getCustomName() + "§8]");
        player.sendMessage("§7\"" + greetDialogue(quest) + "\"");
        player.sendMessage("");

        // ── Check requirements ────────────────────────────────────────────────
        if (!npcQuestManager.meetsRequirements(player, quest)) {
            showProgress(player, quest);
            return;
        }

        // ── Complete quest ────────────────────────────────────────────────────
        npcQuestManager.completeQuest(player, quest);
    }

    // ── Dialogue helpers ──────────────────────────────────────────────────────

    private String greetDialogue(NpcQuestDef q) {
        if (q.isKillQuest()) {
            return "Traveler! I need you to hunt " + q.killCount + " "
                    + mobName(q) + " for me. Can you do it?";
        }
        return "Ah, adventurer! I need " + q.collectCount + "× "
                + itemName(q) + ". Have you brought them?";
    }

    private String completedDialogue(NpcQuestDef q) {
        return switch (q.id % 5) {
            case 0 -> "You've already done so much for me. Thank you, " + q.title + " hero!";
            case 1 -> "The deed is done. I won't forget your service.";
            case 2 -> "You've proven yourself. Our business here is complete.";
            case 3 -> "My gratitude knows no bounds. Go well, adventurer.";
            default -> "Quest complete. You are a legend in these parts!";
        };
    }

    private void showProgress(Player player, NpcQuestDef quest) {
        if (quest.isKillQuest()) {
            int have = npcQuestManager.getKills(player.getUniqueId(), quest.killTarget);
            int need = quest.killCount;
            player.sendMessage("§7Progress: §e" + have + " §8/ §e" + need
                    + " §7" + mobName(quest) + " killed");
            player.sendActionBar("§c✗ §7" + quest.title + ": " + have + "/" + need
                    + " " + mobName(quest));
        } else if (quest.isCollectQuest()) {
            int have = npcQuestManager.countItem(player, quest.collectItem);
            int need = quest.collectCount;
            player.sendMessage("§7Progress: §e" + have + " §8/ §e" + need
                    + " §7" + itemName(quest) + " in inventory");
            player.sendActionBar("§c✗ §7" + quest.title + ": " + have + "/" + need
                    + " " + itemName(quest));
        }

        // Hint about hidden trigger (vague)
        if (quest.hasHiddenTrigger()) {
            player.sendMessage("§8✦ §7The NPC eyes your pack knowingly...");
        }
    }

    private String mobName(NpcQuestDef q) {
        if (q.killTarget == null) return "creatures";
        return q.killTarget.name().replace("_", " ").toLowerCase();
    }

    private String itemName(NpcQuestDef q) {
        if (q.collectItem == null) return "items";
        return q.collectItem.name().replace("_", " ").toLowerCase();
    }
}
