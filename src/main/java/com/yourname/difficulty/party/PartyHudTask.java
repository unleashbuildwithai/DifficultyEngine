package com.yourname.difficulty.party;

import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.DifficultyLevel;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * PartyHudTask — Shows each party member as a BossBar at the top of the screen.
 *
 * ── Per member bar format ────────────────────────────────────────────────────
 *   ❤  PlayerName  HP/MaxHP  [DIFFICULTY]
 *   Progress bar = HP / MaxHP ratio
 *   Color:  GREEN  > 50 %  |  YELLOW 25–50 %  |  RED < 25 %
 *
 * Each viewer sees one BossBar per party member (excluding themselves).
 * Bars are created on party join, updated every second, removed on leave.
 *
 * The skin icon (player head) is approximated in the bar title using a
 * coloured ◆ diamond character colour-coded to the player's difficulty.
 */
public class PartyHudTask extends BukkitRunnable {

    private final PartyManager            partyManager;
    private final PlayerDifficultyManager diffManager;
    private final JavaPlugin              plugin;

    /**
     * viewer UUID → (member UUID → their BossBar shown to this viewer)
     * Outer map keyed by the player WATCHING, inner map keyed by the member
     * BEING WATCHED.
     */
    private final Map<UUID, Map<UUID, BossBar>> bossBarMap = new HashMap<>();

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
            UUID viewerUid = viewer.getUniqueId();

            if (!partyManager.isInParty(viewerUid)) {
                clearBars(viewer);
                continue;
            }

            Set<UUID> members = partyManager.getPartyMembers(viewerUid);

            Map<UUID, BossBar> myBars =
                    bossBarMap.computeIfAbsent(viewerUid, k -> new LinkedHashMap<>());

            // ── Remove bars for members who left ──────────────────────────
            myBars.entrySet().removeIf(entry -> {
                if (!members.contains(entry.getKey())) {
                    entry.getValue().removePlayer(viewer);
                    return true;
                }
                return false;
            });

            // ── Update / create a bar for each party member ───────────────
            for (UUID memberUid : members) {
                if (memberUid.equals(viewerUid)) continue; // skip self

                Player mp = Bukkit.getPlayer(memberUid);
                if (mp == null || !mp.isOnline()) continue;

                double hp    = mp.getHealth();
                var maxAttr  = mp.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double maxHp = maxAttr != null ? maxAttr.getValue() : 20.0;
                double ratio = Math.max(0.0, Math.min(1.0, hp / maxHp));

                BarColor color = ratio > 0.5 ? BarColor.GREEN
                               : ratio > 0.25 ? BarColor.YELLOW
                               : BarColor.RED;

                DifficultyLevel diff      = diffManager.getDifficulty(memberUid);
                String          icon      = diffIcon(diff);
                String          heartBar  = buildHeartBar(hp, maxHp);
                String          hpNums    = "§f" + (int) hp + "§8/§7" + (int) maxHp;
                // Format:  ◆ Name  ❤❤❤♡♡  15/20
                String          title     = icon + " §f" + mp.getName() + "  " + heartBar + " " + hpNums;

                BossBar bar = myBars.get(memberUid);
                if (bar == null) {
                    // Create new bar and show to viewer (SOLID = clean, no segments)
                    bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
                    bar.setVisible(true);
                    bar.addPlayer(viewer);
                    myBars.put(memberUid, bar);
                } else {
                    // Update existing bar
                    bar.setTitle(title);
                    bar.setColor(color);
                }
                bar.setProgress(ratio);
            }
        }
    }

    /** Remove all boss bars for a specific viewer. */
    private void clearBars(Player viewer) {
        Map<UUID, BossBar> myBars = bossBarMap.remove(viewer.getUniqueId());
        if (myBars == null) return;
        for (BossBar bar : myBars.values()) {
            bar.removePlayer(viewer);
        }
    }

    /** Clean up ALL bars — called on plugin disable. */
    public void cleanup() {
        for (Map.Entry<UUID, Map<UUID, BossBar>> viewerEntry : bossBarMap.entrySet()) {
            Player viewer = Bukkit.getPlayer(viewerEntry.getKey());
            for (BossBar bar : viewerEntry.getValue().values()) {
                if (viewer != null) bar.removePlayer(viewer);
            }
        }
        bossBarMap.clear();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a row of heart symbols representing the entity's current health.
     *
     * <p>Up to 10 heart glyphs are shown. Each heart represents an equal
     * fraction of {@code maxHp}. Filled hearts are §c (red), empty are §8 (dark grey).
     *
     * <p>Example — 15 / 20 HP → §c❤❤❤❤❤❤❤❤§8❤❤
     */
    private static String buildHeartBar(double hp, double maxHp) {
        if (maxHp <= 0) return "§8❤❤❤❤❤❤❤❤❤❤";
        int total  = 10;
        int filled = (int) Math.round((hp / maxHp) * total);
        filled = Math.max(0, Math.min(total, filled));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < filled;         i++) sb.append("§c❤");
        for (int i = filled; i < total;     i++) sb.append("§8❤");
        return sb.toString();
    }

    /**
     * A coloured diamond icon that doubles as a "portrait" colour indicator
     * for the party member's difficulty level.
     */
    private static String diffIcon(DifficultyLevel d) {
        if (d == null) return "§7◆";
        return switch (d) {
            case PEACEFUL  -> "§7◆";    // gray
            case EASY      -> "§a◆";    // green
            case MEDIUM    -> "§e◆";    // yellow
            case HARD      -> "§6◆";    // orange
            case NIGHTMARE -> "§c◆";    // red
        };
    }
}
