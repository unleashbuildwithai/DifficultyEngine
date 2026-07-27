package com.yourname.difficulty.boss;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CryingDomeParticleCommand — Admin command that pastes the
 * crying_dome_particles.schem (3 command blocks that spray purple/blue/green
 * drip particles) at the player's current location via WorldEdit.
 *
 * This replaces the old fully-manual workflow described in
 * gen_crying_dome_particles.py ("stand at dome centre, //schem load,
 * //paste -o") with a single in-game command, using the same corrected
 * WorldEdit command syntax + success/failure logging added to
 * {@link CrimsonBossManager#rebuildArena}.
 *
 * Usage: stand at the EXACT centre of the crying obsidian dome, then run
 *   /cryingdome
 *
 * Requires permission: difficultyengine.cape.admin
 */
public class CryingDomeParticleCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public CryingDomeParticleCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("difficultyengine.cape.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can paste at their own location.");
            return true;
        }

        player.sendMessage("§5⚡ §7Pasting crying dome particle emitters at your location...");

        boolean wasOp = player.isOp();
        boolean loadOk;
        boolean pasteOk;
        try {
            if (!wasOp) player.setOp(true);
            loadOk = player.performCommand("/schem load crying_dome_particles");
            pasteOk = player.performCommand("/paste -o");
        } catch (Exception ex) {
            plugin.getLogger().warning("[CryingDomeParticleCommand] performCommand threw: " + ex.getMessage());
            player.sendMessage("§c✗ §7Failed to paste — see console for details.");
            return true;
        } finally {
            if (!wasOp) player.setOp(false);
        }

        if (!loadOk || !pasteOk) {
            player.sendMessage("§c✗ §7Paste FAILED (loadOk=" + loadOk + ", pasteOk=" + pasteOk + ").");
            player.sendMessage("§8   Verify WorldEdit is installed and crying_dome_particles.schem exists in");
            player.sendMessage("§8   plugins/WorldEdit/schematics/ (see gen_crying_dome_particles.py).");
            plugin.getLogger().warning("[CryingDomeParticleCommand] Paste failed for " + player.getName()
                    + " at " + player.getLocation() + " (loadOk=" + loadOk + ", pasteOk=" + pasteOk + ")");
        } else {
            player.sendMessage("§a✓ §7Crying dome particle emitters pasted! Purple/blue/green drips should begin immediately.");
            plugin.getLogger().info("[CryingDomeParticleCommand] Pasted crying_dome_particles for "
                    + player.getName() + " at " + player.getLocation());
        }

        return true;
    }
}
