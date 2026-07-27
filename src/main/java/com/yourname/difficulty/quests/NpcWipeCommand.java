package com.yourname.difficulty.quests;

import com.yourname.difficulty.vip.VipShopListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * NpcWipeCommand — /npcwipe (alias /killallnpc)
 *
 * Removes EVERY plugin-spawned NPC on the server in one shot:
 *   • All quest villagers (both currently spawned in loaded chunks AND their
 *     tracked positions in npc_positions.yml, so they don't silently respawn
 *     next tick via NpcQuestSpawner.restoreMissingNpcs()).
 *   • The VIP shop keeper villager.
 *   • Any other villager entity carrying one of our known NPC PDC tags,
 *     as a defensive catch-all against stray/duplicated NPCs.
 *
 * This exists specifically to let admins fully reset NPC state when quest
 * villagers have piled up / glitched / duplicated, without needing to hunt
 * them down individually across every loaded world.
 */
public class NpcWipeCommand implements CommandExecutor {

    private final JavaPlugin       plugin;
    private final NpcQuestSpawner  npcQuestSpawner;
    private final NamespacedKey    questNpcKey;
    private final NamespacedKey    vipVillagerKey;

    public NpcWipeCommand(JavaPlugin plugin, NpcQuestSpawner npcQuestSpawner) {
        this.plugin          = plugin;
        this.npcQuestSpawner = npcQuestSpawner;
        this.questNpcKey     = new NamespacedKey(plugin, NpcQuestSpawner.PDC_QUEST_NPC_ID);
        this.vipVillagerKey  = new NamespacedKey(plugin, VipShopListener.VIP_VILLAGER_KEY);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§c✗ §7You don't have permission to use §e/npcwipe§7.");
            return true;
        }

        int removed = 0;

        // ── 1. Remove every currently-spawned NPC-tagged villager in every loaded world ──
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Villager villager)) continue;

                boolean isQuestNpc = villager.getPersistentDataContainer()
                        .has(questNpcKey, PersistentDataType.INTEGER);
                boolean isVipNpc = villager.getPersistentDataContainer()
                        .has(vipVillagerKey, PersistentDataType.BYTE);

                if (isQuestNpc || isVipNpc) {
                    villager.remove();
                    removed++;
                }
            }
        }

        // ── 2. Wipe the persisted quest-NPC position registry so
        //      NpcQuestSpawner.restoreMissingNpcs() doesn't bring any back ──
        int wipedRecords = npcQuestSpawner.wipeAllPositions();

        sender.sendMessage("§8[§6DifficultyEngine§8] §a✓ §7NPC Wipe complete!");
        sender.sendMessage("§8  §7Removed §e" + removed + " §7live NPC entit" + (removed == 1 ? "y" : "ies") + ".");
        sender.sendMessage("§8  §7Cleared §e" + wipedRecords + " §7tracked quest-NPC position record(s).");
        sender.sendMessage("§8  §7Quest NPCs will need to be re-placed with §e/questnpc spawn <id>§7.");

        if (!(sender instanceof org.bukkit.entity.Player)) {
            plugin.getLogger().info("[NpcWipe] " + sender.getName() + " ran a full NPC wipe: "
                    + removed + " entities removed, " + wipedRecords + " position records cleared.");
        }

        return true;
    }
}
