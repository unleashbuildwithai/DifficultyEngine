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
                if (Math.random() < 0.3) {
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.PURPLE, 1.0f));
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.GREEN, 1.0f));
                    loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5), 1, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(Color.BLUE, 1.0f));
                }
            }
        }, 5L, 5L);
    }

    public void setBringCommand(BringCommand bc) { this.bringCommand = bc; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        // "striking the ancient debris should be in a circle like the obsidionblocks for thgge nether"
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.ANCIENT_DEBRIS) return;

        Player player   = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Wait, the user didn't specify holding the fire staff anymore for the new portal trigger, but let's keep the magic check
        int magicLevel = skillManager.getLevel(uuid, SkillType.MAGIC);
        if (magicLevel < REQUIRED_MAGIC) {
            return;
        }

        // Check if it's part of a valid Nether Portal frame shape
        List<Block> portalAirBlocks = getPortalAirBlocks(block);
        if (portalAirBlocks.isEmpty()) return;

        event.setCancelled(true);

        // Ignite the portal
        for (Block air : portalAirBlocks) {
            air.setType(Material.WATER);
            activePortalBlocks.add(air.getLocation());
        }
        
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1.0f, 0.5f);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.5f, 1.5f);
        player.sendMessage("§5⚡ §7The Ancient Debris portal ignites!");
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
        
        if (activePortalBlocks.contains(to.getBlock().getLocation())) {
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
                    
                    // Fixed teleport coordinate calculation: Use the player's current X/Z
                    // and find a safe Y in the ancient realm.
                    Location dest = new Location(ancientWorld, player.getLocation().getX(), 77.0, player.getLocation().getZ(), player.getLocation().getYaw(), player.getLocation().getPitch());
                    
                    player.teleport(dest);
                    
                    if (bringCommand != null && bringCommand.isBringEnabled(uuid)) {
                        bringCommand.bringParty(player, dest, 10L);
                    }
                    
                    player.sendTitle("§5§l⚡ ANCIENT REALM", "§7Welcome to the forbidden dimension.", 10, 80, 20);
                }
            }, 30L); // 1.5 second delay while glitching
        }
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
                    for (int offsetY = 0; offsetY <= 1; offsetY++) {
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

    public void triggerViaLightning(Player player, Location blockLoc) {
        // Deprecated trigger, we can just redirect to checking portal shape
        onInteract(new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK, player.getInventory().getItemInMainHand(), blockLoc.getBlock(), null));
    }
}