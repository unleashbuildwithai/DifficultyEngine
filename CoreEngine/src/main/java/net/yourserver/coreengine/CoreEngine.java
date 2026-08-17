package net.yourserver.coreengine;

import net.yourserver.coreengine.commands.BalCommand;
import net.yourserver.coreengine.commands.BuyCommand;
import net.yourserver.coreengine.commands.DelHomeCommand;
import net.yourserver.coreengine.commands.GiveMemberCommand;
import net.yourserver.coreengine.commands.HomeCommand;
import net.yourserver.coreengine.commands.MarketCommand;
import net.yourserver.coreengine.commands.PayCommand;
import net.yourserver.coreengine.commands.SellCommand;
import net.yourserver.coreengine.commands.SetHomeCommand;
import net.yourserver.coreengine.commands.SettingsCommand;
import net.yourserver.coreengine.commands.TpAutoCommand;
import net.yourserver.coreengine.commands.TpCommand;
import net.yourserver.coreengine.commands.TpHereCommand;
import net.yourserver.coreengine.commands.BanCommand;
import net.yourserver.coreengine.commands.KickCommand;
import net.yourserver.coreengine.commands.MarkOffCommand;
import net.yourserver.coreengine.commands.MarkOnCommand;
import net.yourserver.coreengine.commands.ModCommand;
import net.yourserver.coreengine.commands.RegionCommand;
import net.yourserver.coreengine.commands.WandSecureCommand;
import net.yourserver.coreengine.commands.RtpCommand;
import net.yourserver.coreengine.config.ConfigManager;
import net.yourserver.coreengine.database.DatabaseManager;
import net.yourserver.coreengine.database.dao.HomeDao;
import net.yourserver.coreengine.database.dao.MarketDao;
import net.yourserver.coreengine.economy.EconomyManager;
import net.yourserver.coreengine.gui.GUIListener;
import net.yourserver.coreengine.gui.MarketGUIManager;
import net.yourserver.coreengine.gui.SettingsGUIManager;
import net.yourserver.coreengine.gui.SettingsUI;

import net.yourserver.coreengine.hud.StatsHudTask;
import net.yourserver.coreengine.listeners.MarketChatListener;
import net.yourserver.coreengine.listeners.MonsterSpawnListener;
import net.yourserver.coreengine.listeners.PlayerConnectionListener;
import net.yourserver.coreengine.listeners.RegionProtectionListener;
import net.yourserver.coreengine.listeners.RegionSelectionListener;
import net.yourserver.coreengine.protection.RegionManager;
import net.yourserver.coreengine.market.MarketExpirationTask;
import net.yourserver.coreengine.market.MarketLockRegistry;
import net.yourserver.coreengine.market.MarketManager;
import net.yourserver.coreengine.rank.RankManager;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.teleport.TeleportManager;
import net.yourserver.coreengine.teleport.TeleportRequestManager;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

public class CoreEngine extends JavaPlugin {

    private static CoreEngine instance;

    /** Home slots awaiting a chat-typed display name (player UUID → home slot). */
    private final Map<UUID, Integer> pendingHomeRename = new java.util.concurrent.ConcurrentHashMap<>();
    private DatabaseManager databaseManager;
    private ConfigManager configManager;
    private MarketDao marketDao;
    private HomeDao homeDao;
    private MarketLockRegistry lockRegistry;
    private EconomyManager economyManager;
    private RankManager rankManager;
    private MarketManager marketManager;
    private MarketGUIManager marketGuiManager;
    private SettingsUI settingsUI;

    private SettingsGUIManager settingsGuiManager;
    private PlayerSettingsManager playerSettingsManager;
    private TeleportManager teleportManager;
    private RegionManager regionManager;
    private MarketExpirationTask expirationTask;
    private StatsHudTask statsHudTask;

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();
        this.configManager = new ConfigManager(this);
        this.marketDao = new MarketDao(databaseManager, getLogger());
        this.homeDao = new HomeDao(databaseManager, getLogger());
        this.lockRegistry = new MarketLockRegistry();
        this.economyManager = new EconomyManager(this, marketDao, lockRegistry);
        this.rankManager = new RankManager(databaseManager, getLogger());
        this.marketManager = new MarketManager(this, configManager, marketDao,
                economyManager, rankManager, lockRegistry);
        this.marketGuiManager = new MarketGUIManager(marketManager, economyManager, configManager);
        this.playerSettingsManager = new PlayerSettingsManager();
        this.teleportManager = new TeleportManager(this, playerSettingsManager);
        this.regionManager = new RegionManager();
        this.regionManager.setDataFile(new java.io.File(getDataFolder(), "regions.yml"));
        this.regionManager.load();
        this.settingsGuiManager = new SettingsGUIManager(this, marketGuiManager, homeDao,
                playerSettingsManager, economyManager);
        this.settingsUI = new SettingsUI(this, playerSettingsManager);


        this.expirationTask = new MarketExpirationTask(this, configManager, marketDao, marketManager);
        this.expirationTask.start();
        this.statsHudTask = new StatsHudTask(this, economyManager, marketDao, playerSettingsManager);
        this.statsHudTask.start();

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        getServer().getPluginManager().registerEvents(new MarketChatListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MonsterSpawnListener(this), this);
        getServer().getPluginManager().registerEvents(new net.yourserver.coreengine.listeners.MonsterGridListener(configManager), this);
        getServer().getPluginManager().registerEvents(new RegionSelectionListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionProtectionListener(this), this);

        registerCommand("market", new MarketCommand(this));
        registerCommand("sell", new SellCommand(this));
        registerCommand("buy", new BuyCommand(this));
        registerCommand("givemember", new GiveMemberCommand(this));
        registerCommand("settings", new SettingsCommand(this));
        registerCommand("home", new HomeCommand(this));
        registerCommand("sethome", new SetHomeCommand(this));
        registerCommand("delhome", new DelHomeCommand(this));
        registerCommand("tp", new TpCommand(this));
        registerCommand("tphere", new TpHereCommand(this));
        registerCommand("tpauto", new TpAutoCommand(this));
        registerCommand("pay", new PayCommand(this));
        registerCommand("bal", new BalCommand(this));
        registerCommand("rtp", new RtpCommand(this));
        registerCommand("mod", new ModCommand(this));
        registerCommand("ban", new BanCommand(this));
        registerCommand("kick", new KickCommand(this));
        registerCommand("region", new RegionCommand(this));
        registerCommand("wandsecure", new WandSecureCommand(this));
        registerCommand("monstergrid", new net.yourserver.coreengine.commands.MonsterGridCommand(this));
        registerCommand("markon", new MarkOnCommand(this));
        registerCommand("markoff", new MarkOffCommand(this));

        getLogger().info("CoreEngine v" + getDescription().getVersion()
                + " initialized (Market + Create-a-Ville hub + Homes + Teleport + HUD).");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) cmd.setExecutor(executor);
        else getLogger().warning("Command \"" + name + "\" not found in plugin.yml.");
    }

    @Override
    public void onDisable() {
        if (expirationTask != null) expirationTask.cancel();
        if (statsHudTask != null) statsHudTask.cancel();
        if (databaseManager != null) databaseManager.close();
        if (regionManager != null) regionManager.save();
        getLogger().info("CoreEngine disabled.");
    }

    /** Returns true while this player owes a chat-typed home name. */
    public boolean isAwaitingHomeRename(UUID playerUuid) {
        return pendingHomeRename.containsKey(playerUuid);
    }

    /** Starts the chat-based home rename prompt for the given slot. */
    public void requestHomeRename(UUID playerUuid, int slot) {
        pendingHomeRename.put(playerUuid, slot);
    }

    /** Returns and clears the pending rename slot, or null if none. */
    public Integer consumeHomeRename(UUID playerUuid) {
        return pendingHomeRename.remove(playerUuid);
    }

    public static CoreEngine getInstance() { return instance; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public MarketDao getMarketDao() { return marketDao; }
    public HomeDao getHomeDao() { return homeDao; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public RankManager getRankManager() { return rankManager; }
    public MarketManager getMarketManager() { return marketManager; }
    public MarketGUIManager getMarketGuiManager() { return marketGuiManager; }
    public SettingsGUIManager getSettingsGuiManager() { return settingsGuiManager; }
    public SettingsUI getSettingsUI() { return settingsUI; }

    public PlayerSettingsManager getPlayerSettingsManager() { return playerSettingsManager; }
    public TeleportManager getTeleportManager() { return teleportManager; }
    public RegionManager getRegionManager() { return regionManager; }
}