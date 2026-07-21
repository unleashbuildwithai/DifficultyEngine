package com.yourname.difficulty.casting;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collection;

/**
 * BuffLogic — Applies the gameplay effect for each BuffType.
 *
 * Called by CastingEngine after a matching combo is detected.
 * Each method applies particle effects, sounds, potion effects,
 * and any other world-interaction for the combo.
 *
 * Paper 1.21 API names used:
 *  PotionEffectType.SLOWNESS         (was SLOW)
 *  PotionEffectType.STRENGTH         (was INCREASE_DAMAGE)
 *  PotionEffectType.RESISTANCE       (was DAMAGE_RESISTANCE)
 *  PotionEffectType.NAUSEA           (was CONFUSION)
 *  PotionEffectType.MINING_FATIGUE   (was SLOW_DIGGING)
 *  Particle.EXPLOSION_EMITTER        (was EXPLOSION_LARGE)
 *  Particle.SPLASH                   (was WATER_SPLASH)
 *  Particle.ENCHANTED_HIT            (was CRIT_MAGIC)
 *  Particle.TOTEM_OF_UNDYING         (was TOTEM)
 */
public class BuffLogic {

    private static final double AOE_RADIUS = 8.0;

    private final JavaPlugin plugin;

    public BuffLogic(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies the given BuffType effect to the caster.
     */
    public void apply(Player caster, BuffType buff) {
        switch (buff) {
            case INFERNO_BURST  -> infernoBlast(caster);
            case STEAM_BLAST    -> steamBlast(caster);
            case MAGMA_TRAP     -> magmaTrap(caster);
            case TORNADO_FLAME  -> tornadoFlame(caster);
            case TIDAL_SURGE    -> tidalSurge(caster);
            case STONE_SKIN     -> stoneSkin(caster);
            case QUICKSAND      -> quicksand(caster);
            case MIST_VEIL      -> mistVeil(caster);
            case SANDSTORM      -> sandstorm(caster);
            case GALE_FORCE     -> galeForce(caster);
            case MUD_WALL       -> mudWall(caster);
            case GRAND_HARMONY  -> grandHarmony(caster);
            case CLEANSE        -> cleanse(caster);
            case BLAZE_DASH     -> blazeDash(caster);
            case FORTIFY        -> fortify(caster);
        }
    }

    // ── Offensive ─────────────────────────────────────────────────────────────

    private void infernoBlast(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 60, 3, 2, 3, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
        nearbyMobs(caster).forEach(e -> e.setFireTicks(100)); // 5s fire
        caster.sendMessage("§c🔥 §lInferno Burst§r §7— nearby enemies ignited!");
    }

    private void steamBlast(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 80, 4, 2, 4, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 1.2f);
        nearbyMobs(caster).forEach(e -> {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            }
        });
        caster.sendMessage("§b💨 §lSteam Blast§r §7— nearby enemies blinded for 3s!");
    }

    private void magmaTrap(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.LAVA, loc, 40, 3, 0.5, 3, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_POP, 2f, 0.5f);
        nearbyMobs(caster).forEach(e -> {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                e.setFireTicks(60);
            }
        });
        caster.sendMessage("§6🌋 §lMagma Trap§r §7— nearby enemies slowed and burning!");
    }

    private void tornadoFlame(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc, 5, 2, 1, 2, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2f, 0.7f);
        // Launch caster slightly
        caster.setVelocity(caster.getVelocity().add(new org.bukkit.util.Vector(0, 0.8, 0)));
        // Fire nova
        nearbyMobs(caster).forEach(e -> e.setFireTicks(80));
        caster.sendMessage("§e🌪 §lTornado Flame§r §7— fire nova launched!");
    }

    // ── Defensive ─────────────────────────────────────────────────────────────

    private void tidalSurge(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.SPLASH, loc, 100, 4, 1, 4, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_GUARDIAN_ATTACK, 1.5f, 0.5f);
        nearbyMobs(caster).forEach(e -> {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
            }
        });
        caster.sendMessage("§b🌊 §lTidal Surge§r §7— nearby mobs slowed!");
    }

    private void stoneSkin(Player caster) {
        caster.getWorld().spawnParticle(Particle.ENCHANTED_HIT, caster.getLocation(), 30, 1, 1, 1, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.BLOCK_STONE_BREAK, 2f, 0.5f);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1)); // 10s Resistance II
        caster.sendMessage("§2🪨 §lStone Skin§r §7— Resistance II for 10 seconds!");
    }

    private void quicksand(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.FALLING_DUST,
                loc, 80, 4, 0.1, 4, 0, Material.SAND.createBlockData());
        loc.getWorld().playSound(loc, Sound.BLOCK_SAND_STEP, 2f, 0.3f);
        nearbyMobs(caster).forEach(e -> {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 3)); // Slowness IV
            }
        });
        caster.sendMessage("§a🌿 §lQuicksand§r §7— nearby mobs rooted!");
    }

    private void mistVeil(Player caster) {
        caster.getWorld().spawnParticle(Particle.CLOUD, caster.getLocation(), 40, 1, 1, 1, 0.02);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_BOTTLE_FILL, 2f, 1.5f);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0)); // 6s
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,        120, 1));
        caster.sendMessage("§f🌫 §lMist Veil§r §7— invisible + Speed II for 6 seconds!");
    }

    // ── Elemental reactions ───────────────────────────────────────────────────

    private void sandstorm(Player caster) {
        Location loc = caster.getLocation();
        for (int i = 0; i < 5; i++) {
            double t = i * (Math.PI * 2 / 5);
            double x = 3 * Math.cos(t), z = 3 * Math.sin(t);
            loc.getWorld().spawnParticle(Particle.FALLING_DUST,
                    loc.clone().add(x, 1, z), 20, 0.3, 1, 0.3, 0.1,
                    Material.SAND.createBlockData());
        }
        loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_FLAP, 2f, 0.5f);
        nearbyMobs(caster).forEach(e -> {
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
            }
        });
        caster.sendMessage("§e⚡ §lSandstorm§r §7— sand blinds nearby mobs!");
    }

    private void galeForce(Player caster) {
        Location loc = caster.getLocation();
        loc.getWorld().spawnParticle(Particle.END_ROD, loc, 50, 3, 2, 3, 0.5);
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SHOOT, 2f, 1.5f);
        nearbyMobs(caster).forEach(e -> {
            org.bukkit.util.Vector vel = e.getLocation().toVector()
                    .subtract(loc.toVector()).normalize().multiply(1.5);
            vel.setY(1.2);
            e.setVelocity(vel);
        });
        caster.sendMessage("§f🌀 §lGale Force§r §7— nearby mobs launched into the air!");
    }

    private void mudWall(Player caster) {
        Location front = caster.getLocation().add(
                caster.getLocation().getDirection().multiply(2));
        // Place 3 cobblestone walls in front of caster
        for (int i = -1; i <= 1; i++) {
            Location wallLoc = front.clone().add(i, 0, 0);
            wallLoc.getBlock().setType(Material.COBBLESTONE_WALL, false);
            wallLoc.clone().add(0, 1, 0).getBlock().setType(Material.COBBLESTONE_WALL, false);
            // Remove after 10s
            Location wl = wallLoc.clone();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (wl.getBlock().getType() == Material.COBBLESTONE_WALL)
                    wl.getBlock().setType(Material.AIR, false);
                if (wl.clone().add(0, 1, 0).getBlock().getType() == Material.COBBLESTONE_WALL)
                    wl.clone().add(0, 1, 0).getBlock().setType(Material.AIR, false);
            }, 200L);
        }
        caster.getWorld().playSound(front, Sound.BLOCK_STONE_PLACE, 2f, 0.8f);
        caster.sendMessage("§2🧱 §lMud Wall§r §7— barrier placed (10s duration)!");
    }

    private void grandHarmony(Player caster) {
        caster.getWorld().spawnParticle(Particle.DRAGON_BREATH, caster.getLocation(), 100, 2, 2, 2, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        double maxHp = caster.getAttribute(
                org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        caster.setHealth(maxHp); // Full heal
        caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1)); // 15s Strength II
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 1));
        caster.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
        caster.sendMessage("§d✦ §lGrand Harmony§r §7— FULL HEAL + Strength II + Resistance II!");
        caster.sendTitle("§d§l✦ GRAND HARMONY ✦", "§7All elements united!", 5, 60, 10);
    }

    // ── Support ───────────────────────────────────────────────────────────────

    private void cleanse(Player caster) {
        caster.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, caster.getLocation(), 60, 1, 1, 1, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
        caster.getActivePotionEffects().stream()
                .filter(e -> isNegative(e.getType()))
                .forEach(e -> caster.removePotionEffect(e.getType()));
        caster.sendMessage("§a✨ §lCleanse§r §7— all negative effects removed!");
    }

    private void blazeDash(Player caster) {
        caster.getWorld().spawnParticle(Particle.FLAME, caster.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1.5f);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 3)); // 4s Speed IV
        caster.sendMessage("§c🔥 §lBlaze Dash§r §7— Speed IV for 4 seconds!");
    }

    private void fortify(Player caster) {
        caster.getWorld().spawnParticle(Particle.CRIT, caster.getLocation(), 40, 1, 1, 1, 0.1);
        caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_SHIELD_BLOCK, 2f, 0.5f);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 1)); // 30s Absorption II
        caster.sendMessage("§6🛡 §lFortify§r §7— Absorption II for 30 seconds!");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Collection<Entity> nearbyMobs(Player caster) {
        return caster.getWorld().getNearbyEntities(
                caster.getLocation(), AOE_RADIUS, AOE_RADIUS, AOE_RADIUS,
                e -> e instanceof Monster && !e.equals(caster));
    }

    private boolean isNegative(PotionEffectType type) {
        return type == PotionEffectType.POISON
            || type == PotionEffectType.WITHER
            || type == PotionEffectType.WEAKNESS
            || type == PotionEffectType.SLOWNESS
            || type == PotionEffectType.BLINDNESS
            || type == PotionEffectType.HUNGER
            || type == PotionEffectType.NAUSEA
            || type == PotionEffectType.MINING_FATIGUE
            || type == PotionEffectType.LEVITATION
            || type == PotionEffectType.UNLUCK;
    }
}
