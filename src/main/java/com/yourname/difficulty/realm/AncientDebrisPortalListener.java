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
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class AncientDebrisPortalListener implements Listener {

    private static final String REALM_WORLD_NAME    = "ancient_realm";
    private static final int    REQUIRED_MAGIC       = 99;

    private final JavaPlugin   plugin;
    private final SkillManager skillManager;
    private final ItemFactory  itemFactory;

    private BringCommand bringCommand = null;

    private final Set<Location> activePortalBlocks = new HashSet<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, Long> teleportCooldowns = new HashMap<>();

    public AncientDebrisPortalListener(JavaPlugin plugin, SkillManager skillManager,
                                        ItemFactory itemFactory) {
        this.plugin       = plugin;
        this.skillManager = skillManager;
        this.itemFactory  = itemFactory;
        
        // Ambient particle task for portals
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Location loc : activePortalBlocks) {
                // Dense beautiful lime-green, electric blue, and soul aura sworl
                loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 0.5, 0.5), 3, 0.3, 0.4, 0.3, 0.05);
                loc.getWorld().spawnParticle(Particle.SOUL, loc.clone().add(0.5, 0.5, 0.5), 2, 0.3, 0.4, 0.3, 0.02);
                loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 3, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(0, 180, 255), 1.2f)); // electric blue
            }
        }, 5L, 5L);
    }

    public void setBringCommand(BringCommand bc) { this.bringCommand = bc; }

    /**
     * Ignites the portal if the block is part of a valid Ancient Debris portal frame.
     * Called directly by MagicStaffListener when a Lv99 Fire Staff lightning strikes it.
     */
    public void triggerViaLightning(Player player, Location blockLoc) {
        Block block = blockLoc.getBlock();
        if (block.getType() != Material.ANCIENT_DEBRIS) return;

        int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
        if (magicLevel < REQUIRED_MAGIC) {
            player.sendMessage("§c✗ §7You need Magic Level " + REQUIRED_MAGIC + " to ignite this portal.");
            return;
        }

        List<Block> portalAirBlocks = getPortalAirBlocks(block);
        if (portalAirBlocks.isEmpty()) return;

        // Ignite the portal
        for (Block air : portalAirBlocks) {
            air.setType(Material.NETHER_PORTAL);
            activePortalBlocks.add(air.getLocation());
        }
        
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 0.5f);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.5f);
        player.sendMessage("§5⚡ §7The Ancient Debris portal ignites!");
    }
    
    @EventHandler
    public void onPortalPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
        if (event.getBlock().getType() == Material.NETHER_PORTAL) {
            if (activePortalBlocks.contains(event.getBlock().getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        if (activePortalBlocks.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) return;
        
        Block block = to.getBlock();
        if (block.getType() == Material.NETHER_PORTAL && (activePortalBlocks.contains(block.getLocation()) || isAncientDebrisPortalBlock(block))) {
            UUID uuid = player.getUniqueId();
            
            // Check cooldown
            if (teleportCooldowns.containsKey(uuid) && System.currentTimeMillis() - teleportCooldowns.get(uuid) < 5000) {
                return;
            }
            
            teleportCooldowns.put(uuid, System.currentTimeMillis());
            
            // Glitch effect
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 1));
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            
            // Spawn glitch particles
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, player.getLocation().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                
                if (player.getWorld().getName().equals(REALM_WORLD_NAME)) {
                    // Return to overworld
                    Location returnLoc = returnLocations.get(uuid);
                    if (returnLoc == null || returnLoc.getWorld() == null) {
                        World overworld = plugin.getServer().getWorlds().get(0);
                        returnLoc = overworld.getSpawnLocation();
                    }
                    player.teleport(returnLoc);
                    player.sendMessage("§a✓ §7You have returned to the §aOverworld§7!");
                } else {
                    // Go to Ancient Realm
                    returnLocations.put(uuid, player.getLocation().clone());
                    
                    World ancientWorld = plugin.getServer().getWorld(REALM_WORLD_NAME);
                    if (ancientWorld == null) {
                        player.sendMessage("§c✗ §7The Ancient Realm world does not exist!");
                        return;
                    }
                    
                    // Fixed teleport coordinate calculation: Use the precise config/world spawn coordinates
                    double x = plugin.getConfig().getDouble("ancient-realm.spawn-x", -23.320);
                    double y = plugin.getConfig().getDouble("ancient-realm.spawn-y", 77.0);
                    double z = plugin.getConfig().getDouble("ancient-realm.spawn-z", 1.450);
                    Location dest = new Location(ancientWorld, x, y, z, player.getLocation().getYaw(), player.getLocation().getPitch());
                    
                    player.teleport(dest);
                    
                    if (bringCommand != null && bringCommand.isBringEnabled(uuid)) {
                        bringCommand.bringParty(player, dest, 10L);
                    }
                    
                    player.sendTitle("§5§l⚡ ANCIENT REALM", "§7Welcome to the forbidden dimension.", 10, 80, 20);
                }
            }, 30L); // 1.5 second delay while glitching
        }
    }

    private boolean isAncientDebrisPortalBlock(Block block) {
        if (block.getType() != Material.NETHER_PORTAL) return false;
        int[][] dirs = {{1, 0, 0}, {0, 0, 1}}; // X axis portal, Z axis portal
        for (int[] dir : dirs) {
            int dx = dir[0];
            int dz = dir[1];
            for (int w = 0; w < 2; w++) {
                for (int h = 0; h < 3; h++) {
                    Block bottomLeftAir = block.getRelative(-w * dx, -h, -w * dz);
                    if (checkPortalFrameForPortalBlocks(bottomLeftAir, dx, dz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkPortalFrameForPortalBlocks(Block bottomLeftAir, int dx, int dz) {
        // Check if the 2x3 area is air, water, or portal
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 3; h++) {
                Material type = bottomLeftAir.getRelative(w * dx, h, w * dz).getType();
                if (type != Material.AIR && type != Material.WATER && type != Material.NETHER_PORTAL) {
                    return false;
                }
            }
        }
        
        // Check frame (bottom)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, -1, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (top)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, 3, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (left)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(-dx, h, -dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (right)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(2 * dx, h, 2 * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        
        return true;
    }

    private List<Block> getPortalAirBlocks(Block clickedFrameBlock) {
        // Simple 4x5 nether portal shape detection
        // We will scan a 3x3x3 area around the clicked block to find a 2x3 air gap bounded by Ancient Debris
        List<Block> result = new ArrayList<>();
        
        int[][] dirs = {{1, 0, 0}, {0, 0, 1}}; // X axis portal, Z axis portal
        
        for (int[] dir : dirs) {
            int dx = dir[0];
            int dz = dir[1];
            
            // Try to find bottom-left corner of the air gap relative to clicked block
            for (int offsetX = -2; offsetX <= 2; offsetX++) {
                for (int offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    for (int offsetY = -3; offsetY <= 1; offsetY++) { // Expanded offsetY scanner range
                        Block bottomLeftAir = clickedFrameBlock.getRelative(offsetX * dx, offsetY, offsetZ * dz);
                        
                        if (checkPortalFrame(bottomLeftAir, dx, dz)) {
                            // Collect air blocks
                            for (int w = 0; w < 2; w++) {
                                for (int h = 0; h < 3; h++) {
                                    result.add(bottomLeftAir.getRelative(w * dx, h, w * dz));
                                }
                            }
                            return result;
                        }
                    }
                }
            }
        }
        
        return result;
    }
    
    private boolean checkPortalFrame(Block bottomLeftAir, int dx, int dz) {
        // Check if the 2x3 area is air or water
        for (int w = 0; w < 2; w++) {
            for (int h = 0; h < 3; h++) {
                Material type = bottomLeftAir.getRelative(w * dx, h, w * dz).getType();
                if (type != Material.AIR && type != Material.WATER) {
                    return false;
                }
            }
        }
        
        // Check frame (bottom)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, -1, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (top)
        for (int w = 0; w < 2; w++) {
            if (bottomLeftAir.getRelative(w * dx, 3, w * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (left)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(-dx, h, -dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        // Check frame (right)
        for (int h = 0; h < 3; h++) {
            if (bottomLeftAir.getRelative(2 * dx, h, 2 * dz).getType() != Material.ANCIENT_DEBRIS) return false;
        }
        
        return true;
    }

    public boolean isAncientRealm(World world) {
        return world != null && world.getName().equals(REALM_WORLD_NAME);
    }

    public World getAncientRealmWorld() {
        return plugin.getServer().getWorld(REALM_WORLD_NAME);
    }

}
