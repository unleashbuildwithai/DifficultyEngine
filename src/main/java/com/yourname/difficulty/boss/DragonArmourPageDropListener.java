package com.yourname.difficulty.boss;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * DragonArmourPageDropListener — 1% chance to drop the Dragon Armour Page from:
 *   • Vanilla Wither (native boss fight)
 *   • Vanilla Ender Dragon
 *   • Infernal Blazefiend (Crimson boss — carrier is a Blaze, tracked via BossEffectListener)
 *   • Void Zurion (Void boss — carrier IS a Wither, so it's covered by the Wither check above)
 *   • Tempest Overlord (carrier is a Phantom, tracked via BossEffectListener)
 *
 * Random vanilla Blazes/Phantoms do NOT drop the page — only the tracked boss carriers
 * (checked via {@link BossEffectListener#isBoss}) qualify for those two entity types.
 */
public class DragonArmourPageDropListener implements Listener {

    private static final double DROP_CHANCE = 0.01;

    private final ItemFactory itemFactory;
    private final BossEffectListener bossEffectListener;
    private final Random random = new Random();

    public DragonArmourPageDropListener(ItemFactory itemFactory, BossEffectListener bossEffectListener) {
        this.itemFactory = itemFactory;
        this.bossEffectListener = bossEffectListener;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        var entity = event.getEntity();

        boolean qualifies =
                entity instanceof Wither
             || entity instanceof EnderDragon
             || (entity instanceof Blaze   && bossEffectListener.isBoss(entity))
             || (entity instanceof Phantom && bossEffectListener.isBoss(entity));

        if (!qualifies) return;
        if (random.nextDouble() >= DROP_CHANCE) return;

        var loc = entity.getLocation();
        loc.getWorld().dropItemNaturally(loc, itemFactory.buildDragonArmourPage());

        for (var p : loc.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 10000.0) {
                p.sendMessage("§6✦ §e§lDRAGON ARMOUR PAGE §7dropped! §8(Unlocks the Dragon Armour recipes)");
            }
        }
    }
}
