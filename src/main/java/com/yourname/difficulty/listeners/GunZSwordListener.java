package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GunZSwordListener — GunZ: The Duel double-tap dashing for the GunZ Sword.
 *
 * ── Algorithm ─────────────────────────────────────────────────────────────────
 * A per-tick sampler (every 1 tick / 50 ms) records each player's position.
 * The position delta between two consecutive ticks is projected into
 * player-local (WASD) space, giving a stable direction reading.
 *
 * Double-tap detection:
 *   1. Player presses key   → direction becomes active (START recorded)
 *   2. Player releases key  → after STOP_TICKS consecutive absent ticks,
 *                             direction is declared "stopped" (STOP recorded,
 *                             only if it was held ≥ MIN_HOLD_MS first)
 *   3. Player presses again → if gap between STOP and this new START is
 *                             between MIN_GAP_MS and DOUBLE_TAP_WINDOW_MS,
 *                             dash is triggered.
 *
 * Hysteresis (STOP_TICKS = 3):
 *   A brief flicker below the movement threshold (e.g. from a jump, lag, or
 *   terrain bump) does NOT count as releasing the key. The direction must
 *   be absent for at least 3 consecutive ticks (~150 ms) before it is
 *   declared "stopped". This eliminates the "holding W = constant dashing" bug.
 *
 * Blended dash while walking:
 *   If W is currently active and the player double-taps A or D, the dash
 *   carries a forward momentum component so the character slides sideways
 *   while continuing to move forward. Double-tapping S while W is held gives
 *   a partial backslide (forward momentum resists the backward impulse).
 */
public class GunZSwordListener implements Listener {

    /** Direction in player-local space. */
    private enum Dir { W, A, S, D }

    /** How far the player must move per tick to count as "pressing" a key. */
    private static final double MOVE_THRESHOLD = 0.07;

    /** A key must be held for at least this long before releasing it counts as a "tap". */
    private static final long MIN_HOLD_MS = 60L;

    /** A re-press must come at least this long after the stop (avoids diagonal noise). */
    private static final long MIN_GAP_MS = 40L;

    /** Maximum gap between the first key-release and the second press for a double-tap. */
    private static final long DOUBLE_TAP_WINDOW_MS = 320L;

    /** Cooldown between any two dashes (ms). */
    private static final long DASH_COOLDOWN_MS = 800L;

    /**
     * Number of consecutive ticks a direction must be ABSENT before it is
     * officially declared "stopped". Prevents brief velocity dips (jumps,
     * terrain, lag) from registering as key releases when holding a direction.
     */
    private static final int STOP_TICKS = 3;

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;
    private       BukkitTask   samplerTask;

    // ── Per-player tracking ───────────────────────────────────────────────────

    /** Direction set active on the previous sampler tick. */
    private final Map<UUID, Set<Dir>>              prevDirs     = new HashMap<>();
    /** Absolute position on the previous sampler tick (for delta calculation). */
    private final Map<UUID, Location>              prevLocs     = new HashMap<>();
    /** Timestamp when each direction last became ACTIVE (key pressed). */
    private final Map<UUID, EnumMap<Dir, Long>>    startTimes   = new HashMap<>();
    /** Timestamp when each direction was officially declared STOPPED (after STOP_TICKS). */
    private final Map<UUID, EnumMap<Dir, Long>>    stopTimes    = new HashMap<>();
    /** Consecutive ticks each direction has been BELOW threshold (hysteresis counter). */
    private final Map<UUID, EnumMap<Dir, Integer>> inactiveTicks = new HashMap<>();
    /** Last dash timestamp per player. */
    private final Map<UUID, Long>                  dashCooldown = new HashMap<>();

    public GunZSwordListener(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;

        // Start the per-tick sampler immediately
        samplerTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::sample, 2L, 1L);
    }

    /** Cancel the sampler on plugin disable. */
    public void shutdown() {
        if (samplerTask != null) samplerTask.cancel();
    }

    // ── Per-tick sampler ──────────────────────────────────────────────────────

    private void sample() {
        long now = System.currentTimeMillis();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uid = player.getUniqueId();

            if (!isHoldingGunZSword(player)) {
                // Clean up stale state
                prevDirs.remove(uid);
                prevLocs.remove(uid);
                startTimes.remove(uid);
                stopTimes.remove(uid);
                inactiveTicks.remove(uid);
                continue;
            }

            Location currLoc = player.getLocation().clone();
            Location prevLoc = prevLocs.get(uid);
            prevLocs.put(uid, currLoc);

            if (prevLoc == null) {
                prevDirs.put(uid, new HashSet<>());
                continue;
            }

            // ── Compute per-tick position delta ───────────────────────────────
            double dx = currLoc.getX() - prevLoc.getX();
            double dz = currLoc.getZ() - prevLoc.getZ();

            // Project world delta into player-local space using the current yaw
            float  yaw  = currLoc.getYaw();
            double sinY = Math.sin(Math.toRadians(yaw));
            double cosY = Math.cos(Math.toRadians(yaw));

            double fwd   =  dx * (-sinY) + dz * cosY;   // > 0 = forward (W)
            double right =  dx *   cosY  + dz * sinY;   // > 0 = right   (D)

            // ── Classify active directions ─────────────────────────────────────
            Set<Dir> current = new HashSet<>(4);
            if (fwd   >  MOVE_THRESHOLD) current.add(Dir.W);
            if (fwd   < -MOVE_THRESHOLD) current.add(Dir.S);
            if (right >  MOVE_THRESHOLD) current.add(Dir.D);
            if (right < -MOVE_THRESHOLD) current.add(Dir.A);

            EnumMap<Dir, Long>    stops   = stopTimes.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));
            EnumMap<Dir, Long>    starts  = startTimes.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));
            EnumMap<Dir, Integer> inactive = inactiveTicks.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));

            // ── Hysteresis: track consecutive ticks each direction is ABSENT ──
            for (Dir d : Dir.values()) {
                if (!current.contains(d)) {
                    // Direction absent this tick — increment hysteresis counter
                    int cnt = inactive.merge(d, 1, Integer::sum);
                    // Only officially "stop" after STOP_TICKS consecutive absent ticks
                    if (cnt == STOP_TICKS) {
                        Long startAt = starts.get(d);
                        if (startAt != null && (now - startAt) >= MIN_HOLD_MS) {
                            stops.put(d, now);
                        }
                        starts.remove(d);
                    }
                }
            }

            // ── Directions that just RE-APPEARED (key re-pressed) ─────────────
            for (Dir d : current) {
                int wasInactive = inactive.getOrDefault(d, 0);
                inactive.put(d, 0); // reset hysteresis counter as soon as active

                if (wasInactive >= STOP_TICKS) {
                    // Direction was properly stopped and is now starting again
                    Long stopAt = stops.get(d);
                    if (stopAt != null) {
                        long gap = now - stopAt;
                        if (gap >= MIN_GAP_MS && gap <= DOUBLE_TAP_WINDOW_MS) {
                            // ✅ Double-tap!
                            stops.remove(d);
                            starts.remove(d);
                            triggerDash(player, d, current);
                        } else {
                            // Outside window — discard stale stop so it doesn't
                            // accidentally match a future press
                            stops.remove(d);
                        }
                    }
                    // Record start of this new press
                    starts.put(d, now);

                } else if (!starts.containsKey(d)) {
                    // Direction appeared without a prior "officially stopped" state
                    // (e.g. first press ever, or after a very brief flicker)
                    starts.put(d, now);
                }
            }

            prevDirs.put(uid, new HashSet<>(current));
        }
    }

    // ── Dash execution ────────────────────────────────────────────────────────

    /**
     * Executes a dash in the given direction.
     *
     * @param currentDirs  The set of directions currently active this tick.
     *                     Used to detect whether the player is simultaneously
     *                     moving forward, enabling blended strafe dashes.
     */
    private void triggerDash(Player player, Dir dir, Set<Dir> currentDirs) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (now - dashCooldown.getOrDefault(uid, 0L) < DASH_COOLDOWN_MS) return;
        dashCooldown.put(uid, now);

        // Build dash velocity relative to player facing
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) return;
        facing.normalize();

        // Right vector: 90° clockwise of facing in XZ plane
        Vector right = new Vector(facing.getZ(), 0, -facing.getX());

        // Is the player currently pressing W (walking forward)?
        boolean movingForward = currentDirs.contains(Dir.W);

        double speed = 1.2;
        Vector dashVec;

        if (movingForward && dir != Dir.W) {
            // ── Blended directional dash (W held + strafe/back double-tap) ────
            // Preserve partial forward momentum so the character continues moving
            // forward while sliding in the dashed direction.
            dashVec = switch (dir) {
                // Left strafe — dash left, keep ~40 % forward push
                case A -> right.clone().multiply(-speed).add(facing.clone().multiply(0.45));
                // Right strafe — dash right, keep ~40 % forward push
                case D -> right.clone().multiply(speed).add(facing.clone().multiply(0.45));
                // Back-dash while W held — partial; forward momentum resists the back impulse
                case S -> facing.clone().multiply(-speed * 0.55);
                // Shouldn't happen (W + W), but handle gracefully
                default -> facing.clone().multiply(speed);
            };
        } else {
            // ── Standard standalone dash ──────────────────────────────────────
            dashVec = switch (dir) {
                case W -> facing.clone().multiply(speed);
                case S -> facing.clone().multiply(-speed * 0.85);
                case A -> right.clone().multiply(-speed);
                case D -> right.clone().multiply(speed);
            };
        }
        dashVec.setY(0.20); // slight upward arc — GunZ style

        player.setVelocity(dashVec);

        // Wipe stale start/stop state so the dash doesn't immediately re-trigger
        startTimes.getOrDefault(uid, new EnumMap<>(Dir.class)).remove(dir);
        stopTimes.getOrDefault(uid,  new EnumMap<>(Dir.class)).remove(dir);
        inactiveTicks.getOrDefault(uid, new EnumMap<>(Dir.class)).remove(dir);

        // ── Visuals & sound ───────────────────────────────────────────────────
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.CRIT,          loc, 20, 0.35, 0.35, 0.35, 0.28);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc,  9, 0.20, 0.20, 0.20, 0.18);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.6f, 2.0f);

        String label = switch (dir) {
            case W -> "▶▶ FORWARD";
            case S -> movingForward ? "◀◀ BACK SLIDE" : "◀◀ BACK";
            case A -> movingForward ? "◁ LEFT SLIDE"  : "◁ LEFT";
            case D -> movingForward ? "RIGHT SLIDE ▷"  : "RIGHT ▷";
        };
        player.sendActionBar("§7⚡ §f§lDASH §c" + label);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingGunZSword(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return itemFactory.isGunZSword(hand);
    }
}
