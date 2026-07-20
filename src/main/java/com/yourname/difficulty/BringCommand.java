package com.yourname.difficulty;

import com.yourname.difficulty.party.PartyManager;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * BringCommand — /bring [on|off]
 *
 * Toggles whether your party members are pulled along when you enter a portal
 * (Nether, End, or the Ancient Realm lightning portal).
 *
 * When ON:
 *   Nether/End portals: Party members nearby are teleported 1 second after you.
 *   Ancient Realm portal: Handled by AncientDebrisPortalListener via isBringEnabled().
 *
 * Portal vortex visual:
 *   A spiralling column of purple, green, blue and black dust particles
 *   appears at each member's location followed by an upward END_ROD burst.
 *   Intended to look like a magical interdimensional wormhole effect.
 *
 * Permission: difficultyengine.use  (any player in a party)
 */
public class BringCommand implements CommandExecutor, Listener {

    private final JavaPlugin   plugin;
    private final PartyManager partyManager;

    /** Players who currently have party-portal bring enabled. */
    private final Set<UUID> bringEnabled = new HashSet<>();

    public BringCommand(JavaPlugin plugin, PartyManager partyManager) {
        this.plugin       = plugin;
        this.partyManager = partyManager;
    }

    // ── Command handler ───────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /bring."); return true;
        }

        if (!partyManager.isInParty(player.getUniqueId())) {
            player.sendMessage("§c✗ §7You must be §ein a party §7to use §e/bring§7.");
            return true;
        }

        boolean current = bringEnabled.contains(player.getUniqueId());

        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "on" -> {
                    bringEnabled.add(player.getUniqueId());
                    player.sendMessage("§5✦ §a/bring §aON §8— §7Party members will travel portals with you!");
                    player.sendActionBar("§5✦ §a Bring party: §aON");
                }
                case "off" -> {
                    bringEnabled.remove(player.getUniqueId());
                    player.sendMessage("§5✦ §c/bring §cOFF §8— §7You will travel portals alone.");
                    player.sendActionBar("§5✦ §c Bring party: §cOFF");
                }
                default -> player.sendMessage("§7Usage: §e/bring §8[on|off]");
            }
        } else {
            // Toggle
            if (current) {
                bringEnabled.remove(player.getUniqueId());
                player.sendMessage("§5✦ §c/bring §cOFF §7— portal travel disabled for party.");
                player.sendActionBar("§5✦ §c Bring party: §cOFF");
            } else {
                bringEnabled.add(player.getUniqueId());
                player.sendMessage("§5✦ §a/bring §aON §7— your party will travel portals with you!");
                player.sendActionBar("§5✦ §a Bring party: §aON");
            }
        }
        return true;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns {@code true} if this player has bring-party enabled. */
    public boolean isBringEnabled(UUID uuid) {
        return bringEnabled.contains(uuid);
    }

    /**
     * Brings all online party members to the given destination.
     * Spawns the vortex visual at each member's location and at the destination.
     * Called by the portal event handler AND by AncientDebrisPortalListener.
     *
     * @param leader   the player who triggered the portal
     * @param dest     the destination location to teleport members to
     * @param delayTicks extra ticks to wait before bringing members (0 = immediate)
     */
    public void bringParty(Player leader, Location dest, long delayTicks) {
        if (!partyManager.isInParty(leader.getUniqueId())) return;

        Set<UUID> members = partyManager.getPartyMembers(leader.getUniqueId());
        if (members.size() <= 1) return;

        // Announce to party
        for (UUID uid : members) {
            Player m = Bukkit.getPlayer(uid);
            if (m != null && m.isOnline() && !uid.equals(leader.getUniqueId())) {
                m.sendMessage("§5✦ §7Party leader §e" + leader.getName()
                    + " §7is pulling you through a portal!");
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (UUID uid : members) {
                if (uid.equals(leader.getUniqueId())) continue;
                Player m = Bukkit.getPlayer(uid);
                if (m == null || !m.isOnline()) continue;

                // Pre-teleport vortex at member location
                spawnPortalVortex(m.getLocation());

                final Player fm = m;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (!fm.isOnline()) return;
                    fm.teleport(dest);
                    // Arrival vortex
                    spawnPortalVortex(dest);
                    fm.sendMessage("§5✦ §7You were pulled through a portal by §e" + leader.getName() + "§7!");
                    fm.sendTitle("§5⚡ §dPORTAL TRAVEL", "§7Pulled by your party!", 5, 40, 10);
                    fm.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
                }, 20L);
            }
        }, Math.max(1L, delayTicks));
    }

    /**
     * Spawns the interdimensional portal vortex visual — a spiralling column
     * of purple, green, blue and black dust particles plus portal/end-rod effects.
     *
     * @param center centre point of the vortex (typically the player's feet)
     */
    public void spawnPortalVortex(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        Color[] colors = {
            Color.fromRGB(140,  0, 220),   // purple
            Color.fromRGB(  0, 200,  60),  // green
            Color.fromRGB(  0, 100, 240),  // blue
            Color.fromRGB( 15,  15,  15),  // deep black
        };

        // Ascending spiral — 3 full rotations over 3.5 blocks
        for (int i = 0; i < 90; i++) {
            double angle  = (Math.PI * 6.0 * i) / 90.0;
            double t      = (double) i / 90;
            double radius = 1.8 * (1.0 - t * 0.4);
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double y = t * 3.5;
            Location pt = center.clone().add(x, y, z);
            world.spawnParticle(Particle.DUST, pt, 2, 0.04, 0.04, 0.04, 0,
                    new Particle.DustOptions(colors[i % 4], 1.5f));
        }

        // Central burst effects
        world.spawnParticle(Particle.PORTAL,   center.clone().add(0, 1.2, 0), 80, 0.5, 1.5, 0.5, 0.6);
        world.spawnParticle(Particle.END_ROD,  center.clone().add(0, 1.0, 0), 30, 0.3, 1.0, 0.3, 0.07);
        world.spawnParticle(Particle.WITCH,    center.clone().add(0, 2.0, 0), 20, 0.5, 0.5, 0.5, 0.10);

        // Purple reverse column
        for (int y = 0; y <= 4; y++) {
            world.spawnParticle(Particle.DUST,
                center.clone().add(0, y, 0), 3, 0.15, 0.05, 0.15, 0,
                new Particle.DustOptions(Color.fromRGB(120, 0, 200), 1.2f));
        }

        // Sound
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT,   0.9f, 0.5f);
        world.playSound(center, Sound.BLOCK_PORTAL_TRIGGER,       0.5f, 1.3f);
    }

    // ── Nether / End portal auto-bring ────────────────────────────────────────

    /**
     * When a player with /bring ON enters a vanilla portal (Nether or End),
     * their online party members are teleported to the same destination after
     * a short delay.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        if (!isBringEnabled(player.getUniqueId())) return;
        if (!partyManager.isInParty(player.getUniqueId())) return;

        Location dest = event.getTo();
        if (dest == null) return;

        Set<UUID> members = partyManager.getPartyMembers(player.getUniqueId());
        if (members.size() <= 1) return;

        // Wait 1.5s for the player to fully portal before bringing party
        bringParty(player, dest, 30L);
    }
}
