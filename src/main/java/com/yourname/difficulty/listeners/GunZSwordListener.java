package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
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
 * ── Algorithm (Ring-Buffer Keystroke Detection) ──────────────────────────────
 * A per-tick sampler (every 1 tick / 50 ms) reads the player's movement delta
 * and projects it into player-local (WASD) space.
 *
 * Each direction (W/A/S/D) tracks the last TWO press timestamps in a ring
 * buffer.  A "press" is registered the INSTANT a direction transitions from
 * absent → present (inactive→active).  This means every keystroke creates a
 * fresh entry — there is no hysteresis delay.
 *
 * Double-tap trigger:
 *   buf[0] = timestamp of press-before-last
 *   buf[1] = timestamp of most recent press
 *   If (buf[1] - buf[0]) ≤ DOUBLE_TAP_WINDOW_MS → DASH fires immediately.
 *
 * This approach gives 100% reliable detection for WW / AA / SS / DD because
 * every new key-press is registered independently on the tick it first appears.
 *
 * ── Slash Cancel (Animation Cancel) ─────────────────────────────────────────
 * Left-clicking while holding the GunZ Sword within SLASH_WINDOW_MS of a
 * dash redirects the player's velocity in their current facing direction.
 * This lets players change direction mid-dash by slashing.
 *
 * ── Blended Strafe (W + A/S/D) ───────────────────────────────────────────────
 * If W is actively held when a strafe double-tap fires, the dash blends a
 * forward component so the character slides sideways while continuing forward.
 */
public class GunZSwordListener implements Listener {

    /** Direction in player-local space. */
    private enum Dir { W, A, S, D }

    /** Minimum movement per tick to classify a direction as "active". */
    private static final double MOVE_THRESHOLD = 0.07;

    /**
     * Maximum registered duration of a tap press on the server (ms) to be considered a quick tap.
     * Due to server-side friction decay, a physical key tap of up to 200ms can register as 250-300ms.
     * We set this to 300ms to be extremely reliable.
     */
    private static final long MAX_TAP_HOLD_MS = 300L;

    /**
     * Maximum gap between the key release and the subsequent key press (ms)
     * to qualify as a double-tap.
     */
    private static final long MAX_TAP_RELEASE_GAP_MS = 250L;

    /** Cooldown between any two consecutive dashes (ms). */
    private static final long DASH_COOLDOWN_MS = 750L;

    /**
     * Window after a dash fires in which a left-click slash can redirect
     * the dash velocity (animation cancel / direction change).
     */
    private static final long SLASH_WINDOW_MS = 350L;

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;
    private       BukkitTask   samplerTask;

    /** Keystroke state tracker for each player direction. */
    private static class KeyState {
        long lastPressTime = 0L;
        long lastReleaseTime = 0L;
    }

    // ── Per-player keystroke state tracker ───────────────────────────────────
    private final Map<UUID, EnumMap<Dir, KeyState>> keyStates = new HashMap<>();

    /**
     * Directions that were active (movement detected) on the PREVIOUS tick.
     * Used to detect the rising edge (inactive → active) = new key press.
     */
    private final Map<UUID, Set<Dir>>              prevActive    = new HashMap<>();

    /** Timestamp of the last dash per player (for DASH_COOLDOWN_MS). */
    private final Map<UUID, Long>                  dashCooldown  = new HashMap<>();

    /** Timestamp when the most recent dash fired (for slash-cancel window). */
    private final Map<UUID, Long>                  lastDashTime  = new HashMap<>();

    /** Tracks the player's location on the previous tick to compute real movement delta. */
    private final Map<UUID, Location>              lastLocations = new HashMap<>();

    public GunZSwordListener(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;

        // Start the per-tick sampler
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
                keyStates.remove(uid);
                prevActive.remove(uid);
                continue;
            }

            Location loc = player.getLocation();
            Location lastLoc = lastLocations.get(uid);
            lastLocations.put(uid, loc.clone());

            if (lastLoc == null || !lastLoc.getWorld().equals(loc.getWorld())) {
                continue;
            }

            // ── Compute horizontal movement delta ──────────────────────────────
            // Since player.getVelocity() doesn't contain standard walking speed on the server,
            // we use the real position difference between tick samples.
            double vx = loc.getX() - lastLoc.getX();
            double vz = loc.getZ() - lastLoc.getZ();

            // Project world delta into player-local space using the yaw angle
            double yaw  = Math.toRadians(loc.getYaw());
            double sinY = Math.sin(yaw);
            double cosY = Math.cos(yaw);

            // fwd   > 0 = forward (W key); fwd < 0 = backward (S key)
            double fwd   =  vx * (-sinY) + vz * cosY;
            // right > 0 = right  (D key); right < 0 = left    (A key)
            double right =  vx *   cosY  + vz * sinY;

            // Adjust threshold for position delta (around 0.05 per tick for normal walking)
            double threshold = 0.05;

            // ── Classify directions that are currently ACTIVE ─────────────────
            Set<Dir> current = new HashSet<>(4);
            if (fwd   >  threshold) current.add(Dir.W);
            if (fwd   < -threshold) current.add(Dir.S);
            if (right >  threshold) current.add(Dir.D);
            if (right < -threshold) current.add(Dir.A);

            Set<Dir> prev = prevActive.getOrDefault(uid, new HashSet<>());
            EnumMap<Dir, KeyState> states =
                    keyStates.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));

            // ── Detect rising-edge (Press) and falling-edge (Release) ─────────
            for (Dir d : Dir.values()) {
                boolean isPressedNow = current.contains(d);
                boolean wasPressedBefore = prev.contains(d);

                if (isPressedNow && !wasPressedBefore) {
                    // Rising edge (Press)
                    KeyState state = states.computeIfAbsent(d, k -> new KeyState());
                    
                    long inactiveDuration = now - state.lastReleaseTime;
                    long totalGap = now - state.lastPressTime;

                    // 2-Tick Inactive Rule:
                    // inactiveDuration must be >= 80ms (at least 2 server ticks) to prove a real human release,
                    // which perfectly filters out 50ms (1-tick) landing-lag/obstacle jitters.
                    // totalGap must be <= 380ms to ensure a fast, valid double-tap sequence.
                    if (state.lastPressTime > 0 && state.lastReleaseTime > state.lastPressTime 
                            && inactiveDuration >= 80L && totalGap <= 380L) {
                        // Double-tap detected — consume immediately and trigger dash
                        state.lastPressTime = 0L;
                        state.lastReleaseTime = 0L;
                        triggerDash(player, d, current, now);
                    } else {
                        state.lastPressTime = now;
                    }
                } else if (!isPressedNow && wasPressedBefore) {
                    // Falling edge (Release)
                    KeyState state = states.computeIfAbsent(d, k -> new KeyState());
                    state.lastReleaseTime = now;
                }
            }

            prevActive.put(uid, new HashSet<>(current));
        }
    }

    // ── Dash execution ────────────────────────────────────────────────────────

    private void triggerDash(Player player, Dir dir, Set<Dir> currentDirs, long now) {
        UUID uid = player.getUniqueId();

        // Global dash cooldown
        if (now - dashCooldown.getOrDefault(uid, 0L) < DASH_COOLDOWN_MS) return;
        dashCooldown.put(uid, now);
        lastDashTime.put(uid, now);

        // Build dash velocity relative to player facing
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) return;
        facing.normalize();

        // Right vector: 90° clockwise of facing in XZ plane
        Vector right = new Vector(facing.getZ(), 0, -facing.getX());

        // Is the player currently standing still (no active movement inputs, or just the dash key itself)?
        boolean standingStill = currentDirs.isEmpty() || (currentDirs.size() == 1 && currentDirs.contains(dir));

        // Is the player currently walking forward (W held) or standing still?
        // "standing still force direction as if running already" -> treats standstill as if they are running forward
        boolean movingForward = currentDirs.contains(Dir.W) || standingStill;

        double speed = 1.25;
        Vector dashVec;

        if (movingForward && dir != Dir.W) {
            // Blended strafe: preserve ~40% forward momentum while sliding
            dashVec = switch (dir) {
                case A -> right.clone().multiply(-speed).add(facing.clone().multiply(0.45));
                case D -> right.clone().multiply( speed).add(facing.clone().multiply(0.45));
                case S -> facing.clone().multiply(-speed * 0.55);
                default -> facing.clone().multiply(speed);
            };
        } else {
            // Standard standalone dash
            dashVec = switch (dir) {
                case W -> facing.clone().multiply(speed);
                case S -> facing.clone().multiply(-speed * 0.85);
                case A -> right.clone().multiply(-speed);
                case D -> right.clone().multiply( speed);
            };
        }
        dashVec.setY(0.22); // slight upward arc — GunZ style

        player.setVelocity(dashVec);

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
        player.sendActionBar("§7⚡ §f§lDASH §c" + label + " §8(slash to redirect!)");
    }

    // ── Slash Cancel (Animation Cancel / Direction Redirect) ──────────────────

    /**
     * Left-clicking while holding the GunZ Sword within SLASH_WINDOW_MS of
     * a dash will redirect the player's velocity in their current facing
     * direction — this is the "animation cancel" that lets you change direction
     * mid-dash by slashing.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSlashCancel(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!isHoldingGunZSword(player)) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastDash = lastDashTime.get(uid);
        if (lastDash == null || now - lastDash > SLASH_WINDOW_MS) return;

        // ── Redirect dash in current facing direction ─────────────────────────
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) return;
        facing.normalize();

        // Preserve the existing horizontal speed (so we don't slow down)
        Vector current = player.getVelocity();
        double horizSpeed = Math.sqrt(current.getX() * current.getX() + current.getZ() * current.getZ());
        double redirectSpeed = Math.max(horizSpeed, 1.0); // at least full dash speed

        Vector redirected = facing.multiply(redirectSpeed).setY(Math.max(current.getY(), 0.15));
        player.setVelocity(redirected);

        // Visual feedback for the slash redirect
        Location loc = player.getLocation().add(0, 1, 0);
        player.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.3, 0.3, 0.25);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc, 6, 0.2, 0.2, 0.2, 0.15);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.6f);

        player.sendActionBar("§f⚔ §c§lSLASH REDIRECT! §7Direction changed!");

        // Consume the slash window so it only redirects once per dash
        lastDashTime.put(uid, 0L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingGunZSword(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return itemFactory.isGunZSword(hand);
    }
}
