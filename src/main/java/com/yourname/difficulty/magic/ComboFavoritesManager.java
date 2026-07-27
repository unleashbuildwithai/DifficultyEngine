package com.yourname.difficulty.magic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ComboFavoritesManager — Tracks which passive elemental procs each player
 * has favorited (starred), boosting their proc chance from 15% to 30%.
 *
 * ── REWORK NOTE ────────────────────────────────────────────────────────────
 * The old multi-hit combo-chain system (WET_CHAIN, MUDDY_CHAIN, STATUE_CHAIN,
 * FROZEN_CHAIN, BLAZING_CHAIN, SCORCHED_CHAIN, CHILLED_CHAIN, EARTH_TRAP) has
 * been REMOVED ENTIRELY per user direction. This manager now only tracks the
 * 4 passive elemental proc tags (FIRE_PROC/WATER_PROC/EARTH_PROC/AIR_PROC).
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  Each player has a Set<String> of favorited proc tags (e.g. "FIRE_PROC").
 *  Favoriting a proc boosts its dice-roll chance from 15% to 30% (see
 *  ElementalProcManager). Nothing favorited = every proc still rolls at the
 *  base 15% rate (favorites are a pure bonus, never a gate).
 *
 * ── Persistence ───────────────────────────────────────────────────────────────
 *  plugins/DifficultyEngine/combo_favorites.yml
 */
public class ComboFavoritesManager {

    // ── Passive elemental procs (the only special-effect system left) ────────
    // Each fires on ANY basic hit with the matching element (no combo needed),
    // gated behind an Arcane Tome proc-page + a Magic level requirement.
    // See ElementalProcManager for the actual dice-roll + effect logic.
    public static final String FIRE_PROC  = "FIRE_PROC";
    public static final String WATER_PROC = "WATER_PROC";
    public static final String EARTH_PROC = "EARTH_PROC";
    public static final String AIR_PROC   = "AIR_PROC";

    /** Ordered list of PASSIVE PROC tags — used by the GUI to build the row of items. */
    public static final List<String> PROC_TAGS = List.of(
        FIRE_PROC, WATER_PROC, EARTH_PROC, AIR_PROC
    );

    /**
     * Maps each passive PROC tag to the Arcane Tome page index (0-based) that
     * unlocks it. Mirrors ElementalProcManager's FIRE_PAGE/WATER_PAGE/etc.
     * constants (pages 42-45 in the book, 0-indexed 41-44).
     */
    public static final Map<String, Integer> PROC_REQUIRED_PAGE = Map.of(
        FIRE_PROC,  41,
        WATER_PROC, 42,
        EARTH_PROC, 43,
        AIR_PROC,   44
    );

    /** Simple display-info record for a proc tag — used by the GUI listener for action bar text. */
    public record ChainInfo(String displayName) {}

    /** Returns display info (name) for the given proc tag. Falls back to the raw tag if unknown. */
    public static ChainInfo getInfo(String tag) {
        String name = switch (tag) {
            case FIRE_PROC  -> "§cFire Proc: Burn";
            case WATER_PROC -> "§bWater Proc: Wet";
            case EARTH_PROC -> "§2Earth Proc: Muddy";
            case AIR_PROC   -> "§fAir Proc: Chilled/Frozen";
            default -> tag;
        };
        return new ChainInfo(name);
    }

    // ── Instance fields ───────────────────────────────────────────────────────


    private final JavaPlugin              plugin;
    /** player UUID → set of favorited proc tags */
    private final Map<UUID, Set<String>>  favorites = new HashMap<>();
    private       File                    dataFile;
    private       YamlConfiguration       dataCfg;

    public ComboFavoritesManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "combo_favorites.yml");
        dataCfg  = YamlConfiguration.loadConfiguration(dataFile);

        var section = dataCfg.getConfigurationSection("favorites");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    List<String> tags = dataCfg.getStringList("favorites." + key);
                    favorites.put(uuid, new HashSet<>(tags));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        dataCfg.set("favorites", null);
        for (var entry : favorites.entrySet()) {
            dataCfg.set("favorites." + entry.getKey().toString(),
                    new ArrayList<>(entry.getValue()));
        }
        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save combo_favorites.yml", e);
        }
    }

    // ── Favorites API ─────────────────────────────────────────────────────────

    /**
     * Returns true if the given proc tag is in the player's favorites.
     * Returns false if the player has NO favorites (nothing starred).
     */
    public boolean isFavorited(UUID uuid, String procTag) {
        Set<String> favs = favorites.get(uuid);
        if (favs == null || favs.isEmpty()) return false;
        return favs.contains(procTag);
    }

    /** Returns whether the player has ANY favorites starred. */
    public boolean hasAnyFavorite(UUID uuid) {
        Set<String> favs = favorites.get(uuid);
        return favs != null && !favs.isEmpty();
    }

    /** Toggles the given proc tag for the player. Returns true if now favorited. */
    public boolean toggle(UUID uuid, String procTag) {
        Set<String> favs = favorites.computeIfAbsent(uuid, k -> new HashSet<>());
        boolean added = favs.add(procTag);
        if (!added) favs.remove(procTag); // was already present → remove
        save();
        return added;
    }

    /** Returns an unmodifiable view of the player's current favorites set. */
    public Set<String> getFavorites(UUID uuid) {
        return Collections.unmodifiableSet(
                favorites.getOrDefault(uuid, Collections.emptySet()));
    }
}
