package com.yourname.difficulty.realm;

import com.yourname.difficulty.BringCommand;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * AncientDebrisPortalListener — Opens the Ancient Realm via an Ancient Debris ritual.
 *
 * ── Activation Sequence ──────────────────────────────────────────────────
 *  1. Player must have Magic level ≥ 99
 *  2. Player right-clicks an ANCIENT_DEBRIS block while holding a Fire Staff
 *  3. Lightning effect strikes + vortex particles
 *  4. After 2 seconds: player teleported to "ancient_realm" world
 *     If /bring is ON, party members are pulled through with a vortex visual.
 *
 * ── Return Portal ─────────────────────────────────────────────────────────
 *  Inside the ancient_realm: right-click any ANCIENT_DEBRIS block with Fire Staff
 *  → teleport back to entry point in Overworld
 *
 * ── MultiverseCore Integration ────────────────────────────────────────────
 *  The "ancient_realm" world is managed by MultiverseCore.
 *  If it doesn't exist, the listener logs a warning.
 *  Create it with: /mv create ancient_realm NORMAL
 *
 * ── Spawn location ────────────────────────────────────────────────────────
 *  Default spawn in ancient_realm: -23.320, 77, 1.450
 *  Customizable via config.yml: ancient-realm.spawn-x/y/z
 */
public class AncientDebrisPortalListener implements Listener {

    private static final String REALM_WORLD_NAME    = "ancient_realm";
    private static final int    REQUIRED_MAGIC       = 99;
    private static final long   PORTAL_DELAY_TICKS   = 40L; // 2 seconds

    private final JavaPlugin   plugin;
    private final SkillManager skillManager;
    private final ItemFactory  itemFactory;

    /** Optional BringCommand — pulls party members through the portal if /bring is ON. */
    private BringCommand bringCommand = null;

    /** Tracks which players are currently in the portal activation animation. */
    private final Set<UUID> activating = Collections.synchronizedSet(new HashSet<>());

    /** Stores each player's overworld return location (world, xyz). */
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public AncientDebrisPortalListener(JavaPlugin plugin, SkillManager skillManager,
                                        ItemFactory itemFactory) {
        this.plugin       = plugin;
        this.skillManager = skillManager;
        this.itemFactory  = itemFactory;
    }

    /** Wires in the BringCommand so the portal can pull party members through. */
    public void setBringCommand(BringCommand bc) { this.bringCommand = bc; }

    // ── PlayerInteractEvent ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ANCIENT_DEBRIS) return;

        Player player   = event.getPlayer();
        ItemStack held  = player.getInventory().getItemInMainHand();

        // Must be holding a Fire Staff at Lv99 (right-click = portal, lightning handled here)
        MagicElement el = itemFactory.getStaffElement(held);
        if (el != MagicElement.FIRE) return;

        event.setCancelled(true);
        UUID uuid = player.getUniqueId();

        if (activating.contains(uuid)) return; // already in animation

        // ── Check if in ancient_realm (return portal) ─────────────────────
        if (player.getWorld().getName().equals(REALM_WORLD_NAME)) {
            openReturnPortal(player, block.getLocation());
            return;
        }

        // ── Overworld → Ancient Realm ─────────────────────────────────────
        int magicLevel = skillManager.getLevel(uuid, SkillType.MAGIC);
        if (magicLevel < REQUIRED_MAGIC) {
            player.sendMessage("§c✗ §7You need §bMagic level " + REQUIRED_MAGIC
                    + " §7to open the Ancient Realm. (Current: §b" + magicLevel + "§7)");
            player.sendActionBar("§c⚡ §7Requires Magic Level §b99");
            return;
        }

        openPortal(player, block.getLocation());
    }

    // ── Portal opening ritual ─────────────────────────────────────────────────

    private void openPortal(Player player, Location blockLoc) {
        UUID uuid = player.getUniqueId();
        activating.add(uuid);

        // Save return location
        returnLocations.put(uuid, player.getLocation().clone());

        // Phase 1: Lightning + particles
        blockLoc.getWorld().strikeLightning(blockLoc.clone().add(0, 1, 0));
        blockLoc.getWorld().playSound(blockLoc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.5f);
        player.sendMessage("§5⚡ §7The Ancient Debris trembles...");
        player.sendTitle("§5⚡ ANCIENT REALM", "§7Preparing portal...", 5, 60, 5);

        // Phase 2: Particle vortex over next 2 seconds
        plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            double angle = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 38) return; // stop before teleport
                ticks++;
                angle += Math.PI / 8;
                double x = 1.5 * Math.cos(angle);
                double z = 1.5 * Math.sin(angle);
                blockLoc.getWorld().spawnParticle(
                        Particle.PORTAL, blockLoc.clone().add(x, 1, z), 5, 0.1, 0.3, 0.1, 0.3);
                blockLoc.getWorld().spawnParticle(
                        Particle.END_ROD, blockLoc.clone().add(x, 1.5, z), 2, 0.1, 0.1, 0.1, 0.05);
            }
        }, 0L, 1L);

        // Phase 3: Break block + teleport after 2 seconds
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) { activating.remove(uuid); return; }

            // Remove the Ancient Debris block
            blockLoc.getBlock().setType(Material.AIR);

            // Final explosion particle
            blockLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, blockLoc, 3, 0.5, 0.5, 0.5, 0);
            blockLoc.getWorld().playSound(blockLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.5f);

            // Teleport to ancient realm
            World ancientWorld = plugin.getServer().getWorld(REALM_WORLD_NAME);
            if (ancientWorld == null) {
                player.sendMessage("§c✗ §7The Ancient Realm world does not exist!");
                player.sendMessage("§7Ask an admin to run: §e/mv create ancient_realm NORMAL");
                activating.remove(uuid);
                return;
            }

            // Get spawn location from config (defaults set in Main.java to -23.320, 77, 1.450)
            double spawnX = plugin.getConfig().getDouble("ancient-realm.spawn-x", -23.320);
            double spawnY = plugin.getConfig().getDouble("ancient-realm.spawn-y", 77.0);
            double spawnZ = plugin.getConfig().getDouble("ancient-realm.spawn-z", 1.450);
            Location dest = new Location(ancientWorld, spawnX, spawnY, spawnZ, 0, 0);

            player.teleport(dest);
            activating.remove(uuid);

            // ── Bring party if /bring is enabled ──────────────────────────────
            if (bringCommand != null && bringCommand.isBringEnabled(uuid)) {
                bringCommand.bringParty(player, dest, 10L);
            }

            // Arrival effects — use final ref for lambda capture
            final World realmRef = ancientWorld;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendTitle("§5§l⚡ ANCIENT REALM", "§7Welcome to the forbidden dimension.", 10, 80, 20);
                player.sendMessage("§5⚡ §7You have entered the §5§lAncient Realm§7!");
                player.sendMessage("§7Use a §cFire Staff §7on §5Ancient Debris §7to return.");
                player.playSound(player.getLocation(), Sound.AMBIENT_NETHER_WASTES_MOOD, 2f, 0.8f);
                realmRef.setTime(18000);
            }, 5L);

        }, PORTAL_DELAY_TICKS);
    } // end openPortal

    // ── Return portal ─────────────────────────────────────────────────────────

    private void openReturnPortal(Player player, Location blockLoc) {
        UUID uuid = player.getUniqueId();
        activating.add(uuid);

        blockLoc.getWorld().strikeLightningEffect(blockLoc.clone().add(0, 1, 0));
        blockLoc.getWorld().playSound(blockLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2f, 0.7f);
        player.sendMessage("§5⚡ §7Returning to the Overworld...");

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            activating.remove(uuid);
            if (!player.isOnline()) return;

            Location returnLoc = returnLocations.get(uuid);
            if (returnLoc == null || returnLoc.getWorld() == null) {
                // Fall back to overworld spawn
                World overworld = plugin.getServer().getWorlds().get(0);
                returnLoc = overworld.getSpawnLocation();
            }

            player.teleport(returnLoc);
            player.sendMessage("§a✓ §7You have returned to the §aOverworld§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);
        }, 20L);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns true if the given world is the Ancient Realm. */
    public boolean isAncientRealm(World world) {
        return world != null && world.getName().equals(REALM_WORLD_NAME);
    }

    /** Returns the Ancient Realm world, or null if it doesn't exist. */
    public World getAncientRealmWorld() {
        return plugin.getServer().getWorld(REALM_WORLD_NAME);
    }

    // ── Ranged trigger (via Fire Staff Lv99 lightning aimed at debris) ────────

    /**
     * Called by {@code MagicStaffListener.castLightning()} when the lightning
     * ray-trace hits an ANCIENT_DEBRIS block.
     *
     * @param player   the player who cast the lightning
     * @param blockLoc the world location of the struck Ancient Debris block
     */
    public void triggerViaLightning(Player player, Location blockLoc) {
        UUID uuid = player.getUniqueId();
        if (activating.contains(uuid)) return; // already in animation

        int magicLevel = skillManager.getLevel(uuid, SkillType.MAGIC);
        if (magicLevel < REQUIRED_MAGIC) {
            player.sendMessage("§c✗ §7You need §bMagic level " + REQUIRED_MAGIC
                    + " §7to open the Ancient Realm. (Current: §b" + magicLevel + "§7)");
            player.sendActionBar("§c⚡ §7Requires Magic Level §b99");
            return;
        }

        // ── Inside ancient_realm → return portal ─────────────────────────────
        if (player.getWorld().getName().equals(REALM_WORLD_NAME)) {
            openReturnPortal(player, blockLoc);
            return;
        }

        // ── Overworld → ancient_realm ─────────────────────────────────────────
        openPortal(player, blockLoc);
    }
}
