package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

public class SupportPotionListener implements Listener {

    private final ItemFactory itemFactory;

    public SupportPotionListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    private boolean isSupportPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("de_support_potion_")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupportRune(Player player) {
        for (ItemStack s : player.getInventory().getContents()) {
            if (itemFactory.isSupportRune(s)) {
                return true;
            }
        }
        return false;
    }

    private void consumeSupportRune(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (itemFactory.isSupportRune(contents[i])) {
                ItemStack item = contents[i];
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().setItem(i, item);
                } else {
                    player.getInventory().setItem(i, new ItemStack(Material.AIR));
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!isSupportPotion(item)) return;

        Player player = event.getPlayer();

        if (hasSupportRune(player)) {
            // Cancel consumption so the potion is unlimited use!
            event.setCancelled(true);

            // Consume one Support Rune as the "unlimited cast cost"
            consumeSupportRune(player);

            // Manually apply potion effects since the consume event is cancelled
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta != null && meta.hasCustomEffects()) {
                for (PotionEffect effect : meta.getCustomEffects()) {
                    player.addPotionEffect(effect);
                }
            }

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f);
            player.sendMessage("§5✦ §7Blessing applied successfully! §8(1× Support Rune consumed)");
        } else {
            // No Support Rune -> Potion breaks and shatters!
            // Let the item be consumed (or removed) but cancel applying any custom buffs!
            event.setCancelled(true);

            // Remove the potion from their hand (breaks it)
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }

            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.8f);
            player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
            player.sendMessage("§c✗ §4SHATTERED! §7The blessing potion shattered on use because you did not carry a §5Support Rune§7!");
        }
    }
}
