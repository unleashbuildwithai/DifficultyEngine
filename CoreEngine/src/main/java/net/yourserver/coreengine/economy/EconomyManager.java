package net.yourserver.coreengine.economy;

import net.milkbowl.vault.economy.Economy;
import net.yourserver.coreengine.database.dao.MarketDao;
import net.yourserver.coreengine.market.MarketLockRegistry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Player-facing economy facade.
 * <p>
 * If Vault (with a registered economy provider, e.g. EssentialsX) is present,
 * every balance operation is routed through {@code Vault.getEconomy()} so the
 * market shares the SAME money as the rest of the server (monster-kill gold,
 * /bal, /pay, other plugins). If Vault is absent, it transparently falls back
 * to the internal {@code player_profiles.balance} column.
 * <p>
 * Every mutating operation runs while holding the player's reentrant lock
 * from {@link MarketLockRegistry}. Two-party money movement is orchestrated
 * by {@code MarketManager} which acquires the involved players' locks in
 * deterministic (sorted-UUID) order before calling into this class.
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private final MarketDao dao;
    private final MarketLockRegistry lockRegistry;
    private Economy vaultEconomy; // nullable

    public EconomyManager(JavaPlugin plugin, MarketDao dao, MarketLockRegistry lockRegistry) {
        this.plugin = plugin;
        this.dao = dao;
        this.lockRegistry = lockRegistry;
        setupVault();
    }

    private void setupVault() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found - market using internal economy.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.vaultEconomy = rsp.getProvider();
            plugin.getLogger().info("Vault economy detected - market uses shared economy (EssentialsX).");
        } else {
            plugin.getLogger().warning("Vault present but no economy provider - using internal economy.");
        }
    }

    private boolean useVault() {
        return vaultEconomy != null;
    }

    public double getBalance(UUID playerUuid) {
        if (useVault()) {
            return vaultEconomy.getBalance(Bukkit.getOfflinePlayer(playerUuid));
        }
        return dao.getBalance(playerUuid);
    }

    public boolean has(UUID playerUuid, double amount) {
        return getBalance(playerUuid) >= amount;
    }

    public double deposit(UUID playerUuid, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Deposit amount must be >= 0");
        }
        lockRegistry.lock(playerUuid);
        try {
            if (useVault()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
                vaultEconomy.depositPlayer(op, amount);
                return vaultEconomy.getBalance(op);
            }
            return dao.adjustBalance(playerUuid, amount).orElse(0.0);
        } finally {
            lockRegistry.unlock(playerUuid);
        }
    }

    public OptionalDouble withdraw(UUID playerUuid, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Withdrawal amount must be >= 0");
        }
        lockRegistry.lock(playerUuid);
        try {
            if (useVault()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(playerUuid);
                if (!vaultEconomy.has(op, amount)) {
                    return OptionalDouble.empty();
                }
                vaultEconomy.withdrawPlayer(op, amount);
                return OptionalDouble.of(vaultEconomy.getBalance(op));
            }
            return dao.adjustBalance(playerUuid, -amount);
        } finally {
            lockRegistry.unlock(playerUuid);
        }
    }

    public boolean transfer(UUID fromUuid, UUID toUuid, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Transfer amount must be >= 0");
        }
        if (useVault()) {
            OfflinePlayer from = Bukkit.getOfflinePlayer(fromUuid);
            OfflinePlayer to = Bukkit.getOfflinePlayer(toUuid);
            if (!vaultEconomy.has(from, amount)) {
                return false;
            }
            vaultEconomy.withdrawPlayer(from, amount);
            vaultEconomy.depositPlayer(to, amount);
            return true;
        }
        OptionalDouble newFrom = dao.adjustBalance(fromUuid, -amount);
        if (newFrom.isEmpty()) {
            return false;
        }
        dao.adjustBalance(toUuid, amount);
        return true;
    }

    public boolean transferWithLocks(UUID fromUuid, UUID toUuid, double amount) {
        lockRegistry.lockAll(fromUuid, toUuid);
        try {
            return transfer(fromUuid, toUuid, amount);
        } finally {
            lockRegistry.unlockAll(fromUuid, toUuid);
        }
    }
}