package com.yourname.difficulty.listeners;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * MinecartListener — Handles placement, speed boost, and rail magnetization
 *                    for Turbo Minecarts.
 *
 * Only carts tagged with PDC key {@code "turbo_minecart"} are affected.
 * All vanilla minecarts behave normally.
 *
 * Placement:
 *   Right-click any rail with a Turbo Minecart item. The vanilla placement is
 *   cancelled; a tagged minecart is spawned in its place and one item consumed.
 *
 * Speed:
 *   When a player boards a turbo cart, {@code setMaxSpeed(1.2)} is called
 *   (3× the vanilla default of 0.4).
 *
 * Rail magnetization:
 *   {@code VehicleMoveEvent} checks whether the cart is still on a rail.
 *   If it has overshot or drifted off, the nearest rail within 2 blocks is
 *   found and the cart is snapped back to it, preserving horizontal momentum.
 *   Vertical velocity is zeroed to prevent airborne bouncing.
 *
 * Performance:
 *   • All event handlers perform a PDC tag check (fast HashMap lookup) as the
 *     first substantive test — only turbo carts continue past that point.
 *   • The VehicleMoveEvent rail check is a single block-type lookup and returns
 *     immediately if the cart is on rail (the common case).
 */
public class MinecartListener implements Listener {

    private final ItemFactory  itemFactory;
    private final NamespacedKey turboKey;

    public MinecartListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
        this.turboKey    = itemFactory.getTurboMinecartKey();
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    /**
     * Intercepts right-click on a rail with the Turbo Minecart item.
     * Cancels the vanilla placement, spawns a tagged cart, and consumes 1 item.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlaceTurboCart(PlayerInteractEvent event) {
        if (event.getAction()   != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand()     != EquipmentSlot.HAND)        return;

        Player    player = event.getPlayer();
        ItemStack held   = event.getItem();
        if (!itemFactory.isTurboMinecart(held)) return;

        Block rail = event.getClickedBlock();
        if (rail == null || !Tag.RAILS.isTagged(rail.getType())) return;

        // Cancel vanilla minecart placement
        event.setCancelled(true);

        // Spawn a real Minecart at the rail's top-centre
        Location spawnLoc = rail.getLocation().add(0.5, 0.0625, 0.5);
        Minecart cart = (Minecart) rail.getWorld().spawnEntity(spawnLoc, EntityType.MINECART);

        // Tag it as a turbo cart
        cart.getPersistentDataContainer().set(turboKey, PersistentDataType.BYTE, (byte) 1);

        // Set max speed immediately
        cart.setMaxSpeed(1.2);

        // Consume one item from the player's hand
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage("§8[§6DifficultyEngine§8] §7⚡ Turbo Minecart deployed!");
    }

    // ── Speed boost on board ──────────────────────────────────────────────────

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTurboCart(cart)) return;

        cart.setMaxSpeed(1.2);
    }

    // ── Rail magnetization ────────────────────────────────────────────────────

    /**
     * After every move tick, verify the cart is still on a rail.
     * If not, snap it to the nearest rail within 2 blocks.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart cart)) return;
        if (!isTurboCart(cart)) return;

        // Fast path: already on a rail — nothing to do
        Block current = cart.getLocation().getBlock();
        if (Tag.RAILS.isTagged(current.getType())) return;

        // Off-rail — find nearest rail and snap
        Block nearest = findNearestRail(cart.getLocation());
        if (nearest != null) {
            Location snap = nearest.getLocation().add(0.5, 0, 0.5);
            snap.setYaw(cart.getLocation().getYaw());
            snap.setPitch(0f);
            cart.teleport(snap);

            // Preserve horizontal velocity, kill vertical
            Vector v = cart.getVelocity();
            cart.setVelocity(new Vector(v.getX(), 0, v.getZ()));
        } else {
            // No rail found nearby — kill vertical drift
            Vector v = cart.getVelocity();
            if (v.getY() != 0) {
                cart.setVelocity(new Vector(v.getX(), 0, v.getZ()));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isTurboCart(Minecart cart) {
        return cart.getPersistentDataContainer().has(turboKey, PersistentDataType.BYTE);
    }

    /**
     * Searches a 2-block Manhattan radius (same Y and ±1 Y) for any rail block.
     * Returns the nearest one, or {@code null} if none found.
     */
    private Block findNearestRail(Location origin) {
        Block originBlock = origin.getBlock();
        double bestDistSq = Double.MAX_VALUE;
        Block best = null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block candidate = originBlock.getRelative(dx, dy, dz);
                    if (!Tag.RAILS.isTagged(candidate.getType())) continue;

                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                }
            }
        }
        return best;
    }
}
