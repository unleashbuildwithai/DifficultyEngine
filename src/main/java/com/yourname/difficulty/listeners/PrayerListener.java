package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillLevel;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * PrayerListener — Handles the PRAYER skill.
 *
 * ── Bone burying (Dirt only) — 2-click hole mechanic ───────────────────────
 *  1. Right-click a plain Dirt block with a Bone/Bone Meal in hand → digs a
 *     hole in the dirt (no item consumed yet, just the hole state).
 *  2. Right-click the SAME hole again with a Bone/Bone Meal in hand → buries
 *     the bone in the hole (consumed), awarding Prayer XP.
 *
 *  No shovel is required at all for this flow — using a shovel on Dirt is
 *  vanilla behaviour (creates a Dirt Path) and is left completely alone.
 *
 * ── Bone burying (other soil types) — instant, legacy behaviour ────────────
 *  Right-clicking a Bone on Grass / Farmland / Podzol / Coarse Dirt /
 *  Rooted Dirt / Mud still instantly consumes the bone and grants XP,
 *  exactly like before — no hole required.
 *
 *  XP per bone type:
 *    BONE      → 4 XP   (base — awarded on burial)
 *    BONE_MEAL → 2 XP   (base — awarded on burial)
 *
 * ── Protection chance ───────────────────────────────────────────────────────
 *  On any incoming damage (PvP + PvM), a prayer roll is performed.
 *  If it succeeds: the hit is nullified entirely.
 *  Chance = (prayerLevel/99)^1.5 × 30%   → Level 99 = 30% block chance.
 *  Fires at HIGHEST priority (before Defence reduction).
 */
public class PrayerListener implements Listener {

    private final SkillManager skillManager;
    private final Random       rng = new Random();

    /** Tracks in-progress Dirt holes: blockKey → hole state. */
    private final Map<String, HoleState> holes = new HashMap<>();

    private enum HoleState {
        /** Hole has been dug but no bone has been placed in it yet. */
        EMPTY
    }

    private static final Set<Material> BONE_ITEMS = Set.of(
        Material.BONE,
        Material.BONE_MEAL
    );

    /** Legacy instant-burial soil blocks (everything except plain Dirt). */
    private static final Set<Material> LEGACY_SOIL_BLOCKS = Set.of(
        Material.GRASS_BLOCK,
        Material.FARMLAND,
        Material.PODZOL,
        Material.COARSE_DIRT,
        Material.ROOTED_DIRT,
        Material.MUD
    );

    public PrayerListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    // ── Bone burying → Prayer XP ──────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        Block     block  = event.getClickedBlock();

        if (block == null) return;
        Material blockType = block.getType();

        // ── New 2-click hole mechanic (plain Dirt only, Bone-only — no shovel) ──
        if (blockType == Material.DIRT) {
            if (BONE_ITEMS.contains(hand.getType())) {
                handleBoneOnDirt(event, player, hand, block);
            }
            return;
        }

        // ── Legacy instant burial (other soil types) ───────────────────────
        if (!BONE_ITEMS.contains(hand.getType())) return;
        if (!LEGACY_SOIL_BLOCKS.contains(blockType)) return;

        event.setCancelled(true);

        long xp = boneXp(hand.getType());
        awardPrayerXp(player, xp);
        consumeOne(player, hand);

        spawnBuryParticles(block);
    }

    /**
     * Handles right-clicking Dirt with a Bone/Bone Meal in hand.
     * 1st click on a fresh spot  → digs a hole (no consumption).
     * 2nd click on the same spot → buries the bone (consumed) and grants XP.
     */
    private void handleBoneOnDirt(PlayerInteractEvent event, Player player, ItemStack hand, Block block) {
        String key = blockKey(block);
        HoleState state = holes.get(key);

        event.setCancelled(true);

        if (state == null) {
            // ── Dig a hole ──────────────────────────────────────────────────
            holes.put(key, HoleState.EMPTY);
            player.sendActionBar("§6⛏ §7You dig a hole in the dirt. §8(Right-click again with a bone to bury it)");
            spawnDigParticles(block);
            return;
        }

        // ── Bury the bone in the existing hole ─────────────────────────────
        long xp = boneXp(hand.getType());
        awardPrayerXp(player, xp);
        consumeOne(player, hand);

        holes.remove(key);
        player.sendActionBar("§f✦ §7You bury the bone in the hole.");
        spawnBuryParticles(block);
    }


    // ── Prayer protection roll ─────────────────────────────────────────────────

    /**
     * Fires at HIGHEST priority so prayer can cancel the hit before
     * Defence reduction (HIGH) runs — if prayer blocks, no reduction needed.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Skip instant-kill causes
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.VOID) return;
        if (cause == EntityDamageEvent.DamageCause.CUSTOM) return;

        int prayerLevel = skillManager.getLevel(player.getUniqueId(), SkillType.PRAYER);
        if (prayerLevel <= 0) return;

        double chance = SkillBonusManager.prayerProtectionChance(prayerLevel);
        if (rng.nextDouble() < chance) {
            event.setCancelled(true);

            // Prayer sparkle around player
            player.getWorld().spawnParticle(
                Particle.END_ROD,
                player.getLocation().add(0, 1, 0),
                20, 0.4, 0.6, 0.4, 0.05
            );
            player.sendActionBar("§f✦ §7Prayer protected you! §f✦  §8(Lv " + prayerLevel + ")");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String blockKey(Block block) {
        return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    private static void consumeOne(Player player, ItemStack hand) {
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
    }

    private static void spawnDigParticles(Block block) {
        block.getWorld().spawnParticle(
            Particle.BLOCK,
            block.getLocation().add(0.5, 1.0, 0.5),
            10, 0.25, 0.1, 0.25, 0.0,
            block.getBlockData()
        );
    }

    private static void spawnBuryParticles(Block block) {
        block.getWorld().spawnParticle(
            Particle.SMOKE,
            block.getLocation().add(0.5, 1.1, 0.5),
            6, 0.2, 0.2, 0.2, 0.02
        );
        block.getWorld().spawnParticle(
            Particle.END_ROD,
            block.getLocation().add(0.5, 1.0, 0.5),
            4, 0.3, 0.3, 0.3, 0.01
        );
    }

    private static long boneXp(Material mat) {
        return switch (mat) {
            case BONE      -> 4L;
            case BONE_MEAL -> 2L;
            default        -> 1L;
        };
    }


    private void awardPrayerXp(Player player, double amount) {
        int oldLevel = skillManager.getLevel(player.getUniqueId(), SkillType.PRAYER);
        int newLevel = skillManager.addXpFractional(player.getUniqueId(), SkillType.PRAYER, amount);

        String xpDisplay = (amount == Math.floor(amount))
                ? String.valueOf((long) amount)
                : String.valueOf(amount);
        player.sendActionBar("§f+" + xpDisplay + " §fPrayer XP §8(Lv " + newLevel + ")");

        if (newLevel > oldLevel) {
            double chance = SkillBonusManager.prayerProtectionChance(newLevel) * 100;
            player.sendMessage("");
            player.sendMessage("§6⬆ §e" + SkillType.PRAYER.colored()
                    + " §elevel up! §8(§f" + oldLevel + " §8→ §a" + newLevel + "§8)");
            player.sendMessage("  §7Rank: " + SkillLevel.getRank(newLevel));
            player.sendMessage(String.format("  §f🕊 §7Block chance: §a%.1f%%", chance));
            player.sendMessage("");
        }
    }
}
