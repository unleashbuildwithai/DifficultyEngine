package com.yourname.difficulty.monsters;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * CustomMonsterDropListener — Replaces vanilla drops with custom drops.
 *
 * When a custom monster dies (tagged by CustomMonsterManager via PDC),
 * this listener:
 *   1. Clears vanilla drops
 *   2. Parses the drop list from the CustomMonsterDef
 *   3. Adds the custom drops to the drop list
 *
 * Drop format in monsters.yml:
 *   - MATERIAL:amount          (e.g. NETHER_STAR:1)
 *   - MATERIAL:amount:chance%  (e.g. DIAMOND:3:50% = 50% drop chance)
 */
public class CustomMonsterDropListener implements Listener {

    private final JavaPlugin           plugin;
    private final CustomMonsterManager monsterManager;
    private final NamespacedKey        customMobKey;

    public CustomMonsterDropListener(JavaPlugin plugin,
                                      CustomMonsterManager monsterManager) {
        this.plugin         = plugin;
        this.monsterManager = monsterManager;
        this.customMobKey   = new NamespacedKey(plugin, CustomMonsterManager.CUSTOM_MOB_KEY);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();

        // Check if this is a custom monster
        String monsterId = mob.getPersistentDataContainer()
                .getOrDefault(customMobKey, PersistentDataType.STRING, null);
        if (monsterId == null) return;

        CustomMonsterDef def = monsterManager.getDefinition(monsterId);
        if (def == null) return;

        // ── Replace vanilla drops ─────────────────────────────────────────
        event.getDrops().clear();

        // ── Parse and add custom drops ────────────────────────────────────
        for (String dropEntry : def.drops()) {
            ItemStack item = parseDrop(dropEntry);
            if (item != null) event.getDrops().add(item);
        }

        // ── Bonus XP for custom monsters ──────────────────────────────────
        int baseXp = event.getDroppedExp();
        event.setDroppedExp((int)(baseXp * 3.0)); // custom mobs give 3× XP
    }

    // ── Drop parser ───────────────────────────────────────────────────────────

    /**
     * Parses a drop entry string into an ItemStack.
     * Formats:
     *   MATERIAL:amount          → always drops
     *   MATERIAL:amount:chance%  → drops with given chance (0-100)
     */
    private ItemStack parseDrop(String entry) {
        if (entry == null || entry.isBlank()) return null;

        String[] parts = entry.split(":");
        if (parts.length < 2) return null;

        // Parse chance (optional 3rd part)
        if (parts.length >= 3) {
            String chanceStr = parts[2].replace("%", "").trim();
            try {
                double chance = Double.parseDouble(chanceStr) / 100.0;
                if (Math.random() >= chance) return null; // failed chance roll
            } catch (NumberFormatException ignored) {}
        }

        // Parse material
        Material mat;
        try {
            mat = Material.valueOf(parts[0].toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Monsters] Unknown material in drop: " + parts[0]);
            return null;
        }

        // Parse amount
        int amount = 1;
        try {
            amount = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ignored) {}

        amount = Math.max(1, Math.min(amount, 64));
        return new ItemStack(mat, amount);
    }
}
