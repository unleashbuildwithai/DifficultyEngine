package com.yourname.difficulty.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * GoldManager - virtual wallet per player.
 * <p>
 * When Vault (with an economy provider like EssentialsX) is present, gold is
 * stored in the SHARED economy so monster-kill gold, the market shop, and
 * /bal //pay all use the SAME balance. Falls back to gold.yml when Vault is
 * absent. Existing gold.yml balances are migrated into the shared economy on
 * first load (one-time).
 */
public class GoldManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Long> cache = new HashMap<>();
    private Economy vaultEconomy; // nullable

    public GoldManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "gold.yml");
        load();
        setupVault();
        if (useVault()) migrateToVault();
    }

    private void load() {
        if (!dataFile.exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - gold stored in gold.yml.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("Vault economy detected - gold uses shared economy (EssentialsX).");
        }
    }

    private boolean useVault() {
        return vaultEconomy != null;
    }

    /** One-time migration of gold.yml balances into the shared economy. */
    private void migrateToVault() {
        File migrated = new File(plugin.getDataFolder(), "gold.yml.migrated");
        if (!dataFile.exists() || migrated.exists()) return;
        int count = 0;
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long amount = data.getLong(key, 0L);
                if (amount > 0) {
                    vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                    count++;
                }
            } catch (IllegalArgumentException ignored) {
                // skip malformed keys
            }
        }
        dataFile.renameTo(migrated);
        plugin.getLogger().info("Migrated " + count + " gold balances into the shared economy.");
    }

    public void saveAll() {
        if (useVault()) return; // Vault persists itself
        for (Map.Entry<UUID, Long> entry : cache.entrySet()) {
            data.set(entry.getKey().toString(), entry.getValue());
        }
        try { data.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public long getBalance(UUID uuid) {
        if (useVault()) {
            return (long) vaultEconomy.getBalance(Bukkit.getOfflinePlayer(uuid));
        }
        return cache.computeIfAbsent(uuid, id -> data.getLong(id.toString(), 0L));
    }

    public void addGold(UUID uuid, long amount) {
        if (amount <= 0) return;
        if (useVault()) {
            vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
            return;
        }
        cache.merge(uuid, amount, Long::sum);
    }

    public boolean spendGold(UUID uuid, long amount) {
        if (useVault()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (!vaultEconomy.has(op, amount)) return false;
            vaultEconomy.withdrawPlayer(op, amount);
            return true;
        }
        long bal = getBalance(uuid);
        if (bal < amount) return false;
        cache.put(uuid, bal - amount);
        return true;
    }

    public void setBalance(UUID uuid, long amount) {
        if (useVault()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            double current = vaultEconomy.getBalance(op);
            double target = Math.max(0, amount);
            if (current > target) vaultEconomy.withdrawPlayer(op, current - target);
            else if (target > current) vaultEconomy.depositPlayer(op, target - current);
            return;
        }
        cache.put(uuid, Math.max(0, amount));
    }

    public void award(Player player, long amount) {
        if (amount <= 0) return;
        addGold(player.getUniqueId(), amount);
        long bal = getBalance(player.getUniqueId());
        player.sendActionBar("§6+" + formatGold(amount) + " gp §8| §6Balance: " + formatGold(bal) + " gp");
    }

    public static String formatGold(long amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0);
        if (amount >= 1_000)     return String.format("%.1fK", amount / 1_000.0);
        return String.valueOf(amount);
    }
}