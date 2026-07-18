package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * RuneDropListener — Elemental runes drop from specific mob types.
 * Uses EntityType switch to avoid pattern-matching compilation issues.
 *
 * Fire Rune  : Blaze, MagmaCube, Wither (~15%)
 * Water Rune : Drowned, Guardian, ElderGuardian (~12%)
 * Earth Rune : Zombie, Husk, Spider, CaveSpider (~10%)
 * Air Rune   : Phantom, Vex, Ghast, Bat (~15%)
 */
public class RuneDropListener implements Listener {

    private final ItemFactory itemFactory;
    private final Random rand = new Random();

    public RuneDropListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.getKiller() == null) return;

        MagicElement element = getRuneElement(mob.getType());
        if (element == null) return;

        if (rand.nextDouble() >= getDropChance(mob.getType())) return;

        // Drop Rune Dust (crafting material), not finished runes.
        // 4× Rune Dust → 8 Runes at a crafting table (material matches recipe).
        int count = 1 + rand.nextInt(4); // 1–4 dust per drop
        ItemStack dust = itemFactory.buildRuneDust(element, count);
        mob.getWorld().dropItemNaturally(mob.getLocation(), dust);
    }

    // ── Entity type → rune element ────────────────────────────────────────────

    private MagicElement getRuneElement(EntityType type) {
        return switch (type) {
            case BLAZE, MAGMA_CUBE, WITHER           -> MagicElement.FIRE;
            case DROWNED, GUARDIAN, ELDER_GUARDIAN   -> MagicElement.WATER;
            case ZOMBIE, HUSK, SPIDER, CAVE_SPIDER   -> MagicElement.EARTH;
            case PHANTOM, VEX, GHAST, BAT            -> MagicElement.AIR;
            default                                  -> null;
        };
    }

    private double getDropChance(EntityType type) {
        return switch (type) {
            case BLAZE, WITHER, PHANTOM, GHAST       -> 0.15;
            case MAGMA_CUBE, VEX, BAT                -> 0.12;
            case DROWNED, GUARDIAN, ELDER_GUARDIAN   -> 0.12;
            case ZOMBIE, HUSK, SPIDER, CAVE_SPIDER   -> 0.10;
            default                                  -> 0.05;
        };
    }
}
