package com.yourname.difficulty.skills;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * SkillCombatListener — Applies passive skill stat bonuses to combat & gathering.
 *
 * ── MELEE (EntityDamageByEntityEvent — sword/axe attacker) ────────────────
 *   + Flat damage bonus:  meleeLevel × 0.02
 *   + Crit chance:        meleeLevel × 0.3%  → 1.5× damage + particles
 *
 * ── RANGED (EntityDamageByEntityEvent — Arrow projectile) ─────────────────
 *   + Flat damage bonus:  rangedLevel × 0.015
 *   + Tipped/poison arrows: potion effect duration scaled by ranged level
 *     (Level 99 = 2× base duration)
 *
 * ── DEFENCE (EntityDamageEvent — player is victim) ────────────────────────
 *   + Incoming damage reduced by:  defenceLevel × 0.2%  (Lv99 ≈ 19.8%)
 *
 * ── FARMING double-drop (BlockBreakEvent — fully-grown crop) ──────────────
 *   + Double-drop chance: (level/99)^1.5 × 50%  (very rare at Lv1, 50% at Lv99)
 *
 * ── WOODCUTTING double-drop (BlockBreakEvent — log + axe) ─────────────────
 *   + Double-drop chance: (level/99)^1.5 × 33%  (33% at Lv99)
 *
 * ── DEFENCE HP re-application (PlayerJoinEvent) ───────────────────────────
 *   + Reapplies the max-HP AttributeModifier so it persists across restarts.
 */
public class SkillCombatListener implements Listener {

    private final SkillManager  skillManager;
    private final JavaPlugin    plugin;
    private final Random        rng = new Random();

    // ── Weapon material sets (for melee detection) ────────────────────────────
    private static final Set<Material> SWORDS = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );
    private static final Set<Material> AXES = Set.of(
        Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );
    private static final Set<Material> LOGS = Set.of(
        Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
        Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
        Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.BAMBOO_BLOCK,
        Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
        Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
        Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
        Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
        Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
        Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG
    );

    public SkillCombatListener(SkillManager skillManager, JavaPlugin plugin) {
        this.skillManager = skillManager;
        this.plugin       = plugin;
    }

    // ── MELEE + RANGED damage bonuses ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        // ── Case 1: Player melee attack ───────────────────────────────────────
        if (event.getDamager() instanceof Player attacker) {
            Material hand = attacker.getInventory().getItemInMainHand().getType();
            if (SWORDS.contains(hand) || AXES.contains(hand)) {
                applyMeleeBonus(attacker, event);
            } else {
                // Apply lightning buff even for punches or magic staff casts
                applyLightningBuff(attacker, event);
            }
        }

        // ── Case 2: Arrow shot by player ──────────────────────────────────────
        if (event.getDamager() instanceof AbstractArrow arrow) {
            if (arrow.getShooter() instanceof Player shooter) {
                applyRangedBonus(shooter, arrow, event);
            }
        }
    }
    
    private void applyLightningBuff(Player attacker, EntityDamageByEntityEvent event) {
        if (hasLightningDamageBuff(attacker)) {
            double newDmg = event.getDamage();
            if (event.getEntity() instanceof Player) {
                newDmg *= 1.3;
            } else {
                newDmg *= 1.5;
            }
            event.setDamage(newDmg);
        }
    }

    private void applyMeleeBonus(Player attacker, EntityDamageByEntityEvent event) {
        int level = skillManager.getLevel(attacker.getUniqueId(), SkillType.MELEE);

        double bonus     = SkillBonusManager.meleeDamageBonus(level);
        double critChance = SkillBonusManager.meleeCritChance(level);
        boolean isCrit   = rng.nextDouble() < critChance;

        double newDmg = event.getDamage() + bonus;
        if (isCrit) {
            newDmg *= SkillBonusManager.CRIT_MULTIPLIER;
            // Crit particles at victim location
            if (event.getEntity() instanceof LivingEntity victim) {
                Location loc = victim.getLocation().add(0, victim.getHeight() / 2, 0);
                loc.getWorld().spawnParticle(Particle.CRIT, loc, 12, 0.3, 0.3, 0.3, 0.1);
                attacker.sendActionBar("§c✦ CRITICAL HIT! §6×" + SkillBonusManager.CRIT_MULTIPLIER);
            }
        }
        
        event.setDamage(newDmg);
        applyLightningBuff(attacker, event);
    }

    private void applyRangedBonus(Player shooter, AbstractArrow arrow,
                                   EntityDamageByEntityEvent event) {
        int level = skillManager.getLevel(shooter.getUniqueId(), SkillType.RANGED);

        double bonus = SkillBonusManager.rangedDamageBonus(level);
        double newDmg = event.getDamage() + bonus;
        
        event.setDamage(newDmg);
        applyLightningBuff(shooter, event);

        // Scale tipped arrow potion effects
        if (arrow instanceof Arrow tipped && !tipped.getCustomEffects().isEmpty()
                && event.getEntity() instanceof LivingEntity victim) {
            double scale = SkillBonusManager.arrowEffectScale(level);
            // Schedule 1-tick later so vanilla applies its effects first,
            // then we override durations with the scaled versions.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                for (PotionEffect originalEffect : tipped.getCustomEffects()) {
                    int scaledDuration = (int) Math.round(originalEffect.getDuration() * scale);
                    victim.addPotionEffect(new PotionEffect(
                        originalEffect.getType(),
                        scaledDuration,
                        originalEffect.getAmplifier(),
                        originalEffect.isAmbient(),
                        originalEffect.hasParticles(),
                        originalEffect.hasIcon()
                    ), true); // true = override existing
                }
            }, 1L);
        }
    }

    private boolean hasLightningDamageBuff(Player player) {
        if (!player.hasMetadata("lightning_damage_buff")) return false;
        long expiry = player.getMetadata("lightning_damage_buff").get(0).asLong();
        if (System.currentTimeMillis() > expiry) {
            player.removeMetadata("lightning_damage_buff", plugin);
            return false;
        }
        return true;
    }

    // ── DEFENCE damage reduction ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        // Skip void/fall (let those be full) — only reduce hit/projectile damage
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID) return;
        if (cause == EntityDamageEvent.DamageCause.CUSTOM) return;

        int level = skillManager.getLevel(victim.getUniqueId(), SkillType.DEFENCE);
        if (level <= 0) return;

        double reduction = SkillBonusManager.defenceDamageReduction(level);
        double newDmg    = event.getDamage() * (1.0 - reduction);
        event.setDamage(Math.max(0, newDmg));
    }

    // ── FARMING / WOODCUTTING double drops ────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material block = event.getBlock().getType();
        ItemStack hand  = player.getInventory().getItemInMainHand();

        // ── Woodcutting double-drop ───────────────────────────────────────────
        if (LOGS.contains(block) && AXES.contains(hand.getType())) {
            int wcLevel = skillManager.getLevel(player.getUniqueId(), SkillType.WOODCUTTING);
            if (rng.nextDouble() < SkillBonusManager.woodcuttingDoubleDropChance(wcLevel)) {
                // Drop an extra log
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation(),
                    new ItemStack(block, 1)
                );
                player.sendActionBar("§a⛏ §7Double log drop! §8(Woodcutting Lv " + wcLevel + ")");
            }
            return;
        }

        // ── Farming double-drop (fully-grown crops only) ──────────────────────
        if (isFarmingCrop(block)) {
            if (!isFullyGrown(event.getBlock())) return;
            int farmLevel = skillManager.getLevel(player.getUniqueId(), SkillType.FARMING);
            if (rng.nextDouble() < SkillBonusManager.farmingDoubleDropChance(farmLevel)) {
                // Drop an extra copy of the block's natural drops
                for (ItemStack drop : event.getBlock().getDrops(hand)) {
                    event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation(), drop
                    );
                }
                player.sendActionBar("§2✿ §7Double crop drop! §8(Farming Lv " + farmLevel + ")");
            }
        }
    }

    // ── DEFENCE HP re-application on join ────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int defLevel  = skillManager.getLevel(player.getUniqueId(), SkillType.DEFENCE);
        // Schedule 1 tick so the player fully loads before modifying attributes
        plugin.getServer().getScheduler().runTaskLater(plugin, () ->
            SkillBonusManager.applyDefenceHpBonus(player, defLevel), 1L);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isFarmingCrop(Material mat) {
        return switch (mat) {
            case WHEAT, POTATOES, CARROTS, BEETROOTS, MELON, PUMPKIN,
                 COCOA, NETHER_WART, SWEET_BERRY_BUSH,
                 CAVE_VINES, CAVE_VINES_PLANT, BAMBOO, SUGAR_CANE -> true;
            default -> false;
        };
    }

    private static boolean isFullyGrown(org.bukkit.block.Block block) {
        try {
            org.bukkit.block.data.Ageable ageable =
                (org.bukkit.block.data.Ageable) block.getBlockData();
            return ageable.getAge() >= ageable.getMaximumAge();
        } catch (ClassCastException e) {
            return true; // Not ageable = always harvestable (melon, pumpkin, etc.)
        }
    }
}
