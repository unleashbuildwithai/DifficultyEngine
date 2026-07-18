package com.separateplug.spirit;

import org.bukkit.Material;
import org.bukkit.Particle;

/**
 * The four elemental spirit types that players are randomly bound to on first join.
 *
 * ── Custom Model Data (texture pack required) ─────────────────────────────────
 *   Fire  Spirit Staff  →  BLAZE_ROD        CustomModelData 2001
 *   Water Spirit Staff  →  PRISMARINE_SHARD CustomModelData 2002
 *   Earth Spirit Staff  →  STICK            CustomModelData 2003
 *   Air   Spirit Staff  →  FEATHER          CustomModelData 2004
 *
 * All four staves use a "ghost sword" model:
 *   - Blade  = large grey ghost body (from the reference picture)
 *   - Handle = dark stick/hilt held at the ghost's torso
 *   Your texture pack artist needs to create these models keyed to the above IDs.
 */
public enum SpiritType {

    FIRE(
        "§c",
        "§c§lFire Spirit",
        "A restless soul of raging flames.",
        Material.BLAZE_ROD,
        2001,
        Particle.FLAME
    ),
    WATER(
        "§b",
        "§b§lWater Spirit",
        "A serene soul of flowing water.",
        Material.PRISMARINE_SHARD,
        2002,
        Particle.DRIPPING_WATER
    ),
    EARTH(
        "§2",
        "§2§lEarth Spirit",
        "A patient soul of ancient stone.",
        Material.STICK,
        2003,
        Particle.BLOCK
    ),
    AIR(
        "§7",
        "§7§lAir Spirit",
        "A swift soul of endless wind.",
        Material.FEATHER,
        2004,
        Particle.CLOUD
    );

    /** Colour code prefix (e.g. §c). */
    public final String       color;
    /** Display name shown on the staff item. */
    public final String       displayName;
    /** Lore description. */
    public final String       description;
    /** Base material for the ItemStack (model overridden by CustomModelData). */
    public final Material     baseMaterial;
    /** CustomModelData integer — texture pack maps this to the ghost-sword model. */
    public final int          customModelData;
    /** Particle to trail from the staff during flight. */
    public final Particle     trailParticle;

    SpiritType(String color, String displayName, String description,
               Material baseMaterial, int customModelData, Particle trailParticle) {
        this.color           = color;
        this.displayName     = displayName;
        this.description     = description;
        this.baseMaterial    = baseMaterial;
        this.customModelData = customModelData;
        this.trailParticle   = trailParticle;
    }

    /** Pretty display with colour for messages. */
    public String colored() { return color + name().charAt(0) + name().substring(1).toLowerCase(); }
}
