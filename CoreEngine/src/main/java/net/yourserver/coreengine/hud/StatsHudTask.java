package net.yourserver.coreengine.hud;

import net.kyori.adventure.text.Component;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.database.dao.MarketDao;
import net.yourserver.coreengine.database.dao.MarketDao.PlayerStats;
import net.yourserver.coreengine.economy.EconomyManager;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sidebar HUD showing balance, kills, deaths, AFK shards, and time played.
 * Enabled per-player via the Create-a-Ville hub's "HUD Stats" toggle.
 */
public class StatsHudTask extends BukkitRunnable {

    private final CoreEngine plugin;
    private final EconomyManager economy;
    private final MarketDao dao;
    private final PlayerSettingsManager settings;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public StatsHudTask(CoreEngine plugin, EconomyManager economy, MarketDao dao,
                        PlayerSettingsManager settings) {
        this.plugin = plugin;
        this.economy = economy;
        this.dao = dao;
        this.settings = settings;
    }

    @Override
    public void run() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (settings.get(p.getUniqueId()).hudStats) {
                update(p);
            } else {
                remove(p);
            }
        }
    }

    private void update(Player p) {
        Scoreboard sb = boards.computeIfAbsent(p.getUniqueId(),
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective obj = sb.getObjective("stats");
        if (obj == null) {
            obj = sb.registerNewObjective("stats", Criteria.DUMMY, Component.text("§6§lStats"));
        }
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        double balance = economy.getBalance(p.getUniqueId());
        PlayerStats stats = dao.getPlayerStats(p.getUniqueId());
        long playTicks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);

        for (String entry : sb.getEntries()) {
            sb.resetScores(entry);
        }

        obj.getScore("§7Balance: §a" + MoneyFormat.formatWithSymbol(balance)).setScore(6);
        obj.getScore("§7Kills: §f" + stats.kills()).setScore(5);
        obj.getScore("§7Deaths: §f" + stats.deaths()).setScore(4);
        obj.getScore("§7Shards: §f" + stats.shards()).setScore(3);
        obj.getScore("§7Played: §f" + formatTime(playTicks)).setScore(2);
        obj.getScore("§8 ").setScore(1);

        p.setScoreboard(sb);
    }

    private void remove(Player p) {
        Scoreboard sb = boards.remove(p.getUniqueId());
        if (sb != null) {
            Objective obj = sb.getObjective("stats");
            if (obj != null) {
                obj.unregister();
            }
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private String formatTime(long ticks) {
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    /** Starts the repeating task (every 1 second). */
    public StatsHudTask start() {
        this.runTaskTimer(plugin, 20L, 20L);
        return this;
    }
}
