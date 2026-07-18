package com.yourname.difficulty.party;

import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.DifficultyLevel;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * PartyHudTask — Updates a scoreboard sidebar for every player in a party.
 * Runs every 20 ticks (1 second).
 *
 * Sidebar format:
 *  §6=== Party ===
 *  §fPlayerA §c♥20 §7NM §e1,234 dmg
 *  §fPlayerB §a♥14 §7HARD §e567 dmg
 *  ...
 */
public class PartyHudTask extends BukkitRunnable {

    private final PartyManager            partyManager;
    private final PlayerDifficultyManager diffManager;
    private final JavaPlugin              plugin;

    /** Player UUID → their personal scoreboard (so we don't mess with others) */
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public PartyHudTask(PartyManager partyManager,
                        PlayerDifficultyManager diffManager,
                        JavaPlugin plugin) {
        this.partyManager = partyManager;
        this.diffManager  = diffManager;
        this.plugin       = plugin;
    }

    @Override
    public void run() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            UUID uid = viewer.getUniqueId();

            if (!partyManager.isInParty(uid)) {
                // Remove party board if they left
                if (boards.containsKey(uid)) {
                    viewer.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                    boards.remove(uid);
                }
                continue;
            }

            Scoreboard board = boards.computeIfAbsent(uid,
                k -> Bukkit.getScoreboardManager().getNewScoreboard());
            viewer.setScoreboard(board);

            // Remove stale objective
            Objective old = board.getObjective("party_hud");
            if (old != null) old.unregister();

            Objective obj = board.registerNewObjective("party_hud", Criteria.DUMMY,
                "§6=== Party ===");
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);

            Set<UUID> members = partyManager.getPartyMembers(uid);
            int score = members.size() + 1;

            for (UUID m : members) {
                Player mp = Bukkit.getPlayer(m);
                if (mp == null) continue;

                String name      = mp.getName();
                double hp        = Math.round(mp.getHealth());
                var maxHpAttr    = mp.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp     = maxHpAttr != null ? maxHpAttr.getValue() : 20.0;
                String hpColor   = hp / maxHp > 0.5 ? "§a" : hp / maxHp > 0.25 ? "§e" : "§c";
                DifficultyLevel diff = diffManager.getDifficulty(m);
                String diffTag   = diffAbbrev(diff);
                double dmg       = partyManager.getRollingDamage(m);
                String dmgStr    = dmg > 0 ? " §e" + formatDmg(dmg) : "";

                // Unique entry per player (Scoreboard requires unique strings for scores)
                String entry = hpColor + "♥" + (int)hp + " §7" + diffTag + " §f" + name + dmgStr;
                // Truncate if too long (max ~40 chars visible)
                if (entry.length() > 38) entry = entry.substring(0, 38);

                Score s = obj.getScore(entry);
                s.setScore(score--);
            }
        }
    }

    public void cleanup() {
        for (Map.Entry<UUID, Scoreboard> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        boards.clear();
    }

    private String diffAbbrev(DifficultyLevel d) {
        if (d == null) return "?";
        return switch (d) {
            case PEACEFUL  -> "PCFL";
            case EASY      -> "EASY";
            case MEDIUM    -> "MED";
            case HARD      -> "HARD";
            case NIGHTMARE -> "NM";
        };
    }

    private String formatDmg(double dmg) {
        if (dmg >= 1000) return String.format("%.1fK", dmg / 1000);
        return String.format("%.0f", dmg);
    }
}
