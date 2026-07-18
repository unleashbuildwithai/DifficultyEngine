package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CapeVisualTask — Ambient visual effects for equipped skill capes.
 *
 * Runs every 10 ticks (0.5 s). For each online player wearing a recognised
 * cape in the elytra/chestplate slot two effects are applied:
 *
 * ── BACK LABEL (hologram) ─────────────────────────────────────────────────
 *  An invisible ArmorStand (marker — no hitbox, no health bar) is placed
 *  0.3 blocks BEHIND the player at torso height (~0.8 blocks from feet).
 *  This makes the cape name appear to float ON the player's back rather than
 *  floating above their head alongside their name-tag.
 *  It teleports with the player every tick and is removed when the cape is
 *  taken off.
 *
 *  Examples:
 *    Melee Cape       →  §c[Melee Cape]
 *    Ranged Cape      →  §a[Ranged Cape]
 *    Defence Cape     →  §9[Defence Cape]
 *    Prayer Cape      →  §f[Prayer Cape]
 *    Magic Cape       →  §d[Magic Cape]
 *    Woodcutting Cape →  §2[WC Cape]
 *    Fishing Cape     →  §b[Fishing Cape]
 *    Farming Cape     →  §e[Farming Cape]
 *    Max Cape         →  §6[MAX CAPE]
 *    Boss Cape        →  §5[BOSS CAPE]
 *
 * ── PARTICLES ────────────────────────────────────────────────────────────
 *  Cape → Particle mapping (every other tick for subtlety, 1 per second):
 *    MELEE       → CRIT          (red sparks)
 *    RANGED      → ENCHANTED_HIT (green sparks)
 *    DEFENCE     → END_ROD       (white-blue rods)
 *    PRAYER      → ENCHANT       (floating letters)
 *    MAGIC       → DUST rainbow  (cycling rainbow sparkles)
 *    WOODCUTTING → HAPPY_VILLAGER
 *    FISHING     → FALLING_WATER
 *    FARMING     → COMPOSTER
 *    BOSS Cape   → SOUL_FIRE_FLAME
 *    Max Cape    → FIREWORK
 */
public class CapeVisualTask extends BukkitRunnable {

    /** Scorecard tag applied to every hologram stand so they can be bulk-removed. */
    public static final String HOLOGRAM_TAG = "DE_cape_sign";

    /**
     * Scoreboard tag applied to every temporary fish / axolotl entity spawned
     * by the Fishing cape effect so they can be bulk-swept on plugin disable.
     */
    private static final String FISH_TAG = "DE_cape_fish";

    /** How far behind the player the label stand is placed (blocks). */
    private static final double BACK_OFFSET = 0.30;
    /** Height from player feet where the stand is spawned (blocks). */
    private static final double BACK_HEIGHT = 0.80;

    private final SkillCapeManager        capeManager;
    private final CapeDataManager         capeDataManager;
    private final JavaPlugin              plugin;
    /** Live map of player UUID → their current cape hologram stand. */
    private final Map<UUID, ArmorStand>   holograms = new HashMap<>();
    private int                           tick      = 0;

    public CapeVisualTask(SkillCapeManager capeManager, CapeDataManager capeDataManager, JavaPlugin plugin) {
        this.capeManager     = capeManager;
        this.capeDataManager = capeDataManager;
        this.plugin          = plugin;
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
            // Cape is stored separately in CapeDataManager — not in the chestplate slot
            ItemStack chest = capeDataManager.getEquippedCape(player.getUniqueId());

            if (chest == null) {
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
        // ── Back position ─────────────────────────────────────────────────────
        // Get the horizontal direction the player is FACING, then negate it to
        // get the direction toward their back.
        Vector facing = player.getLocation().getDirection();
        Vector back   = new Vector(-facing.getX(), 0, -facing.getZ());
        if (back.lengthSquared() > 1e-6) back.normalize();

        // Place 0.3 blocks behind, 0.8 blocks up from feet  →  sits on back
        Location hologramPos = player.getLocation().clone()
                .add(back.multiply(BACK_OFFSET))
                .add(0, BACK_HEIGHT, 0);

        ArmorStand stand = holograms.get(player.getUniqueId());

        if (stand == null || stand.isDead() || !stand.isValid()) {
            // Spawn a fresh invisible marker stand
            stand = (ArmorStand) player.getWorld()
                    .spawnEntity(hologramPos, EntityType.ARMOR_STAND);
            stand.setInvisible(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setCanPickupItems(false);
            stand.setPersistent(false);        // won't save to world file
            stand.setCustomNameVisible(true);
            stand.setMarker(true);             // no hitbox → no health bar popup
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.addScoreboardTag(HOLOGRAM_TAG);
            holograms.put(player.getUniqueId(), stand);
        }

        // Update name and follow the player
        stand.setCustomName(capeSymbolText(cape));

        // Teleport to follow player (only if moved meaningfully)
        Location sl = stand.getLocation();
        if (!sl.getWorld().equals(hologramPos.getWorld())
                || sl.distanceSquared(hologramPos) > 0.01) {
            stand.teleport(hologramPos);
        }
    }

    // ── Cape symbol text ──────────────────────────────────────────────────────

    private String capeSymbolText(ItemStack cape) {
        if (capeManager.isBossCape(cape)) return "§5[BOSS CAPE]";
        if (capeManager.isMaxCape(cape))  return "§6[MAX CAPE]";
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return "";
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
        if (capeManager.isMaxCape(cape) || capeManager.isBossCape(cape)) return true;
        return (tick % 2) == 0;
    }

    private void spawnCapeParticles(Player player, ItemStack cape) {
        // Offset 0.45 blocks behind the player so particles appear on the
        // cape's surface and stay out of the first-person camera view.
        Vector pFacing = player.getLocation().getDirection();
        Vector pBack   = new Vector(-pFacing.getX(), 0, -pFacing.getZ());
        if (pBack.lengthSquared() > 1e-6) pBack.normalize();
        pBack.multiply(0.45);

        Location loc  = player.getLocation().clone().add(pBack).add(0, 1.0, 0);
        Location locH = player.getLocation().clone().add(pBack).add(0, 1.5, 0);

        if (capeManager.isBossCape(cape)) {
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc,  10, 0.3, 0.2, 0.3, 0.04);
            player.getWorld().spawnParticle(Particle.SOUL,            locH,  5, 0.2, 0.1, 0.2, 0.03);
            return;
        }
        if (capeManager.isMaxCape(cape)) {
            player.getWorld().spawnParticle(Particle.FIREWORK, loc,  12, 0.35, 0.25, 0.35, 0.06);
            player.getWorld().spawnParticle(Particle.END_ROD,  locH,  5, 0.2,  0.15, 0.2,  0.02);
            return;
        }

        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return;

        switch (skill) {
            case MELEE -> {
                player.getWorld().spawnParticle(Particle.CRIT,         loc,  9, 0.3, 0.25, 0.3, 0.06);
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, locH, 4, 0.2, 0.1,  0.2, 0.03);
            }
            case RANGED -> {
                player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc,  9, 0.3, 0.2, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.CRIT,          locH, 3, 0.2, 0.1, 0.2, 0.02);
            }
            case DEFENCE -> {
                player.getWorld().spawnParticle(Particle.END_ROD,  loc,  7, 0.3, 0.2, 0.3, 0.015);
                player.getWorld().spawnParticle(Particle.ENCHANT, locH, 4, 0.25, 0.1, 0.25, 0.08);
            }
            case PRAYER -> {
                player.getWorld().spawnParticle(Particle.ENCHANT,       loc,  11, 0.35, 0.25, 0.35, 0.12);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, locH,  3, 0.2,  0.1,  0.2,  0.0);
            }
            case MAGIC -> {
                // ── Rainbow sparkles ─────────────────────────────────────────
                // Cycle hue across the full spectrum using the tick counter.
                // Two layers with offset hues so the rainbow feels rich.
                float hue1 = (tick % 100) / 100.0f;
                float hue2 = ((tick + 33) % 100) / 100.0f;
                float hue3 = ((tick + 66) % 100) / 100.0f;

                Particle.DustOptions dust1 = new Particle.DustOptions(hsbToColor(hue1), 1.3f);
                Particle.DustOptions dust2 = new Particle.DustOptions(hsbToColor(hue2), 1.1f);
                Particle.DustOptions dust3 = new Particle.DustOptions(hsbToColor(hue3), 1.0f);

                player.getWorld().spawnParticle(Particle.DUST, loc,  7, 0.3, 0.25, 0.3, 0, dust1);
                player.getWorld().spawnParticle(Particle.DUST, locH, 4, 0.2, 0.1,  0.2, 0, dust2);
                player.getWorld().spawnParticle(Particle.DUST, loc,  3, 0.2, 0.2,  0.2, 0, dust3);
            }
            case WOODCUTTING -> {
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc,  7, 0.35, 0.2,  0.35, 0.0);
                player.getWorld().spawnParticle(Particle.COMPOSTER,      locH, 4, 0.25, 0.15, 0.25, 0.0);
            }
            case FISHING -> {
                // ── Heavy water-cascade effect ────────────────────────────────
                player.getWorld().spawnParticle(Particle.FALLING_WATER, loc,  22, 0.45, 0.35, 0.45, 0.0);
                player.getWorld().spawnParticle(Particle.FALLING_WATER, locH, 12, 0.30, 0.20, 0.30, 0.0);
                player.getWorld().spawnParticle(Particle.SPLASH,        loc,  10, 0.40, 0.25, 0.40, 0.04);
                player.getWorld().spawnParticle(Particle.SPLASH,        locH,  6, 0.25, 0.15, 0.25, 0.03);

                // ── 1-2 tropical fish launched outward every 2 s ─────────────
                if (tick % 4 == 0) {
                    spawnTemporaryFish(player, loc);
                    if (Math.random() < 0.5) spawnTemporaryFish(player, loc);
                }
                // ── Axolotl cameo every 6 s ───────────────────────────────────
                if (tick % 12 == 0) {
                    spawnTemporaryAxolotl(player, loc);
                }
            }
            case FARMING -> {
                player.getWorld().spawnParticle(Particle.COMPOSTER,      loc,  7, 0.35, 0.2,  0.35, 0.0);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, locH, 3, 0.2,  0.15, 0.2,  0.0);
            }
        }
    }

    // ── Fishing cape: temporary entity helpers ────────────────────────────────

    /**
     * Spawns a Tropical Fish at the cape position with a random outward velocity.
     * The fish has AI disabled and is automatically removed after 3 s (60 ticks).
     * It is tagged {@value #FISH_TAG} so the cleanup sweep can remove orphans.
     */
    private void spawnTemporaryFish(Player player, Location loc) {
        Location spawnLoc = loc.clone().add(
                (Math.random() - 0.5) * 0.4, 0.0, (Math.random() - 0.5) * 0.4);

        org.bukkit.entity.TropicalFish fish = (org.bukkit.entity.TropicalFish)
                player.getWorld().spawnEntity(spawnLoc, EntityType.TROPICAL_FISH);

        fish.setAI(false);
        fish.setGravity(true);
        fish.setPersistent(false);
        fish.setInvulnerable(true);
        fish.addScoreboardTag(FISH_TAG);

        // Random tropical-fish appearance
        org.bukkit.entity.TropicalFish.Pattern[] patterns =
                org.bukkit.entity.TropicalFish.Pattern.values();
        org.bukkit.DyeColor[] dyeColors = org.bukkit.DyeColor.values();
        fish.setPattern(patterns[(int)(Math.random() * patterns.length)]);
        fish.setPatternColor(dyeColors[(int)(Math.random() * dyeColors.length)]);
        fish.setBodyColor(dyeColors[(int)(Math.random() * dyeColors.length)]);

        // Launch with a gentle random outward + upward kick so it "flows" out
        fish.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.55,
                0.18 + Math.random() * 0.14,
                (Math.random() - 0.5) * 0.55
        ));

        // Auto-remove after 3 seconds (60 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (!fish.isDead()) fish.remove(); }, 60L);
    }

    /**
     * Spawns a colourful Axolotl at the cape position for a brief cameo.
     * Gravity is disabled so it floats momentarily before being removed after 2.5 s.
     */
    private void spawnTemporaryAxolotl(Player player, Location loc) {
        org.bukkit.entity.Axolotl axolotl = (org.bukkit.entity.Axolotl)
                player.getWorld().spawnEntity(loc.clone(), EntityType.AXOLOTL);

        axolotl.setAI(false);
        axolotl.setGravity(false);
        axolotl.setPersistent(false);
        axolotl.setInvulnerable(true);
        axolotl.setAdult();   // ensure adult form (setBaby(bool) removed in 1.21)
        axolotl.addScoreboardTag(FISH_TAG);

        // Random axolotl colour variant
        org.bukkit.entity.Axolotl.Variant[] variants =
                org.bukkit.entity.Axolotl.Variant.values();
        axolotl.setVariant(variants[(int)(Math.random() * variants.length)]);

        // Gentle upward + outward float
        axolotl.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.18,
                0.09,
                (Math.random() - 0.5) * 0.18
        ));

        // Auto-remove after 2.5 seconds (50 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (!axolotl.isDead()) axolotl.remove(); }, 50L);
    }

    // ── Rainbow colour helper ─────────────────────────────────────────────────

    /**
     * Converts a 0-1 hue value (full saturation, full brightness) to a
     * Bukkit {@link org.bukkit.Color} suitable for {@link Particle.DustOptions}.
     */
    private static org.bukkit.Color hsbToColor(float hue) {
        // Manual HSB → RGB conversion (avoids java.awt dependency)
        int   h  = (int)(hue * 6);
        float f  = hue * 6 - h;
        float q  = 1 - f;
        float r, g, b;
        switch (h % 6) {
            case 0  -> { r = 1; g = f; b = 0; }
            case 1  -> { r = q; g = 1; b = 0; }
            case 2  -> { r = 0; g = 1; b = f; }
            case 3  -> { r = 0; g = q; b = 1; }
            case 4  -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return org.bukkit.Color.fromRGB(
                Math.max(0, Math.min(255, (int)(r * 255))),
                Math.max(0, Math.min(255, (int)(g * 255))),
                Math.max(0, Math.min(255, (int)(b * 255))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isWearingCape(Player player) {
        return capeDataManager.hasCape(player.getUniqueId());
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

        // Sweep orphaned fish / axolotl entities left over from a previous crash
        plugin.getServer().getWorlds().forEach(world ->
            world.getEntities().forEach(e -> {
                if (e.getScoreboardTags().contains(FISH_TAG)) e.remove();
            })
        );
    }
}
