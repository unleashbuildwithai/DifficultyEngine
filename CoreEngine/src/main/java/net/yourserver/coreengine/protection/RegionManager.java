package net.yourserver.coreengine.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory protected-region store + per-player selection (pos1/pos2).
 * Regions make blocks unbreakable by non-OP players and block monster spawns.
 * A region can also be marked {@code secure}, which makes it unbreakable by
 * EVERYONE (including OP/admins) until the owner toggles it off.
 * <p>
 * Regions are persisted to {@code regions.yml} so they survive restarts.
 */
public class RegionManager {

    /** A cuboid protected region. */
    public record Region(String name, String world, int x1, int y1, int z1,
                         int x2, int y2, int z2, boolean secure) {
        public boolean contains(String world, int x, int y, int z) {
            return this.world.equals(world)
                    && x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    private final Map<String, Region> regions = new ConcurrentHashMap<>();
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();
    private File dataFile;

    public void setDataFile(File dataFile) {
        this.dataFile = dataFile;
    }

    public void setPos1(UUID uuid, Location loc) {
        Location[] sel = selections.computeIfAbsent(uuid, k -> new Location[2]);
        sel[0] = loc.clone();
    }

    public void setPos2(UUID uuid, Location loc) {
        Location[] sel = selections.computeIfAbsent(uuid, k -> new Location[2]);
        sel[1] = loc.clone();
    }

    public Location[] getSelection(UUID uuid) {
        return selections.get(uuid);
    }

    public void clearSelection(UUID uuid) {
        selections.remove(uuid);
    }

    /** Creates a region from the player's selection; returns null if incomplete. */
    public Region createRegion(String name, UUID uuid, boolean secure) {
        Location[] sel = selections.get(uuid);
        if (sel == null || sel[0] == null || sel[1] == null) return null;
        if (!sel[0].getWorld().equals(sel[1].getWorld())) return null;
        Region region = new Region(name, sel[0].getWorld().getName(),
                sel[0].getBlockX(), sel[0].getBlockY(), sel[0].getBlockZ(),
                sel[1].getBlockX(), sel[1].getBlockY(), sel[1].getBlockZ(), secure);
        regions.put(name.toLowerCase(), region);
        save();
        return region;
    }

    public boolean deleteRegion(String name) {
        boolean removed = regions.remove(name.toLowerCase()) != null;
        if (removed) save();
        return removed;
    }

    /** Toggles the secure flag on an existing region; returns false if not found. */
    public boolean setSecure(String name, boolean secure) {
        Region region = regions.get(name.toLowerCase());
        if (region == null) return false;
        Region updated = new Region(region.name(), region.world(),
                region.x1(), region.y1(), region.z1(),
                region.x2(), region.y2(), region.z2(), secure);
        regions.put(name.toLowerCase(), updated);
        save();
        return true;
    }

    public Collection<Region> getRegions() {
        return regions.values();
    }

    public Region getRegionAt(String world, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (region.contains(world, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    /** Returns only SECURE regions at the location (used by /wandsecure off). */
    public Region getSecureRegionAt(String world, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (region.secure() && region.contains(world, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    /** Returns the wand-secure region (name prefix "wand_") at the location, regardless of secure state. */
    public Region getWandRegionAt(String world, int x, int y, int z) {
        for (Region region : regions.values()) {
            if (region.name().startsWith("wand_") && region.contains(world, x, y, z)) {
                return region;
            }
        }
        return null;
    }

    public void save() {
        if (dataFile == null) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (Region r : regions.values()) {
            String path = "regions." + r.name().toLowerCase() + ".";
            yaml.set(path + "world", r.world());
            yaml.set(path + "x1", r.x1());
            yaml.set(path + "y1", r.y1());
            yaml.set(path + "z1", r.z1());
            yaml.set(path + "x2", r.x2());
            yaml.set(path + "y2", r.y2());
            yaml.set(path + "z2", r.z2());
            yaml.set(path + "secure", r.secure());
        }
        try {
            yaml.save(dataFile);
        } catch (IOException e) {
            Bukkit.getLogger().warning("CoreEngine: failed to save regions: " + e.getMessage());
        }
    }

    public void load() {
        if (dataFile == null || !dataFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        if (!yaml.contains("regions")) return;
        for (String key : yaml.getConfigurationSection("regions").getKeys(false)) {
            String path = "regions." + key + ".";
            Region region = new Region(
                    key,
                    yaml.getString(path + "world"),
                    yaml.getInt(path + "x1"), yaml.getInt(path + "y1"), yaml.getInt(path + "z1"),
                    yaml.getInt(path + "x2"), yaml.getInt(path + "y2"), yaml.getInt(path + "z2"),
                    yaml.getBoolean(path + "secure"));
            regions.put(key.toLowerCase(), region);
        }
    }
}