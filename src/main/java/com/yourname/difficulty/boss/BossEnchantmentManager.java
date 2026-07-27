package com.yourname.difficulty.boss;

import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BossEnchantmentManager — Handles the castEnchantment() buff system that was
 * previously embedded directly in {@link BossEffectListener}.
 *
 * <p>Extracted during the 400-line-file cleanup pass so BossEffectListener can
 * focus purely on boss registration / damage events. Behaviour is unchanged
 * from the original implementation.
 */
public class BossEnchantmentManager {

    /** Minimum Magic level required to use castEnchantment(). */
    private static final int ENCHANT_MIN_LEVEL = 60;

    private final EffectRegistry registry;
    private final SkillManager   skillManager;

    /** Active castEnchantment buffs: UUID → expiry timestamp (ms) */
    private final Map<UUID, Long> enchantmentBuffs = new HashMap<>();

    public BossEnchantmentManager(EffectRegistry registry, SkillManager skillManager) {
        this.registry     = registry;
        this.skillManager = skillManager;
    }

    /**
     * Applies a named enchantment buff to the target player.
     *
     * <p>The caster must have Magic level ≥ 60. The buff is stored in
     * {@code enchantmentBuffs} with an expiry timestamp.
     *
     * @param caster     the player casting the enchantment
     * @param target     the player receiving the buff
     * @param buffType   a string identifier for the buff (e.g. "STRENGTH_AURA")
     * @param durationMs duration in milliseconds
     * @return true if the enchantment was applied, false if level requirement not met
     */
    public boolean castEnchantment(Player caster, Player target,
                                   String buffType, long durationMs) {
        int magicLevel = skillManager.getLevel(caster.getUniqueId(), SkillType.MAGIC);
        if (magicLevel < ENCHANT_MIN_LEVEL) {
            caster.sendMessage("§c✗ §7You need §bMagic level " + ENCHANT_MIN_LEVEL
                    + " §7to cast enchantments. (Current: §b" + magicLevel + "§7)");
            return false;
        }

        long expiry = System.currentTimeMillis() + durationMs;
        enchantmentBuffs.put(target.getUniqueId(), expiry);
        registry.apply(target.getUniqueId(), EffectType.ENCHANTED, durationMs);

        // Apply visual potion effect
        switch (buffType.toUpperCase()) {
            case "STRENGTH_AURA" ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        (int)(durationMs / 50), 0, false, true, true));
            case "SHIELD_AURA" ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                        (int)(durationMs / 50), 0, false, true, true));
            case "SPEED_AURA" ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        (int)(durationMs / 50), 1, false, true, true));
            case "REGEN_AURA" ->
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        (int)(durationMs / 50), 0, false, true, true));
            default -> {}
        }

        target.sendMessage("§5✦ §d" + caster.getName()
                + " §7cast §5" + buffType + " §7on you!");
        caster.sendMessage("§5✦ §7Enchantment §5" + buffType
                + " §7applied to §d" + target.getName() + "§7!");
        return true;
    }

    /**
     * Returns true if the given player has an active castEnchantment buff.
     */
    public boolean hasEnchantmentBuff(UUID playerUuid) {
        Long expiry = enchantmentBuffs.get(playerUuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            enchantmentBuffs.remove(playerUuid);
            return false;
        }
        return true;
    }
}
