package com.yourname.difficulty;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PlayerDifficultyManager — Persistent per-player data store.
 *
 * Manages three types of player state:
 *  1. Difficulty level (PEACEFUL / EASY / MEDIUM / HARD / NIGHTMARE).
 *  2. HP bar display toggle.
 *  3. Soulfur Curse expiry — a timestamp (epoch ms) set when a player dies
 *     with 50+ Soulfur Potion sips. The curse lasts 24 real hours and
 *     persists across server restarts by being written to player_data.yml.
 *
 * NIGHTMARE PDC tag:
 *   Written directly to the online player entity whenever their difficulty
 *   is NIGHTMARE so NightmareAggroListener can do a fast PDC check.
 */
public class PlayerDifficultyManager {

    private final Main plugin;
    private final File dataFile;
    private final Map<UUID, DifficultyLevel> data           = new HashMap<>();
    private final Set<UUID>                  hpDisplayEnabled = new HashSet<>();

    /** Soulfur Curse expiry — epoch milliseconds. 0 = not cursed. */
    private final Map<UUID, Long> cursedUntil = new HashMap<>();

    private final NamespacedKey nightmareKey;

    public PlayerDifficultyManager(Main plugin) {
        this.plugin       = plugin;
        this.nightmareKey = new NamespacedKey(plugin, "nightmare_status");

        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        this.dataFile = new File(plugin.getDataFolder(), "player_data.yml");
        loadAll();
    }

    // ── Difficulty ────────────────────────────────────────────────────────────

    public DifficultyLevel getDifficulty(UUID uuid) {
        return data.getOrDefault(uuid, DifficultyLevel.EASY);
    }

    public void setDifficulty(UUID uuid, DifficultyLevel level) {
        data.put(uuid, level);
        saveAll();
        Player onlinePlayer = plugin.getServer().getPlayer(uuid);
        if (onlinePlayer != null) syncNightmareTag(onlinePlayer, level);
    }

    public void syncNightmareTag(Player player, DifficultyLevel level) {
        if (level == DifficultyLevel.NIGHTMARE) {
            player.getPersistentDataContainer()
                  .set(nightmareKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            player.getPersistentDataContainer().remove(nightmareKey);
        }
    }

    public boolean isNightmareTagged(Player player) {
        return player.getPersistentDataContainer()
                     .has(nightmareKey, PersistentDataType.BYTE);
    }

    // ── HP bar toggle ─────────────────────────────────────────────────────────

    public boolean toggleHpDisplay(UUID uuid) {
        boolean nowOn;
        if (hpDisplayEnabled.contains(uuid)) {
            hpDisplayEnabled.remove(uuid);
            nowOn = false;
        } else {
            hpDisplayEnabled.add(uuid);
            nowOn = true;
        }
        saveAll();
        return nowOn;
    }

    public boolean isHpDisplayEnabled(UUID uuid) {
        return hpDisplayEnabled.contains(uuid);
    }

    // ── Soulfur Curse ─────────────────────────────────────────────────────────

    /**
     * Sets the curse expiry to {@code now + 24 hours} for the given player
     * and immediately persists it.
     */
    public void setCursed(UUID uuid) {
        long expiry = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        cursedUntil.put(uuid, expiry);
        saveAll();
    }

    /**
     * Returns {@code true} if the player currently has an active Soulfur Curse
     * (expiry timestamp is in the future).
     */
    public boolean isCursed(UUID uuid) {
        Long expiry = cursedUntil.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            cursedUntil.remove(uuid); // auto-expire
            return false;
        }
        return true;
    }

    /** Removes the curse immediately (e.g. for admin commands). */
    public void clearCurse(UUID uuid) {
        cursedUntil.remove(uuid);
        saveAll();
    }

    /** Returns the raw expiry epoch ms, or 0 if not cursed. */
    public long getCursedUntil(UUID uuid) {
        return cursedUntil.getOrDefault(uuid, 0L);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<UUID, DifficultyLevel> e : data.entrySet()) {
            yaml.set("players." + e.getKey(), e.getValue().name());
        }
        for (UUID uuid : hpDisplayEnabled) {
            yaml.set("hpbar." + uuid, true);
        }
        for (Map.Entry<UUID, Long> e : cursedUntil.entrySet()) {
            // Only save unexpired entries
            if (e.getValue() > System.currentTimeMillis()) {
                yaml.set("cursed." + e.getKey(), e.getValue());
            }
        }

        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save player_data.yml: " + ex.getMessage());
        }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        if (yaml.isConfigurationSection("players")) {
            for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    DifficultyLevel level = DifficultyLevel.valueOf(
                            yaml.getString("players." + key, "EASY"));
                    data.put(uuid, level);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (yaml.isConfigurationSection("hpbar")) {
            for (String key : yaml.getConfigurationSection("hpbar").getKeys(false)) {
                try {
                    if (yaml.getBoolean("hpbar." + key, false))
                        hpDisplayEnabled.add(UUID.fromString(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        if (yaml.isConfigurationSection("cursed")) {
            long now = System.currentTimeMillis();
            for (String key : yaml.getConfigurationSection("cursed").getKeys(false)) {
                try {
                    long expiry = yaml.getLong("cursed." + key, 0L);
                    if (expiry > now) cursedUntil.put(UUID.fromString(key), expiry);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("Loaded difficulty data for " + data.size() +
                " player(s). HP bar: " + hpDisplayEnabled.size() +
                ", cursed: " + cursedUntil.size() + ".");
    }

    public Main getPlugin()            { return plugin; }
    public NamespacedKey getNightmareKey() { return nightmareKey; }
}
