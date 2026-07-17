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

        // ── Momentum injection ("imaginary cutout") ────────────────────────────
        // If the cart has slowed due to block-edge friction, re-apply full speed
        // in the last known direction so it pushes through without stopping.
        if (horizSpeedSq < MIN_TURBO_SPEED_SQ) {
            Vector dir = lastDirection.get(uid);
            if (dir != null) {
                cart.setVelocity(new Vector(
                    dir.getX() * TURBO_MAX_SPEED,
                    vel.getY(),
                    dir.getZ() * TURBO_MAX_SPEED
                ));
            }
        }
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
