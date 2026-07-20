package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleEntityCollisionEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MinecartListener — Placement, high-speed propulsion, rail snap, and
 *                    collision bypass for Turbo Minecarts.
 *
 * ── Speed ────────────────────────────────────────────────────────────────────
 * setMaxSpeed(2.0) = 5× the vanilla cap (0.4). Every tick the cart's
 * horizontal speed is checked: if it has fallen below MIN_TURBO_SPEED (the
 * "stuck on edge" scenario), the last known travel direction is re-injected
 * at full turbo speed. This is the "imaginary cutout" — the cart phases
 * through block-edge friction as if the geometry isn't there.
 *
 * ── Collision bypass ─────────────────────────────────────────────────────────
 * VehicleEntityCollisionEvent is cancelled for all non-player entities so mobs
 * standing on the track cannot halt the cart.
 *
 * ── Rail magnetization ────────────────────────────────────────────────────────
 * If the cart drifts off-rail (high-speed overshoot), it is teleported to the
 * nearest rail within 2 blocks and its horizontal velocity is preserved.
 *
 * Only carts tagged with PDC key "turbo_minecart" are affected.
 * All vanilla minecarts behave completely normally.
 */
public class MinecartListener implements Listener {

    /** Target speed (blocks/tick) when cart is on a rail. */
    private static final double TURBO_MAX_SPEED = 2.0;
    /**
     * Minimum acceptable horizontal speed. If the cart drops below this
     * the momentum-injection fires to push it back to TURBO_MAX_SPEED.
     * Set slightly below TURBO_MAX_SPEED so normal deceleration (curves,
     * slopes) doesn't retrigger, but edge-stuck does.
     */
    private static final double MIN_TURBO_SPEED    = 0.5;
    private static final double MIN_TURBO_SPEED_SQ = MIN_TURBO_SPEED * MIN_TURBO_SPEED;

    private final ItemFactory   itemFactory;
    private final NamespacedKey turboKey;

    /**
     * Tracks the last non-trivial horizontal velocity direction for each
     * turbo cart so the momentum-injection has a direction to restore.
     * Key = cart entity UUID.
     */
    private final Map<UUID, Vector> lastDirection = new HashMap<>();

    public MinecartListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
        this.turboKey    = itemFactory.getTurboMinecartKey();
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceTurboCart(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand()   != EquipmentSlot.HAND)        return;

        Player    player = event.getPlayer();
        ItemStack held   = event.getItem();
        if (!itemFactory.isTurboMinecart(held)) return;

        Block rail = event.getClickedBlock();
        if (rail == null || !Tag.RAILS.isTagged(rail.getType())) return;

        event.setCancelled(true);

        Location spawnLoc = rail.getLocation().add(0.5, 0.0625, 0.5);
        Minecart cart = (Minecart) rail.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);

        cart.getPersistentDataContainer().set(turboKey, PersistentDataType.BYTE, (byte) 1);
        applyTurboSettings(cart);

        if (held.getAmount() > 1) held.setAmount(held.getAmount() - 1);
        else player.getInventory().setItemInMainHand(null);

        player.sendMessage("§8[§6DifficultyEngine§8] §7⚡ Turbo Minecart deployed!");
    }

    // ── Board ─────────────────────────────────────────────────────────────────

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTurboCart(cart)) return;
        applyTurboSettings(cart);
    }

    // ── Momentum maintenance + rail snap ──────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTurboCart(cart)) return;

        UUID   uid = cart.getUniqueId();
        Vector vel = cart.getVelocity();
        double horizSpeedSq = vel.getX() * vel.getX() + vel.getZ() * vel.getZ();

        // Remember direction when moving freely
        if (horizSpeedSq > 0.05) {
            lastDirection.put(uid, new Vector(vel.getX(), 0, vel.getZ()).normalize());
        }

        // ── Rail check ────────────────────────────────────────────────────────
        Block current = cart.getLocation().getBlock();
        boolean onRail = Tag.RAILS.isTagged(current.getType());

        if (!onRail) {
            Block nearest = findNearestRail(cart.getLocation());
            if (nearest != null) {
                Location snap = nearest.getLocation().add(0.5, 0, 0.5);
                snap.setYaw(cart.getLocation().getYaw());
                snap.setPitch(0f);
                cart.teleport(snap);
            }
            // Kill vertical drift regardless
            Vector v = cart.getVelocity();
            cart.setVelocity(new Vector(v.getX(), 0, v.getZ()));
            return;
        }

    // ── Track Scanner & Velocity Booster ──────────────────────────────────
    boolean slopeDetected = false;
    Vector dir = lastDirection.get(uid);
    if (dir != null && onRail) {
        slopeDetected = hasSlopeAhead(current, dir);
    }
    
    // Dynamic friction override max speed
    if (slopeDetected) {
        cart.setMaxSpeed(TURBO_MAX_SPEED * 2.5);
    } else {
        cart.setMaxSpeed(TURBO_MAX_SPEED);
    }

    // ── Momentum injection ("imaginary cutout") ────────────────────────────
    // If the cart has slowed due to block-edge friction or is on a slope, re-apply speed
    if (horizSpeedSq < MIN_TURBO_SPEED_SQ || slopeDetected) {
        if (dir != null) {
            double speedMult = slopeDetected ? 2.5 : 1.0;
            
            // Anti-stall override vanilla friction
            cart.setVelocity(new Vector(
                dir.getX() * TURBO_MAX_SPEED * speedMult,
                slopeDetected ? vel.getY() : vel.getY(), // Let vanilla handle Y naturally, but horizontal boost forces it up/down
                dir.getZ() * TURBO_MAX_SPEED * speedMult
            ));
        }
    }
}

private boolean hasSlopeAhead(Block start, Vector dir) {
    if (dir == null || (Math.abs(dir.getX()) < 0.1 && Math.abs(dir.getZ()) < 0.1)) return false;
    
    // Check if current block itself is a slope
    if (start.getBlockData() instanceof org.bukkit.block.data.Rail railData) {
        org.bukkit.block.data.Rail.Shape shape = railData.getShape();
        if (shape == org.bukkit.block.data.Rail.Shape.ASCENDING_NORTH ||
            shape == org.bukkit.block.data.Rail.Shape.ASCENDING_SOUTH ||
            shape == org.bukkit.block.data.Rail.Shape.ASCENDING_EAST ||
            shape == org.bukkit.block.data.Rail.Shape.ASCENDING_WEST) {
            return true;
        }
    }
    
    int dx = Math.abs(dir.getX()) > Math.abs(dir.getZ()) ? (dir.getX() > 0 ? 1 : -1) : 0;
    int dz = Math.abs(dir.getZ()) > Math.abs(dir.getX()) ? (dir.getZ() > 0 ? 1 : -1) : 0;
    
    if (dx == 0 && dz == 0) return false;
    
    Block current = start;
    // Track pre-read: scan 5 blocks ahead
    for (int i = 0; i < 5; i++) {
        Block nextFlat = current.getRelative(dx, 0, dz);
        Block nextUp = current.getRelative(dx, 1, dz);
        Block nextDown = current.getRelative(dx, -1, dz);
        
        if (Tag.RAILS.isTagged(nextUp.getType()) || Tag.RAILS.isTagged(nextDown.getType())) {
            return true; 
        } else if (Tag.RAILS.isTagged(nextFlat.getType())) {
            current = nextFlat; 
        } else {
            break; 
        }
    }
    
    return false;
}

    // ── Entity collision bypass ───────────────────────────────────────────────

    /**
     * Cancels collisions between the turbo cart and non-player entities.
     * Mobs, items, and other entities on the track will no longer stop the cart.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVehicleEntityCollision(VehicleEntityCollisionEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTurboCart(cart)) return;
        Entity collider = event.getEntity();
        // Allow the rider/player collision (boarding), cancel everything else
        if (collider instanceof Player) return;
        event.setCancelled(true);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyTurboSettings(Minecart cart) {
        cart.setMaxSpeed(TURBO_MAX_SPEED);
        cart.setSlowWhenEmpty(false);
    }

    private boolean isTurboCart(Minecart cart) {
        return cart.getPersistentDataContainer().has(turboKey, PersistentDataType.BYTE);
    }

    private Block findNearestRail(Location origin) {
        Block originBlock = origin.getBlock();
        double bestDistSq = Double.MAX_VALUE;
        Block  best       = null;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block candidate = originBlock.getRelative(dx, dy, dz);
                    if (!Tag.RAILS.isTagged(candidate.getType())) continue;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < bestDistSq) { bestDistSq = d; best = candidate; }
                }
            }
        }
        return best;
    }
}
