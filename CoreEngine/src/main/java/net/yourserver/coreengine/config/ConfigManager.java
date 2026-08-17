package net.yourserver.coreengine.config;

import net.yourserver.coreengine.rank.PlayerRank;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed accessor over the plugin's {@code config.yml}. Every Module 1
 * constant (NPC teleport location, listing/escrow expiration windows,
 * expiration-task cadence, rank listing caps, quick-sell buyback prices)
 * is read through here.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        this.config = plugin.getConfig();
    }

    /** Reloads config.yml from disk (used by the admin reload flow). */
    public void reload() {
        plugin.reloadConfig();
    }

    // ------------------------------------------------------------------
    // Market NPC teleport
    // ------------------------------------------------------------------

    public Location getMarketNpcLocation() {
        String worldName = config.getString("market.npc-teleport.world", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Market NPC world '" + worldName + "' not found; falling back to default world.");
            world = plugin.getServer().getWorlds().get(0);
        }
        double x = config.getDouble("market.npc-teleport.x", 0.5);
        double y = config.getDouble("market.npc-teleport.y", 64.0);
        double z = config.getDouble("market.npc-teleport.z", 0.5);
        float yaw = (float) config.getDouble("market.npc-teleport.yaw", 0.0);
        float pitch = (float) config.getDouble("market.npc-teleport.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ------------------------------------------------------------------
    // MonsterGrid — Market safe-zone anchor (/monstergrid)
    // ------------------------------------------------------------------

    /** Whether the monster-exclusion grid around the Market NPC is active. */
    public boolean isMonsterGridEnabled() {
        return config.getBoolean("monstergrid.enabled", true);
    }

    public void setMonsterGridEnabled(boolean enabled) {
        config.set("monstergrid.enabled", enabled);
        plugin.saveConfig();
    }

    /** Exclusion radius (blocks) anchored on the Market NPC location. */
    public int getMonsterGridRadius() {
        return Math.max(1, Math.min(500, config.getInt("monstergrid.radius", 32)));
    }

    public void setMonsterGridRadius(int radius) {
        config.set("monstergrid.radius", Math.max(1, Math.min(500, radius)));
        plugin.saveConfig();
    }

    // ------------------------------------------------------------------
    // Expiration timers
    // ------------------------------------------------------------------

    /** Hours an active SELL listing stays live before expiring to escrow. */
    public long getSellListingExpirationHours() {
        return Math.max(1, config.getLong("market.sell-listing-expiration-hours", 24));
    }

    /** Days a claimable escrow entry stays claimable before cleanup. */
    public long getEscrowClaimExpirationDays() {
        return Math.max(1, config.getLong("market.escrow-claim-expiration-days", 90));
    }

    /** Minutes between expiration/cleanup sweeps. */
    public long getExpirationTaskIntervalMinutes() {
        return Math.max(1, config.getLong("market.expiration-task-interval-minutes", 5));
    }

    // ------------------------------------------------------------------
    // Dynamic (Supply & Demand) Price Engine
    // ------------------------------------------------------------------

    /**
     * Number of most-recent confirmed trades to sample for the robust
     * average (default 100,000; 10k-1M range per the anti-manipulation
     * requirement).
     */
    public int getBasePriceSampleSize() {
        return Math.max(1000, config.getInt("market.dynamic-price.base-price-sample-size", 100000));
    }

    /** True if the robust average should use the median (vs the mean). */
    public boolean useMedian() {
        String m = config.getString("market.dynamic-price.central-measure", "median");
        return !m.equalsIgnoreCase("mean");
    }

    // ------------------------------------------------------------------
    // Rank listing caps
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------

    /**
     * Max COMBINED active (SELL + BUY) listings for a rank tier, taken from
     * {@code config.yml market.rank-listing-caps}. Falls back to the
     * {@link PlayerRank} enum default if the config entry is missing.
     */
    public int getRankListingCap(PlayerRank rank) {
        return Math.max(0, config.getInt("market.rank-listing-caps." + rank.getTier(), rank.getMaxListings()));
    }

    // ------------------------------------------------------------------
    // GUI pagination
    // ------------------------------------------------------------------

    /** Number of listings per page in the browsing grid (slots 18-44). */
    public int getListingsPerPage() {
        return Math.max(1, Math.min(27, config.getInt("market.listings-per-page", 27)));
    }

    // ------------------------------------------------------------------
    // Server Quick-Sell (Buyback) Floor
    // ------------------------------------------------------------------

    /**
     * Reads the configured per-unit buyback price for a material. Falls back
     * to {@code market.default-buyback-fallback-price} if present and &gt;= 0.
     *
     * @return the price, or -1.0 if the material is not accepted for
     *         quick-sell.
     */
    public double getBuybackPrice(String materialName) {
        String path = "market.buyback-prices." + materialName;
        if (config.contains(path)) {
            return config.getDouble(path, -1.0);
        }
        double fallback = config.getDouble("market.default-buyback-fallback-price", -1.0);
        return fallback;
    }

    /** All configured buyback prices as an immutable-ish copy (material -> unit price). */
    public Map<String, Double> getAllBuybackPrices() {
        Map<String, Double> result = new HashMap<>();
        var section = config.getConfigurationSection("market.buyback-prices");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                result.put(key.toUpperCase(), section.getDouble(key, -1.0));
            }
        }
        return result;
    }
}
