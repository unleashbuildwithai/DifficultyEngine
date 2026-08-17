package net.yourserver.coreengine.listeners;

import net.yourserver.coreengine.CoreEngine;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Tags players as "in combat" so teleport features can refuse to move them. */
public class CombatListener implements Listener {

    private final CoreEngine plugin;

    public CombatListener(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim) {
            plugin.getTeleportRequestManager().markCombat(victim.getUniqueId());
        }
        if (event.getDamager() instanceof Player attacker) {
            plugin.getTeleportRequestManager().markCombat(attacker.getUniqueId());
        }
    }
}
