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
 * GoldDropListener â€” Awards virtual gold on mob/boss kills.
 *
 * â”€â”€ Mob gold drop chance: 20% â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Regular mob tiers (by max HP) â€” 20% drop chance:
 *   < 30 HP  â†’ 1â€“10 gold
 *   30â€“100   â†’ 10â€“100 gold
 *   100â€“200  â†’ 100â€“500 gold
 *   > 200    â†’ 300â€“1000 gold
 *
 * â”€â”€ Difficulty multipliers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *  Nightmare : 100%  (full value)
 *  Hard      :  75%
 *  Medium    :  50%
 *  Easy      :  25%
 *  Peaceful  :  20%  â€” and peaceful players do NOT drop coins on death
 *
 * â”€â”€ Player death â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

    /** Tracks damage dealt to each entity by each player. entityId â†’ (playerUUID â†’ totalDamage) */
    private final Map<UUID, Map<UUID, Double>> damageLog = new HashMap<>();

    public GoldDropListener(GoldManager goldManager,
                             PlayerDifficultyManager difficultyManager,
                             JavaPlugin plugin) {
        this.goldManager       = goldManager;
        this.difficultyManager = difficultyManager;
        this.plugin            = plugin;
        this.coinPileKey       = new NamespacedKey(plugin, "coin_pile");
    }

    // â”€â”€ Track damage per player on all entities â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        Player damager = getDamager(event.getDamager());
        if (damager == null) return;

        UUID mobId = event.getEntity().getUniqueId();
        damageLog.computeIfAbsent(mobId, k -> new HashMap<>())
                 .merge(damager.getUniqueId(), event.getFinalDamage(), Double::sum);
    }

    // â”€â”€ Award gold on mob death â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Player death: drop entire coin balance (non-peaceful only) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        long balance = goldManager.getBalance(victim.getUniqueId());
        if (balance <= 0) return;

        DifficultyLevel diff = difficultyManager.getDifficulty(victim.getUniqueId());

        // Peaceful players keep their coins on death
        if (diff == DifficultyLevel.PEACEFUL) {
            victim.sendMessage("Â§aâ˜® Â§7Peaceful mode: your Â§e" + GoldManager.formatGold(balance)
                    + " gp Â§7is safe.");
            return;
        }

        // All other difficulties lose their coins on death
        goldManager.spendGold(victim.getUniqueId(), balance);
        ItemStack coinPile = buildCoinPile(balance);
        victim.getWorld().dropItemNaturally(victim.getLocation(), coinPile);
        victim.sendMessage("Â§6â˜  Â§7You dropped Â§e" + GoldManager.formatGold(balance) + " gp Â§7on death!");
    }

    // â”€â”€ Pick up a coin pile â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
        p.sendMessage("Â§6âœ¦ Â§7Picked up Â§e" + GoldManager.formatGold(amount) + " gpÂ§7!");
    }

    // â”€â”€ Boss gold (split by damage contribution + difficulty multiplier) â”€â”€â”€â”€â”€â”€

    private void awardBossGold(LivingEntity mob, Map<UUID, Double> log) {
        long baseGold = 500L + rand.nextInt(2001); // 500â€“2500
        if (log == null || log.isEmpty()) return;

        double totalDmg = log.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<UUID, Double> entry : log.entrySet()) {
            UUID   uid   = entry.getKey();
            double share = entry.getValue() / totalDmg;
            long   award = (long) (baseGold * share);

            Player p = plugin.getServer().getPlayer(uid);
            if (p == null) continue;

            DifficultyLevel diff = difficultyManager.getDifficulty(uid);
            // Nightmare gets 2Ã— from bosses (existing behaviour), then scale by difficulty
            if (diff == DifficultyLevel.NIGHTMARE) award *= 2;
            award = Math.max(1L, (long)(award * coinMultiplier(diff)));

            goldManager.award(p, award);
            p.sendMessage("Â§6[Boss Kill] Â§e" + GoldManager.formatGold(award)
                    + " gp Â§8(Â§7" + String.format("%.0f", share * 100) + "% dmg Â· "
                    + diff.getDisplayName() + "Â§8)");
        }
    }

    // â”€â”€ Regular mob gold (difficulty-scaled) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void awardRegularGold(LivingEntity mob, Map<UUID, Double> log) {
        var hpAttr = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHp = hpAttr != null ? hpAttr.getValue() : 20.0;

        long baseGold;
        if (maxHp < 30)       baseGold = 1L  + rand.nextInt(10);   // 1â€“10
        else if (maxHp < 100) baseGold = 10L + rand.nextInt(91);   // 10â€“100
        else if (maxHp < 200) baseGold = 100L + rand.nextInt(401); // 100â€“500
        else                  baseGold = 300L + rand.nextInt(701); // 300â€“1000

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

    // â”€â”€ Difficulty â†’ coin multiplier â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Returns the fraction of the base gold award a player receives based on
     * their chosen difficulty.
     *
     * Nightmare 100% Â· Hard 75% Â· Medium 50% Â· Easy 25% Â· Peaceful 20%
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

    // â”€â”€ Coin pile item builder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private ItemStack buildCoinPile(long amount) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Â§6âœ¦ Coin Pile Â§8(Â§e" + GoldManager.formatGold(amount) + " gpÂ§8)");
            meta.setLore(List.of(
                "Â§7Pick up to collect Â§e" + GoldManager.formatGold(amount) + " gpÂ§7!",
                "Â§8[DifficultyEngine â€” Coin Pile]"
            ));
            meta.getPersistentDataContainer().set(coinPileKey, PersistentDataType.LONG, amount);
            item.setItemMeta(meta);
        }
        return item;
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
