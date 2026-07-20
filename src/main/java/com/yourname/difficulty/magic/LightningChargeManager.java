package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LightningChargeManager implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory;
    private final Map<UUID, Integer> playerCharges = new HashMap<>();

    public LightningChargeManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        
        // Task to display charges above the food bar using the Action Bar
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                int charges = getCharges(player);
                if (charges > 0) {
                    StringBuilder bar = new StringBuilder("§e⚡ Lightning Charges: §b");
                    for (int i = 0; i < charges; i++) {
                        bar.append("⚡");
                    }
                    player.sendActionBar(bar.toString());
                }
            }
        }, 10L, 10L); // Update every half second
    }

    public int getCharges(Player player) {
        return playerCharges.getOrDefault(player.getUniqueId(), 0);
    }

    public void setCharges(Player player, int charges) {
        playerCharges.put(player.getUniqueId(), charges);
    }

    public void addCharges(Player player, int amount) {
        setCharges(player, getCharges(player) + amount);
    }

    public boolean consumeCharge(Player player) {
        int charges = getCharges(player);
        if (charges > 0) {
            setCharges(player, charges - 1);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrinkBottle(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (itemFactory.isChargedMagicBottle(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            
            // Consume the item
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItem(event.getHand() == EquipmentSlot.HAND ? 
                    player.getInventory().getHeldItemSlot() : 40, new ItemStack(Material.AIR));
            }
            
            // Give empty bottle
            player.getInventory().addItem(itemFactory.buildEmptyMagicBottle()).values().forEach(
                dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
            
            // Add 4 charges
            addCharges(player, 4);
            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCastBottle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        
        // We use left-click to cast from the bottle or cast with charges
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        boolean hasBottle = itemFactory.isChargedMagicBottle(item);
        boolean hasEmptyBottle = itemFactory.isEmptyMagicBottle(item);
        
        if (hasBottle) {
            // Left click with full bottle -> cast and leave 3 charges
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItem(player.getInventory().getHeldItemSlot(), new ItemStack(Material.AIR));
            }
            
            player.getInventory().addItem(itemFactory.buildEmptyMagicBottle()).values().forEach(
                dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
            
            addCharges(player, 3);
            castCustomLightning(player);
            
        } else if (getCharges(player) > 0) {
            // Player has charges, left-click with empty bottle or anything else casts it?
            // Let's restrict it to empty magic bottle to be safe, or just bare hands/empty bottle.
            if (hasEmptyBottle || item.getType() == Material.AIR) {
                if (consumeCharge(player)) {
                    castCustomLightning(player);
                }
            }
        }
    }
    
    private void castCustomLightning(Player player) {
        // Find target block up to 50 blocks away
        org.bukkit.block.Block target = player.getTargetBlockExact(50, org.bukkit.FluidCollisionMode.NEVER);
        org.bukkit.Location loc = target != null ? target.getLocation().add(0.5, 1.0, 0.5) : player.getLocation().add(player.getLocation().getDirection().multiply(10));
        
        // Spawn actual lightning (no damage to player usually by default, but let's just use effect if we want)
        // Wait, standard lightning strike
        player.getWorld().strikeLightning(loc);
        
        // Custom visual for the right-click/left-click lightning
        // "very light flame pixels and white "like make them 35% less" and leace the lightning strike make it look more blue and thin black outline blue coat and white middle"
        
        // We can simulate the custom bolt with particles
        org.bukkit.Location start = loc.clone().add(0, 15, 0); // Strike from sky
        
        double distance = start.distance(loc);
        org.bukkit.util.Vector dir = loc.toVector().subtract(start.toVector()).normalize();
        
        for (double d = 0; d < distance; d += 0.5) {
            org.bukkit.Location pt = start.clone().add(dir.clone().multiply(d));
            
            // White middle
            player.getWorld().spawnParticle(Particle.DUST, pt, 5, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(org.bukkit.Color.WHITE, 1.5f));
            
            // Blue coat
            player.getWorld().spawnParticle(Particle.DUST, pt, 10, 0.3, 0.3, 0.3, 0, new Particle.DustOptions(org.bukkit.Color.BLUE, 1.0f));
            
            // Thin black outline
            player.getWorld().spawnParticle(Particle.DUST, pt, 3, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.BLACK, 0.5f));
        }
        
        // Flame pixels at base (35% less opacity/amount)
        player.getWorld().spawnParticle(Particle.FLAME, loc, 15, 1.0, 0.5, 1.0, 0.05); // reduced from normal burst
    }
}
