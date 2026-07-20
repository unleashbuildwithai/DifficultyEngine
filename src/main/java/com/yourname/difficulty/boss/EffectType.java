package com.yourname.difficulty.boss;

/**
 * EffectType — All possible buffs and debuffs tracked by the EffectRegistry.
 *
 * LEACHED       — HP drain per tick (Target.getHealth() * 0.70 / 100)
 * NAUSEATED     — Nausea (Confusion) applied by boss damage
 * SPLAT         — Player entered a Splat zone (boss AoE ground marker)
 * ENCHANTED     — Player has an active castEnchantment buff
 * VULNERABLE    — Boss is vulnerable (Wind broke the Shriek distortion)
 */
public enum EffectType {
    LEACHED,
    NAUSEATED,
    SPLAT,
    ENCHANTED,
    VULNERABLE
}
