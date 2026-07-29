package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the invisible ArmorStand "hologram" name-tag that floats behind
 * each player displaying their equipped cape's name. Extracted from
 * {@link CapeVisualTask} to keep it under the 400-line limit.
 */
final class CapeHologramManager {

    /** Scoreboard tag applied to every hologram stand. */
    static final String HOLOGRAM_TAG = "DE_cape_sign";

    private static final double BACK_OFFSET = 0.30;
    private static final double BACK_HEIGHT = 0.80;

    private final SkillCapeManager capeManager;

    /** Live map of player UUID → their current cape hologram stand. */
    private final Map<UUID, ArmorStand> holograms    = new HashMap<>();

    /**
     * Tracks the display name of each player's last-known equipped cape.
     * Used to detect swaps so the old stand can be killed immediately.
     */
    private final Map<UUID, String>     lastCapeName = new HashMap<>();

    CapeHologramManager(SkillCapeManager capeManager) {
        this.capeManager = capeManager;
    }

    ArmorStand getHologram(UUID uuid) {
        return holograms.get(uuid);
    }

    /** Detects a cape swap (name changed) and removes the old stand if so. Returns the current name. */
    String handleSwapDetection(UUID uuid, ItemStack cape) {
        String currentName = getCapeName(cape);
        String lastName    = lastCapeName.get(uuid);
        if (!currentName.equals(lastName)) {
            ArmorStand old = holograms.remove(uuid);
            if (old != null && !old.isDead()) old.remove();
            lastCapeName.put(uuid, currentName);
        }
        return currentName;
    }

    void removeFor(UUID uuid) {
        ArmorStand old = holograms.remove(uuid);
        if (old != null && !old.isDead()) old.remove();
        lastCapeName.remove(uuid);
    }

    void updateHologram(Player player, ItemStack cape) {
        // Position: directly behind the player at torso height
        org.bukkit.util.Vector facing = player.getLocation().getDirection();
        org.bukkit.util.Vector back   = new org.bukkit.util.Vector(-facing.getX(), 0, -facing.getZ());
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

    void cleanup() {
        for (ArmorStand stand : holograms.values()) {
            if (!stand.isDead()) stand.remove();
        }
        holograms.clear();
        lastCapeName.clear();
    }
}
