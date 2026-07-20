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
 * ComboFavoritesManager — Tracks which combo-hint chains each player has starred.
 *
 * ── How it works ──────────────────────────────────────────────────────────────
 *  Each player has a Set<String> of favorited "chain tags" (e.g. "WET_CHAIN").
 *  Hints for a given chain only display in the action bar when:
 *    1. The player has a §5Spell Combo Book§r in their inventory, AND
 *    2. That chain's tag is in their favorites set.
 *
 *  If the favorites set is EMPTY → NO hints show, even with the book.
 *  This gives players full control over which combos they want guided.
 *
 * ── Chain Tags ────────────────────────────────────────────────────────────────
 *  WET_CHAIN     — After applying WET:   "Earth=Muddy, Air=Chilled"
 *  MUDDY_CHAIN   — After MUDDY:          "Fire=Statue, Air=Mud Launch"
 *  CHILLED_CHAIN — After CHILLED:        "Air=Frozen"
 *  FROZEN_CHAIN  — After FROZEN:         "Air=☠ Shatter, Fire=Thaw Explosion"
 *  BLAZING_CHAIN — After BLAZING:        "Air=Inferno, Water=Steam, Fire=Vortex"
 *  SCORCHED_CHAIN— After SCORCHED:       "Fire=Blazing, Air=Fanned Flames"
 *  STATUE_CHAIN  — After STATUE:         "Air=☠ Crumble, Earth=Crumble"
 *  EARTH_TRAP    — After 1st Earth hit:  "hit again=Suffocate"
 *
 * ── Persistence ───────────────────────────────────────────────────────────────
 *  plugins/DifficultyEngine/combo_favorites.yml
 */
public class ComboFavoritesManager {

    // ── All available chain tags ──────────────────────────────────────────────
    public static final String WET_CHAIN     = "WET_CHAIN";
    public static final String MUDDY_CHAIN   = "MUDDY_CHAIN";
    public static final String CHILLED_CHAIN = "CHILLED_CHAIN";
    public static final String FROZEN_CHAIN  = "FROZEN_CHAIN";
    public static final String BLAZING_CHAIN = "BLAZING_CHAIN";
    public static final String SCORCHED_CHAIN= "SCORCHED_CHAIN";
    public static final String STATUE_CHAIN  = "STATUE_CHAIN";
    public static final String EARTH_TRAP    = "EARTH_TRAP";

    /** Ordered list of all tags — used by the GUI to build items in order. */
    public static final List<String> ALL_TAGS = List.of(
        WET_CHAIN, MUDDY_CHAIN, CHILLED_CHAIN, FROZEN_CHAIN,
        BLAZING_CHAIN, SCORCHED_CHAIN, STATUE_CHAIN, EARTH_TRAP
    );

    /**
     * Maps each chain tag to the Arcane Tome page index (0-based) that teaches it.
     * The FavoritesGUI uses this to show a locked item (§8🔒 ???) for chains whose
     * corresponding page has not yet been unlocked by the player.
     *
     *  WET_CHAIN     → page index  7  (Page  8 in the book — §b[WET])
     *  MUDDY_CHAIN   → page index  8  (Page  9 — §6[MUDDY])
     *  CHILLED_CHAIN → page index  9  (Page 10 — §b[CHILLED])
     *  FROZEN_CHAIN  → page index 10  (Page 11 — §b[FROZEN])
     *  BLAZING_CHAIN → page index 13  (Page 14 — §c[BLAZING])
     *  SCORCHED_CHAIN→ page index 12  (Page 13 — §c[SCORCHED])
     *  STATUE_CHAIN  → page index 11  (Page 12 — §e[STATUE])
     *  EARTH_TRAP    → page index  4  (Page  5 — Earth Staff)
     */
    public static final Map<String, Integer> CHAIN_REQUIRED_PAGE = Map.of(
        WET_CHAIN,      7,
        MUDDY_CHAIN,    8,
        CHILLED_CHAIN,  9,
        FROZEN_CHAIN,   10,
        BLAZING_CHAIN,  13,
        SCORCHED_CHAIN, 12,
        STATUE_CHAIN,   11,
        EARTH_TRAP,     4
    );

    // ── Display info for each tag (used by FavoritesGUI) ─────────────────────
    public record ChainInfo(String displayName, String trigger, String hint, String color) {}

    private static final Map<String, ChainInfo> CHAIN_INFO;
    static {
        Map<String, ChainInfo> m = new HashMap<>();
        m.put(WET_CHAIN,     new ChainInfo("§bWet Chain",       "Applies WET",     "→ Earth=Muddy · Air=Chilled",   "§b"));
        m.put(MUDDY_CHAIN,   new ChainInfo("§6Muddy Chain",     "Applies MUDDY",   "→ Fire=Statue · Air=Mud Launch","§6"));
        m.put(CHILLED_CHAIN, new ChainInfo("§3Chilled Chain",   "Applies CHILLED", "→ Air again = FROZEN!",         "§3"));
        m.put(FROZEN_CHAIN,  new ChainInfo("§bFrozen Chain",    "Applies FROZEN",  "→ Air=☠ Shatter · Fire=Thaw",  "§b"));
        m.put(BLAZING_CHAIN, new ChainInfo("§cBlazing Chain",   "Applies BLAZING", "→ Air=Inferno · Water=Steam",   "§c"));
        m.put(SCORCHED_CHAIN,new ChainInfo("§eScorched Chain",  "Applies SCORCHED","→ Fire=Blazing · Air=Fan",      "§e"));
        m.put(STATUE_CHAIN,  new ChainInfo("§6Statue Chain",    "Applies STATUE",  "→ Air=☠ Crumble · Earth=Chip", "§6"));
        m.put(EARTH_TRAP,    new ChainInfo("§2Earth Trap",      "1st Earth hit",   "→ hit again = SUFFOCATE!",      "§2"));
        CHAIN_INFO = Collections.unmodifiableMap(m);
    }

    public static ChainInfo getInfo(String tag) {
        return CHAIN_INFO.getOrDefault(tag,
            new ChainInfo("§7Unknown", "?", "?", "§7"));
    }

    // ── Instance fields ───────────────────────────────────────────────────────

    private final JavaPlugin              plugin;
    /** player UUID → set of favorited chain tags */
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
     * Returns true if the given chain tag is in the player's favorites.
     * Returns false if the player has NO favorites (nothing starred).
     */
    public boolean isFavorited(UUID uuid, String chainTag) {
        Set<String> favs = favorites.get(uuid);
        if (favs == null || favs.isEmpty()) return false;
        return favs.contains(chainTag);
    }

    /** Returns whether the player has ANY favorites starred. */
    public boolean hasAnyFavorite(UUID uuid) {
        Set<String> favs = favorites.get(uuid);
        return favs != null && !favs.isEmpty();
    }

    /** Toggles the given chain tag for the player. Returns true if now favorited. */
    public boolean toggle(UUID uuid, String chainTag) {
        Set<String> favs = favorites.computeIfAbsent(uuid, k -> new HashSet<>());
        boolean added = favs.add(chainTag);
        if (!added) favs.remove(chainTag); // was already present → remove
        save();
        return added;
    }

    /** Returns an unmodifiable view of the player's current favorites set. */
    public Set<String> getFavorites(UUID uuid) {
        return Collections.unmodifiableSet(
                favorites.getOrDefault(uuid, Collections.emptySet()));
    }
}
