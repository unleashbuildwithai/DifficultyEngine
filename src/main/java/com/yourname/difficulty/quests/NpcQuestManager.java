package com.yourname.difficulty.quests;

import com.yourname.difficulty.currency.GoldManager;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * NpcQuestManager — persistence + logic layer for the 300-quest NPC system.
 *
 * Two YAML files:
 *   npcquest_kills.yml   — per-player accumulated kill counts per EntityType
 *   npcquest_progress.yml — per-player completed quest IDs (comma-separated)
 *
 * PDC keys on players:
 *   "has_quest_cape"       (BYTE 1) — main 150 complete
 *   "has_boss_quest_cape"  (BYTE 1) — secret 150 complete
 */
public class NpcQuestManager {

    // ── PDC keys ──────────────────────────────────────────────────────────────
    public static final String PDC_QUEST_CAPE       = "has_quest_cape";
    public static final String PDC_BOSS_QUEST_CAPE  = "has_boss_quest_cape";

    private final JavaPlugin   plugin;
    private final GoldManager  goldManager;

    private final File             killFile;
    private final File             progressFile;
    private YamlConfiguration      killData;
    private YamlConfiguration      progressData;

    /** uuid → entityTypeName → count */
    private final Map<UUID, Map<String, Integer>> killCache = new HashMap<>();
    /** uuid → set of completed quest IDs */
    private final Map<UUID, Set<Integer>>         doneCache = new HashMap<>();

    public NpcQuestManager(JavaPlugin plugin, GoldManager goldManager) {
        this.plugin      = plugin;
        this.goldManager = goldManager;
        plugin.getDataFolder().mkdirs();
        this.killFile     = new File(plugin.getDataFolder(), "npcquest_kills.yml");
        this.progressFile = new File(plugin.getDataFolder(), "npcquest_progress.yml");
        load();
    }

    // ── Kill tracking (called from QuestKillListener) ─────────────────────────

    /**
     * Records one kill of the given entity type for the player.
     * Called every time the player kills any mob.
     */
    public void onKill(Player player, EntityType type) {
        UUID uuid = player.getUniqueId();
        String key = type.name();
        killCache.computeIfAbsent(uuid, k -> new HashMap<>())
                 .merge(key, 1, Integer::sum);
    }

    /** Returns how many of this entity type the player has killed total. */
    public int getKills(UUID uuid, EntityType type) {
        return killCache.getOrDefault(uuid, Map.of()).getOrDefault(type.name(), 0);
    }

    // ── Quest completion ──────────────────────────────────────────────────────

    /** Returns true if the player has already completed this quest. */
    public boolean isCompleted(UUID uuid, int questId) {
        return doneCache.getOrDefault(uuid, Set.of()).contains(questId);
    }

    /**
     * Checks whether the player currently satisfies all requirements for this quest.
     * Does NOT consume items or mark as complete.
     */
    public boolean meetsRequirements(Player player, NpcQuestDef quest) {
        UUID uuid = player.getUniqueId();
        if (quest.isKillQuest()) {
            return getKills(uuid, quest.killTarget) >= quest.killCount;
        }
        if (quest.isCollectQuest()) {
            return countItem(player, quest.collectItem) >= quest.collectCount;
        }
        return false;
    }

    /**
     * Awards the quest: consumes items (collect quests), pays gold, marks complete,
     * and checks cape thresholds. Fires on a successful NPC interaction.
     */
    public void completeQuest(Player player, NpcQuestDef quest) {
        UUID uuid = player.getUniqueId();

        // ── Consume collected items ───────────────────────────────────────────
        if (quest.isCollectQuest()) {
            removeItems(player, quest.collectItem, quest.collectCount);
        }

        // ── Hidden trigger ────────────────────────────────────────────────────
        boolean hiddenFired = false;
        if (quest.hasHiddenTrigger()
                && countItem(player, quest.hiddenItem) >= quest.hiddenCount) {
            removeItems(player, quest.hiddenItem, quest.hiddenCount);
            hiddenFired = true;
        }

        // ── Pay gold ──────────────────────────────────────────────────────────
        long reward = quest.baseGold + (hiddenFired ? quest.bonusGold : 0L);
        goldManager.award(player, reward);

        // ── Mark done ─────────────────────────────────────────────────────────
        doneCache.computeIfAbsent(uuid, k -> new HashSet<>()).add(quest.id);

        // ── Announce ──────────────────────────────────────────────────────────
        player.sendMessage("");
        player.sendMessage("§6[Quest] §a✦ §e" + quest.title + " §acompleted!");
        player.sendMessage("  §7Reward: §6+" + GoldManager.formatGold(quest.baseGold) + " gp"
                + (hiddenFired ? " §8+§6" + GoldManager.formatGold(quest.bonusGold) + " §7(secret bonus!)" : ""));
        if (hiddenFired) {
            player.sendMessage("  §d✦ §7Hidden trigger activated!");
        }
        player.sendMessage("");
        player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // ── Cape checks ───────────────────────────────────────────────────────
        checkCapes(player);

        // ── Autosave ──────────────────────────────────────────────────────────
        saveAll();
    }

    // ── Completion counts ─────────────────────────────────────────────────────

    /** Number of main quests (id 1–150) completed by this player. */
    public int countMain(UUID uuid) {
        Set<Integer> done = doneCache.getOrDefault(uuid, Set.of());
        int count = 0;
        for (int id : done) if (id >= 1 && id <= 150) count++;
        return count;
    }

    /** Number of secret quests (id 151–300) completed by this player. */
    public int countSecret(UUID uuid) {
        Set<Integer> done = doneCache.getOrDefault(uuid, Set.of());
        int count = 0;
        for (int id : done) if (id >= 151 && id <= 300) count++;
        return count;
    }

    // ── Cape award ────────────────────────────────────────────────────────────

    private void checkCapes(Player player) {
        UUID uuid = player.getUniqueId();
        NamespacedKey questCapeKey = new NamespacedKey(plugin, PDC_QUEST_CAPE);
        NamespacedKey bossCapeKey  = new NamespacedKey(plugin, PDC_BOSS_QUEST_CAPE);

        // Main cape (150/150 main quests)
        if (countMain(uuid) >= 150) {
            byte current = player.getPersistentDataContainer()
                    .getOrDefault(questCapeKey, PersistentDataType.BYTE, (byte) 0);
            if (current == 0) {
                player.getPersistentDataContainer()
                        .set(questCapeKey, PersistentDataType.BYTE, (byte) 1);
                announceCapeMilestone(player, false);
            }
        }

        // Boss quest cape (150/150 secret quests)
        if (countSecret(uuid) >= 150) {
            byte current = player.getPersistentDataContainer()
                    .getOrDefault(bossCapeKey, PersistentDataType.BYTE, (byte) 0);
            if (current == 0) {
                player.getPersistentDataContainer()
                        .set(bossCapeKey, PersistentDataType.BYTE, (byte) 1);
                announceCapeMilestone(player, true);
            }
        }
    }

    private void announceCapeMilestone(Player player, boolean boss) {
        if (boss) {
            player.sendMessage("§4★ §c§lBOSS QUEST CAPE §4★");
            player.sendMessage("§7You have completed §c§lall 150 secret quests§7!");
            player.sendMessage("§7The ground trembles as fire erupts around you...");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.8f);
        } else {
            player.sendMessage("§6★ §e§lQUEST SKILL CAPE §6★");
            player.sendMessage("§7You have completed §e§lall 150 main quests§7!");
            player.sendMessage("§7The Quest Skill Cape is now yours. Equip it via §e/cape§7.");
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        // Broadcast server-wide
        String msg = boss
                ? "§4★ §c" + player.getName() + " §7has earned the §cBoss Quest Cape§7! §4★"
                : "§6★ §e" + player.getName() + " §7has earned the §eQuest Skill Cape§7! §6★";
        plugin.getServer().broadcastMessage(msg);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Count how many of this material are in the player's inventory. */
    public int countItem(Player player, org.bukkit.Material mat) {
        int count = 0;
        for (ItemStack is : player.getInventory().getContents()) {
            if (is != null && is.getType() == mat) count += is.getAmount();
        }
        return count;
    }

    /** Remove up to 'amount' of the given material from inventory. */
    private void removeItems(Player player, org.bukkit.Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack is = contents[i];
            if (is == null || is.getType() != mat) continue;
            if (is.getAmount() <= remaining) {
                remaining -= is.getAmount();
                contents[i] = null;
            } else {
                is.setAmount(is.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        // kills
        killData = new YamlConfiguration();
        for (var entry : killCache.entrySet()) {
            String uuid = entry.getKey().toString();
            for (var mobEntry : entry.getValue().entrySet()) {
                killData.set("kills." + uuid + "." + mobEntry.getKey(), mobEntry.getValue());
            }
        }
        save(killData, killFile);

        // progress
        progressData = new YamlConfiguration();
        for (var entry : doneCache.entrySet()) {
            String uuid = entry.getKey().toString();
            String ids = entry.getValue().stream()
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "," + b).orElse("");
            progressData.set("done." + uuid, ids);
        }
        save(progressData, progressFile);
    }

    private void load() {
        killData     = YamlConfiguration.loadConfiguration(killFile);
        progressData = YamlConfiguration.loadConfiguration(progressFile);

        // kills
        if (killData.isConfigurationSection("kills")) {
            for (String uuidStr : killData.getConfigurationSection("kills").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Integer> map = new HashMap<>();
                    var section = killData.getConfigurationSection("kills." + uuidStr);
                    if (section != null) {
                        for (String mob : section.getKeys(false)) {
                            map.put(mob, killData.getInt("kills." + uuidStr + "." + mob, 0));
                        }
                    }
                    killCache.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // progress
        if (progressData.isConfigurationSection("done")) {
            for (String uuidStr : progressData.getConfigurationSection("done").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String raw = progressData.getString("done." + uuidStr, "");
                    Set<Integer> ids = new HashSet<>();
                    if (!raw.isEmpty()) {
                        for (String s : raw.split(",")) {
                            try { ids.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
                        }
                    }
                    doneCache.put(uuid, ids);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("[NpcQuestManager] Loaded data for "
                + killCache.size() + " player kill records and "
                + doneCache.size() + " progress records.");
    }

    private void save(YamlConfiguration yaml, File file) {
        try { yaml.save(file); }
        catch (IOException e) { plugin.getLogger().warning("[NpcQuestManager] Save failed: " + e.getMessage()); }
    }
}
