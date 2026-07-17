 package com.yourname.difficulty.listeners;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SitListener — Directional edge-sitting on slabs and stairs.
 *
 * STAIRS:
 *   The stair's {@code getFacing()} gives the flat/low side — that's where
 *   feet dangle. The seat is offset 0.3 blocks toward that edge and the
 *   player faces outward (toward the flat side = looking over the ledge).
 *
 * SLABS (non-double only):
 *   The player's horizontal facing at click time determines the sitting edge.
 *   Standing north of the slab and right-clicking → sits at the north edge.
 *   The seat is offset 0.3 blocks in that direction; player faces outward.
 *
 * SPACE CHECK (both types):
 *   The block immediately in the "feet" direction must be passable (air,
 *   flowers, water, etc.). If it's solid, the sit is denied with a message.
 *
 * Toggle: /sit on|off
 */
public class SitListener implements Listener {

    private final Set<UUID>             sitEnabled = new HashSet<>();
    private final Map<UUID, ArmorStand> seats      = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public void setSitEnabled(UUID uuid, boolean enabled) {
        if (enabled) sitEnabled.add(uuid);
        else         sitEnabled.remove(uuid);
    }

    public boolean isSitEnabled(UUID uuid) {
        return sitEnabled.contains(uuid);
    }

    // ── Right-click to sit ────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand()   != EquipmentSlot.HAND)        return;

        Player player = event.getPlayer();
        if (!sitEnabled.contains(player.getUniqueId())) return;
        if (player.isInsideVehicle())                   return;
        if (seats.containsKey(player.getUniqueId()))    return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        BlockData data = block.getBlockData();

        // Resolve "feet direction" and Y offset per block type
        BlockFace feetFace; // direction player's feet will dangle
        double    yOffset;  // ArmorStand Y from block base

        if (data instanceof Slab slab) {
            if (slab.getType() == Slab.Type.DOUBLE) return; // full block — skip
            feetFace = getHorizontalFacing(player);          // player chooses edge
            yOffset  = (slab.getType() == Slab.Type.TOP) ? 0.5 : 0.0;

        } else if (data instanceof Stairs stair) {
            feetFace = stair.getFacing();   // flat/low side of the step
            yOffset  = 0.0;

        } else {
            return; // full block or unsupported
        }

        // ── Space check ───────────────────────────────────────────────────────
        Block spaceBlock = block.getRelative(feetFace);
        if (!spaceBlock.isPassable()) {
            event.setCancelled(true);
            player.sendMessage("§8[§6DifficultyEngine§8] §c✗ No room — something blocks where your legs would hang.");
            return;
        }

        event.setCancelled(true);

        // ── Build sit location at the edge ────────────────────────────────────
        double x = block.getX() + 0.5;
        double z = block.getZ() + 0.5;

        // Offset 0.3 blocks toward the feet edge so the player sits on the ledge
        switch (feetFace) {
            case NORTH -> z = block.getZ() + 0.2;
            case SOUTH -> z = block.getZ() + 0.8;
            case WEST  -> x = block.getX() + 0.2;
            case EAST  -> x = block.getX() + 0.8;
            default    -> {} // keep center
        }

        float sitYaw = faceToYaw(feetFace); // face outward over the ledge

        World world = block.getWorld();
        Location sitLoc = new Location(world, x, block.getY() + yOffset, z, sitYaw, 0f);

        // ── Spawn invisible marker seat ───────────────────────────────────────
        ArmorStand seat = (ArmorStand) world.spawnEntity(sitLoc, EntityType.ARMOR_STAND);
        seat.setVisible(false);
        seat.setGravity(false);
        seat.setInvulnerable(true);
        seat.setSmall(true);
        seat.setMarker(true);
        seat.setCollidable(false);
        seat.addPassenger(player);

        seats.put(player.getUniqueId(), seat);
        player.sendMessage("§8[§6DifficultyEngine§8] §7You sit on the ledge. §8(Sneak to stand up)");
    }

    // ── Dismount ──────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVehicleExit(VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        removeSeat(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeSeat(event.getPlayer());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void removeSeat(Player player) {
        ArmorStand seat = seats.remove(player.getUniqueId());
        if (seat != null && !seat.isDead()) {
            seat.eject();
            seat.remove();
        }
    }

    /**
     * Returns the four-way cardinal facing of the player based on their yaw.
     * Used to determine which edge of a slab to sit on.
     */
    private BlockFace getHorizontalFacing(Player player) {
        float yaw = ((player.getLocation().getYaw() % 360) + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135)              return BlockFace.WEST;
        if (yaw < 225)              return BlockFace.NORTH;
        return BlockFace.EAST;
    }

    /**
     * Converts a cardinal BlockFace to the Bukkit yaw angle that makes the
     * player face in that direction (looking out over the ledge).
     */
    private float faceToYaw(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> 270f;
            default    -> 0f;
        };
    }
}
