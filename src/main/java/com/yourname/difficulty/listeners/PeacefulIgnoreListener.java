package com.yourname.difficulty.listeners;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * PeacefulIgnoreListener — makes ALL hostile mobs ignore PEACEFUL players.
 * This includes phantoms, which normally target players with insomnia.
 */
public class PeacefulIgnoreListener implements Listener {

    private final PlayerDifficultyManager manager;

    public PeacefulIgnoreListener(PlayerDifficultyManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        // Cover both regular hostile mobs and phantoms explicitly.
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Phantom)) return;
        if (!(event.getTarget() instanceof Player target)) return;

        if (manager.getDifficulty(target.getUniqueId()) != DifficultyLevel.PEACEFUL) return;

        // Peaceful player — the mob must not target them.
        event.setCancelled(true);
        event.setTarget(null);
    }
}
