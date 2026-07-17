package com.yourname.difficulty;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Stores each player's chosen DifficultyLevel and persists it to disk.
 * Data file: plugins/DifficultyEngine/player_data.yml
 */
public class PlayerDifficultyManager {

    private final Main plugin;
    private final File dataFile;
    private final Map<UUID, DifficultyLevel> data = new HashMap<>();

    /**
     * Players who have opted into the live HP display above mob heads.
     * Stored in memory only — resets on server restart (intentional: it's a
     * session preference, not something that needs to persist).
     */
    private final Set<UUID> hpDisplayEnabled = new HashSet<>();

    public PlayerDifficultyManager(Main plugin) {
        this.plugin = plugin;

        // Ensure the data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.dataFile = new File(plugin.getDataFolder(), "player_data.yml");
        loadAll();
    }

    /** Returns the difficulty for a player UUID. Defaults to EASY if not set. */
    public DifficultyLevel getDifficulty(UUID uuid) {
        return data.getOrDefault(uuid, DifficultyLevel.EASY);
    }

    // -------------------------------------------------------------------------
    // HP display toggle
    // -------------------------------------------------------------------------

    /**
     * Flips the HP display flag for a player.
     *
     * @return {@code true} if the display is now ON, {@code false} if now OFF.
     */
    public boolean toggleHpDisplay(UUID uuid) {
        if (hpDisplayEnabled.contains(uuid)) {
            hpDisplayEnabled.remove(uuid);
            return false;
        }
        hpDisplayEnabled.add(uuid);
        return true;
    }

    /** Returns {@code true} if the player has HP display enabled. */
    public boolean isHpDisplayEnabled(UUID uuid) {
        return hpDisplayEnabled.contains(uuid);
    }

    // -------------------------------------------------------------------------
    // Difficulty persistence
    // -------------------------------------------------------------------------

    /** Sets and immediately persists a player's difficulty. */
    public void setDifficulty(UUID uuid, DifficultyLevel level) {
        data.put(uuid, level);
        saveAll();
    }

    /** Saves all player data to disk. */
    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, DifficultyLevel> entry : data.entrySet()) {
            yaml.set("players." + entry.getKey().toString(), entry.getValue().name());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save player_data.yml: " + e.getMessage());
        }
    }

    /** Loads all player data from disk. */
    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        if (!yaml.isConfigurationSection("players")) return;

        for (String key : yaml.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String levelName = yaml.getString("players." + key, "EASY");
                DifficultyLevel level = DifficultyLevel.valueOf(levelName);
                data.put(uuid, level);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed entries
            }
        }
        plugin.getLogger().info("Loaded difficulty data for " + data.size() + " player(s).");
    }

    public Main getPlugin() {
        return plugin;
    }
}
