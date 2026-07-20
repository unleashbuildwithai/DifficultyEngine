 package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.util.Vector;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MagicGlowTask — Ambient particle glow for held elemental staffs.
 *
 * Runs every 4 ticks (0.2 s).  When a player holds any elemental staff,
 * a deep element-specific glow is emitted near their hand position:
 *
 *   FIRE  → intense orange-red FLAME + deep crimson DUST
 *   WATER → blue DRIPPING_WATER + vivid azure DUST
 *   EARTH → emerald green DUST + nature HAPPY_VILLAGER sparks
 *   AIR   → bright cyan END_ROD + luminous cyan DUST   (DISTINCT from others)
 *
 * The task also spawns a secondary "aura" ring at torso height when the
 * player is holding their staff, reinforcing the "deeper glow" visual.
 */
public class MagicGlowTask extends BukkitRunnable {

    // ── Vibrant element glow colours ─────────────────────────────────────────
    private static final Color FIRE_DEEP  = Color.fromRGB(255,  55,   0);
    private static final Color FIRE_BRIGHT = Color.fromRGB(255, 140,  20);
    private static final Color WATER_DEEP  = Color.fromRGB( 20, 100, 255);
    private static final Color WATER_BRIGHT = Color.fromRGB( 80, 200, 255);
    private static final Color EARTH_DEEP  = Color.fromRGB(  0, 140,  30);
    private static final Color EARTH_BRIGHT = Color.fromRGB( 80, 210,  40);
    // AIR gets a VERY different colour — vivid cyan / electric blue
    private static final Color AIR_DEEP    = Color.fromRGB(  0, 220, 255);
    private static final Color AIR_BRIGHT  = Color.fromRGB(160, 245, 255);

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;
    private int tick = 0;

    /**
     * Tracks the last fake LIGHT block sent to each player holding a Fire staff.
     * On each tick we restore the previous location and place a new one at the
     * player's current position so the light follows them smoothly.
     */
    private final Map<UUID, Location> fireLightMap = new HashMap<>();

    public MagicGlowTask(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    @Override
    public void run() {
        tick++;
        Set<UUID> activeFirePlayers = new HashSet<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            MagicElement el = itemFactory.getStaffElement(hand);
            if (el == null) {
                // Not holding any staff — remove any fire light this player had
                removeFakeLight(player);
                continue;
            }

            // ── Staff glow is a level-99 Magic perk only ────────────────────
            if (skillManager.getLevel(player.getUniqueId(), SkillType.MAGIC) < 99) {
                removeFakeLight(player);
                continue;
            }

            // ── Back-right hand position (pitch-independent) ───────────────
            // Use horizontal yaw only so looking straight down never brings
            // particles into the first-person camera frustum.
            // horiz  = horizontal forward vector (no pitch)
            // rightV = 90° clockwise of horiz in XZ plane
            double yawRad = Math.toRadians(player.getLocation().getYaw());
            Vector horiz  = new Vector(-Math.sin(yawRad), 0,  Math.cos(yawRad));
            Vector rightV = new Vector( Math.cos(yawRad), 0,  Math.sin(yawRad));

            Location hand1 = player.getLocation().clone()
                    .add(horiz.clone().multiply(-0.55))   // 0.55 blocks behind
                    .add(rightV.clone().multiply(0.32))   // 0.32 blocks to right
                    .add(0, 0.85, 0);
            // Aura ring: further behind + slightly right at mid-chest
            Location aura = player.getLocation().clone()
                    .add(horiz.clone().multiply(-0.65))   // 0.65 blocks behind
                    .add(rightV.clone().multiply(0.28))   // 0.28 blocks to right
                    .add(0, 1.10, 0);

            spawnElementGlow(player, el, hand1, aura);

            // ── Fire staff: dynamic LIGHT block (level 15) follows player ───
            if (el == MagicElement.FIRE) {
                activeFirePlayers.add(player.getUniqueId());
                updateFireLight(player);
            } else {
                removeFakeLight(player);
            }
        }

        // Clean up any players who left or stopped holding fire staff
        fireLightMap.keySet().removeIf(uuid -> !activeFirePlayers.contains(uuid));
    }

    // ── Fire light helpers ─────────────────────────────────────────────────────

    /** Place / update the fake LIGHT block at the player's head position. */
    private void updateFireLight(Player player) {
        UUID uuid = player.getUniqueId();
        // Target: one block above the player's feet (typically air while standing)
        Location newLoc = player.getLocation().clone();
        newLoc.setY(Math.floor(newLoc.getY()) + 1);
        newLoc = newLoc.getBlock().getLocation();

        Location prev = fireLightMap.get(uuid);

        // Restore previous fake block if we moved
        if (prev != null && !blockLocEqual(prev, newLoc)) {
            player.sendBlockChange(prev, prev.getBlock().getBlockData());
        }

        // Only place the light block if the target block is passable (air/transparent)
        if (newLoc.getBlock().isPassable()) {
            Light lightData = (Light) Material.LIGHT.createBlockData();
            lightData.setLevel(15);
            player.sendBlockChange(newLoc, lightData);
            fireLightMap.put(uuid, newLoc);
        } else if (prev == null || !blockLocEqual(prev, newLoc)) {
            // Block not passable — track null so we don't spam restore
            fireLightMap.put(uuid, newLoc);
        }
    }

    /** Remove the fake light block for a player and restore the real block. */
    private void removeFakeLight(Player player) {
        Location prev = fireLightMap.remove(player.getUniqueId());
        if (prev != null) {
            player.sendBlockChange(prev, prev.getBlock().getBlockData());
        }
    }

    /** Block-grid equality (ignores yaw/pitch). */
    private boolean blockLocEqual(Location a, Location b) {
        return a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** Call on plugin disable to restore all fake light blocks server-wide. */
    public void cleanupLights() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            removeFakeLight(player);
        }
        fireLightMap.clear();
    }

    private void spawnElementGlow(Player player, MagicElement el, Location hand1, Location aura) {
        switch (el) {
            case FIRE -> {
                // Deep crimson flame glow at hand
                player.getWorld().spawnParticle(Particle.FLAME, hand1,
                        4, 0.07, 0.07, 0.07, 0.005);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.06, 0.06, 0.06, 0,
                        new Particle.DustOptions(FIRE_DEEP, 1.6f));
                // Vivid orange aura ring every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(FIRE_BRIGHT, 1.3f));
                    player.getWorld().spawnParticle(Particle.SMALL_FLAME, aura,
                            2, 0.18, 0.04, 0.18, 0.01);
                }
            }
            case WATER -> {
                // Deep azure water glow at hand
                player.getWorld().spawnParticle(Particle.DRIPPING_WATER, hand1,
                        3, 0.07, 0.07, 0.07, 0.01);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.06, 0.06, 0.06, 0,
                        new Particle.DustOptions(WATER_DEEP, 1.6f));
                // Vivid blue aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(WATER_BRIGHT, 1.3f));
                    player.getWorld().spawnParticle(Particle.BUBBLE_POP, aura,
                            2, 0.15, 0.04, 0.15, 0.02);
                }
            }
            case EARTH -> {
                // Deep emerald earth glow at hand
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.07, 0.07, 0.07, 0,
                        new Particle.DustOptions(EARTH_DEEP, 1.6f));
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, hand1,
                        2, 0.06, 0.06, 0.06, 0.01);
                // Vivid green aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            4, 0.20, 0.05, 0.20, 0,
                            new Particle.DustOptions(EARTH_BRIGHT, 1.3f));
                }
            }
            case AIR -> {
                // DISTINCT vibrant CYAN glow — completely different from other elements
                player.getWorld().spawnParticle(Particle.END_ROD, hand1,
                        4, 0.08, 0.08, 0.08, 0.015);
                player.getWorld().spawnParticle(Particle.DUST, hand1,
                        3, 0.07, 0.07, 0.07, 0,
                        new Particle.DustOptions(AIR_DEEP, 1.7f));
                // Bright electric cyan aura every other tick
                if (tick % 2 == 0) {
                    player.getWorld().spawnParticle(Particle.DUST, aura,
                            5, 0.22, 0.06, 0.22, 0,
                            new Particle.DustOptions(AIR_BRIGHT, 1.4f));
                    player.getWorld().spawnParticle(Particle.END_ROD, aura,
                            2, 0.18, 0.04, 0.18, 0.01);
                }
            }
        }
    }
}
