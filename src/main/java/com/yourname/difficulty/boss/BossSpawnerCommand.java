package com.yourname.difficulty.boss;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

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
 * Requires permission: difficultyengine.cape.admin
 */
public class BossSpawnerCommand implements CommandExecutor, TabCompleter {

    // ── Tempest Sanctum coordinates ───────────────────────────────────────────
    private static final double TEMPEST_X =  114.924;
    private static final double TEMPEST_Y =  -38.0;
    private static final double TEMPEST_Z =  -47.278;

    private static final double TEMPEST_MAX_HP = 400.0;

    private final JavaPlugin         plugin;
    private final CrimsonBossManager crimsonBoss;
    private final BossEffectListener bossEffectListener;
    private final Random             random = new Random();

    public BossSpawnerCommand(JavaPlugin plugin,
                               CrimsonBossManager crimsonBoss,
                               BossEffectListener bossEffectListener) {
        this.plugin             = plugin;
        this.crimsonBoss        = crimsonBoss;
        this.bossEffectListener = bossEffectListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§7Usage: §e/spawnboss <tempest|crimson>");
            sender.sendMessage("§8  §etempest §7— Tempest Overlord at Tempest Sanctum (114, -38, -47)");
            sender.sendMessage("§8  §ecrimson §7— Infernal Blazefiend at Crimson Pit (-108, -26, -14)");
            return true;
        }

        World world = plugin.getServer().getWorlds().get(0); // overworld

        switch (args[0].toLowerCase()) {

            // ── Tempest Sanctum ───────────────────────────────────────────────
            case "tempest" -> {
                Location loc = new Location(world, TEMPEST_X, TEMPEST_Y, TEMPEST_Z);

                // Spawn a Wither as the Tempest Overlord
                Wither wither = (Wither) world.spawnEntity(loc, EntityType.WITHER);
                wither.setCustomName("§5⚡ §l§dThe Tempest Overlord");
                wither.setCustomNameVisible(true);
                wither.setRemoveWhenFarAway(false);

                var hp = wither.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                if (hp != null) hp.setBaseValue(TEMPEST_MAX_HP);
                wither.setHealth(TEMPEST_MAX_HP);

                // Register with effect system (Shriek, Leached, etc.)
                bossEffectListener.registerBoss(wither);
                bossEffectListener.spawnShriek(wither);

                // Announce to all players in range
                for (Player p : world.getPlayers()) {
                    if (p.getLocation().distance(loc) > 120) continue;
                    p.sendMessage("");
                    p.sendMessage("§5⚡ §b§l⛈ THE TEMPEST OVERLORD HAS AWAKENED! ⛈ §5⚡");
                    p.sendMessage("§7The Tempest Sanctum §5crackles§7 with deadly storm energy!");
                    p.sendMessage("§6⚠ §eBring §7Air §eand §7Water §emagic — lightning is everywhere!");
                    p.sendMessage("§5⚠ §dDestroy its §5Shriek §5⚡ §dwith an §bAir Staff §dto expose its weakness!");
                    p.sendMessage("");
                    p.sendTitle("§5§l⚡ BOSS AWAKENS!", "§7§oThe Tempest Overlord roars...", 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                }

                // Dramatic lightning entrance ring
                for (int i = 0; i < 10; i++) {
                    final int fi = i;
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        double angle = fi * Math.PI * 2.0 / 10.0;
                        world.strikeLightningEffect(loc.clone().add(
                                Math.cos(angle) * 7, 0, Math.sin(angle) * 7));
                    }, fi * 3L);
                }

                // Extra lightning bolts 1 second later for drama
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    for (int i = 0; i < 4; i++) {
                        final int fi = i;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            double dx = (random.nextDouble() - 0.5) * 12;
                            double dz = (random.nextDouble() - 0.5) * 12;
                            world.strikeLightningEffect(loc.clone().add(dx, 0, dz));
                        }, fi * 5L);
                    }
                }, 20L);

                sender.sendMessage("§a✓ §7Tempest Overlord spawned at Tempest Sanctum §8(114, -38, -47)§7.");
            }

            // ── Crimson Pit ────────────────────────────────────────────────────
            case "crimson" -> {
                crimsonBoss.spawnBoss();
                sender.sendMessage("§a✓ §7The Infernal Blazefiend spawned at Crimson Pit §8(-108, -26, -14)§7.");
            }

            default -> {
                sender.sendMessage("§c✗ §7Unknown arena: §e" + args[0]);
                sender.sendMessage("§7Valid arenas: §etempest§7, §ecrimson");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return Arrays.asList("tempest", "crimson");
        return List.of();
    }
}
