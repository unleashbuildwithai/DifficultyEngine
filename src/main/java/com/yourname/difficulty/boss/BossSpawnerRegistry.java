package com.yourname.difficulty.boss;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * BossSpawnerRegistry — persisted, authoritative record of exactly which
 * block Locations are "real" boss/utility spawner blocks.
 *
 * ── Why this exists ──────────────────────────────────────────────────────
 * Previously, spawner-block identity was checked purely via Bukkit's
 * {@code Block.hasMetadata(...)}. Block metadata is a loose, coordinate-keyed
 * side table that is NOT tied to "was this exact block legitimately placed
 * via our Registry item" — it can end up applied to a coordinate through
 * unrelated code paths (e.g. {@code CrimsonBossManager.rebuildArena()}
 * re-stamping metadata at a freshly-chosen boss-respawn location), and any
 * player later placing the SAME vanilla material at that exact coordinate
 * (via creative mode, /give, WorldEdit, natural generation, etc.) would
 * silently inherit "real spawner" status. This is the exact bug reported:
 * a creative-placed Gilded Blackstone block (never obtained from the
 * Registry) worked as a real Blazefiend Spawner.
 *
 * This registry fixes that by keeping an explicit, persisted
 * (world,x,y,z) -> type Set, saved to spawner_locations.yml. A block only
 * counts as a real spawner if BOTH:
 *   1. Its material matches the expected type, AND
 *   2. Its exact Location is present in this registry for that type.
 *
 * Locations are added ONLY by:
 *   - CrimsonBossSpawner.onSpawnerPlace() — a player placing a genuine
 *     Registry-obtained spawner item.
 *   - CrimsonBossManager.rebuildArena() — the automatic boss/arena
 *     respawn cycle relocating a boss's spawner block (intended design:
 *     "search the caves" flavor text implies the spawner block moves).
 *
 * Never populated by loose block metadata, so stray identical vanilla
 * blocks placed anywhere else in the world are never mistaken for a real
 * spawner, and the registry survives server restarts (unlike Block
 * metadata, which is in-memory only and does not persist to disk).
 */
public class BossSpawnerRegistry {

    public static final String TYPE_BLAZEFIEND    = "blazefiend";
    public static final String TYPE_TEMPEST       = "tempest";
    public static final String TYPE_VOID          = "void";
    public static final String TYPE_GILDED        = "gilded";
    public static final String TYPE_NO_SPAWN_ZONE = "no_spawn_zone";

    private final JavaPlugin plugin;
    private final File       file;

    /** type -> set of "world,x,y,z" coordinate keys. */
    private final Map<String, Set<String>> data = new HashMap<>();

    public BossSpawnerRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "spawner_locations.yml");
        load();
    }

    private static String key(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public boolean isRegistered(Location loc, String type) {
        if (loc == null || loc.getWorld() == null) return false;
        Set<String> set = data.get(type);
        return set != null && set.contains(key(loc));
    }

    public void register(Location loc, String type) {
        if (loc == null || loc.getWorld() == null) return;
        data.computeIfAbsent(type, k -> new HashSet<>()).add(key(loc));
        save();
    }

    public void unregister(Location loc, String type) {
        if (loc == null || loc.getWorld() == null) return;
        Set<String> set = data.get(type);
        if (set != null) {
            set.remove(key(loc));
            save();
        }
    }

    /** Returns all currently-registered locations for a given type (for admin/debug listing). */
    public Set<Location> getAll(String type) {
        Set<Location> result = new HashSet<>();
        Set<String> set = data.get(type);
        if (set == null) return result;
        for (String s : set) {
            String[] parts = s.split(",");
            if (parts.length != 4) continue;
            World world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            try {
                int x = Integer.parseInt(parts[1]);
                int y = Integer.parseInt(parts[2]);
                int z = Integer.parseInt(parts[3]);
                result.add(new Location(world, x, y, z));
            } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.isConfigurationSection("locations")) return;
        for (String type : yaml.getConfigurationSection("locations").getKeys(false)) {
            Set<String> set = new HashSet<>(yaml.getStringList("locations." + type));
            data.put(type, set);
        }
        plugin.getLogger().info("[BossSpawnerRegistry] Loaded spawner location registry ("
                + data.values().stream().mapToInt(Set::size).sum() + " total entries).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
            yaml.set("locations." + entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
        }
        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("[BossSpawnerRegistry] Could not save spawner_locations.yml: " + ex.getMessage());
        }
    }
}
