package com.yourname.difficulty.monsters;

import org.bukkit.entity.EntityType;
import java.util.List;

/**
 * CustomMonsterDef — Immutable definition for a custom monster.
 *
 * Loaded from monsters.yml by CustomMonsterManager.
 * All values are final and stored at load time.
 */
public record CustomMonsterDef(
        String       id,
        EntityType   entityType,
        String       name,
        double       health,
        double       damage,
        double       speed,
        List<String> drops,
        List<String> effects
) {
    /** Returns a short description suitable for admin feedback. */
    public String summary() {
        return id + " [" + entityType + " | HP=" + (int)health
                + " | DMG=" + (int)damage + " | SPD=" + speed + "]";
    }
}
