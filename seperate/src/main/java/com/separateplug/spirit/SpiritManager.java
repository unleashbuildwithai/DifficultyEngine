package com.separateplug.spirit;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * SpiritManager — Manages player spirit bindings and persistence.
 *
 * ── Spirit binding rules ──────────────────────────────────────────────────────
 *  • On first join, a random spirit (FIRE/WATER/EARTH/AIR) is assigned.
 *  • The bound spirit is permanent — it does not change unless edited by an admin.
 *  • Players receive their bound spirit's staff on first join.
 *  • If a player dies: 5% chance their bound spirit staff drops (handled in listener).
 *  • If a player kills another: 5% chance they receive a copy of the victim's staff.
 *
 * ── Persistence ───────────────────────────────────────────────────────────────
 *  Data is saved to:  plugins/SeparatePlug/spirits/<uuid>.yml
 */
public class SpiritManager {

    private static final Random RAND = new Random();

    private final JavaPlugin           plugin;
    private final File                 spiritDir;
    private final Map<UUID, SpiritType> boundSpirits = new HashMap<>();

    public SpiritManager(JavaPlugin plugin) {
        this.plugin    = plugin;
        this.spiritDir = new File(plugin.getDataFolder(), "spirits");
        if (!spiritDir.exists()) spiritDir.mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return true if this player has never joined before (no spirit file). */
    public boolean isFirstJoin(UUID uuid) {
        return !new File(spiritDir, uuid + ".yml").exists();
    }

    /**
     * Assign a random spirit to a player.
     * Should only be called on first join (use {@link #isFirstJoin} to check).
     */
    public SpiritType assignRandomSpirit(UUID uuid) {
        SpiritType[] types = SpiritType.values();
        SpiritType type = types[RAND.nextInt(types.length)];
        boundSpirits.put(uuid, type);
        saveSync(uuid);
        return type;
    }

    /** @return the player's bound SpiritType, or null if not yet assigned. */
    public SpiritType getBoundSpirit(UUID uuid) {
        return boundSpirits.get(uuid);
    }

    /** Load a player's spirit from disk (call on join). */
    public void loadPlayer(Player player) {
        File f = new File(spiritDir, player.getUniqueId() + ".yml");
        if (!f.exists()) return; // first-time players are handled elsewhere
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        String raw = cfg.getString("spirit");
        if (raw == null) return;
        try {
            boundSpirits.put(player.getUniqueId(), SpiritType.valueOf(raw));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[SpiritManager] Unknown spirit type '" + raw
                + "' for player " + player.getName());
        }
    }

    /** Save a player's spirit to disk (call on quit). */
    public void savePlayer(UUID uuid) { saveAsync(uuid); }

    /** Persist all loaded spirits synchronously (call from onDisable). */
    public void saveAll() {
        for (UUID uuid : boundSpirits.keySet()) saveSync(uuid);
    }

    /** Evict a player from the in-memory cache (call on quit). */
    public void evict(UUID uuid) { boundSpirits.remove(uuid); }

    // ── IO ────────────────────────────────────────────────────────────────────

    private void saveAsync(UUID uuid) {
        SpiritType spirit = boundSpirits.get(uuid);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File f = new File(spiritDir, uuid + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            if (spirit != null) cfg.set("spirit", spirit.name());
            try { cfg.save(f); }
            catch (IOException e) {
                plugin.getLogger().warning("[SpiritManager] Failed to save spirit for " + uuid);
            }
        });
    }

    private void saveSync(UUID uuid) {
        SpiritType spirit = boundSpirits.get(uuid);
        File f = new File(spiritDir, uuid + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        if (spirit != null) cfg.set("spirit", spirit.name());
        try { cfg.save(f); }
        catch (IOException e) {
            plugin.getLogger().warning("[SpiritManager] Failed to save spirit for " + uuid);
        }
    }
}
