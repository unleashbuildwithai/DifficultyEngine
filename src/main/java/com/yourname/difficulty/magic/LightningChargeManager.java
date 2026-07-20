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

import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;

public class LightningChargeManager implements Listener {

    private final JavaPlugin plugin;
    private final ItemFactory itemFactory;
    private final SkillManager skillManager;
    private final Map<UUID, Integer> playerCharges = new HashMap<>();
    private final Map<UUID, Long> damageBuffEndTime = new HashMap<>();

    public LightningChargeManager(JavaPlugin plugin, ItemFactory itemFactory, SkillManager skillManager) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.skillManager = skillManager;
        
        // Task to display charges above the food bar using the Action Bar
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (itemFactory.getStaffElement(hand) != MagicElement.FIRE) {
                    continue; // Only show when actively holding the Lightning (Fire) Staff
                }
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

    public boolean hasDamageBuff(Player player) {
        Long end = damageBuffEndTime.get(player.getUniqueId());
        if (end != null && System.currentTimeMillis() < end) return true;
        damageBuffEndTime.remove(player.getUniqueId());
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
            
            // Give empty bottle back
            player.getInventory().addItem(itemFactory.buildEmptyMagicBottle()).values().forEach(
                dropped -> player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
            
            int magicLevel = skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC);
            
            if (magicLevel >= 99) {
                // Add 4 charges for Lightning Strike
                addCharges(player, 4);
                player.sendMessage("§b⚡ §7You absorbed §b4 Lightning Charges§7!");
            } else {
                // 5 minute damage buff
                player.setMetadata("lightning_damage_buff", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (5 * 60 * 1000L)));
                player.sendMessage("§b⚡ §7You feel a surge of lightning power! §8(+50% damage vs monsters, +30% vs players for 5m)");
            }

            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }
}
