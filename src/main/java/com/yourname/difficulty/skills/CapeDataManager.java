package com.yourname.difficulty.skills;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CapeDataManager — Stores player-equipped capes SEPARATELY from the armour slot.
 *
 * By keeping the cape in its own data store (rather than the chestplate slot),
 * players can wear both chestplate armour AND a skill cape simultaneously.
 *
 * Data is persisted to:  plugins/DifficultyEngine/capes/<uuid>.yml
 *
 * Lifecycle:
 *   • loadPlayer(Player)  — called automatically on join
 *   • savePlayer(UUID)    — called automatically on quit and on every equip/unequip
 *   • saveAll()           — called from Main.onDisable()
 */
public class CapeDataManager implements Listener {

    private final JavaPlugin              plugin;
    private final File                    capeDir;
    private final Map<UUID, ItemStack>    equippedCapes = new HashMap<>();

    public CapeDataManager(JavaPlugin plugin) {
        this.plugin  = plugin;
        this.capeDir = new File(plugin.getDataFolder(), "capes");
        if (!capeDir.exists()) capeDir.mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Equip a cape for a player. The old cape (if any) is returned — caller
     * should give it back to the player's inventory.
     */
    public ItemStack equipCape(UUID uuid, ItemStack cape) {
        ItemStack old = equippedCapes.put(uuid, cape.clone());
        saveAsync(uuid);
        return old;
    }

    /** Remove the equipped cape and return it (null if none). */
    public ItemStack unequipCape(UUID uuid) {
        ItemStack old = equippedCapes.remove(uuid);
        saveAsync(uuid);
        return old;
    }

    /** @return the currently equipped cape, or {@code null}. */
    public ItemStack getEquippedCape(UUID uuid) {
        return equippedCapes.get(uuid);
    }

    public boolean hasCape(UUID uuid) {
        return equippedCapes.containsKey(uuid);
    }

    /** Persist all loaded players synchronously (call from onDisable). */
    public void saveAll() {
        for (UUID uuid : equippedCapes.keySet()) saveSync(uuid);
    }

    // ── Auto load / save on join / quit ───────────────────────────────────────

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        loadPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onQuit(PlayerQuitEvent event) {
        saveAsync(event.getPlayer().getUniqueId());
        // Keep in memory until next join (harmless) — or evict:
        equippedCapes.remove(event.getPlayer().getUniqueId());
    }

    // ── IO ────────────────────────────────────────────────────────────────────

    private void loadPlayer(Player player) {
        File f = new File(capeDir, player.getUniqueId() + ".yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ItemStack cape = cfg.getItemStack("cape");
        if (cape != null) equippedCapes.put(player.getUniqueId(), cape);
    }

    private void saveAsync(UUID uuid) {
        ItemStack snapshot = equippedCapes.get(uuid);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File f = new File(capeDir, uuid + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            if (snapshot != null) cfg.set("cape", snapshot);
            try { cfg.save(f); }
            catch (IOException e) {
                plugin.getLogger().warning("[CapeDataManager] Failed to save cape for "
                    + uuid + ": " + e.getMessage());
            }
        });
    }

    private void saveSync(UUID uuid) {
        ItemStack snapshot = equippedCapes.get(uuid);
        File f = new File(capeDir, uuid + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        if (snapshot != null) cfg.set("cape", snapshot);
        try { cfg.save(f); }
        catch (IOException e) {
            plugin.getLogger().warning("[CapeDataManager] Failed to save cape for "
                + uuid + ": " + e.getMessage());
        }
    }
}
