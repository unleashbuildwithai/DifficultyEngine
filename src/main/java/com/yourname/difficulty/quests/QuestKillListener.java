package com.yourname.difficulty.quests;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * QuestKillListener — increments quest progress whenever a player kills a mob.
 * Checks all QuestTypes each kill.  Runs at MONITOR so the mob is confirmed dead.
 */
public class QuestKillListener implements Listener {

    private final QuestManager questManager;

    public QuestKillListener(QuestManager questManager) {
        this.questManager = questManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        Player killer = mob.getKiller();
        if (killer == null) return;

        EntityType type = mob.getType();

        for (QuestType q : QuestType.values()) {
            // Permanent quest already done — skip
            if (questManager.isPermaDone(killer.getUniqueId(), q)) continue;

            boolean matches;
            if (q.targetType == null) {
                // MONSTER_HUNTER — any hostile mob counts
                matches = mob instanceof Monster || mob instanceof Ghast
                       || mob instanceof Slime  || mob instanceof ElderGuardian
                       || mob instanceof Guardian;
            } else if (q == QuestType.FIRST_BOSS) {
                // Any boss counts toward FIRST_BOSS
                matches = mob instanceof Wither || mob instanceof EnderDragon
                       || mob instanceof ElderGuardian;
            } else if (q == QuestType.SPIDER_SLAYER) {
                // Both Spider and CaveSpider count
                matches = (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER);
            } else {
                matches = (type == q.targetType);
            }

            if (matches) {
                questManager.incrementProgress(killer, q);
            }
        }
    }
}
