package com.yourname.difficulty.skills;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SkillManager — Persistent per-player skill XP storage.
 *
 * Data is saved to plugins/DifficultyEngine/skills.yml.
 * Format:
 *   skills:
 *     <uuid>:
 *       MELEE: 1234
 *       RANGED: 567
 *       ...
 */
public class SkillManager {

    private final JavaPlugin plugin;
    private final File       dataFile;

    /** uuid → (SkillType → totalXp) */
    private final Map<UUID, Map<SkillType, Long>> skillData = new HashMap<>();

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public SkillManager(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "skills.yml");
        loadAll();
    }

    // ── XP API ────────────────────────────────────────────────────────────────

    /** Returns total accumulated XP for the given skill. */
    public long getXp(UUID uuid, SkillType skill) {
        return skillData
                .getOrDefault(uuid, Map.of())
                .getOrDefault(skill, 0L);
    }

    /** Returns the current level (1–99) for the given skill. */
    public int getLevel(UUID uuid, SkillType skill) {
        return SkillLevel.getLevelForXp(getXp(uuid, skill));
    }

    /**
     * Adds XP to the given skill.
     * Returns the new level after the addition (for level-up detection).
     */
    public int addXp(UUID uuid, SkillType skill, long amount) {
        int oldLevel = getLevel(uuid, skill);

        skillData.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class))
                 .merge(skill, amount, Long::sum);

        int newLevel = getLevel(uuid, skill);

        // Autosave — not every tick, but on each XP gain.
        // In a high-load scenario you'd batch saves; for a custom plugin this is fine.
        saveAll();

        return newLevel;
    }

    /** Returns the sum of all skill levels (like RS Total Level). */
    public int getTotalLevel(UUID uuid) {
        int total = 0;
        for (SkillType skill : SkillType.values()) {
            total += getLevel(uuid, skill);
        }
        return total;
    }

    /** Returns true if ALL skills are level 99. */
    public boolean isMaxed(UUID uuid) {
        for (SkillType skill : SkillType.values()) {
            if (getLevel(uuid, skill) < SkillLevel.MAX_LEVEL) return false;
        }
        return true;
    }

    /**
     * Instantly sets a skill to Level 99 by writing the max XP value.
     * Used by the admin cape equip perk (difficultyengine.cape.admin).
     */
    public void setToMax(UUID uuid, SkillType skill) {
        long maxXp = SkillLevel.XP_TABLE[SkillLevel.MAX_LEVEL - 1]; // 13,034,431
        skillData.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class))
                 .put(skill, maxXp);
        saveAll();
    }

    /**
     * Instantly sets ALL skills to Level 99.
     * Used by the admin Max Cape equip perk.
     */
    public void setAllToMax(UUID uuid) {
        for (SkillType skill : SkillType.values()) {
            setToMax(uuid, skill);
        }
    }

    /**
     * Sets a skill to an exact level (1–99) by writing the corresponding XP value.
     * Used by the /skilllvl admin command.
     *
     * @param uuid  the player's UUID
     * @param skill the skill to update
     * @param level the target level (clamped to 1–99)
     */
    public void setLevel(UUID uuid, SkillType skill, int level) {
        int clamped = Math.max(1, Math.min(SkillLevel.MAX_LEVEL, level));
        long xp = SkillLevel.getXpForLevel(clamped);
        skillData.computeIfAbsent(uuid, k -> new EnumMap<>(SkillType.class))
                 .put(skill, xp);
        saveAll();
    }

    // ── Account-sharing support ───────────────────────────────────────────────

    /**
     * Returns a defensive copy of this player's full skill-XP map.
     * Used by {@code AccountProfileManager} to snapshot a profile before
     * switching to another shared-account profile.
     */
    public Map<SkillType, Long> getAllXp(UUID uuid) {
        return new EnumMap<>(skillData.getOrDefault(uuid, Map.of()));
    }

    /**
     * Overwrites this player's ENTIRE skill-XP map with the given data,
     * replacing whatever was there before. Used by {@code AccountProfileManager}
     * when switching between shared-account profiles.
     */
    public void setAllXp(UUID uuid, Map<SkillType, Long> xpMap) {
        skillData.put(uuid, new EnumMap<>(xpMap));
        saveAll();
    }


    // ── Persistence ───────────────────────────────────────────────────────────

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<SkillType, Long>> playerEntry : skillData.entrySet()) {
            String uuidStr = playerEntry.getKey().toString();
            for (Map.Entry<SkillType, Long> skillEntry : playerEntry.getValue().entrySet()) {
                yaml.set("skills." + uuidStr + "." + skillEntry.getKey().name(),
                         skillEntry.getValue());
            }
        }
        try {
            yaml.save(dataFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[SkillManager] Could not save skills.yml: " + ex.getMessage());
        }
    }

    private void loadAll() {
        if (!dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);

        if (!yaml.isConfigurationSection("skills")) return;

        for (String uuidStr : yaml.getConfigurationSection("skills").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                Map<SkillType, Long> map = new EnumMap<>(SkillType.class);
                for (SkillType skill : SkillType.values()) {
                    long xp = yaml.getLong("skills." + uuidStr + "." + skill.name(), 0L);
                    if (xp > 0) map.put(skill, xp);
                }
                if (!map.isEmpty()) skillData.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }

        plugin.getLogger().info("[SkillManager] Loaded skill data for "
                + skillData.size() + " player(s).");
    }
}
