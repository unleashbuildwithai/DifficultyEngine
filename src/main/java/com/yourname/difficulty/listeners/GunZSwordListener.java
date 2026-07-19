package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * GunZSwordListener — Implements GunZ: The Duel style dashing mechanics
 * for the Lv99 Melee GunZ Sword.
 *
 * ── Dashing ───────────────────────────────────────────────────────────────────
 *  Double-tap a movement direction while holding the GunZ Sword to dash:
 *  • WW (double-tap forward / sprint start) → Forward dash
 *  • AA (rapid left strafe start)           → Left dash
 *  • DD (rapid right strafe start)          → Right dash
 *  • SS (rapid backward movement start)     → Backward dash
 *
 *  Dashes share an 800ms cooldown. Each dash propels the player ~3 blocks
 *  in the chosen direction with a small upward arc.
 *
 * ── Controls ─────────────────────────────────────────────────────────────────
 *  Forward dash: start sprinting (double-tap W in vanilla Minecraft)
 *  Side/Back dash: the plugin detects rapid repeated lateral movement
 *                  (stop + restart in same direction within 350ms)
 */
public class GunZSwordListener implements Listener {

    /** Direction relative to the player's facing. */
    private enum Dir { W, A, S, D }

    private static final long DOUBLE_TAP_WINDOW_MS = 350L; // max gap between taps
    private static final long DASH_COOLDOWN_MS      = 800L; // cooldown between any dashes

    private final ItemFactory  itemFactory;
    private final SkillManager skillManager;
    private final JavaPlugin   plugin;

    // ── Per-player direction tracking ─────────────────────────────────────────
    /** Which directions the player was moving last tick. */
    private final Map<UUID, Set<Dir>>          previousDirs = new HashMap<>();
    /** When each direction last ended (player stopped moving that way). */
    private final Map<UUID, Map<Dir, Long>>    dirEndedAt   = new HashMap<>();
    /** When the player's last dash occurred. */
    private final Map<UUID, Long>              dashCooldown = new HashMap<>();

    public GunZSwordListener(ItemFactory itemFactory, SkillManager skillManager, JavaPlugin plugin) {
        this.itemFactory  = itemFactory;
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    // ── Forward dash — detected via sprint toggle ─────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        Player player = event.getPlayer();
        if (!isHoldingGunZSword(player)) return;
        triggerDash(player, Dir.W);
    }

    // ── Lateral + backward dash — detected via movement direction edge ─────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only process if position actually changed
        if (event.getTo() == null) return;
        double dx = event.getTo().getX() - event.getFrom().getX();
        double dz = event.getTo().getZ() - event.getFrom().getZ();
        if (dx * dx + dz * dz < 0.0004) return; // ignore tiny or pure head-rotation moves

        Player player = event.getPlayer();
        if (!isHoldingGunZSword(player)) {
            previousDirs.remove(player.getUniqueId());
            dirEndedAt.remove(player.getUniqueId());
            return;
        }

        // ── Convert world delta to player-relative (WASD) components ──────────
        float yaw    = player.getLocation().getYaw();
        double sinY  = Math.sin(Math.toRadians(yaw));
        double cosY  = Math.cos(Math.toRadians(yaw));

        // relForward > 0 = player moving forward (W), < 0 = backward (S)
        // relRight   > 0 = player moving right (D), < 0 = left (A)
        double relForward = dx * (-sinY) + dz * cosY;
        double relRight   = dx * cosY   + dz * sinY;

        // ── Determine which directions are active this tick ────────────────────
        Set<Dir> currentDirs = new HashSet<>();
        if (relForward >  0.015) currentDirs.add(Dir.W);
        if (relForward < -0.015) currentDirs.add(Dir.S);
        if (relRight   >  0.015) currentDirs.add(Dir.D);
        if (relRight   < -0.015) currentDirs.add(Dir.A);

        UUID uid = player.getUniqueId();
        Set<Dir> prev    = previousDirs.getOrDefault(uid, new HashSet<>());
        Map<Dir, Long> ended = dirEndedAt.computeIfAbsent(uid, k -> new EnumMap<>(Dir.class));

        long now = System.currentTimeMillis();

        // ── Detect directions that just STOPPED ───────────────────────────────
        for (Dir d : prev) {
            if (!currentDirs.contains(d)) {
                ended.put(d, now); // record when this direction ended
            }
        }

        // ── Detect directions that just STARTED — check for double-tap ────────
        for (Dir d : currentDirs) {
            if (!prev.contains(d) && d != Dir.W) { // W is handled by sprint event
                Long endTime = ended.get(d);
                if (endTime != null) {
                    long gap = now - endTime;
                    if (gap > 20 && gap < DOUBLE_TAP_WINDOW_MS) {
                        // Double-tap detected!
                        triggerDash(player, d);
                        ended.remove(d);
                    }
                }
            }
        }

        // Update previous directions
        previousDirs.put(uid, new HashSet<>(currentDirs));
    }

    // ── Dash execution ────────────────────────────────────────────────────────

    private void triggerDash(Player player, Dir dir) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Cooldown check
        if (now - dashCooldown.getOrDefault(uid, 0L) < DASH_COOLDOWN_MS) return;
        dashCooldown.put(uid, now);

        // Build dash velocity relative to player facing
        Vector facing = player.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) return;
        facing.normalize();

        // Right vector (90° clockwise of facing in XZ plane)
        Vector right = new Vector(facing.getZ(), 0, -facing.getX());

        double speed = 1.15; // blocks of distance
        Vector dashVec = switch (dir) {
            case W -> facing.clone().multiply(speed);
            case S -> facing.clone().multiply(-speed * 0.85);
            case A -> right.clone().multiply(-speed);
            case D -> right.clone().multiply(speed);
        };
        dashVec.setY(0.18); // small upward arc — GunZ style

        player.setVelocity(dashVec);

        // ── Visual & sound ────────────────────────────────────────────────────
        String dirLabel = switch (dir) {
            case W -> "▶▶";
            case S -> "◀◀";
            case A -> "▲ LEFT";
            case D -> "RIGHT ▲";
        };

        player.getWorld().spawnParticle(Particle.CRIT,
            player.getLocation().add(0, 1, 0), 18, 0.35, 0.35, 0.35, 0.25);
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
            player.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.15);
        player.getWorld().playSound(player.getLocation(),
            Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        player.getWorld().playSound(player.getLocation(),
            Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.6f, 2.0f);

        player.sendActionBar("§7⚡ §f§lDASH §8" + dirLabel);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingGunZSword(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return itemFactory.isGunZSword(hand);
    }
}
