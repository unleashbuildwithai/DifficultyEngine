package com.yourname.difficulty.skills;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

/**
 * SkillListener — Awards XP to skills based on player actions.
 *
 * ── Skill → Trigger mappings ──────────────────────────────────────────────
 *  MELEE       : Kill entity with sword/axe (in hand, melee range)
 *  RANGED      : Kill entity with bow/crossbow
 *  DEFENCE     : (Passive) XP awarded per entity kill when wearing shield
 *                 — shield blocking XP handled via EntityDamageByEntityEvent
 *  WOODCUTTING : Break log/wood blocks with an axe
 *  FISHING     : Successfully catch a fish
 *  FARMING     : Harvest fully-grown crops (per crop rarity)
 *
 * ── Mob tier XP formula ───────────────────────────────────────────────────
 *  skillXP = baseMobXP(maxHealth) × weaponMultiplier(material)
 *
 *  Tiers by max HP:
 *    1–20   HP → 5  base XP
 *    21–60  HP → 15 base XP
 *    61–120 HP → 35 base XP
 *    121–200 HP → 70 base XP
 *    200+   HP → 150 base XP
 *
 *  Weapon tier multipliers:
 *    Wood/Gold/Stone → 0.8×
 *    Iron            → 1.0×
 *    Diamond         → 1.5×
 *    Netherite       → 2.0×
 */
public class SkillListener implements Listener {

    private final JavaPlugin              plugin;
    private final SkillManager            skillManager;
    private final SkillCapeManager        capeManager;
    private final PlayerDifficultyManager difficultyManager;

    // ── Material sets ─────────────────────────────────────────────────────────

    private static final Set<Material> SWORDS = Set.of(
        Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD,
        Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
    );

    private static final Set<Material> AXES = Set.of(
        Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
        Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    );

    private static final Set<Material> BOWS = Set.of(
        Material.BOW, Material.CROSSBOW
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

    // Crops with their rarity-based XP values
    // Common = 2, Uncommon = 4, Rare = 6, Epic = 10
    private static long cropXp(Material mat) {
        return switch (mat) {
            // Common crops
            case WHEAT, POTATOES, CARROTS -> 2L;
            // Uncommon crops
            case BEETROOTS, MELON, PUMPKIN -> 4L;
            // Rare crops
            case COCOA, NETHER_WART, SWEET_BERRY_BUSH -> 6L;
            // Epic crops
            case CAVE_VINES, CAVE_VINES_PLANT, BAMBOO, SUGAR_CANE -> 10L;
            default -> 0L;
        };
    }

    public SkillListener(JavaPlugin plugin, SkillManager skillManager,
                         SkillCapeManager capeManager, PlayerDifficultyManager difficultyManager) {
        this.plugin             = plugin;
        this.skillManager       = skillManager;
        this.capeManager        = capeManager;
        this.difficultyManager  = difficultyManager;
    }

    // ── Entity Kill (Melee / Ranged) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Entity dead = event.getEntity();
        if (!(dead instanceof LivingEntity le)) return;

        double maxHp     = le.getMaxHealth();
        long   baseXp    = mobTierXp(maxHp);
        if (baseXp == 0) return;

        ItemStack hand    = killer.getInventory().getItemInMainHand();
        Material  mat     = hand.getType();
        UUID      uuid    = killer.getUniqueId();

        // Apply difficulty multiplier — higher difficulty = more XP per kill
        double diffMult = difficultyKillMultiplier(
                difficultyManager.getDifficulty(uuid));

        if (SWORDS.contains(mat) || AXES.contains(mat)) {
            long xp = Math.round(baseXp * weaponMultiplier(mat) * diffMult);
            awardXp(killer, uuid, SkillType.MELEE, xp);
        } else if (BOWS.contains(mat)) {
            long xp = Math.round(baseXp * weaponMultiplier(mat) * diffMult);
            awardXp(killer, uuid, SkillType.RANGED, xp);
        }
        // If killed with bare hands or other tools, no skill XP
    }

    // ── Block Break (Woodcutting / Farming) ───────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player   player = event.getPlayer();
        UUID     uuid   = player.getUniqueId();
        Material block  = event.getBlock().getType();
        ItemStack hand  = player.getInventory().getItemInMainHand();

        // ── Woodcutting ──────────────────────────────────────────────────────
        if (LOGS.contains(block) && AXES.contains(hand.getType())) {
            long xp = Math.round(5L * weaponMultiplier(hand.getType()));
            awardXp(player, uuid, SkillType.WOODCUTTING, xp);
            return;
        }

        // ── Farming ──────────────────────────────────────────────────────────
        long farmXp = cropXp(block);
        if (farmXp > 0) {
            // Only give XP if fully grown (block is the plant itself, not soil)
            // Bukkit's block data check for maturity
            if (isFullyGrown(block, event.getBlock())) {
                awardXp(player, uuid, SkillType.FARMING, farmXp);
            }
        }
    }

    // ── Fishing ───────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (event.getCaught() == null) return;

        Player player = event.getPlayer();
        // Base fishing XP per catch; bonus for rare fish types
        long xp = 10L;
        if (event.getCaught().hasMetadata("rare_fish")) xp = 30L; // future hook

        awardXp(player, player.getUniqueId(), SkillType.FISHING, xp);
    }

    // ── Defence XP via shield blocking ───────────────────────────────────────
    // Registered via EntityDamageByEntityEvent in the main DifficultyEngine listener.
    // We expose a direct method so DifficultyEngine can call it.

    public void awardDefenceXp(Player blocker, long amount) {
        awardXp(blocker, blocker.getUniqueId(), SkillType.DEFENCE, amount);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Awards XP, shows gain message, handles level-up and cape award. */
    private void awardXp(Player player, UUID uuid, SkillType skill, long amount) {
        if (amount <= 0) return;

        int oldLevel = skillManager.getLevel(uuid, skill);
        int newLevel = skillManager.addXp(uuid, skill, amount);

        // XP gain action bar message
        player.sendActionBar("§7+" + amount + " §" + colorChar(skill)
                + skill.getDisplayName() + " XP §8(Lv " + newLevel + ")");

        // Level-up
        if (newLevel > oldLevel) {
            player.sendMessage("");
            player.sendMessage("§6⬆ §e" + skill.colored()
                    + " §elevel up! §8(§f" + oldLevel + " §8→ §a" + newLevel + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(newLevel));
            player.sendMessage("");

            // Reapply Defence HP bonus whenever Defence levels up
            if (skill == SkillType.DEFENCE) {
                SkillBonusManager.applyDefenceHpBonus(player, newLevel);
                double extraHearts = SkillBonusManager.defenceExtraHp(newLevel) / 2.0;
                player.sendMessage("  §b⛨ §7Defence HP bonus: §a+" + extraHearts + " §7hearts");
            }

            // Cape at 99
            if (newLevel >= SkillLevel.MAX_LEVEL) {
                capeManager.checkAndAward(player, skill, skillManager);
            }
        }
    }

    // ── XP tables ─────────────────────────────────────────────────────────────

    private static long mobTierXp(double maxHealth) {
        if (maxHealth <= 0)   return 0L;
        if (maxHealth <= 20)  return 5L;
        if (maxHealth <= 60)  return 15L;
        if (maxHealth <= 120) return 35L;
        if (maxHealth <= 200) return 70L;
        return 150L;
    }

    /**
     * XP bonus multiplier for kills based on the player's chosen difficulty.
     * Higher difficulty = tougher mobs = more XP rewarded.
     *
     *  PEACEFUL  → 0.5×  (mobs are nerfed, so half XP)
     *  EASY      → 1.0×  (baseline)
     *  MEDIUM    → 1.25×
     *  HARD      → 1.5×
     *  NIGHTMARE → 2.0×  (mobs are 50% stronger — double XP)
     */
    private static double difficultyKillMultiplier(DifficultyLevel level) {
        return switch (level) {
            case PEACEFUL  -> 0.5;
            case EASY      -> 1.0;
            case MEDIUM    -> 1.25;
            case HARD      -> 1.5;
            case NIGHTMARE -> 2.0;
        };
    }

    private static double weaponMultiplier(Material mat) {
        String name = mat.name();
        if (name.startsWith("NETHERITE_"))  return 2.0;
        if (name.startsWith("DIAMOND_"))    return 1.5;
        if (name.startsWith("IRON_"))       return 1.0;
        // Wood, Gold, Stone → 0.8
        return 0.8;
    }

    private static char colorChar(SkillType skill) {
        String code = skill.getColorCode();
        return code.length() >= 2 ? code.charAt(1) : 'f';
    }

    /**
     * Returns true if a crop block is fully grown.
     * Handles WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, COCOA.
     */
    private static boolean isFullyGrown(Material mat, org.bukkit.block.Block block) {
        try {
            org.bukkit.block.data.Ageable ageable =
                (org.bukkit.block.data.Ageable) block.getBlockData();
            return ageable.getAge() >= ageable.getMaximumAge();
        } catch (ClassCastException e) {
            // Not an Ageable block (e.g. PUMPKIN, MELON, SUGAR_CANE) — always harvestable
            return true;
        }
    }
}
