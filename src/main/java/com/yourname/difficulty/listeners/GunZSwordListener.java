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
 * player-local (WASD) space, giving a stable direction reading that is immune
 * to the noise of PlayerMoveEvent and to sprint-key vs double-tap ambiguity.
 *
 * Double-tap detection:
 *   1. Player presses key   → direction becomes active (START recorded)
 *   2. Player releases key  → direction becomes inactive (STOP recorded,
 *                             only if the key was held ≥ MIN_HOLD_MS)
 *   3. Player presses again → if gap between STOP and this new START is
 *                             between MIN_GAP_MS and DOUBLE_TAP_WINDOW_MS,
 *                             dash is triggered.
 *
 * Each of W / A / S / D is tracked fully independently.
 * No sprint events are used — this eliminates the "any sprint = dash" bug.
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

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;
    private       BukkitTask   samplerTask;

    // ── Per-player tracking ───────────────────────────────────────────────────

    /** Direction set active on the previous sampler tick. */
    private final Map<UUID, Set<Dir>>              prevDirs    = new HashMap<>();
    /** Absolute position on the previous sampler tick (for delta calculation). */
    private final Map<UUID, Location>              prevLocs    = new HashMap<>();
    /** Timestamp when each direction last became ACTIVE (key pressed). */
    private final Map<UUID, EnumMap<Dir, Long>>    startTimes  = new HashMap<>();
    /** Timestamp when each direction last became INACTIVE (key released). */
    private final Map<UUID, EnumMap<Dir, Long>>    stopTimes   = new HashMap<>();
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

            Set<Dir>           prev  = prevDirs.getOrDefault(uid, new HashSet<>());
            EnumMap<Dir, Long> stops  = stopTimes.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));
            EnumMap<Dir, Long> starts = startTimes.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));

            // ── Directions that just STOPPED ──────────────────────────────────
            for (Dir d : prev) {
                if (!current.contains(d)) {
                    Long startAt = starts.get(d);
                    // Only count the release if the key was held long enough
                    if (startAt != null && (now - startAt) >= MIN_HOLD_MS) {
                        stops.put(d, now);
                    }
                    starts.remove(d);
                }
            }

            // ── Directions that just STARTED ──────────────────────────────────
            for (Dir d : current) {
                if (!prev.contains(d)) {
                    Long stopAt = stops.get(d);
                    if (stopAt != null) {
                        long gap = now - stopAt;
                        if (gap >= MIN_GAP_MS && gap <= DOUBLE_TAP_WINDOW_MS) {
                            // ✅ Double-tap!
                            stops.remove(d);
                            starts.remove(d);
                            triggerDash(player, d);
                        }
                    }
                    // Record start of this key press (even if no dash triggered)
                    starts.put(d, now);
                    // Clear any stale stop record once the key is pressed
                    if (!stops.containsKey(d)) {
                        stops.remove(d); // already absent — no-op, just clarity
                    }
                }
            }

            prevDirs.put(uid, new HashSet<>(current));
        }
    }

    // ── Dash execution ────────────────────────────────────────────────────────

    private void triggerDash(Player player, Dir dir) {
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

        double speed = 1.2;
        Vector dashVec = switch (dir) {
            case W -> facing.clone().multiply(speed);
            case S -> facing.clone().multiply(-speed * 0.85);
            case A -> right.clone().multiply(-speed);
            case D -> right.clone().multiply(speed);
        };
        dashVec.setY(0.20); // slight upward arc — GunZ style

        player.setVelocity(dashVec);

        // Wipe stale start/stop state so the dash doesn't immediately re-trigger
        startTimes.getOrDefault(uid, new EnumMap<>(Dir.class)).remove(dir);
        stopTimes.getOrDefault(uid,  new EnumMap<>(Dir.class)).remove(dir);

        // ── Visuals & sound ───────────────────────────────────────────────────
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.CRIT,          loc, 20, 0.35, 0.35, 0.35, 0.28);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc,  9, 0.20, 0.20, 0.20, 0.18);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.6f, 2.0f);

        String label = switch (dir) {
            case W -> "▶▶ FORWARD";
            case S -> "◀◀ BACK";
            case A -> "◁ LEFT";
            case D -> "RIGHT ▷";
        };
        player.sendActionBar("§7⚡ §f§lDASH §c" + label);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingGunZSword(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return itemFactory.isGunZSword(hand);
    }
}
