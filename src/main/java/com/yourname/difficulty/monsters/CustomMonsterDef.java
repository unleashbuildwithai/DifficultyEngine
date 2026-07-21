package com.yourname.difficulty.monsters;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * CustomMonsterDef — Immutable definition for a custom monster.
 *
 * Loaded from monsters.yml by CustomMonsterManager.
 * All values are final and stored at load time.
 *
 * ── Clean custom-entity model ──────────────────────────────────────────────
 * {@code baseEntityType} is used ONLY as an invisible/silent physics + hitbox
 * carrier (position, collision, pathfinding). It is never shown to the
 * client — the mob is always spawned invisible.
 *
 * The actual "look" of the monster comes from an attached ItemDisplay
 * entity (see CustomMonsterManager), driven by {@code displayItem},
 * {@code displayCustomModelData}, and {@code displayScale}. This keeps the
 * custom monster's visual completely independent of the vanilla mob model
 * underneath — no giant bats, no reskinned withers, no vanilla silhouettes.
 */
public record CustomMonsterDef(
        String       id,
        EntityType   baseEntityType,
        String       name,
        double       health,
        double       damage,
        double       speed,
        List<String> drops,
        List<String> effects,
        Material     displayItem,
        int          displayCustomModelData,
        float        displayScale
) {
    /** Returns a short description suitable for admin feedback. */
    public String summary() {
        return id + " [" + baseEntityType + " | HP=" + (int)health
                + " | DMG=" + (int)damage + " | SPD=" + speed
                + " | display=" + displayItem + "#" + displayCustomModelData + "]";
    }
}
