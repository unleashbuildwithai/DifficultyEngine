package com.yourname.difficulty.currency;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GoldManager — virtual wallet per player stored in gold.yml.
 * Gold is awarded on mob kills and split among party members on boss kills.
 */
public class GoldManager {

    private final JavaPlugin plugin;
    private final File       dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Long> cache = new HashMap<>();

    public GoldManager(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "gold.yml");
        load();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveAll() {
        for (Map.Entry<UUID, Long> entry : cache.entrySet()) {
            data.set(entry.getKey().toString(), entry.getValue());
        }
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public long getBalance(UUID uuid) {
        return cache.computeIfAbsent(uuid, id ->
            data.getLong(id.toString(), 0L));
    }

    public void addGold(UUID uuid, long amount) {
        if (amount <= 0) return;
        cache.merge(uuid, amount, Long::sum);
    }

    public boolean spendGold(UUID uuid, long amount) {
        long bal = getBalance(uuid);
        if (bal < amount) return false;
        cache.put(uuid, bal - amount);
        return true;
    }

    /**
     * Awards gold to the player, shows action-bar notification, and saves lazily.
     * @param player  the recipient
     * @param amount  gold to award
     */
    public void award(Player player, long amount) {
        if (amount <= 0) return;
        addGold(player.getUniqueId(), amount);
        long bal = getBalance(player.getUniqueId());
        player.sendActionBar("§6+" + formatGold(amount) + " gp §8| §6Balance: " + formatGold(bal) + " gp");
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    public static String formatGold(long amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }
}
