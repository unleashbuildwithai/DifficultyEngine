package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * RuneDropListener — Rune Dust drops from specific mobs when killed by a player.
 *
 * Rune Dust is the crafting material for Elemental Runes.
 * Harder / rarer mobs drop significantly more dust.
 *
 * ── Fire Rune Dust ────────────────────────────────────────────────────────────
 *   Blaze           40 %   2–5  dust
 *   MagmaCube (L)   30 %   2–4  dust
 *   MagmaCube (M)   20 %   1–2  dust
 *   MagmaCube (S)   10 %   1    dust
 *   Wither Skeleton 25 %   1–3  dust
 *   Piglin Brute    20 %   2–4  dust
 *   Ghast           35 %   3–6  dust
 *   Strider         10 %   1    dust
 *   Wither         100 %  20–30 dust
 *
 * ── Water Rune Dust ───────────────────────────────────────────────────────────
 *   Drowned         20 %   1–2  dust
 *   Guardian        40 %   2–5  dust
 *   Elder Guardian 100 %  15–25 dust
 *   Squid            8 %   1    dust
 *   Glow Squid      15 %   1–2  dust
 *   Axolotl         10 %   1    dust
 *
 * ── Earth Rune Dust ───────────────────────────────────────────────────────────
 *   Zombie          15 %   1    dust
 *   Husk            20 %   1–2  dust
 *   Zombie Villager 12 %   1    dust
 *   Spider          12 %   1    dust
 *   Cave Spider     18 %   1–2  dust
 *   Creeper         20 %   2–3  dust
 *   Pillager        25 %   1–2  dust
 *   Vindicator      25 %   2–4  dust
 *   Ravager         65 %   6–12 dust
 *   Warden         100 %  25–35 dust
 *
 * ── Air Rune Dust ─────────────────────────────────────────────────────────────
 *   Phantom         35 %   2–4  dust
 *   Vex             30 %   2–3  dust
 *   Evoker          70 %   5–10 dust
 *   Bat              5 %   1    dust
 *   Breeze          60 %   4–8  dust
 */
public class RuneDropListener implements Listener {

    private final ItemFactory itemFactory;
    private final Random      rand = new Random();

    public RuneDropListener(ItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  DROP EVENT
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity mob = event.getEntity();
        if (mob.getKiller() == null) return;

        DropResult result = getDropResult(mob);
        if (result == null) return;
        if (rand.nextDouble() >= result.chance) return;

        int count = result.min + (result.min == result.max ? 0 : rand.nextInt(result.max - result.min + 1));
        if (count <= 0) return;

        // Drop in 1–2 separate piles if count > 16 (prevents single towering stack)
        if (count > 16) {
            int half = count / 2;
            mob.getWorld().dropItemNaturally(mob.getLocation(), itemFactory.buildRuneDust(result.element, count - half));
            mob.getWorld().dropItemNaturally(mob.getLocation(), itemFactory.buildRuneDust(result.element, half));
        } else {
            mob.getWorld().dropItemNaturally(mob.getLocation(), itemFactory.buildRuneDust(result.element, count));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LOOT TABLE
    // ══════════════════════════════════════════════════════════════════════════

    private DropResult getDropResult(LivingEntity mob) {
        return switch (mob.getType()) {

            // ── FIRE ─────────────────────────────────────────────────────────
            case BLAZE           -> drop(MagicElement.FIRE, 0.40, 2,  5);
            case MAGMA_CUBE      -> magmaCubeDrop(mob);
            case WITHER_SKELETON -> drop(MagicElement.FIRE, 0.25, 1,  3);
            case PIGLIN_BRUTE    -> drop(MagicElement.FIRE, 0.20, 2,  4);
            case GHAST           -> drop(MagicElement.FIRE, 0.35, 3,  6);
            case STRIDER         -> drop(MagicElement.FIRE, 0.10, 1,  1);
            case WITHER          -> drop(MagicElement.FIRE, 1.00, 20, 30);

            // ── WATER ────────────────────────────────────────────────────────
            case DROWNED         -> drop(MagicElement.WATER, 0.20, 1,  2);
            case GUARDIAN        -> drop(MagicElement.WATER, 0.40, 2,  5);
            case ELDER_GUARDIAN  -> drop(MagicElement.WATER, 1.00, 15, 25);
            case SQUID           -> drop(MagicElement.WATER, 0.08, 1,  1);
            case GLOW_SQUID      -> drop(MagicElement.WATER, 0.15, 1,  2);
            case AXOLOTL         -> drop(MagicElement.WATER, 0.10, 1,  1);

            // ── EARTH ────────────────────────────────────────────────────────
            case ZOMBIE          -> drop(MagicElement.EARTH, 0.15, 1,  1);
            case HUSK            -> drop(MagicElement.EARTH, 0.20, 1,  2);
            case ZOMBIE_VILLAGER -> drop(MagicElement.EARTH, 0.12, 1,  1);
            case SPIDER          -> drop(MagicElement.EARTH, 0.12, 1,  1);
            case CAVE_SPIDER     -> drop(MagicElement.EARTH, 0.18, 1,  2);
            case CREEPER         -> drop(MagicElement.EARTH, 0.20, 2,  3);
            case PILLAGER        -> drop(MagicElement.EARTH, 0.25, 1,  2);
            case VINDICATOR      -> drop(MagicElement.EARTH, 0.25, 2,  4);
            case RAVAGER         -> drop(MagicElement.EARTH, 0.65, 6,  12);
            case WARDEN          -> drop(MagicElement.EARTH, 1.00, 25, 35);

            // ── AIR ──────────────────────────────────────────────────────────
            case PHANTOM         -> drop(MagicElement.AIR, 0.35, 2,  4);
            case VEX             -> drop(MagicElement.AIR, 0.30, 2,  3);
            case EVOKER          -> drop(MagicElement.AIR, 0.70, 5,  10);
            case BAT             -> drop(MagicElement.AIR, 0.05, 1,  1);
            case BREEZE          -> drop(MagicElement.AIR, 0.60, 4,  8);

            default              -> null;
        };
    }

    /** Magma Cubes scale by size: large (4) > medium (2) > small (1). */
    private DropResult magmaCubeDrop(LivingEntity mob) {
        int size = (mob instanceof Slime s) ? s.getSize() : 1;
        if (size >= 4) return drop(MagicElement.FIRE, 0.30, 2, 4); // large
        if (size >= 2) return drop(MagicElement.FIRE, 0.20, 1, 2); // medium
        return           drop(MagicElement.FIRE, 0.10, 1, 1);      // small
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private static DropResult drop(MagicElement el, double chance, int min, int max) {
        return new DropResult(el, chance, min, max);
    }

    private record DropResult(MagicElement element, double chance, int min, int max) {}
}
