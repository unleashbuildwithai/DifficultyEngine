package com.yourname.difficulty.listeners;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fishing Cape — single rainbow-particle axolotl companion that swims in a
 * sinusoidal path behind the player. Extracted from {@link CapeVisualTask}
 * to keep it under the 400-line limit.
 */
final class FishingCapeOrbit {

    /** Scoreboard tag applied to every temporary fish/axolotl entity. */
    static final String FISH_TAG = "DE_cape_fish";

    /** One rainbow axolotl lazily swimming behind the player. */
    private static final int    AXOLOTL_COUNT  = 1;
    private static final double SWIM_SIDE_AMP  = 1.80;
    private static final double SWIM_SIDE_FREQ = 0.42;
    private static final double SWIM_VERT_AMP  = 1.20;
    private static final double SWIM_VERT_FREQ = 0.65;
    private static final double SWIM_BACK_DIST = 3.20;
    private static final double SWIM_HEIGHT    = 1.40;
    private static final double ORBIT_SPEED    = 0.055;

    private final JavaPlugin plugin;
    private final Team capeEntityTeam;

    private final Map<UUID, List<Entity>> fishingOrbit  = new HashMap<>();
    private final Map<UUID, Double>       fishingAngles = new HashMap<>();

    FishingCapeOrbit(JavaPlugin plugin, Team capeEntityTeam) {
        this.plugin = plugin;
        this.capeEntityTeam = capeEntityTeam;
    }

    /**
     * Moves the single rainbow axolotl companion along a sinusoidal swim path
     * behind the player and renders cycling rainbow DUST particles around it.
     */
    void updateFishingOrbit(Player player, int tick) {
        UUID uuid = player.getUniqueId();
        double t  = fishingAngles.getOrDefault(uuid, 0.0) + ORBIT_SPEED;
        fishingAngles.put(uuid, t);

        List<Entity> entities = fishingOrbit.get(uuid);
        boolean needRespawn = (entities == null
                || entities.size() < AXOLOTL_COUNT
                || entities.stream().anyMatch(e -> e == null || e.isDead()));

        if (needRespawn) {
            spawnFishingOrbit(player, t);
            return;
        }

        Entity axolotl = entities.get(0);

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
        Color rainbowColor = RainbowColorUtil.hsbToColor(hue);
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
        List<Entity> old = fishingOrbit.get(uuid);
        if (old != null) {
            for (Entity e : old) if (e != null && !e.isDead()) e.remove();
        }

        List<Entity> entities = new ArrayList<>();

        // Initial position: directly behind player at swim height
        Vector facing = player.getLocation().getDirection();
        Vector back   = new Vector(-facing.getX(), 0, -facing.getZ());
        if (back.lengthSquared() > 1e-6) back.normalize();

        Location spawnLoc = player.getLocation().clone()
                .add(back.clone().multiply(SWIM_BACK_DIST))
                .add(0, SWIM_HEIGHT, 0);

        // Spawn ONE BLUE axolotl (rarest variant — complemented by rainbow particles)
        Axolotl axolotl = player.getWorld().spawn(
            spawnLoc,
            Axolotl.class,
            a -> {
                a.setAI(false);
                a.setGravity(false);
                a.setPersistent(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                a.setCustomNameVisible(false);
                a.setCollidable(false);
                a.setAdult();
                a.setVariant(Axolotl.Variant.BLUE);
                a.addScoreboardTag(FISH_TAG);
                capeEntityTeam.addEntry(a.getUniqueId().toString());
            });
        entities.add(axolotl);

        fishingOrbit.put(uuid, entities);
    }

    /** Removes all orbit entities for the given player and clears their angle. */
    void despawnFishingOrbit(UUID uuid) {
        List<Entity> entities = fishingOrbit.remove(uuid);
        fishingAngles.remove(uuid);
        if (entities == null) return;
        for (Entity e : entities) {
            if (e != null && !e.isDead()) e.remove();
        }
    }

    /** Despawns orbit entities for every tracked player (used by cleanup()). */
    void despawnAll() {
        for (UUID uuid : new HashSet<>(fishingOrbit.keySet())) {
            despawnFishingOrbit(uuid);
        }
    }
}
