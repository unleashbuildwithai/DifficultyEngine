package com.yourname.difficulty.casting;

import org.bukkit.potion.PotionEffect;

import java.util.List;

/**
 * ArmedSupportEffect — Represents a Support Blessing Potion's effect(s) that
 * have been "armed" on a player (via {@code SupportPotionListener}) and are
 * ready to be discharged at range via a Support Staff right-click splash
 * cast (handled in {@code CastingEngine#onSupportStaffUse}).
 *
 * Arming rules (per design spec):
 *  - Drinking a Support Blessing Potion while holding the Support Staff
 *    requires: the master Support Book + the matching Support Page for that
 *    potion + a Support Rune present in inventory. Missing any of these
 *    shatters the potion (handled entirely in SupportPotionListener).
 *  - On successful drink: the potion's effect(s) apply to the drinker AND
 *    are armed for a ranged splash cast via Support Staff right-click.
 *  - Non-stacking: drinking another potion while one is still armed (not
 *    yet expired) wastes the new potion — does not refresh/replace it.
 *  - Casting the armed effect(s) onto a target that already has the exact
 *    same PotionEffectType active does NOT re-apply/extend it for that
 *    target specifically, but the Support Rune is still consumed for the cast.
 */
public record ArmedSupportEffect(List<PotionEffect> effects, String potionId, long expiryMillis) {

    /** True if this armed effect's arming window has expired. */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiryMillis;
    }
}
