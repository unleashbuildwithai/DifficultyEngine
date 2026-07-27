package com.yourname.difficulty.magic;

import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * ElementalProcManager — Passive per-element proc effects on ANY basic hit.
 *
 * ── REWORK NOTE ────────────────────────────────────────────────────────────
 * The old multi-hit combo-chain system (WET→MUDDY→STATUE, CHILLED→FROZEN→
 * SHATTERED, SCORCHED→BLAZING→INFERNO, earth trap/suffocate, etc.) has been
 * REMOVED ENTIRELY per user direction. This is now the ONLY special-effect
 * system for elemental magic: each element has ONE independent status proc
 * that can trigger on ANY basic hit — no chains, no "if target already has
 * X, do Y" branching between elements.
 *
 * ── The 4 procs ────────────────────────────────────────────────────────────
 *  🔥 FIRE  → BURN    — fire DoT + brief slow.
 *             Escape: target channelling Downpour (Water Lv99 rain ritual)
 *             extinguishes the burn immediately (see MagicStaffListener).
 *  💧 WATER → WET     — slow debuff. Boosted proc chance while an active
 *             Downpour is raining on the target's world.
 *  🌿 EARTH → MUDDY   — heavy slow debuff. Escape: target breaks a block
 *             with a pickaxe while Muddy → cancels it early.
 *  💨 AIR   → CHILLED/FROZEN — stun that scales with the CASTER's Magic
 *             level (0.5s → 5s max) on players. On MONSTERS only, Frozen
 *             also rolls a separate flat 0.02% instant-kill chance (never
 *             on players). Escape/speed-up: if the frozen/chilled TARGET is
 *             holding a Fire Staff, the remaining timer melts away faster.
 *
 * ── Gating (unchanged from before) ─────────────────────────────────────────
 *  1. Player must have unlocked the matching Arcane Tome proc page.
 *  2. Player's Magic skill level must meet the element's minimum.
 *  3. Real dice roll: 15% base, 30% if favorited via the Favorites GUI
 *     (plus any situational bonus chance passed in by the caller, e.g. the
 *     Downpour boost for Water).
 */
public class ElementalProcManager {

    private static final Random RAND = new Random();

    /** Base proc chance (not favorited). */
    public static final double BASE_CHANCE = 0.15;
    /** Boosted proc chance when the matching tag is favorited. */
    public static final double FAVORITED_CHANCE = 0.30;
    /** Extra proc chance for WET while an active Downpour is raining. */
    public static final double DOWNPOUR_WET_BONUS = 0.20;

    /** Arcane Tome page index (0-based) required to unlock each element's proc. */
    public static final int FIRE_PAGE  = 41;
    public static final int WATER_PAGE = 42;
    public static final int EARTH_PAGE = 43;
    public static final int AIR_PAGE   = 44;

    /** Minimum Magic level required for each element's proc. */
    public static final int FIRE_LEVEL_REQ  = 20;
    public static final int WATER_LEVEL_REQ = 20;
    public static final int EARTH_LEVEL_REQ = 35;
    public static final int AIR_LEVEL_REQ   = 50;

    /** Flat instant-kill chance rolled on MONSTERS ONLY when Frozen is applied. Never rolled on players. */
    public static final double FROZEN_MONSTER_INSTAKILL_CHANCE = 0.0002; // 0.02%

    /** Min/Max Frozen/Chilled stun duration (ticks), scaled by the CASTER's Magic level. */
    private static final int FROZEN_MIN_TICKS = 10;   // 0.5s
    private static final int FROZEN_MAX_TICKS = 100;  // 5s

    // ── Metadata keys for the 3 surviving status effects ──────────────────────
    public static final String META_BURN    = "proc_burn";
    public static final String META_WET     = "proc_wet";
    public static final String META_MUDDY   = "proc_muddy";
    public static final String META_FROZEN  = "proc_frozen";

    private final JavaPlugin            plugin;
    private final SpellBookManager      spellBookManager;
    private final ComboFavoritesManager favoritesManager;
    private final SkillManager          skillManager;

    /** Entity UUID → active "thaw speed-up" tick task while Frozen (checks for held Fire Staff). */
    private final Map<UUID, BukkitTask> frozenTasks = new HashMap<>();

    /** Optional reference to check staff element on held items — wired by MagicStaffListener's ItemFactory. */
    private com.yourname.difficulty.items.ItemFactory itemFactory = null;

    public ElementalProcManager(JavaPlugin plugin, SpellBookManager spellBookManager,
                                ComboFavoritesManager favoritesManager, SkillManager skillManager) {
        this.plugin           = plugin;
        this.spellBookManager = spellBookManager;
        this.favoritesManager = favoritesManager;
        this.skillManager     = skillManager;
    }

    /** Wires in the ItemFactory so held-staff checks (fire staff thaw, etc.) work. */
    public void setItemFactory(com.yourname.difficulty.items.ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    /**
     * Attempts to roll and apply the passive proc for the given element on a
     * basic hit. Safe to call unconditionally from every handle*Hit method —
     * internally checks page-unlock + level-gate + dice roll before doing
     * anything, so callers do not need to pre-check eligibility themselves.
     *
     * @return true if the proc fired and its effect was applied.
     */
    public boolean rollAndApply(Player shooter, LivingEntity target, MagicElement element) {
        return rollAndApply(shooter, target, element, 0.0);
    }

    /**
     * Overload allowing the caller to pass a situational bonus chance (e.g.
     * the Downpour bonus for Water procs). The bonus is added on top of the
     * base/favorited chance before rolling.
     */
    public boolean rollAndApply(Player shooter, LivingEntity target, MagicElement element, double bonusChance) {
        if (shooter == null || target == null || element == null) return false;
        if (spellBookManager == null || skillManager == null) return false;

        int    pageIndex;
        int    levelReq;
        String procTag;
        switch (element) {
            case FIRE  -> { pageIndex = FIRE_PAGE;  levelReq = FIRE_LEVEL_REQ;  procTag = ComboFavoritesManager.FIRE_PROC;  }
            case WATER -> { pageIndex = WATER_PAGE; levelReq = WATER_LEVEL_REQ; procTag = ComboFavoritesManager.WATER_PROC; }
            case EARTH -> { pageIndex = EARTH_PAGE; levelReq = EARTH_LEVEL_REQ; procTag = ComboFavoritesManager.EARTH_PROC; }
            case AIR   -> { pageIndex = AIR_PAGE;   levelReq = AIR_LEVEL_REQ;   procTag = ComboFavoritesManager.AIR_PROC;   }
            default    -> { return false; }
        }

        // ── Gate 1: matching proc page must be unlocked ────────────────────
        if (!spellBookManager.getUnlockedPages(shooter.getUniqueId()).contains(pageIndex)) return false;

        // ── Gate 2: Magic level requirement ────────────────────────────────
        int magicLevel = skillManager.getLevel(shooter.getUniqueId(), SkillType.MAGIC);
        if (magicLevel < levelReq) return false;

        // ── Gate 3: real dice roll (15% base / 30% favorited + any bonus) ──
        boolean favorited = favoritesManager != null
                && favoritesManager.isFavorited(shooter.getUniqueId(), procTag);
        double chance = (favorited ? FAVORITED_CHANCE : BASE_CHANCE) + Math.max(0.0, bonusChance);
        if (RAND.nextDouble() >= chance) return false;

        applyEffect(shooter, target, element, magicLevel);
        return true;
    }

    private void applyEffect(Player shooter, LivingEntity target, MagicElement element, int casterMagicLevel) {
        switch (element) {
            case FIRE  -> applyBurn(shooter, target);
            case WATER -> applyWetProc(shooter, target);
            case EARTH -> applyMuddyProc(shooter, target);
            case AIR   -> applyFrozenProc(shooter, target, casterMagicLevel);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FIRE → BURN
    // ══════════════════════════════════════════════════════════════════════

    /** Applies Burn: fire DoT + brief slow. Escaped early by channelling Downpour (see MagicStaffListener). */
    public void applyBurn(Player shooter, LivingEntity target) {
        target.setMetadata(META_BURN, new FixedMetadataValue(plugin, true));
        target.setFireTicks(Math.max(target.getFireTicks(), 100));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0, false, true, true));
        target.getWorld().spawnParticle(Particle.FLAME,
            target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.08);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.4f);
        if (shooter != null) shooter.sendActionBar("§c✦ §7Fire Proc! §8Burn triggered!");
        if (target instanceof Player tp) tp.sendActionBar("§c✦ §7You're §cBurning§7! §8Channel Downpour to extinguish!");
    }

    /** Returns true if the target currently has the Burn status (used by the Downpour extinguish hook). */
    public boolean isBurning(LivingEntity target) {
        return target.hasMetadata(META_BURN);
    }

    /** Extinguishes Burn early — called when the target begins channelling Downpour. */
    public void extinguishBurn(LivingEntity target) {
        if (!target.hasMetadata(META_BURN)) return;
        target.removeMetadata(META_BURN, plugin);
        target.setFireTicks(0);
        target.getWorld().spawnParticle(Particle.CLOUD,
            target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.2f);
        if (target instanceof Player tp) tp.sendActionBar("§b✦ §7Downpour extinguished your Burn!");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WATER → WET
    // ══════════════════════════════════════════════════════════════════════

    /** Applies Wet: a simple slow debuff. */
    public void applyWetProc(Player shooter, LivingEntity target) {
        target.setMetadata(META_WET, new FixedMetadataValue(plugin,
            System.currentTimeMillis() + 4000L));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true, true));
        target.getWorld().spawnParticle(Particle.SPLASH,
            target.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.1);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_BUCKET_EMPTY, 0.8f, 1.4f);
        if (shooter != null) shooter.sendActionBar("§b✦ §7Water Proc! §8Wet triggered!");
        if (target instanceof Player tp) tp.sendActionBar("§b✦ §7You're §bWet§7!");
    }

    public boolean isWetProc(LivingEntity target) {
        if (!target.hasMetadata(META_WET)) return false;
        long ex = (long) target.getMetadata(META_WET).get(0).value();
        if (System.currentTimeMillis() > ex) { target.removeMetadata(META_WET, plugin); return false; }
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  EARTH → MUDDY
    // ══════════════════════════════════════════════════════════════════════

    /** Applies Muddy: heavy slow debuff. Escaped early by mining a block with a pickaxe. */
    public void applyMuddyProc(Player shooter, LivingEntity target) {
        target.setMetadata(META_MUDDY, new FixedMetadataValue(plugin, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 3, false, true, true));
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 0.5, 0), 40, 0.4, 0.4, 0.4, Material.MUD.createBlockData());
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_MUD_BREAK, 0.8f, 0.9f);
        if (shooter != null) shooter.sendActionBar("§2✦ §7Earth Proc! §8Muddy triggered!");
        if (target instanceof Player tp) tp.sendActionBar("§2✦ §7You're §2Muddy§7! §8Mine a block with a pickaxe to break free!");
    }

    public boolean isMuddyProc(LivingEntity target) {
        return target.hasMetadata(META_MUDDY);
    }

    /** Breaks the target free of Muddy early — called when they mine a block with a pickaxe. */
    public void clearMuddy(LivingEntity target) {
        if (!target.hasMetadata(META_MUDDY)) return;
        target.removeMetadata(META_MUDDY, plugin);
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.getWorld().spawnParticle(Particle.BLOCK,
            target.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, Material.MUD.createBlockData());
        if (target instanceof Player tp) tp.sendActionBar("§2✦ §7You dug yourself free of the mud!");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  AIR → CHILLED / FROZEN
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Applies Chilled/Frozen: a stun scaling with the CASTER's Magic level
     * (0.5s → 5s). On monsters only, additionally rolls a flat 0.02%
     * instant-kill chance. Never instant-kills players. If the target is
     * holding a Fire Staff, the freeze duration melts away faster (checked
     * on a repeating tick task).
     */
    public void applyFrozenProc(Player shooter, LivingEntity target, int casterMagicLevel) {
        // Monster-only rare instakill roll (checked BEFORE applying the stun —
        // if it hits, the target dies immediately and no stun is needed).
        boolean isMonster = !(target instanceof Player);
        if (isMonster && RAND.nextDouble() < FROZEN_MONSTER_INSTAKILL_CHANCE) {
            target.getWorld().spawnParticle(Particle.SNOWFLAKE,
                target.getLocation().add(0, 1, 0), 80, 0.8, 0.8, 0.8, 0.3);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);
            target.setHealth(0);
            if (shooter != null) shooter.sendActionBar("§b§l☠ FROZEN SOLID! §7A rare freeze instantly killed it!");
            return;
        }

        double lvlFrac = Math.max(0.0, Math.min(1.0, casterMagicLevel / 99.0));
        int ticks = FROZEN_MIN_TICKS + (int) Math.round((FROZEN_MAX_TICKS - FROZEN_MIN_TICKS) * lvlFrac);

        UUID uuid = target.getUniqueId();
        long expiry = System.currentTimeMillis() + (long) ticks * 50L;
        target.setMetadata(META_FROZEN, new FixedMetadataValue(plugin, expiry));
        target.setFreezeTicks(140);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,       ticks, 255, false, true, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 255, false, true, true));
        target.getWorld().spawnParticle(Particle.SNOWFLAKE,
            target.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
        if (shooter != null) shooter.sendActionBar("§f✦ §7Air Proc! §8Chilled/Frozen triggered! §7(" + String.format("%.1f", ticks / 20.0) + "s)");
        if (target instanceof Player tp) {
            tp.sendActionBar("§b✦ §7You're §bFrozen§7! §8Holding a Fire Staff melts it faster!");
        }

        // Cancel any prior thaw-watch task for this entity
        BukkitTask old = frozenTasks.remove(uuid);
        if (old != null) old.cancel();

        // Watches for a held Fire Staff each tick to speed up the thaw.
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!target.isValid() || !target.hasMetadata(META_FROZEN)) {
                BukkitTask t = frozenTasks.remove(uuid);
                if (t != null) t.cancel();
                return;
            }
            long ex = (long) target.getMetadata(META_FROZEN).get(0).value();
            boolean holdingFireStaff = false;
            if (target instanceof Player tp2 && itemFactory != null) {
                ItemStack hand = tp2.getInventory().getItemInMainHand();
                holdingFireStaff = itemFactory.getStaffElement(hand) == MagicElement.FIRE;
            }
            if (holdingFireStaff) {
                // Melt 3x faster: pull the expiry closer by an extra 100ms per 100ms real-time.
                ex -= 200L;
            }
            if (System.currentTimeMillis() > ex) {
                removeFrozenProc(target);
                BukkitTask t = frozenTasks.remove(uuid);
                if (t != null) t.cancel();
            } else {
                target.setMetadata(META_FROZEN, new FixedMetadataValue(plugin, ex));
            }
        }, 2L, 2L);
        frozenTasks.put(uuid, task);
    }

    public boolean isFrozenProc(LivingEntity target) {
        if (!target.hasMetadata(META_FROZEN)) return false;
        long ex = (long) target.getMetadata(META_FROZEN).get(0).value();
        if (System.currentTimeMillis() > ex) { removeFrozenProc(target); return false; }
        return true;
    }

    private void removeFrozenProc(LivingEntity target) {
        target.removeMetadata(META_FROZEN, plugin);
        target.setFreezeTicks(0);
        target.removePotionEffect(PotionEffectType.SLOWNESS);
        target.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        if (target instanceof Player tp) tp.sendActionBar("§b✦ §7You thawed out.");
    }
}
