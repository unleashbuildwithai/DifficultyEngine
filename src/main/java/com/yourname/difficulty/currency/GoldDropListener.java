package com.yourname.difficulty.currency;

import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.DifficultyLevel;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * GoldDropListener — Awards virtual gold on mob/boss kills.
 *
 * Regular mob tiers (by max HP):
 *   < 30 HP  → 1–10 gold
 *   30–100   → 10–100 gold
 *   100–200  → 100–500 gold
 *   > 200    → 300–1000 gold
 *
 * Bosses (Wither/EnderDragon/ElderGuardian) → 500–2500 gold split among
 * damage contributors proportionally. Nightmare players get 2× their share.
 */
public class GoldDropListener implements Listener {

    private final GoldManager              goldManager;
    private final PlayerDifficultyManager  difficultyManager;
    private final JavaPlugin               plugin;
    private final Random                   rand = new Random();

    /**
     * Tracks damage dealt to each entity by each player.
     * entityId → (playerUUID → totalDamage)
     */
    private final Map<UUID, Map<UUID, Double>> damageLog = new HashMap<>();

    public GoldDropListener(GoldManager goldManager,
                            PlayerDifficultyManager difficultyManager,
                            JavaPlugin plugin) {
        this.goldManager       = goldManager;
        this.difficultyManager = difficultyManager;
        this.plugin            = plugin;
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

    // ── Award gold on death ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Map<UUID, Double> log = damageLog.remove(mob.getUniqueId());

        if (isBoss(mob)) {
            awardBossGold(mob, log);
        } else {
            awardRegularGold(mob, log);
        }
    }

    // ── Boss gold (split by damage contribution) ──────────────────────────────

    private void awardBossGold(LivingEntity mob, Map<UUID, Double> log) {
        long baseGold = 500L + rand.nextInt(2001); // 500–2500
        if (log == null || log.isEmpty()) return;

        double totalDmg = log.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<UUID, Double> entry : log.entrySet()) {
            UUID   uid    = entry.getKey();
            double share  = entry.getValue() / totalDmg;
            long   award  = (long) (baseGold * share);

            // Nightmare players get 2× their share from bosses
            Player p = plugin.getServer().getPlayer(uid);
            if (p != null) {
                DifficultyLevel diff = difficultyManager.getDifficulty(uid);
                if (diff == DifficultyLevel.NIGHTMARE) award *= 2;
                goldManager.award(p, award);
                p.sendMessage("§6[Boss Kill] §e" + GoldManager.formatGold(award)
                        + " gp §8(§7" + String.format("%.0f", share * 100) + "% damage§8)");
            }
        }
    }

    // ── Regular mob gold ──────────────────────────────────────────────────────

    private void awardRegularGold(LivingEntity mob, Map<UUID, Double> log) {
        var hpAttr = mob.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
        double maxHp = hpAttr != null ? hpAttr.getValue() : 20.0;

        long gold;
        if (maxHp < 30)        gold = 1L  + rand.nextInt(10);   // 1–10
        else if (maxHp < 100)  gold = 10L + rand.nextInt(91);   // 10–100
        else if (maxHp < 200)  gold = 100L + rand.nextInt(401); // 100–500
        else                   gold = 300L + rand.nextInt(701); // 300–1000

        if (log == null || log.isEmpty()) return;
        double totalDmg = log.values().stream().mapToDouble(Double::doubleValue).sum();

        for (Map.Entry<UUID, Double> entry : log.entrySet()) {
            Player p = plugin.getServer().getPlayer(entry.getKey());
            if (p == null) continue;
            double share = entry.getValue() / totalDmg;
            long award = Math.max(1L, (long)(gold * share));
            goldManager.award(p, award);
        }
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
