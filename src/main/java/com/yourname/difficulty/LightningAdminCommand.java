package com.yourname.difficulty;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * LightningAdminCommand — /lightningadmin [player] [on|off]
 *
 * Toggles ZERO-cooldown fast casting on all magic staffs for a target player.
 * The player still consumes runes per cast — only the inter-cast delay is removed.
 *
 * Admins with the full cape.admin permission already have no cooldown on lightning;
 * this command extends that zero-cooldown behaviour to ALL spells for any player.
 *
 * Usage:
 *   /lightningadmin              — list currently enabled players
 *   /lightningadmin Steve on     — enable instant-cast for Steve
 *   /lightningadmin Steve off    — disable instant-cast for Steve
 *
 * Permission: difficultyengine.lightningadmin  (default: op)
 */
public class LightningAdminCommand implements CommandExecutor {

    /** Players currently granted zero-cooldown fast cast. */
    private final Set<UUID> fastCastPlayers = new HashSet<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!sender.hasPermission("difficultyengine.lightningadmin")) {
            sender.sendMessage("§c✗ §7No permission. §8(difficultyengine.lightningadmin)");
            return true;
        }

        // ── /lightningadmin — list active players ─────────────────────────────
        if (args.length == 0) {
            if (fastCastPlayers.isEmpty()) {
                sender.sendMessage("§e⚡ §7No players currently have Lightning Admin fast-cast.");
            } else {
                sender.sendMessage("§e⚡ §7Fast-cast active for §e" + fastCastPlayers.size() + " §7player(s):");
                for (UUID uid : fastCastPlayers) {
                    Player p = Bukkit.getPlayer(uid);
                    String name = (p != null) ? p.getName() : "§8[offline: " + uid + "§8]";
                    sender.sendMessage("§8  • §e" + name);
                }
            }
            return true;
        }

        // ── /lightningadmin <player> <on|off> ────────────────────────────────
        if (args.length < 2) {
            sender.sendMessage("§7Usage: §e/lightningadmin §7[player] §8[on|off]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage("§c✗ §7Player not online: §e" + args[0]);
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "on" -> {
                fastCastPlayers.add(target.getUniqueId());
                target.sendMessage(
                    "§e⚡ §6Lightning Admin §afast-cast enabled§7! "
                    + "§8(All spells cast instantly — runes still consumed)");
                target.sendActionBar("§e⚡ §6FAST CAST §aON §8— instant spell cooldown!");
                sender.sendMessage("§e⚡ §7Enabled fast-cast for §e" + target.getName() + "§7.");
            }
            case "off" -> {
                boolean had = fastCastPlayers.remove(target.getUniqueId());
                if (!had) {
                    sender.sendMessage("§7Fast-cast was already off for §e" + target.getName() + "§7.");
                } else {
                    target.sendMessage("§e⚡ §7Lightning Admin fast-cast §cdisabled§7. Normal cooldowns restored.");
                    target.sendActionBar("§e⚡ §c FAST CAST OFF §8— normal cooldowns restored.");
                    sender.sendMessage("§e⚡ §7Disabled fast-cast for §e" + target.getName() + "§7.");
                }
            }
            default -> sender.sendMessage("§7Usage: §e/lightningadmin §7[player] §8[on|off]");
        }
        return true;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns {@code true} if this player currently has fast-cast active. */
    public boolean hasFastCast(UUID uuid) {
        return fastCastPlayers.contains(uuid);
    }

    /** Clears all fast-cast grants (called on plugin disable). */
    public void disableAll() {
        for (UUID uid : new HashSet<>(fastCastPlayers)) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) {
                p.sendMessage("§e⚡ §7Lightning Admin fast-cast §cdisabled §8(server/plugin restart).");
            }
        }
        fastCastPlayers.clear();
    }
}
