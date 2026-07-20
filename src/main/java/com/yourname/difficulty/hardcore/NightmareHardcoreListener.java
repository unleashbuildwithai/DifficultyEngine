package com.yourname.difficulty.hardcore;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * NightmareHardcoreListener — Manages the "Nightmare Hardcore" opt-in mode.
 *
 * ── What it does ─────────────────────────────────────────────────────────
 *  • Tracks which players have activated Hardcore mode via their UUID in
 *    plugins/DifficultyEngine/hardcore.yml
 *
 *  • On PlayerDeathEvent (Nightmare Hardcore players only):
 *      1. Reset ALL skill levels to 1 (SkillManager.resetAll)
 *      2. Wipe gold balance (GoldManager.setBalance → 0)
 *      3. Server-wide death broadcast: "§4☠ [Player] died in NM Hardcore!"
 *      4. 30-second spectate window before respawn
 *
 *  • Survivor Tokens: Every 30 minutes survived in HC mode, the player
 *    receives a special "Survivor Token" item in their inventory.
 *
 * ── Activation ───────────────────────────────────────────────────────────
 *  Players must be on NIGHTMARE difficulty to activate HC mode.
 *  Toggle via /difficulty nightmare_hardcore (confirmed with 10s prompt).
 *  Dropping back to /difficulty nightmare deactivates HC (no skill restore).
 *
 * ── Persistence ─────────────────────────────────────────────────────────
 *  Stored in plugins/DifficultyEngine/hardcore.yml
 *  Keys: <uuid> → true/false + minutesSurvived
 */
public class NightmareHardcoreListener implements Listener {

    private final JavaPlugin              plugin;
    private final PlayerDifficultyManager difficultyManager;
    private final SkillManager            skillManager;
    private final GoldManager             goldManager;

    /** UUID → true if hardcore mode is active. */
    private final Set<UUID> hardcoreSet = new HashSet<>();

    /** UUID → epoch ms when they activated hardcore (for survival timer). */
    private final Map<UUID, Long> activationTime = new HashMap<>();

    /** UUID → total minutes survived across all sessions. */
    private final Map<UUID, Integer> minutesSurvived = new HashMap<>();

    /** Players who are in the 30-second post-death spectate window. */
    private final Set<UUID> inDeathSpectate = new HashSet<>();

    /** Players waiting to confirm HC activation (within 10s window). */
    private final Set<UUID> pendingConfirmation = new HashSet<>();

    private final File dataFile;
    private final NamespacedKey survivorTokenKey;

    /** Survivor Token award interval in minutes. */
    private static final int TOKEN_INTERVAL_MINUTES = 30;

    public NightmareHardcoreListener(JavaPlugin plugin,
                                      PlayerDifficultyManager difficultyManager,
                                      SkillManager skillManager,
                                      GoldManager goldManager) {
        this.plugin            = plugin;
        this.difficultyManager = difficultyManager;
        this.skillManager      = skillManager;
        this.goldManager       = goldManager;
        this.dataFile          = new File(plugin.getDataFolder(), "hardcore.yml");
        this.survivorTokenKey  = new NamespacedKey(plugin, "de_survivor_token");
        load();
        startSurvivorTask();
    }

    // ── Activation API ────────────────────────────────────────────────────────

    /**
     * Initiates the 10-second confirmation prompt for a player.
     * They must type /difficulty nightmare_hardcore again within 10s.
     *
     * @return false if player is not on NIGHTMARE difficulty
     */
    public boolean requestActivation(Player player) {
        if (difficultyManager.getDifficulty(player.getUniqueId()) != DifficultyLevel.NIGHTMARE) {
            player.sendMessage("§c✗ §7You must be on §4Nightmare §7difficulty to enable Hardcore mode.");
            return false;
        }
        if (isHardcore(player.getUniqueId())) {
            player.sendMessage("§c✗ §7Hardcore mode is already §4ACTIVE§c.");
            return false;
        }

        pendingConfirmation.add(player.getUniqueId());

        player.sendMessage("");
        player.sendMessage("§4§l⚠ WARNING — NIGHTMARE HARDCORE MODE ⚠");
        player.sendMessage("§7If you die in Hardcore mode:");
        player.sendMessage("§c  • ALL skill levels reset to 1");
        player.sendMessage("§c  • ALL gold is wiped");
        player.sendMessage("§c  • 30-second spectate penalty");
        player.sendMessage("§7Rewards: §eSurvivor Tokens §7every 30 minutes survived.");
        player.sendMessage("");
        player.sendMessage("§7Type §e/difficulty nightmare_hardcore §7again within §c10 seconds §7to confirm.");
        player.sendMessage("");

        // Auto-cancel after 10 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                pendingConfirmation.remove(player.getUniqueId()), 200L);

        return true;
    }

    /**
     * Activates hardcore mode for the player.
     * Should be called when the player confirms within 10 seconds.
     *
     * @return true if activated, false if no pending confirmation
     */
    public boolean confirmActivation(Player player) {
        if (!pendingConfirmation.contains(player.getUniqueId())) {
            return requestActivation(player); // Start the prompt instead
        }

        pendingConfirmation.remove(player.getUniqueId());
        hardcoreSet.add(player.getUniqueId());
        activationTime.put(player.getUniqueId(), System.currentTimeMillis());
        save();

        player.sendMessage("§4☠ §c§lNIGHTMARE HARDCORE ACTIVATED! §4☠");
        player.sendMessage("§7Good luck. You'll need it.");
        player.sendTitle("§4§l☠ HARDCORE MODE ☠", "§cNo mercy. No retreat.", 5, 60, 10);
        return true;
    }

    /**
     * Deactivates hardcore mode for the player.
     * Called when they drop back to /difficulty nightmare.
     */
    public void deactivate(Player player) {
        if (!hardcoreSet.contains(player.getUniqueId())) return;

        hardcoreSet.remove(player.getUniqueId());
        long activeMs = System.currentTimeMillis()
                - activationTime.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        activationTime.remove(player.getUniqueId());
        save();

        int minutesActive = (int)(activeMs / 60_000);
        player.sendMessage("§7Nightmare Hardcore deactivated. You survived §e"
                + minutesActive + " minutes§7 this session.");
    }

    /** Returns true if the player has Nightmare Hardcore mode active. */
    public boolean isHardcore(UUID uuid) {
        return hardcoreSet.contains(uuid);
    }

    // ── PlayerDeathEvent ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (!isHardcore(uuid)) return;

        // ── 1. Reset all skills to level 1 ────────────────────────────────
        for (SkillType skill : SkillType.values()) {
            long currentXp = skillManager.getXp(uuid, skill);
            if (currentXp > 0) {
                // Set to 1 XP (level 1)
                skillManager.addXp(uuid, skill, -currentXp + 1);
            }
        }

        // ── 2. Wipe gold balance ──────────────────────────────────────────
        long goldLost = goldManager.getBalance(uuid);
        goldManager.spendGold(uuid, goldLost);

        // ── 3. Server-wide broadcast ─────────────────────────────────────
        String deathMsg = "§4☠ §c" + player.getName()
                + " §7perished in §4§lNightmare Hardcore §7mode and lost everything!";
        plugin.getServer().broadcastMessage(deathMsg);
        if (goldLost > 0) {
            plugin.getServer().broadcastMessage("§8  └ §7Lost §e"
                    + GoldManager.formatGold(goldLost) + " gp §7and all skill levels.");
        }

        // ── 4. Custom death message ───────────────────────────────────────
        event.setDeathMessage("§4☠ §c" + player.getName()
                + " §7died in §4Nightmare Hardcore");

        // ── 5. Reset activation timer ────────────────────────────────────
        activationTime.put(uuid, System.currentTimeMillis());
        save();

        // ── 6. 30-second spectate penalty ────────────────────────────────
        inDeathSpectate.add(uuid);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            inDeathSpectate.remove(uuid);
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.SURVIVAL);
                p.sendMessage("§a✓ §7Spectate period over. You may now respawn.");
                p.sendMessage("§7All skills have been reset to level 1.");
                p.sendMessage("§7Use §e/skills §7to see your current levels.");
            }
        }, 600L); // 30 seconds = 600 ticks

        // Force spectator mode after 1 tick (death processing finishes)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Player p = plugin.getServer().getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.SPECTATOR);
                p.sendMessage("");
                p.sendMessage("§4☠ §c§lYOU DIED IN HARDCORE MODE §4☠");
                p.sendMessage("§7Spectating for §c30 seconds§7...");
                p.sendMessage("§7Skills reset: §cAll skills → Level 1");
                p.sendMessage("§7Gold lost:    §e" + GoldManager.formatGold(goldLost) + " gp");
                p.sendMessage("");
            }
        }, 1L);
    }

    // ── Join event — re-apply spectate if player was in death window ──────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!isHardcore(uuid)) return;

        // Inform player they're in hardcore mode
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            int survived = minutesSurvived.getOrDefault(uuid, 0);
            player.sendMessage("§4☠ §c§lNightmare Hardcore Mode Active");
            player.sendMessage("§7Total time survived: §e" + survived + " minutes");
            player.sendMessage("§7Next Survivor Token in: §e"
                    + (TOKEN_INTERVAL_MINUTES - (survived % TOKEN_INTERVAL_MINUTES)) + " minutes");
        }, 20L);
    }

    // ── Survivor Token task ───────────────────────────────────────────────────

    private void startSurvivorTask() {
        // Check every minute for HC players who've earned a Survivor Token
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : new HashSet<>(hardcoreSet)) {
                Player p = plugin.getServer().getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;

                long startMs = activationTime.getOrDefault(uuid, now);
                int totalMinutes = (int)((now - startMs) / 60_000);

                int prev = minutesSurvived.getOrDefault(uuid, 0);
                minutesSurvived.put(uuid, totalMinutes);

                // Award a token for each new 30-minute milestone
                int prevTokens = prev / TOKEN_INTERVAL_MINUTES;
                int newTokens  = totalMinutes / TOKEN_INTERVAL_MINUTES;
                if (newTokens > prevTokens) {
                    ItemStack token = buildSurvivorToken(totalMinutes);
                    p.getInventory().addItem(token);
                    p.sendMessage("§6✦ §e§lSurvivor Token awarded! §7("
                            + totalMinutes + " minutes survived)");
                    p.sendTitle("§6§l✦ TOKEN EARNED ✦",
                            "§7" + totalMinutes + " minutes in Nightmare Hardcore", 5, 60, 10);
                    save();
                }
            }
        }, 1200L, 1200L); // every 60 seconds (check every minute)
    }

    // ── Survivor Token item ───────────────────────────────────────────────────

    public ItemStack buildSurvivorToken(int minutesSurvived) {
        ItemStack token = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = token.getItemMeta();
        if (m != null) {
            m.setDisplayName("§6§l✦ Survivor Token");
            m.setLore(List.of(
                "§7Earned for surviving §e" + minutesSurvived + " minutes",
                "§7in §4§lNightmare Hardcore §7mode.",
                "",
                "§7Trade at the VIP Shop for",
                "§7exclusive cosmetics and rewards.",
                "",
                "§8[DifficultyEngine — Hardcore]"
            ));
            m.getPersistentDataContainer().set(survivorTokenKey,
                    PersistentDataType.INTEGER, minutesSurvived);
            token.setItemMeta(m);
        }
        return token;
    }

    /** Returns true if the item is a Survivor Token. */
    public boolean isSurvivorToken(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(survivorTokenKey, PersistentDataType.INTEGER);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (UUID uuid : hardcoreSet) {
            cfg.set("players." + uuid + ".active", true);
            cfg.set("players." + uuid + ".activationMs",
                    activationTime.getOrDefault(uuid, System.currentTimeMillis()));
            cfg.set("players." + uuid + ".minutesSurvived",
                    minutesSurvived.getOrDefault(uuid, 0));
        }
        try { cfg.save(dataFile); }
        catch (IOException e) {
            plugin.getLogger().warning("[Hardcore] Failed to save: " + e.getMessage());
        }
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        if (!cfg.isConfigurationSection("players")) return;
        for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                if (cfg.getBoolean("players." + key + ".active", false)) {
                    hardcoreSet.add(uuid);
                    activationTime.put(uuid, cfg.getLong("players." + key + ".activationMs",
                            System.currentTimeMillis()));
                    minutesSurvived.put(uuid, cfg.getInt("players." + key + ".minutesSurvived", 0));
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }
}
