package com.yourname.difficulty.listeners;

import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LevelProtectionListener — Prevents high-level players from attacking
 * low-level players who are significantly weaker.
 *
 * ── Rules ───────────────────────────────────────────────────────────────────
 *  • Only applies when the VICTIM's combat level is ≤ 50.
 *  • If the ATTACKER's combat level is 5+ higher than the victim's:
 *      – Attack is cancelled.
 *      – Green VILLAGER_HAPPY particles flash around the victim (shield visual).
 *      – Red CRIT particles flash around the attacker (blocked visual).
 *      – Attacker action bar: "⛦ This player is protected by level difference!"
 *      – Attacker chat message (rate-limited to once per 5 s):
 *        their combat level vs victim's combat level.
 *
 * ── Combat level formula (OSRS-adapted) ────────────────────────────────────
 *  Calls {@link SkillBonusManager#getCombatLevel(int, int, int, int, int)}
 *  which uses: 0.25×(Def+floor(Pray/2)) + max(0.65×Melee, 0.4875×Ranged, 0.4875×Magic)
 *  Result capped at 99.
 *
 * ── Passive aura scan ───────────────────────────────────────────────────────
 *  Every 2 seconds, each online player scans their 8-block radius.
 *  If a nearby player is protected from you (≤50 combat, 5+ gap), gentle
 *  green END_ROD sparkles ring their feet — a visual indicator of protection.
 *
 * ── Bypass ──────────────────────────────────────────────────────────────────
 *  Players with difficultyengine.protection.bypass are exempt from both
 *  the protection check AND being protected (they always can attack and
 *  can always be attacked).
 */
public class LevelProtectionListener implements Listener {

    private static final int    PROTECTION_THRESHOLD    = 50;   // victim must be ≤ this
    private static final int    LEVEL_GAP               = 5;    // attacker must be this much higher
    private static final long   MESSAGE_COOLDOWN_MS      = 5_000L;
    private static final String BYPASS_PERMISSION       = "difficultyengine.protection.bypass";

    private final SkillManager skillManager;
    /** Last time (ms) attacker UUID showed the level-gap chat message to victim UUID. */
    private final Map<UUID, Long> messageCooldowns = new HashMap<>();

    public LevelProtectionListener(SkillManager skillManager, JavaPlugin plugin) {
        this.skillManager = skillManager;
        startAuraScan(plugin);
    }

    // ── Combat damage check ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerAttackPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity()  instanceof Player victim))   return;

        // Bypass: either player has the bypass permission
        if (attacker.hasPermission(BYPASS_PERMISSION)) return;
        if (victim.hasPermission(BYPASS_PERMISSION))   return;

        int attackerCombat = getCombatLevel(attacker);
        int victimCombat   = getCombatLevel(victim);

        // Only protect victims at combat ≤ 50
        if (victimCombat > PROTECTION_THRESHOLD) return;
        // Only block if attacker is 5+ levels above victim
        if (attackerCombat <= victimCombat + LEVEL_GAP) return;

        // ── Block the attack ─────────────────────────────────────────────────
        event.setCancelled(true);

        // Green sparkles around victim (shield barrier visual)
        victim.getWorld().spawnParticle(
            Particle.HAPPY_VILLAGER,
            victim.getLocation().add(0, 1, 0),
            20, 0.5, 0.7, 0.5, 0.05
        );
        // Red crit burst on attacker (blocked visual)
        attacker.getWorld().spawnParticle(
            Particle.CRIT,
            attacker.getLocation().add(0, 1, 0),
            15, 0.4, 0.4, 0.4, 0.1
        );

        // Action bar to attacker
        attacker.sendActionBar(
            "§c⛦ §7This player is §aprotected §7by level difference!"
        );

        // Rate-limited chat message
        UUID cooldownKey = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - messageCooldowns.getOrDefault(cooldownKey, 0L) >= MESSAGE_COOLDOWN_MS) {
            messageCooldowns.put(cooldownKey, now);
            attacker.sendMessage("§8" + "─".repeat(40));
            attacker.sendMessage("  §c⛦ §7Attack blocked — level protection active.");
            attacker.sendMessage("  §7Your combat level:   §e" + attackerCombat);
            attacker.sendMessage("  §7Their combat level:  §a" + victimCombat
                    + " §8(protected ≤ " + PROTECTION_THRESHOLD + ")");
            attacker.sendMessage("  §8Players are protected when combat ≤ 50");
            attacker.sendMessage("  §8and you are 5+ levels above them.");
            attacker.sendMessage("§8" + "─".repeat(40));
        }
    }

    // ── Passive green aura scan ───────────────────────────────────────────────

    private void startAuraScan(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    if (player.hasPermission(BYPASS_PERMISSION)) continue;
                    int playerCombat = getCombatLevel(player);

                    for (Player nearby : player.getWorld().getPlayers()) {
                        if (nearby.equals(player)) continue;
                        if (nearby.getLocation().distanceSquared(player.getLocation()) > 64) continue; // 8² = 64
                        if (nearby.hasPermission(BYPASS_PERMISSION)) continue;

                        int nearbyCombat = getCombatLevel(nearby);
                        // Does nearby need protection from this player?
                        if (nearbyCombat <= PROTECTION_THRESHOLD
                                && playerCombat > nearbyCombat + LEVEL_GAP) {
                            // Gentle green aura ring at their feet
                            nearby.getWorld().spawnParticle(
                                Particle.END_ROD,
                                nearby.getLocation().add(0, 0.1, 0),
                                6, 0.4, 0.05, 0.4, 0.0
                            );
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // every 2 seconds
    }

    // ── Combat level helper ───────────────────────────────────────────────────

    private int getCombatLevel(Player player) {
        UUID id = player.getUniqueId();
        int melee   = skillManager.getLevel(id, SkillType.MELEE);
        int ranged  = skillManager.getLevel(id, SkillType.RANGED);
        int defence = skillManager.getLevel(id, SkillType.DEFENCE);
        int prayer  = skillManager.getLevel(id, SkillType.PRAYER);
        int magic   = skillManager.getLevel(id, SkillType.MAGIC);
        return SkillBonusManager.getCombatLevel(melee, ranged, defence, prayer, magic);
    }
}
