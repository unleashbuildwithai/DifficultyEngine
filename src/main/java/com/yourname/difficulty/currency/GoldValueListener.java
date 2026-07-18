package com.yourname.difficulty.currency;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GoldValueListener — Stamps a gold value line into item lore when picked up.
 * Only runs once per item (checked via existing lore marker).
 */
public class GoldValueListener implements Listener {

    private static final String MARKER = "§6Gold Value:";

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Already stamped?
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        for (String line : lore) {
            if (line.startsWith(MARKER)) return;
        }

        long value = GoldValueRegistry.getValue(item.getType());
        lore.add("§8" + "─".repeat(16));
        lore.add(MARKER + " §e" + GoldManager.formatGold(value) + " gp");
        meta.setLore(lore);
        item.setItemMeta(meta);
        event.getItem().setItemStack(item);
    }
}
