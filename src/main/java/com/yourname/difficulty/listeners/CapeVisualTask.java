package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CapeVisualTask — Ambient visual effects for equipped skill capes.
 *
 * Runs every 10 ticks (0.5 s).  For each online player wearing a recognised
 * cape two effects are applied:
 *
 * ── BACK LABEL (hologram) ─────────────────────────────────────────────────
 *  An invisible ArmorStand (marker) is placed 0.3 blocks BEHIND the player
 *  at torso height (~0.8 blocks from feet).
 *
 * ── SHAPED PARTICLES ──────────────────────────────────────────────────────
 *  Each skill cape renders its attribute symbol in DUST particles:
 *    MELEE       → ⚔  sword shape
 *    RANGED      → 🏹  bow + arrow shape
 *    DEFENCE     → 🛡  shield shape
 *    PRAYER      → ✟  cross shape
 *    MAGIC       → ★  six-pointed star (rainbow cycling)
 *    WOODCUTTING → 🪓  axe shape
 *    FISHING     → water cascade + tropical fish + axolotl pixel art
 *    FARMING     → 🛒  minecart shape
 *    BOSS Cape   → SOUL_FIRE_FLAME + SOUL cloud
 *    Max Cape    → FIREWORK + END_ROD burst
 *
 * ── SWAP FIX ──────────────────────────────────────────────────────────────
 *  A lastCapeName map tracks the displayed cape name.  When a swap is
 *  detected the old hologram stand is removed immediately — this prevents
 *  the "ghost name / health-bar glitched into the world" bug.
 *
 * ── FISH / AXOLOTL ENTITY FIX ─────────────────────────────────────────────
 *  All spawned TropicalFish and Axolotl entities are added to the
 *  "de_cape_mobs" scoreboard team.  That team has:
 *    COLLISION_RULE      = NEVER  → entity cannot physically push the player.
 *    NAME_TAG_VISIBILITY = NEVER  → suppresses the health-bar overlay that
 *                                   appears when the player looks at the entity.
 */
public class CapeVisualTask extends BukkitRunnable {

    /** Scoreboard tag applied to every hologram stand. */
    public static final String HOLOGRAM_TAG = "DE_cape_sign";

    /** Scoreboard tag applied to every temporary fish entity. */
    private static final String FISH_TAG = "DE_cape_fish";

    private static final double BACK_OFFSET = 0.30;
    private static final double BACK_HEIGHT = 0.80;

    private final SkillCapeManager      capeManager;
    private final CapeDataManager       capeDataManager;
    private final JavaPlugin            plugin;

    /**
     * Scoreboard team applied to all spawned fish and axolotl entities.
     * Configured with COLLISION_RULE = NEVER and NAME_TAG_VISIBILITY = NEVER.
     */
    private final Team capeEntityTeam;

    /** Live map of player UUID → their current cape hologram stand. */
    private final Map<UUID, ArmorStand> holograms    = new HashMap<>();

    /**
     * Tracks the display name of each player's last-known equipped cape.
     * Used to detect swaps so the old stand can be killed immediately.
     */
    private final Map<UUID, String>     lastCapeName = new HashMap<>();

    private int tick = 0;

    // ── Fishing cape — single rainbow axolotl companion ──────────────────────
    /** One rainbow axolotl lazily swimming behind the player. */
    private static final int    AXOLOTL_COUNT  = 1;
    /** Swim-path amplitudes and frequencies. */
    private static final double SWIM_SIDE_AMP  = 1.80;   // wider side-to-side (was 0.80)
    private static final double SWIM_SIDE_FREQ = 0.42;   // side-to-side speed
    private static final double SWIM_VERT_AMP  = 1.20;   // larger up-down range (was 0.25)
    private static final double SWIM_VERT_FREQ = 0.65;   // slower vertical bob (was 1.10)
    private static final double SWIM_BACK_DIST = 3.20;   // further behind player (was 0.65)
    private static final double SWIM_HEIGHT    = 1.40;   // base height above feet
    /** Time-counter advance per run cycle. */
    private static final double ORBIT_SPEED    = 0.055;

    private final Map<UUID, List<org.bukkit.entity.Entity>> fishingOrbit  = new HashMap<>();
    private final Map<UUID, Double>                         fishingAngles = new HashMap<>();

    public CapeVisualTask(SkillCapeManager capeManager,
                          CapeDataManager  capeDataManager,
                          JavaPlugin       plugin) {
        this.capeManager     = capeManager;
        this.capeDataManager = capeDataManager;
        this.plugin          = plugin;
        this.capeEntityTeam  = ensureCapeEntityTeam();
    }

    /**
     * Creates (or retrieves) the {@code "de_cape_mobs"} scoreboard team on the
     * main scoreboard and configures it so that cape fish/axolotl entities:
     * <ul>
     *   <li>Never collide with players (no physical push).</li>
     *   <li>Never show a name-tag / health-bar overlay.</li>
     * </ul>
     */
    private Team ensureCapeEntityTeam() {
        Scoreboard sb = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("de_cape_mobs");
        if (team == null) {
            team = sb.registerNewTeam("de_cape_mobs");
        }
        team.setOption(Team.Option.COLLISION_RULE,      Team.OptionStatus.NEVER);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        return team;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Main loop
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public void run() {
        tick++;

        // ── Sweep stale holograms (player offline / dead / cape removed) ──────────
        holograms.entrySet().removeIf(entry -> {
            Player     p     = plugin.getServer().getPlayer(entry.getKey());
            ArmorStand stand = entry.getValue();
            if (p == null || !p.isOnline() || p.isDead() || !isWearingCape(p)) {
                if (!stand.isDead()) stand.remove();
                lastCapeName.remove(entry.getKey());
                despawnFishingOrbit(entry.getKey());
                return true;
            }
            return false;
        });

        // ── Process every online player ────────────────────────────────────
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isDead()) {
                ArmorStand old = holograms.remove(player.getUniqueId());
                if (old != null && !old.isDead()) old.remove();
                lastCapeName.remove(player.getUniqueId());
                despawnFishingOrbit(player.getUniqueId());
                continue;
            }
            ItemStack cape = capeDataManager.getEquippedCape(player.getUniqueId());

            if (cape == null) {
                // Cape unequipped — destroy hologram immediately
                ArmorStand old = holograms.remove(player.getUniqueId());
                if (old != null && !old.isDead()) old.remove();
                lastCapeName.remove(player.getUniqueId());
                despawnFishingOrbit(player.getUniqueId());
                continue;
            }

            // ── Cape-swap fix: detect name change → kill old stand ─────────
            String currentName = getCapeName(cape);
            String lastName    = lastCapeName.get(player.getUniqueId());
            if (!currentName.equals(lastName)) {
                // Cape changed — remove old hologram so no ghost lingers
                ArmorStand old = holograms.remove(player.getUniqueId());
                if (old != null && !old.isDead()) old.remove();
                lastCapeName.put(player.getUniqueId(), currentName);
            }

            // ── Update / create hologram ───────────────────────────────────
            updateHologram(player, cape);

            // ── Emit particles (every other run for skill capes) ───────────
            if (!shouldEmitParticles(cape)) continue;
            spawnCapeParticles(player, cape);

            // ── Fishing orbit cleanup for non-fishing capes ────────────────
            SkillType activeSk = capeManager.getCapeSkill(cape);
            if (activeSk != SkillType.FISHING) {
                despawnFishingOrbit(player.getUniqueId());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Hologram management
    // ═══════════════════════════════════════════════════════════════════════

    private void updateHologram(Player player, ItemStack cape) {
        // Position: directly behind the player at torso height
        Vector facing = player.getLocation().getDirection();
        Vector back   = new Vector(-facing.getX(), 0, -facing.getZ());
        if (back.lengthSquared() > 1e-6) back.normalize();

        Location hologramPos = player.getLocation().clone()
                .add(back.multiply(BACK_OFFSET))
                .add(0, BACK_HEIGHT, 0);

        ArmorStand stand = holograms.get(player.getUniqueId());

        if (stand == null || stand.isDead() || !stand.isValid()) {
            // Remove stale reference if any
            if (stand != null && !stand.isDead()) stand.remove();

            // world.spawn() with Consumer — all flags set BEFORE entity packet is
            // sent to the client, so the client never sees an untouched ArmorStand
            // and the crosshair cannot show "Armour Stand" even for one tick.
            stand = player.getWorld().spawn(hologramPos, ArmorStand.class, s -> {
                s.setMarker(true);          // no hitbox → crosshair can't target
                s.setInvisible(true);
                s.setSmall(true);
                s.setGravity(false);
                s.setCanPickupItems(false);
                s.setPersistent(false);     // not saved to world NBT
                s.setBasePlate(false);
                s.setArms(false);
                s.addScoreboardTag(HOLOGRAM_TAG);
            });
            holograms.put(player.getUniqueId(), stand);
        }

        // Hide label when the player looks steeply downward (avoids double-image)
        if (player.getLocation().getPitch() > 55f) {
            stand.setCustomNameVisible(false);
            stand.setCustomName(null);
        } else {
            stand.setCustomNameVisible(true);
            stand.setCustomName(capeLabel(cape));
        }

        // Only teleport when the stand has actually moved (saves bandwidth)
        Location sl = stand.getLocation();
        if (!sl.getWorld().equals(hologramPos.getWorld())
                || sl.distanceSquared(hologramPos) > 0.09) {
            stand.teleport(hologramPos);
        }
    }

    // ── Label text ────────────────────────────────────────────────────────

    private String capeLabel(ItemStack cape) {
        if (capeManager.isBossCape(cape)) return "§5[BOSS CAPE]";
        if (capeManager.isMaxCape(cape))  return "§6[MAX CAPE]";
        SkillType skill = capeManager.getCapeSkill(cape);
        if (skill == null) return "§8[Cape]";
        return switch (skill) {
            case MELEE       -> "§c[⚔ Melee Cape]";
            case RANGED      -> "§a[🏹 Ranged Cape]";
            case DEFENCE     -> "§9[🛡 Defence Cape]";
            case PRAYER      -> "§f[✟ Prayer Cape]";
            case MAGIC       -> "§d[✦ Magic Cape]";
            case WOODCUTTING -> "§2[🪓 WC Cape]";
            case FISHING     -> "§b[🐟 Fishing Cape]";
            case FARMING     -> "§e[🛒 Farming Cape]";
        };
    }

    /** Stable identifier used for swap-detection (avoids colour-code noise). */
    private String getCapeName(ItemStack cape) {
        if (cape == null || !cape.hasItemMeta()) return "";
        var m = cape.getItemMeta();
        return m.hasDisplayName() ? m.getDisplayName() : "";
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Particle dispatch
    // ═══════════════════════════════════════════════════════════════════════

    private boolean shouldEmitParticles(ItemStack cape) {
        // Boss + Max cape emit every tick; skill capes emit every other tick
        return capeManager.isMaxCape(cape) || capeManager.isBossCape(cape)
                || (tick % 2) == 0;
    }

    private void spawnCapeParticles(Player player, ItemStack cape) {
        // Common back-offset location (cape surface, mid and upper torso)
        Vector pFacing = player.getLocation().getDirection();
        Vector pBack   = new Vector(-pFacing.getX(), 0, -pFacing.getZ());
        if (pBack.lengthSquared() > 1e-6) pBack.normalize();
        // Push particles 1.2 blocks behind and BELOW eye-level (1.62 m) so they
        // never clip into first-person view even at wide FoV or when looking upward.
        pBack.multiply(1.2);

        Location loc  = player.getLocation().clone().add(pBack).add(0, 0.85, 0);
        Location locH = player.getLocation().clone().add(pBack).add(0, 1.20, 0);

        // Right-hand vector (perpendicular to facing, horizontal)
        // right = (-fz, 0, fx) — same as facing × (0,1,0)
        Vector right = new Vector(-pFacing.getZ(), 0, pFacing.getX());
        if (right.lengthSquared() > 1e-6) right.normalize();

        // ── Special capes ─────────────────────────────────────────────────
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

        // ── Fishing cape: water cascade + dual-ring orbit ─────────────────
        if (skill == SkillType.FISHING) {
            try {
                player.getWorld().spawnParticle(Particle.FALLING_WATER, loc,  10, 0.35, 0.25, 0.35, 0.0);
                player.getWorld().spawnParticle(Particle.FALLING_WATER, locH,  5, 0.20, 0.12, 0.20, 0.0);
                player.getWorld().spawnParticle(Particle.SPLASH,        loc,   5, 0.30, 0.15, 0.30, 0.03);
                player.getWorld().spawnParticle(Particle.UNDERWATER,    loc,   3, 0.30, 0.20, 0.30, 0.0);
            } catch (Exception ex) {
                plugin.getLogger().warning("[CapeVFX] Fishing water particles error: " + ex.getMessage());
            }
            // Update orbit rings (teleports persistent entities to their positions)
            updateFishingOrbit(player);
            return;
        }

        // ── All other skill capes: shaped attribute symbol ─────────────
        spawnSymbolParticles(player, skill, loc, right);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Shaped attribute symbol particles
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Renders each cape's attribute symbol as DUST particles arranged in a
     * 2-D sprite on the plane perpendicular to the player's facing direction.
     *
     * Coordinates: (rightOffset, upOffset) in blocks, centred on {@code center}.
     */
    private void spawnSymbolParticles(Player player, SkillType skill,
                                      Location center, Vector right) {
        double[][] shape = getSymbolShape(skill);

        // Magic cape: rainbow-cycling colour per tick
        Particle.DustOptions dust;
        if (skill == SkillType.MAGIC) {
            float hue = (tick % 100) / 100.0f;
            dust = new Particle.DustOptions(hsbToColor(hue), 1.3f);
        } else {
            dust = new Particle.DustOptions(getSkillColor(skill), 1.2f);
        }

        for (double[] pt : shape) {
            double rOff = pt[0];
            double uOff = pt[1];

            // Tiny random jitter for a shimmering/sparkle look
            double jitter = (Math.random() - 0.5) * 0.04;

            // ~75 % chance to spawn each point — gives a twinkling effect
            if (Math.random() < 0.25) continue;

            Location pLoc = center.clone()
                    .add(right.clone().multiply(rOff))
                    .add(0, uOff + jitter, 0);

            player.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, dust);
        }

        // Sparse ambient particle around the symbol for depth
        Particle ambient = getAmbientParticle(skill);
        if (ambient != null) {
            player.getWorld().spawnParticle(ambient, center, 2, 0.25, 0.30, 0.25, 0.01);
        }
    }

    // ── Symbol shape definitions ──────────────────────────────────────────

    /**
     * Returns an array of {rightOffset, upOffset} pairs (in blocks) that
     * together trace the outline of each skill's attribute symbol.
     * Centre (0,0) is at the cape surface; scale is roughly ±0.5 blocks.
     */
    private double[][] getSymbolShape(SkillType skill) {
        return switch (skill) {

            // ─── MELEE — crossed sword ────────────────────────────────────
            case MELEE -> new double[][] {
                { 0.00,  0.52},   // blade tip
                { 0.00,  0.37},   // blade upper
                { 0.00,  0.22},   // blade mid
                {-0.22,  0.04},   // crossguard left
                {-0.11,  0.04},   // crossguard inner-left
                { 0.00,  0.04},   // crossguard centre
                { 0.11,  0.04},   // crossguard inner-right
                { 0.22,  0.04},   // crossguard right
                { 0.00, -0.12},   // grip upper
                { 0.00, -0.28},   // grip lower
                { 0.00, -0.44},   // pommel
            };

            // ─── DEFENCE — kite shield ────────────────────────────────────
            case DEFENCE -> new double[][] {
                {-0.10,  0.48},   // top-left
                { 0.00,  0.52},   // top-centre
                { 0.10,  0.48},   // top-right
                {-0.27,  0.28},   // left upper
                { 0.27,  0.28},   // right upper
                {-0.30,  0.06},   // left mid
                { 0.30,  0.06},   // right mid
                {-0.27, -0.14},   // left lower
                { 0.27, -0.14},   // right lower
                {-0.14, -0.34},   // bottom-left
                { 0.14, -0.34},   // bottom-right
                { 0.00, -0.52},   // bottom point
            };

            // ─── RANGED — bow + arrow ─────────────────────────────────────
            case RANGED -> new double[][] {
                // Bow limbs (left arc)
                {-0.30,  0.42},   // bow top
                {-0.36,  0.22},   // bow upper curve
                {-0.38,  0.00},   // bow centre (limb)
                {-0.36, -0.22},   // bow lower curve
                {-0.30, -0.42},   // bow bottom
                // Bowstring (diagonal segments)
                {-0.24,  0.32},   // string top
                {-0.12,  0.12},   // string upper
                { 0.00,  0.00},   // nock
                {-0.12, -0.12},   // string lower
                {-0.24, -0.32},   // string bottom
                // Arrow shaft (rightward)
                { 0.12,  0.00},
                { 0.24,  0.00},
                { 0.36,  0.00},
                // Arrowhead
                { 0.46,  0.10},
                { 0.46, -0.10},
                { 0.54,  0.00},   // tip
            };

            // ─── FARMING — minecart ───────────────────────────────────────
            case FARMING -> new double[][] {
                // Cart body (flat-bed rectangle)
                {-0.24,  0.26},   // top-left
                {-0.08,  0.26},   // top mid-left
                { 0.08,  0.26},   // top mid-right
                { 0.24,  0.26},   // top-right
                {-0.24,  0.08},   // body bottom-left
                { 0.24,  0.08},   // body bottom-right
                {-0.24,  0.17},   // left side
                { 0.24,  0.17},   // right side
                // Axle bar
                {-0.12, -0.02},
                { 0.00, -0.02},
                { 0.12, -0.02},
                // Left wheel
                {-0.20, -0.10},   // wheel top
                {-0.26, -0.20},   // wheel outer
                {-0.18, -0.28},   // wheel bottom-outer
                {-0.10, -0.22},   // wheel inner
                // Right wheel
                { 0.20, -0.10},
                { 0.26, -0.20},
                { 0.18, -0.28},
                { 0.10, -0.22},
            };

            // ─── PRAYER — latin cross ─────────────────────────────────────
            case PRAYER -> new double[][] {
                { 0.00,  0.52},   // top
                { 0.00,  0.36},   // upper shaft
                { 0.00,  0.22},   // crossbar row
                {-0.28,  0.22},   // left arm
                {-0.14,  0.22},   // inner-left
                { 0.14,  0.22},   // inner-right
                { 0.28,  0.22},   // right arm
                { 0.00,  0.07},   // lower shaft upper
                { 0.00, -0.10},   // lower shaft mid
                { 0.00, -0.26},   // lower shaft lower
                { 0.00, -0.42},   // base
            };

            // ─── MAGIC — six-pointed star (Star of David) ─────────────────
            case MAGIC -> new double[][] {
                // Outer points
                { 0.00,  0.52},   // top
                { 0.26,  0.14},   // upper-right
                { 0.38, -0.20},   // lower-right
                { 0.00, -0.38},   // bottom
                {-0.38, -0.20},   // lower-left
                {-0.26,  0.14},   // upper-left
                // Inner hexagon ring
                { 0.00,  0.24},   // inner-top
                { 0.20,  0.06},   // inner-upper-right
                { 0.20, -0.16},   // inner-lower-right
                { 0.00, -0.22},   // inner-bottom
                {-0.20, -0.16},   // inner-lower-left
                {-0.20,  0.06},   // inner-upper-left
            };

            // ─── WOODCUTTING — axe ────────────────────────────────────────
            case WOODCUTTING -> new double[][] {
                // Handle (vertical)
                { 0.04,  0.48},   // top
                { 0.04,  0.32},
                { 0.04,  0.16},
                { 0.04,  0.00},
                { 0.04, -0.18},   // handle base
                // Axe head (upper-right)
                { 0.22,  0.48},   // blade top-left
                { 0.36,  0.38},   // blade top arc
                { 0.42,  0.22},   // blade outer upper
                { 0.42,  0.06},   // blade outer lower
                { 0.34, -0.06},   // blade lower arc
                { 0.18,  0.02},   // blade inner lower
                // Blade cutting edge (rightmost vertical)
                { 0.44,  0.34},
                { 0.44,  0.14},
            };

            default -> new double[][]{};
        };
    }

    /** Primary DUST colour for each skill cape's symbol. */
    private static Color getSkillColor(SkillType skill) {
        return switch (skill) {
            case MELEE       -> Color.fromRGB(220,  40,  40);   // crimson
            case RANGED      -> Color.fromRGB( 40, 200,  80);   // lime-green
            case DEFENCE     -> Color.fromRGB( 60, 120, 255);   // royal-blue
            case PRAYER      -> Color.fromRGB(240, 220, 180);   // warm white/gold
            case WOODCUTTING -> Color.fromRGB( 80, 160,  50);   // forest-green
            case FARMING     -> Color.fromRGB(180, 110,  40);   // harvest-gold
            default          -> Color.fromRGB(200, 200, 200);
        };
    }

    /** Secondary ambient particle that fills the space around the symbol. */
    private static Particle getAmbientParticle(SkillType skill) {
        return switch (skill) {
            case MELEE       -> Particle.CRIT;
            case RANGED      -> Particle.ENCHANTED_HIT;
            case DEFENCE     -> Particle.END_ROD;
            case PRAYER      -> Particle.ENCHANT;
            case FARMING     -> Particle.COMPOSTER;
            case WOODCUTTING -> Particle.HAPPY_VILLAGER;
            default          -> null;  // MAGIC uses pure rainbow dust
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Fishing cape — dual orbit rings
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Moves the single rainbow axolotl companion along a sinusoidal swim path
     * behind the player and renders cycling rainbow DUST particles around it.
     */
    private void updateFishingOrbit(Player player) {
        UUID uuid = player.getUniqueId();
        double t  = fishingAngles.getOrDefault(uuid, 0.0) + ORBIT_SPEED;
        fishingAngles.put(uuid, t);

        List<org.bukkit.entity.Entity> entities = fishingOrbit.get(uuid);
        boolean needRespawn = (entities == null
                || entities.size() < AXOLOTL_COUNT
                || entities.stream().anyMatch(e -> e == null || e.isDead()));

        if (needRespawn) {
            spawnFishingOrbit(player, t);
            return;
        }

        org.bukkit.entity.Entity axolotl = entities.get(0);

        // ── Compute swim destination in player-local space ────────────────
        Vector facing = player.getLocation().getDirection();
        Vector back   = new Vector(-facing.getX(), 0, -facing.getZ());
        if (back.lengthSquared() > 1e-6) back.normalize();
        Vector side = new Vector(-facing.getZ(), 0, facing.getX());
        if (side.lengthSquared() > 1e-6) side.normalize();

        double sideOff = SWIM_SIDE_AMP * Math.sin(t * SWIM_SIDE_FREQ);
        double vertOff = SWIM_VERT_AMP * Math.sin(t * SWIM_VERT_FREQ);

        Location dest = player.getLocation().clone()
                .add(back.clone().multiply(SWIM_BACK_DIST))
                .add(side.clone().multiply(sideOff))
                .add(0, SWIM_HEIGHT + vertOff, 0);

        // Orient axolotl to face direction of travel
        Vector vel = dest.toVector().subtract(axolotl.getLocation().toVector());
        if (vel.lengthSquared() > 0.001) {
            dest.setYaw((float) Math.toDegrees(Math.atan2(-vel.getX(), vel.getZ())));
        }
        axolotl.teleport(dest);

        // ── Rainbow particle ring around the axolotl ─────────────────────
        float hue = (float)((tick * 0.022) % 1.0);
        Color rainbowColor = hsbToColor(hue);
        Particle.DustOptions rainbow = new Particle.DustOptions(rainbowColor, 1.2f);
        Location axLoc = axolotl.getLocation().add(0, 0.3, 0);
        for (int i = 0; i < 7; i++) {
            double ang = Math.PI * 2.0 * i / 7;
            Location pLoc = axLoc.clone().add(
                    Math.cos(ang) * 0.40, 0.15, Math.sin(ang) * 0.40);
            player.getWorld().spawnParticle(Particle.DUST, pLoc, 1, 0, 0, 0, 0, rainbow);
        }
    }

    /**
     * Spawns the single BLUE axolotl companion for the Fishing Cape.
     * One entity, no AI, no gravity, no collision — rainbow particles added each tick.
     */
    private void spawnFishingOrbit(Player player, double t) {
        UUID uuid = player.getUniqueId();
        // Despawn any previous entities
        List<org.bukkit.entity.Entity> old = fishingOrbit.get(uuid);
        if (old != null) {
            for (org.bukkit.entity.Entity e : old) if (e != null && !e.isDead()) e.remove();
        }

        List<org.bukkit.entity.Entity> entities = new ArrayList<>();

        // Initial position: directly behind player at swim height
        Vector facing = player.getLocation().getDirection();
        Vector back   = new Vector(-facing.getX(), 0, -facing.getZ());
        if (back.lengthSquared() > 1e-6) back.normalize();

        Location spawnLoc = player.getLocation().clone()
                .add(back.clone().multiply(SWIM_BACK_DIST))
                .add(0, SWIM_HEIGHT, 0);

        // Spawn ONE BLUE axolotl (rarest variant — complemented by rainbow particles)
        org.bukkit.entity.Axolotl axolotl = player.getWorld().spawn(
            spawnLoc,
            org.bukkit.entity.Axolotl.class,
            a -> {
                a.setAI(false);
                a.setGravity(false);
                a.setPersistent(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setCustomNameVisible(false);
                a.setCollidable(false);
                a.setAdult();
                a.setVariant(org.bukkit.entity.Axolotl.Variant.BLUE);
                a.addScoreboardTag(FISH_TAG);
                capeEntityTeam.addEntry(a.getUniqueId().toString());
            });
        entities.add(axolotl);

        fishingOrbit.put(uuid, entities);
    }

    /** Removes all orbit entities for the given player and clears their angle. */
    private void despawnFishingOrbit(UUID uuid) {
        List<org.bukkit.entity.Entity> entities = fishingOrbit.remove(uuid);
        fishingAngles.remove(uuid);
        if (entities == null) return;
        for (org.bukkit.entity.Entity e : entities) {
            if (e != null && !e.isDead()) e.remove();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Axolotl pixel-art (replaces real entity that flops in air)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Draws a tiny pixel-art axolotl using DUST particles (pink / dark-pink / white).
     * The grid is 7 wide × 5 tall, each "pixel" ~0.10 blocks apart.
     *
     * Legend:  0 = empty  1 = body (pink)  2 = gills (dark-pink)  3 = eye (white)
     */
    private void spawnAxolotlPixelArt(Player player, Location center, Vector right) {
        int[][] pixels = {
            { 0, 2, 0, 0, 0, 2, 0 },   // row 4 — gill tufts
            { 0, 1, 1, 1, 1, 1, 0 },   // row 3 — head
            { 1, 1, 3, 1, 3, 1, 1 },   // row 2 — eyes
            { 1, 1, 1, 1, 1, 1, 1 },   // row 1 — body
            { 0, 1, 0, 1, 0, 1, 0 },   // row 0 — legs / feet
        };

        Color pink     = Color.fromRGB(255, 150, 180);
        Color darkPink = Color.fromRGB(200,  80, 120);
        Color white    = Color.fromRGB(240, 240, 240);

        final double spacing = 0.10;
        int cols = pixels[0].length;   // 7
        int rows = pixels.length;      // 5

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int pixel = pixels[rows - 1 - row][col]; // flip: row 0 → bottom
                if (pixel == 0) continue;

                Color c = switch (pixel) {
                    case 1  -> pink;
                    case 2  -> darkPink;
                    case 3  -> white;
                    default -> pink;
                };

                double rOff = (col - cols / 2.0) * spacing;
                double uOff = row * spacing + 0.10;   // sit slightly above loc

                Location pLoc = center.clone()
                        .add(right.clone().multiply(rOff))
                        .add(0, uOff, 0);

                player.getWorld().spawnParticle(
                        Particle.DUST, pLoc, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(c, 1.0f));
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Axolotl water-bubble cameo (50 % alternate to pixel art)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Spawns a real Axolotl entity surrounded by a sphere of water/bubble
     * particles, giving the illusion of an "invisible water bubble" — without
     * placing any actual water blocks or affecting the world.
     * Auto-removed after 2.5 s (50 ticks).
     *
     * The entity is added to the "de_cape_mobs" scoreboard team so it cannot
     * physically push the player and its health bar is hidden.
     */
    private void spawnAxolotlWaterBubble(Player player, Location center) {
        org.bukkit.entity.Axolotl axolotl = (org.bukkit.entity.Axolotl)
                player.getWorld().spawnEntity(
                        center.clone().add(
                                (Math.random() - 0.5) * 0.2, 0.2,
                                (Math.random() - 0.5) * 0.2),
                        EntityType.AXOLOTL);

        axolotl.setAI(false);
        axolotl.setGravity(false);
        axolotl.setPersistent(false);
        axolotl.setInvulnerable(true);
        axolotl.setAdult();
        axolotl.setSilent(true);
        axolotl.setCustomNameVisible(false);
        axolotl.setCollidable(false);
        axolotl.addScoreboardTag(FISH_TAG);

        // Team membership → no collision + no health-bar overlay
        capeEntityTeam.addEntry(axolotl.getUniqueId().toString());

        org.bukkit.entity.Axolotl.Variant[] variants =
                org.bukkit.entity.Axolotl.Variant.values();
        axolotl.setVariant(variants[(int)(Math.random() * variants.length)]);

        axolotl.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.10,
                (Math.random() - 0.5) * 0.04,
                (Math.random() - 0.5) * 0.10));

        // Schedule water-bubble particle rings around the axolotl every 3 ticks
        for (int delay = 0; delay <= 48; delay += 3) {
            final int d = delay;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (axolotl.isDead()) return;
                Location a = axolotl.getLocation().add(0, 0.25, 0);

                // Ring of falling-water / splash particles at r ≈ 0.38 blocks
                final int ringPoints = 10;
                for (int i = 0; i < ringPoints; i++) {
                    double angle  = Math.PI * 2 * i / ringPoints;
                    double radius = 0.38;
                    double yOff   = (Math.random() - 0.5) * 0.30;
                    Location pLoc = a.clone().add(
                            Math.cos(angle) * radius, yOff,
                            Math.sin(angle) * radius);
                    player.getWorld().spawnParticle(Particle.FALLING_WATER, pLoc, 1, 0, 0, 0, 0);
                    if (i % 2 == 0)
                        player.getWorld().spawnParticle(Particle.SPLASH, pLoc, 1, 0, 0, 0, 0.02);
                }

                // Underwater ambient particles inside the bubble
                player.getWorld().spawnParticle(Particle.UNDERWATER, a, 4, 0.25, 0.15, 0.25, 0);
                // Drip particles at top of bubble
                player.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                        a.clone().add(0, 0.35, 0), 2, 0.15, 0, 0.15, 0);
            }, d);
        }

        // Auto-remove after 2.5 s (50 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (!axolotl.isDead()) axolotl.remove(); }, 50L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Fishing cape: temporary tropical-fish entities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Spawns a temporary TropicalFish near the player's back.
     * Added to the "de_cape_mobs" team → no collision, no health-bar.
     */
    private void spawnTemporaryFish(Player player, Location loc) {
        Location spawnLoc = loc.clone().add(
                (Math.random() - 0.5) * 0.4, 0.0, (Math.random() - 0.5) * 0.4);

        TropicalFish fish = (TropicalFish)
                player.getWorld().spawnEntity(spawnLoc, EntityType.TROPICAL_FISH);

        fish.setAI(false);
        fish.setGravity(false);
        fish.setPersistent(false);
        fish.setInvulnerable(true);
        fish.setSilent(true);
        fish.setCustomNameVisible(false);
        fish.setCollidable(false);
        fish.addScoreboardTag(FISH_TAG);

        // Team membership → no collision + no health-bar overlay
        capeEntityTeam.addEntry(fish.getUniqueId().toString());

        TropicalFish.Pattern[]    patterns  = TropicalFish.Pattern.values();
        org.bukkit.DyeColor[]     dyeColors = org.bukkit.DyeColor.values();
        fish.setPattern(patterns[(int)(Math.random() * patterns.length)]);
        fish.setPatternColor(dyeColors[(int)(Math.random() * dyeColors.length)]);
        fish.setBodyColor(dyeColors[(int)(Math.random() * dyeColors.length)]);

        fish.setVelocity(new Vector(
                (Math.random() - 0.5) * 0.40,
                (Math.random() - 0.5) * 0.15,
                (Math.random() - 0.5) * 0.40));

        // Auto-remove after 3 seconds (60 ticks)
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> { if (!fish.isDead()) fish.remove(); }, 60L);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Rainbow colour helper (HSB → RGB)
    // ═══════════════════════════════════════════════════════════════════════

    private static Color hsbToColor(float hue) {
        int   h = (int)(hue * 6);
        float f = hue * 6 - h;
        float q = 1 - f;
        float r, g, b;
        switch (h % 6) {
            case 0  -> { r = 1; g = f; b = 0; }
            case 1  -> { r = q; g = 1; b = 0; }
            case 2  -> { r = 0; g = 1; b = f; }
            case 3  -> { r = 0; g = q; b = 1; }
            case 4  -> { r = f; g = 0; b = 1; }
            default -> { r = 1; g = 0; b = q; }
        }
        return Color.fromRGB(
                Math.max(0, Math.min(255, (int)(r * 255))),
                Math.max(0, Math.min(255, (int)(g * 255))),
                Math.max(0, Math.min(255, (int)(b * 255))));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private boolean isWearingCape(Player player) {
        return capeDataManager.hasCape(player.getUniqueId());
    }

    /**
     * Removes ALL hologram stands managed by this task.
     * Called from {@code Main#onDisable()} and at startup to sweep orphans.
     */
    public void cleanup() {
        for (ArmorStand stand : holograms.values()) {
            if (!stand.isDead()) stand.remove();
        }
        holograms.clear();
        lastCapeName.clear();

        // Sweep orphaned hologram stands (from previous crash / reload)
        plugin.getServer().getWorlds().forEach(world ->
            world.getEntitiesByClass(ArmorStand.class).forEach(stand -> {
                if (stand.getScoreboardTags().contains(HOLOGRAM_TAG)) stand.remove();
            })
        );

        // Sweep orphaned fish entities
        plugin.getServer().getWorlds().forEach(world ->
            world.getEntities().forEach(e -> {
                if (e.getScoreboardTags().contains(FISH_TAG)) e.remove();
            })
        );

        // Despawn all fishing orbit entities
        for (UUID uuid : new HashSet<>(fishingOrbit.keySet())) {
            despawnFishingOrbit(uuid);
        }

        // Clear team entries (best-effort)
        try {
            for (String entry : new HashSet<>(capeEntityTeam.getEntries())) {
                capeEntityTeam.removeEntry(entry);
            }
        } catch (Exception ignored) { /* team may have been unregistered externally */ }
    }

    /**
     * Resets and cleans up all holographic and companion entities for a specific player immediately.
     */
    public void removePlayerCapeVisuals(UUID uuid) {
        ArmorStand stand = holograms.remove(uuid);
        if (stand != null && !stand.isDead()) {
            stand.remove();
        }
        lastCapeName.remove(uuid);
        despawnFishingOrbit(uuid);
    }
}
