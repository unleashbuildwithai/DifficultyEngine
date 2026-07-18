package com.yourname.difficulty.quests;

import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * QuestManager — tracks per-player quest progress in questdata.yml.
 *
 * YAML path: quests.[playerUUID].[QUEST_NAME].progress = int
 *            quests.[playerUUID].[QUEST_NAME].completions = int
 */
public class QuestManager {

    private final JavaPlugin   plugin;
    private final GoldManager  goldManager;
    private final SkillManager skillManager;
    private final ItemFactory  itemFactory;

    private final File                dataFile;
    private YamlConfiguration         data;

    /** In-memory "claimable" flags (cleared after claiming) */
    private final Set<String> claimable = new HashSet<>(); // "uuid:questName"

    public QuestManager(JavaPlugin plugin, GoldManager goldManager,
                        SkillManager skillManager, ItemFactory itemFactory) {
        this.plugin       = plugin;
        this.goldManager  = goldManager;
        this.skillManager = skillManager;
        this.itemFactory  = itemFactory;
        this.dataFile     = new File(plugin.getDataFolder(), "questdata.yml");
        load();
    }

    private void load() {
        plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveAll() {
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ── Progress ──────────────────────────────────────────────────────────────

    private String path(UUID uuid, QuestType q, String key) {
        return "quests." + uuid + "." + q.name() + "." + key;
    }

    public int getProgress(UUID uuid, QuestType q) {
        return data.getInt(path(uuid, q, "progress"), 0);
    }

    public int getCompletions(UUID uuid, QuestType q) {
        return data.getInt(path(uuid, q, "completions"), 0);
    }

    public boolean isPermaDone(UUID uuid, QuestType q) {
        return !q.repeatable && getCompletions(uuid, q) >= 1;
    }

    public boolean isClaimable(UUID uuid, QuestType q) {
        return claimable.contains(uuid + ":" + q.name());
    }

    /**
     * Increments progress. If target reached and not already claimable, marks as
     * claimable and sends a notification to the player.
     *
     * @return true if the quest is now ready to claim (just hit target)
     */
    public boolean incrementProgress(Player player, QuestType q) {
        UUID uuid = player.getUniqueId();
        if (isPermaDone(uuid, q)) return false;
        if (isClaimable(uuid, q)) return false;

        int current = getProgress(uuid, q) + 1;
        data.set(path(uuid, q, "progress"), current);

        if (current >= q.targetCount) {
            claimable.add(uuid + ":" + q.name());
            player.sendMessage("");
            player.sendMessage("§6[Quest] §e" + q.displayName + " §7completed!");
            player.sendMessage("§7Open §e/questbook §7to claim your reward!");
            player.sendMessage("");
            player.playSound(player.getLocation(),
                org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            return true;
        }

        // Progress notification every 10 kills or at 50%
        if (current % 10 == 0 || current == q.targetCount / 2) {
            player.sendActionBar("§6[Quest] §e" + q.displayName
                    + " §7- §a" + current + "/" + q.targetCount);
        }
        return false;
    }

    /**
     * Claims the reward for a completed quest.
     * Gives items, gold, and XP to the player.
     */
    public void claimReward(Player player, QuestType q) {
        UUID uuid = player.getUniqueId();
        if (!isClaimable(uuid, q)) return;

        claimable.remove(uuid + ":" + q.name());

        // Increment completions
        int comp = getCompletions(uuid, q) + 1;
        data.set(path(uuid, q, "completions"), comp);

        // Reset progress (repeatable = 0, permanent = target (locked))
        data.set(path(uuid, q, "progress"), q.repeatable ? 0 : q.targetCount);

        // Parse and award rewards
        awardRewards(player, q);

        player.sendMessage("§6[Quest] §aReward claimed for §e" + q.displayName + "§a!");
    }

    // ── Reward parsing ────────────────────────────────────────────────────────

    private void awardRewards(Player player, QuestType q) {
        for (String token : q.rewardSpec.split(",")) {
            String[] parts = token.split(":");
            if (parts.length < 2) continue;
            String type = parts[0].trim();
            int    val  = Integer.parseInt(parts[1].trim());

            switch (type) {
                case "GOLD"        -> goldManager.award(player, val);
                case "XP"          -> skillManager.addXp(player.getUniqueId(), SkillType.MAGIC, val);
                case "FIRE_RUNE"   -> giveRune(player, MagicElement.FIRE,  val);
                case "WATER_RUNE"  -> giveRune(player, MagicElement.WATER, val);
                case "EARTH_RUNE"  -> giveRune(player, MagicElement.EARTH, val);
                case "AIR_RUNE"    -> giveRune(player, MagicElement.AIR,   val);
            }
        }
        player.sendMessage("§7Rewards:");
        for (String token : q.rewardSpec.split(",")) {
            String[] p = token.split(":");
            player.sendMessage("  §e+" + p[1] + " §6" + p[0]);
        }
    }

    private void giveRune(Player player, MagicElement el, int count) {
        // Give in stacks of 64
        while (count > 0) {
            int stack = Math.min(64, count);
            player.getInventory().addItem(itemFactory.buildRune(el, stack));
            count -= stack;
        }
    }
}
