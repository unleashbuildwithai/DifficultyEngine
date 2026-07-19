package com.yourname.difficulty.currency;

import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.DifficultyLevel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * GoldDropListener — Awards virtual gold on mob/boss kills.
 *
 * ── Mob gold drop chance: 20% ──────────────────────────────────────────────────
 *  Regular mob tiers (by max HP) — 20% drop chance:
 *   < 30 HP  → 1–10 gold
 *   30–100   → 10–100 gold
 *   100–200  → 100–500 gold
 *   > 200    → 300–1000 gold
 *
 * ── Difficulty multipliers ────────────────────────────────────────────────────
 *  Nightmare : 100%  (full value)
 *  Hard      :  75%
 *  Medium    :  50%
 *  Easy      :  25%
 *  Peaceful  :  20%  — and peaceful players do NOT drop coins on death
 *
 * ── Player death ─────────────────────────────────────────────────────────────
 *  Non-peaceful players drop their entire coin balance as a physical Coin Pile.
 *  Picking it up awards the stored gold to the picker-upper.
 */
public class GoldDropListener implements Listener {

    private static final double MOB_GOLD_CHANCE = 0.20; // 20% base chance for regular mobs

    private final GoldManager              goldManager;
    private final PlayerDifficultyManager  difficultyManager;
    private final JavaPlugin               plugin;
    private final Random                   rand = new Random();
    private final NamespacedKey            coinPileKey;

    /** Tracks damage dealt to each entity by each player. entityId → (playerUUID → totalDamage) */
    private final Map<UUID, Map<UUID, Double>> damageLog = new HashMap<>();

    public GoldDropListener(GoldManager goldManager,
                             PlayerDifficultyManager difficultyManager,
                             JavaPlugin plugin) {
        this.goldManager       = goldManager;
        this.difficultyManager = difficultyManager;
        this.plugin            = plugin;
        this.coinPileKey       = new NamespacedKey(plugin, "coin_pile");
    }

    // ── Track damage per player on all entities ───────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player damager = getDamager(event.getDamager());
        if (damager == null) return;

        UUID mobId = event.getEntity().getUniqueId();
        damageLog.computeIfAbsent(mobId, k -> new HashMap<>())
                 .merge(damager.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    // ── Award gold on mob death ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob instanceof Player) return; // players handled separately
        Map<UUID, Double> log = damageLog.remove(mob.getUniqueId());

        if (isBoss(mob)) {
            awardBossGold(mob, log);
        } else {
            // 20% base chance for regular mobs
            if (rand.nextDouble() < MOB_GOLD_CHANCE) {
                awardRegularGold(mob, log);
            }
        }
    }

    // ── Player death: drop entire coin balance (non-peaceful only) ────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        long balance = goldManager.getBalance(victim.getUniqueId());
        if (balance <= 0) return;

        DifficultyLevel diff = difficultyManager.getDifficulty(victim.getUniqueId());

        // Peaceful players keep their coins on death
        if (diff == DifficultyLevel.PEACEFUL) {
            victim.sendMessage("§a☮ §7Peaceful mode: your §e" + GoldManager.formatGold(balance)
                    + " gp §7is safe.");
            return;
        }

        // All other difficulties lose their coins on death
        goldManager.spendGold(victim.getUniqueId(), balance);
        ItemStack coinPile = buildCoinPile(balance);
        victim.getWorld().dropItemNaturally(victim.getLocation(), coinPile);
        victim.sendMessage("§6☠ §7You dropped §e" + GoldManager.formatGold(balance) + " gp §7on death!");
    }

    // ── Pick up a coin pile ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        if (item == null || !item.hasItemMeta()) return;
        long amount = item.getItemMeta().getPersistentDataContainer()
                         .getOrDefault(coinPileKey, PersistentDataType.LONG, 0L);
        if (amount <= 0) return;

        event.setCancelled(true);
        event.getItem().remove();
        Player p = event.getPlayer();
        goldManager.award(p, amount);
        p.sendMessage("§6✦ §7Picked up §e" + GoldManager.formatGold(amount) + " gp§7!");
    }

    // ── Boss gold (split by damage contribution + difficulty multiplier) ──────

    private void awardBossGold(LivingEntity mob, Map<UUID, Double> log) {
        long baseGold = 500L + rand.nextInt(2001); // 500–2500
        if (log == null || log.isEmpty()) return;

        double totalDmg = log.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<UUID, Double> entry : log.entrySet()) {
            UUID   uid   = entry.getKey();
            double share = entry.getValue() / totalDmg;
            long   award = (long) (baseGold * share);

            Player p = plugin.getServer().getPlayer(uid);
            if (p == null) continue;

            DifficultyLevel diff = difficultyManager.getDifficulty(uid);
            // Nightmare gets 2× from bosses (existing behaviour), then scale by difficulty
            if (diff == DifficultyLevel.NIGHTMARE) award *= 2;
            award = Math.max(1L, (long)(award * coinMultiplier(diff)));

            goldManager.award(p, award);
            p.sendMessage("§6[Boss Kill] §e" + GoldManager.formatGold(award)
                    + " gp §8(§7" + String.format("%.0f", share * 100) + "% dmg · "
                    + diff.getDisplayName() + "§8)");
        }
    }

    // ── Regular mob gold (difficulty-scaled) ──────────────────────────────────

    private void awardRegularGold(LivingEntity mob, Map<UUID, Double> log) {
        var hpAttr = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHp = hpAttr != null ? hpAttr.getValue() : 20.0;

        long baseGold;
        if (maxHp < 30)       baseGold = 1L  + rand.nextInt(10);   // 1–10
        else if (maxHp < 100) baseGold = 10L + rand.nextInt(91);   // 10–100
        else if (maxHp < 200) baseGold = 100L + rand.nextInt(401); // 100–500
        else                  baseGold = 300L + rand.nextInt(701); // 300–1000

        if (log == null || log.isEmpty()) return;
        double totalDmg = log.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<UUID, Double> entry : log.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null) continue;

            double share = entry.getValue() / totalDmg;
            DifficultyLevel diff = difficultyManager.getDifficulty(p.getUniqueId());
            long award = Math.max(1L, (long)(baseGold * share * coinMultiplier(diff)));
            goldManager.award(p, award);
        }
    }

    // ── Difficulty → coin multiplier ──────────────────────────────────────────

    /**
     * Returns the fraction of the base gold award a player receives based on
     * their chosen difficulty.
     *
     * Nightmare 100% · Hard 75% · Medium 50% · Easy 25% · Peaceful 20%
     */
    private double coinMultiplier(DifficultyLevel diff) {
        if (diff == null) return 0.50; // fallback
        return switch (diff) {
            case NIGHTMARE -> 1.00;
            case HARD      -> 0.75;
            case MEDIUM    -> 0.50;
            case EASY      -> 0.25;
            case PEACEFUL  -> 0.20;
        };
    }

    // ── Coin pile item builder ────────────────────────────────────────────────

    private ItemStack buildCoinPile(long amount) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6✦ Coin Pile §8(§e" + GoldManager.formatGold(amount) + " gp§8)");
            meta.setLore(List.of(
                "§7Pick up to collect §e" + GoldManager.formatGold(amount) + " gp§7!",
                "§8[DifficultyEngine — Coin Pile]"
            ));
            meta.getPersistentDataContainer().set(coinPileKey, PersistentDataType.LONG, amount);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBoss(LivingEntity e) {
        return e instanceof WitherSkeleton
            || e instanceof EnderDragon
            || e instanceof ElderGuardian
            || e instanceof Wither;
    }

    private Player getDamager(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
