package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CapeVisualTask — Ambient visual effects for equipped skill capes.
 *
 * Runs every 10 ticks (0.5 s). For each online player wearing a recognised
 * cape in the elytra/chestplate slot two effects are applied:
 *
 * ── FLOATING SYMBOL (hologram) ────────────────────────────────────────────
 *  An invisible ArmorStand with a coloured symbol is maintained 2.4 blocks
 *  above the player's feet — just above their name-tag — showing which cape
 *  they have equipped. The stand teleports with the player every tick.
 *  It is removed the moment the cape is taken off.
 *
 *  Examples:
 *    Melee Cape       →  §c⚔ Melee
 *    Ranged Cape      →  §a➤ Ranged
 *    Defence Cape     →  §9⛨ Defence
 *    Prayer Cape      →  §f🕊 Prayer
 *    Magic Cape       →  §d✦ Magic
 *    Woodcutting Cape →  §2⛏ WC
 *    Fishing Cape     →  §b≋ Fishing
 *    Farming Cape     →  §e✿ Farming
 *    Max Cape         →  §6★ MAX ★
 *    Boss Cape        →  §4☠ §5BOSS §4☠
 *
 * ── PARTICLES ────────────────────────────────────────────────────────────
 *  Cape → Particle mapping (every other tick for subtlety, 1 per second):
 *    MELEE       → CRIT          (red sparks)
 *    RANGED      → ENCHANTED_HIT (green sparks)
 *    DEFENCE     → END_ROD       (white-blue rods)
 *    PRAYER      → ENCHANT       (floating letters)
 *    MAGIC       → WITCH         (purple)
 *    WOODCUTTING → HAPPY_VILLAGER
 *    FISHING     → FALLING_WATER
 *    FARMING     → COMPOSTER
 *    BOSS Cape   → SOUL_FIRE_FLAME
 *    Max Cape    → FIREWORK
 */
public class CapeVisualTask extends BukkitRunnable {

    /** Scorecard tag applied to every hologram stand so they can be bulk-removed. */
    public static final String HOLOGRAM_TAG = "DE_cape_sign";

    private final SkillCapeManager        capeManager;
    private final JavaPlugin              plugin;
    /** Live map of player UUID → their current cape hologram stand. */
    private final Map<UUID, ArmorStand>   holograms = new HashMap<>();
    private int                           tick      = 0;

    public CapeVisualTask(SkillCapeManager capeManager, JavaPlugin plugin) {
        this.capeManager = capeManager;
        this.plugin      = plugin;
    }

    @Override
    public void run() {
        tick++;

        // ── Update / remove holograms for all tracked players ─────────────────
        holograms.entrySet().removeIf(entry -> {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            ArmorStand stand = entry.getValue();
            if (p == null || !p.isOnline() || !isWearingCape(p)) {
                if (!stand.isDead()) stand.remove();
                return true; // remove from map
            }
            return false;
        });

        // ── Process all online players ────────────────────────────────────────
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack chest = player.getInventory().getChestplate();

            if (chest == null || !capeManager.isAnyCape(chest)) {
                // Remove hologram if cape was just unequipped
                ArmorStand old = holograms.remove(player.getUniqueId());
                if (old != null && !old.isDead()) old.remove();
                continue;
            }

            // ── Hologram: create or teleport ──────────────────────────────────
            updateHologram(player, chest);

            // ── Particles (every other tick = ~1 s for regular capes) ─────────
            if (!shouldEmitParticles(chest, tick)) continue;
            spawnCapeParticles(player, chest);
        }
    }

    // ── Hologram management ───────────────────────────────────────────────────

    private void updateHologram(Player player, ItemStack cape) {
        // Position: 2.0 blocks above feet = just above the player's name-tag
        Location hologramPos = player.getLocation().clone().add(0, 2.0, 0);

        ArmorStand stand = holograms.get(player.getUniqueId());

        if (stand == null || stand.isDead() || !stand.isValid()) {
            // Spawn a fresh invisible stand
            stand = (ArmorStand) player.getWorld()
                    .spawnEntity(hologramPos, EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setCanPickupItems(false);
            stand.setPersistent(false);           // won't save to world file
            stand.setCustomNameVisible(true);
            stand.addScoreboardTag(HOLOGRAM_TAG);
            holograms.put(player.getUniqueId(), stand);
        }

        // Update name and follow the player
        stand.setCustomName(capeSymbolText(cape));

        // Teleport to follow player (only if moved meaningfully)
        Location sl = stand.getLocation();
        if (!sl.getWorld().equals(hologramPos.getWorld())
                || sl.distanceSquared(hologramPos) > 0.04) {
            stand.teleport(hologramPos);
        }
    }

    // ── Cape symbol text ──────────────────────────────────────────────────────

    private String capeSymbolText(ItemStack cape) {
        if (capeManager.isBossCape(cape)) return "§5[BOSS CAPE]";
        if (capeManager.isMaxCape(cape))  return "§6[MAX CAPE]";
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return "";
        // Short coloured label — only safe ASCII so it always renders
        return switch (skill) {
            case MELEE       -> "§c[Melee Cape]";
            case RANGED      -> "§a[Ranged Cape]";
            case DEFENCE     -> "§9[Defence Cape]";
            case PRAYER      -> "§f[Prayer Cape]";
            case MAGIC       -> "§d[Magic Cape]";
            case WOODCUTTING -> "§2[WC Cape]";
            case FISHING     -> "§b[Fishing Cape]";
            case FARMING     -> "§e[Farming Cape]";
        };
    }

    // ── Particle emission ─────────────────────────────────────────────────────

    private boolean shouldEmitParticles(ItemStack cape, int tick) {
        // Max and Boss capes pulse every tick; all others every 2 ticks (~1 s)
        if (capeManager.isMaxCape(cape) || capeManager.isBossCape(cape)) return true;
        return (tick % 2) == 0;
    }

    private void spawnCapeParticles(Player player, ItemStack cape) {
        // Base location: mid-torso (cape position on back)
        var loc  = player.getLocation().add(0, 1.0, 0);
        // Slightly above for upward trail
        var locH = player.getLocation().add(0, 1.5, 0);

        if (capeManager.isBossCape(cape)) {
            // Soul fire streams upward from the cape
            player.getWorld().spawnParticle(
                    Particle.SOUL_FIRE_FLAME, loc,  10, 0.3, 0.2, 0.3, 0.04);
            player.getWorld().spawnParticle(
                    Particle.SOUL,            locH,  5, 0.2, 0.1, 0.2, 0.03);
            return;
        }
        if (capeManager.isMaxCape(cape)) {
            player.getWorld().spawnParticle(
                    Particle.FIREWORK, loc,  12, 0.35, 0.25, 0.35, 0.06);
            player.getWorld().spawnParticle(
                    Particle.END_ROD,  locH,  5, 0.2,  0.15, 0.2,  0.02);
            return;
        }

        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return;

        switch (skill) {
            case MELEE -> {
                // Red sparks burst outward from cape
                player.getWorld().spawnParticle(Particle.CRIT,         loc,  9, 0.3, 0.25, 0.3, 0.06);
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, locH, 4, 0.2, 0.1,  0.2, 0.03);
            }
            case RANGED -> {
                // Green enchanted sparks drift upward
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc,  9, 0.3, 0.2, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.CRIT,          locH, 3, 0.2, 0.1, 0.2, 0.02);
            }
            case DEFENCE -> {
                // Blue-white rods slowly drift up
                player.getWorld().spawnParticle(Particle.END_ROD,  loc,  7, 0.3, 0.2, 0.3, 0.015);
                player.getWorld().spawnParticle(Particle.ENCHANT, locH, 4, 0.25,0.1, 0.25,0.08);
            }
            case PRAYER -> {
                // Golden floating letters rise from the cape
                player.getWorld().spawnParticle(Particle.ENCHANT,       loc,  11, 0.35, 0.25, 0.35, 0.12);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, locH,  3, 0.2,  0.1,  0.2,  0.0);
            }
            case MAGIC -> {
                // Purple witch particles swirl
                player.getWorld().spawnParticle(Particle.WITCH,  loc,  9, 0.3, 0.25, 0.3, 0.025);
                player.getWorld().spawnParticle(Particle.ENCHANT, locH, 5, 0.2, 0.1,  0.2, 0.1);
            }
            case WOODCUTTING -> {
                // Happy particles and leaf-like floaters
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc,  7, 0.35, 0.2,  0.35, 0.0);
                player.getWorld().spawnParticle(Particle.COMPOSTER,      locH, 4, 0.25, 0.15, 0.25, 0.0);
            }
            case FISHING -> {
                // Water drips fall from the cape
                player.getWorld().spawnParticle(Particle.FALLING_WATER, loc,  9, 0.3,  0.2, 0.3,  0.0);
                player.getWorld().spawnParticle(Particle.SPLASH,        locH, 4, 0.2,  0.1, 0.2,  0.02);
            }
            case FARMING -> {
                // Crop/composter particles drift upward
                player.getWorld().spawnParticle(Particle.COMPOSTER,      loc,  7, 0.35, 0.2,  0.35, 0.0);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, locH, 3, 0.2,  0.15, 0.2,  0.0);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isWearingCape(Player player) {
        ItemStack chest = player.getInventory().getChestplate();
        return chest != null && capeManager.isAnyCape(chest);
    }

    /**
     * Removes all hologram stands managed by this task.
     * Call from {@code Main#onDisable()} so stands don't persist after reload.
     */
    public void cleanup() {
        for (ArmorStand stand : holograms.values()) {
            if (!stand.isDead()) stand.remove();
        }
        holograms.clear();

        // Also sweep the world for any orphaned stands from a previous crash
        plugin.getServer().getWorlds().forEach(world ->
            world.getEntitiesByClass(ArmorStand.class).forEach(stand -> {
                if (stand.getScoreboardTags().contains(HOLOGRAM_TAG)) stand.remove();
            })
        );
    }
}
