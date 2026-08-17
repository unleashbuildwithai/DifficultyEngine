package net.yourserver.coreengine.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.database.dao.HomeDao;
import net.yourserver.coreengine.database.dao.HomeDao.HomeEntry;
import net.yourserver.coreengine.economy.EconomyManager;
import net.yourserver.coreengine.rank.PlayerRank;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.util.MoneyFormat;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Optional;

public class SettingsGUIManager {

    public static final String ACTION_HOMES = "hub_homes";
    public static final String ACTION_MARKET = "hub_market";
    public static final String ACTION_TELEPORT = "hub_teleport";
    public static final String ACTION_PAY = "hub_pay";
    public static final String ACTION_BALANCE = "hub_balance";
    public static final String ACTION_PRIVACY = "hub_privacy";
    public static final String ACTION_NIGHTVISION = "hub_nightvision";
    public static final String ACTION_REMOVE_MONSTERS = "hub_remove_monsters";
    public static final String ACTION_HUD_STATS = "hub_hud_stats";
    public static final String ACTION_TP_PRIVACY = "hub_tp_privacy";
    public static final String ACTION_HOME_TELEPORT = "home_tp";
    public static final String ACTION_HOME_SAVE = "home_save";
    public static final String ACTION_HOME_OPTIONS = "home_options";
    public static final String ACTION_HOME_LOCKED = "home_locked";
    public static final String ACTION_HOME_DELETE = "home_del";
    public static final String ACTION_HOME_RENAME = "home_rename";
    public static final String ACTION_HOME_BACK = "home_back";

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final CoreEngine plugin;
    private final MarketGUIManager marketGui;
    private final HomeDao homeDao;
    private final PlayerSettingsManager settings;
    private final EconomyManager economy;

    public SettingsGUIManager(CoreEngine plugin, MarketGUIManager marketGui, HomeDao homeDao,
                              PlayerSettingsManager settings, EconomyManager economy) {
        this.plugin = plugin;
        this.marketGui = marketGui;
        this.homeDao = homeDao;
        this.settings = settings;
        this.economy = economy;
    }

    /** Parses legacy § color codes into a real Adventure Component. */
    private static Component legacy(String s) {
        return LEGACY.deserialize(s);
    }

    public void openHub(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CustomGUIHolder("CREATE_A_VILLE_SETTINGS"), 54,
                legacy("§8[§aCreate-a-Ville§8] §fSettings"));

        PlayerSettingsManager.PlayerSettings s = settings.get(player.getUniqueId());

        inv.setItem(10, button(Material.COMPASS, "§bHomes", ACTION_HOMES, "§7Open your saved homes."));
        inv.setItem(11, button(Material.EMERALD, "§aMarket", ACTION_MARKET, "§7Open the Market."));
        inv.setItem(12, button(Material.ENDER_PEARL, "§dTeleport", ACTION_TELEPORT, "§7/tp <player>"));
        inv.setItem(13, button(Material.GOLD_INGOT, "§ePay", ACTION_PAY, "§7/pay <player> <amount>"));
        inv.setItem(14, button(Material.GOLD_NUGGET, "§6Balance", ACTION_BALANCE,
                "§f" + MoneyFormat.formatWithSymbol(economy.getBalance(player.getUniqueId()))));
        inv.setItem(15, button(Material.GHAST_TEAR, "§fPrivacy (Ghost)", ACTION_PRIVACY,
                s.ghostMode ? "§aON" : "§cOFF"));
        inv.setItem(16, button(Material.GLOWSTONE_DUST, "§fNightvision", ACTION_NIGHTVISION,
                s.nightvision ? "§aON" : "§cOFF"));
        inv.setItem(19, button(Material.ZOMBIE_HEAD, "§fRemove Monsters", ACTION_REMOVE_MONSTERS,
                s.removeMonsters ? "§aON" : "§cOFF"));
        inv.setItem(20, button(Material.PAPER, "§fHUD Stats", ACTION_HUD_STATS,
                s.hudStats ? "§aON" : "§cOFF", "§7Balance, kills, shards, time, deaths"));
        inv.setItem(21, button(Material.SHIELD, "§fTP Privacy", ACTION_TP_PRIVACY,
                "§7" + s.tpPrivacy.name()));

        player.openInventory(inv);
    }

    public void openHomes(Player player) {
        Inventory inv = Bukkit.createInventory(
                new CustomGUIHolder("HOMES_MENU"), 54,
                legacy("§8[§aHomes§8] §fYour Homes"));

        int maxHomes = plugin.getRankManager().getRank(player.getUniqueId()).getMaxHomes();
        List<HomeEntry> homes = homeDao.getHomes(player.getUniqueId());

        for (int slot = 1; slot <= 50; slot++) {
            HomeEntry entry = findHome(homes, slot);
            if (slot <= maxHomes) {
                if (entry != null) {
                    inv.setItem(slot - 1, savedHomeItem(entry));
                } else {
                    inv.setItem(slot - 1, emptyHomeItem(slot));
                }
            } else {
                inv.setItem(slot - 1, lockedHomeItem(slot));
            }
        }
        inv.setItem(53, button(Material.BARRIER, "§cBack", ACTION_HOME_BACK, "§7Return to hub."));
        player.openInventory(inv);
    }

    public void openHomeOptions(Player player, int slot) {
        String title = "Home " + slot;
        Optional<HomeEntry> existing = homeDao.getHome(player.getUniqueId(), slot);
        if (existing.isPresent() && existing.get().name() != null && !existing.get().name().isEmpty()) {
            title = existing.get().name();
        }
        Inventory inv = Bukkit.createInventory(
                new CustomGUIHolder("HOMES_MENU"), 27,
                legacy("§8[§aHome§8] §f" + title));
        inv.setItem(11, button(Material.ENDER_PEARL, "§aTeleport", ACTION_HOME_TELEPORT,
                "§7Teleport to " + title + "."));
        inv.setItem(13, button(Material.NAME_TAG, "§dRename", ACTION_HOME_RENAME,
                "§7Give this home a custom name."));
        inv.setItem(15, button(Material.RED_BED, "§cDelete", ACTION_HOME_DELETE,
                "§7Delete " + title + "."));
        inv.setItem(22, button(Material.BARRIER, "§cBack", ACTION_HOME_BACK, "§7Return to homes list."));
        player.openInventory(inv);
    }

    private HomeEntry findHome(List<HomeEntry> homes, int slot) {
        for (HomeEntry e : homes) {
            if (e.slot() == slot) return e;
        }
        return null;
    }

    private ItemStack savedHomeItem(HomeEntry entry) {
        ItemStack item = new ItemStack(Material.GREEN_BED);
        var meta = item.getItemMeta();
        String label = (entry.name() != null && !entry.name().isEmpty())
                ? entry.name() : "Home " + entry.slot();
        meta.displayName(legacy("§a" + label));
        meta.lore(List.of(
                legacy("§7" + entry.worldName() + " §8(" + (int) entry.x() + ", " + (int) entry.y() + ", " + (int) entry.z() + ")"),
                legacy("§cClick to teleport / rename / delete")));
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_HOME_OPTIONS);
        meta.getPersistentDataContainer().set(PDCKeys.homeSlot(), PersistentDataType.INTEGER, entry.slot());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack emptyHomeItem(int slot) {
        ItemStack item = new ItemStack(Material.GRAY_BED);
        var meta = item.getItemMeta();
        meta.displayName(legacy("§fNew Home §7#" + slot));
        meta.lore(List.of(legacy("§aClick to save your location here.")));
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_HOME_SAVE);
        meta.getPersistentDataContainer().set(PDCKeys.homeSlot(), PersistentDataType.INTEGER, slot);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack lockedHomeItem(int slot) {
        ItemStack item = new ItemStack(Material.BLACK_BED);
        var meta = item.getItemMeta();
        meta.displayName(legacy("§8Locked Home §7#" + slot));
        meta.lore(List.of(legacy("§cLocked - requires a higher member rank.")));
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, ACTION_HOME_LOCKED);
        meta.getPersistentDataContainer().set(PDCKeys.homeSlot(), PersistentDataType.INTEGER, slot);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, String action, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(legacy(name));
        meta.lore(List.of(java.util.Arrays.stream(lore).map(SettingsGUIManager::legacy)
                .toArray(Component[]::new)));
        meta.getPersistentDataContainer().set(PDCKeys.guiAction(), PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }
}