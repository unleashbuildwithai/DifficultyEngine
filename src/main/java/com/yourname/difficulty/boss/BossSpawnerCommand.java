package com.yourname.difficulty.boss;

import com.yourname.difficulty.boss.gilded.GildedBossManager;
import com.yourname.difficulty.boss.tempest.TempestOverlordManager;
import com.yourname.difficulty.boss.voidwither.VoidWitherManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;

/**
 * BossSpawnerCommand — Admin command to spawn dungeon bosses at their arenas.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────
 *  /spawnboss tempest  — Spawns the §5⚡ Tempest Overlord§r at Tempest Sanctum
 *                        Coordinates: 114.924, -38, -47.278
 *
 *  /spawnboss crimson  — Spawns §c🔥 The Infernal Blazefiend§r at Crimson Pit
 *                        Coordinates: -107.964, -26, -14.444
 *
 *  /spawnboss void      — Spawns §0☠ The Void Zurion§r at the Void Realm
 *
 * Requires permission: difficultyengine.cape.admin
 *
 * NOTE: All actual spawn/AI logic lives in the dedicated manager classes
 * (CrimsonBossManager, TempestOverlordManager, VoidWitherManager). This
 * command is now a thin dispatcher only.
 */
public class BossSpawnerCommand implements CommandExecutor, TabCompleter {

    // ── Tempest Sanctum coordinates ───────────────────────────────────────────
    private static final double TEMPEST_X =  114.924;
    private static final double TEMPEST_Y =  -38.0;
    private static final double TEMPEST_Z =  -47.278;

    private final JavaPlugin             plugin;
    private final CrimsonBossManager     crimsonBoss;
    private final BossEffectListener     bossEffectListener;
    private final TempestOverlordManager tempestOverlordManager;
    private final VoidWitherManager      voidWitherManager;
    private final GildedBossManager      gildedBossManager;

    public BossSpawnerCommand(JavaPlugin plugin,
                               CrimsonBossManager crimsonBoss,
                               BossEffectListener bossEffectListener,
                               TempestOverlordManager tempestOverlordManager,
                               VoidWitherManager voidWitherManager,
                               GildedBossManager gildedBossManager) {
        this.plugin                 = plugin;
        this.crimsonBoss            = crimsonBoss;
        this.bossEffectListener     = bossEffectListener;
        this.tempestOverlordManager = tempestOverlordManager;
        this.voidWitherManager      = voidWitherManager;
        this.gildedBossManager      = gildedBossManager;
    }

    private World voidWorld() {
        World voidWorld = plugin.getServer().getWorld("void_realm");
        if (voidWorld == null) voidWorld = plugin.getServer().getWorld("ancient_realm");
        if (voidWorld == null) voidWorld = plugin.getServer().getWorlds().get(0);
        return voidWorld;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        World tempWorld = plugin.getServer().getWorld("ancient_realm");
        if (tempWorld == null) {
            tempWorld = plugin.getServer().getWorlds().get(0); // fallback to overworld
        }
        final World world = tempWorld;

        if (label.equalsIgnoreCase("tpboss") || label.equalsIgnoreCase("bosstp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can teleport.");
                return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§7Usage: §e/tpboss <tempest|crimson|void>");
                return true;
            }
            switch (args[0].toLowerCase()) {
                case "tempest" -> {
                    Location loc = new Location(world, TEMPEST_X, TEMPEST_Y, TEMPEST_Z);
                    player.teleport(loc);
                    player.sendMessage("§a✓ §7Teleported to §5Tempest Sanctum§7 in the Ancient Realm!");
                }
                case "crimson" -> {
                    Location loc = new Location(world, CrimsonBossManager.SPAWN_X, CrimsonBossManager.SPAWN_Y, CrimsonBossManager.SPAWN_Z);
                    player.teleport(loc);
                    player.sendMessage("§a✓ §7Teleported to §cCrimson Pit§7 in the Ancient Realm!");
                }
                case "void" -> {
                    Location loc = new Location(voidWorld(), 0.0, 64.0, 0.0);
                    player.teleport(loc);
                    player.sendMessage("§a✓ §7Teleported to §0Void Realm§7!");
                }
                case "gilded" -> {
                    player.sendMessage("§6✓ §7Use §e/spawnboss gilded §7at your §5Gilded Sanctum§7 spawner location — no fixed coordinates set.");
                }
                default -> sender.sendMessage("§c✗ §7Unknown arena: §e" + args[0]);
            }
            return true;
        }

        if (label.equalsIgnoreCase("rebuildvoid") || label.equalsIgnoreCase("voidrebuild")) {
            Player player = (sender instanceof Player p) ? p : null;
            Location loc = new Location(voidWorld(), 0.0, 64.0, 0.0);
            crimsonBoss.rebuildArena(player, loc);
            voidWitherManager.spawnVoidWither(loc);
            sender.sendMessage("§a✓ §7Void Realm schematic loaded and Void Wither respawned!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7Usage: §e/spawnboss <tempest|crimson|void|rebuildvoid>");
            sender.sendMessage("§8  §etempest      §7— Tempest Overlord at Tempest Sanctum (114, -38, -47)");
            sender.sendMessage("§8  §ecrimson      §7— Infernal Blazefiend at Crimson Pit (-108, -26, -14)");
            sender.sendMessage("§8  §evoid         §7— Void Wither at Void Realm (0, 64, 0)");
            sender.sendMessage("§8  §erebuildvoid  §7— Reload Void schematic & spawn Wither");
            return true;
        }

        switch (args[0].toLowerCase()) {

            // ── Tempest Sanctum ───────────────────────────────────────────────
            case "tempest" -> {
                Location loc = new Location(world, TEMPEST_X, TEMPEST_Y, TEMPEST_Z);
                tempestOverlordManager.spawnTempestOverlord(loc);
                sender.sendMessage("§a✓ §7Tempest Overlord spawned at Tempest Sanctum §8(114, -38, -47)§7.");
            }

            // ── Crimson Pit ────────────────────────────────────────────────────
            case "crimson" -> {
                crimsonBoss.spawnBoss();
                sender.sendMessage("§a✓ §7The Infernal Blazefiend spawned at Crimson Pit §8(-108, -26, -14)§7.");
            }

            // ── Void Realm ─────────────────────────────────────────────────────
            case "void" -> {
                Location loc = new Location(voidWorld(), 0.0, 64.0, 0.0);
                voidWitherManager.spawnVoidWither(loc);
                sender.sendMessage("§a✓ §7The Void Wither spawned at Void Realm §8(0, 64, 0)§7.");
            }

            case "rebuildvoid" -> {
                Player player = (sender instanceof Player p) ? p : null;
                Location loc = new Location(voidWorld(), 0.0, 64.0, 0.0);
                crimsonBoss.rebuildArena(player, loc);
                voidWitherManager.spawnVoidWither(loc);
                sender.sendMessage("§a✓ §7Void Realm schematic loaded and Void Wither respawned!");
            }

            // ── Gilded Sanctum ─────────────────────────────────────────────────
            case "gilded" -> {
                if (gildedBossManager == null) {
                    sender.sendMessage("§c✗ §7Gilded boss system not available.");
                } else if (!(sender instanceof Player player)) {
                    sender.sendMessage("§c✗ §7Only players can spawn the Gilded Enforcer (needs a location).");
                } else {
                    gildedBossManager.spawnGildedEnforcer(player.getLocation());
                    sender.sendMessage("§6✓ §7The Gilded Enforcer spawned at your location!");
                }
            }

            default -> {
                sender.sendMessage("§c✗ §7Unknown arena: §e" + args[0]);
                sender.sendMessage("§7Valid arenas: §etempest§7, §ecrimson§7, §evoid§7, §egilded§7, §erebuildvoid");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return Arrays.asList("tempest", "crimson", "void", "gilded", "rebuildvoid");
        return List.of();
    }
}
