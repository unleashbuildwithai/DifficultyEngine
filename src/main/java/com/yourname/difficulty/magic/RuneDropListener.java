package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * RuneDropListener — All magic-related mob drops.
 *
 * ── Rune Dust ─────────────────────────────────────────────────────────────────
 *  Specific mobs drop element-matched rune dust (see loot table below).
 *
 * ── Spell Combo Book (ENCHANTED_BOOK + PDC) ───────────────────────────────────
 *  8% chance when the killing blow was dealt by a magic staff projectile.
 *  Detected via META_STAFF_HIT metadata set by MagicStaffListener.
 *  Carrying the book → combo hints show in action bar when casting spells.
 *
 * ── Lore Books (WRITTEN_BOOK) ─────────────────────────────────────────────────
 *  Novice Magic Primer    : 10% from zombies, skeletons, spiders
 *  Mage's Primer          :  5% from pillagers, husks, cave spiders
 *  Elemental Theory       :  3% from blazes, evokers, elder guardians
 *  Hidden Arts            :  1% from wardens, ravagers, wither skeletons
 *
 * ── Ancient Kill Tome ─────────────────────────────────────────────────────────
 *  Boss-event only — handled by BossEventListener.
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

        // ── Rune Dust drops ───────────────────────────────────────────────────
        DropResult result = getRuneDropResult(mob);
        if (result != null && rand.nextDouble() < result.chance) {
            int count = result.min + (result.min == result.max ? 0 : rand.nextInt(result.max - result.min + 1));
            if (count > 0) {
                if (count > 16) {
                    int half = count / 2;
                    mob.getWorld().dropItemNaturally(mob.getLocation(),
                        itemFactory.buildRuneDust(result.element, count - half));
                    mob.getWorld().dropItemNaturally(mob.getLocation(),
                        itemFactory.buildRuneDust(result.element, half));
                } else {
                    mob.getWorld().dropItemNaturally(mob.getLocation(),
                        itemFactory.buildRuneDust(result.element, count));
                }
            }
        }

        // ── Spell Combo Book: 8% if killed by a magic staff projectile ────────
        if (mob.hasMetadata(MagicStaffListener.META_STAFF_HIT) && rand.nextDouble() < 0.08) {
            mob.getWorld().dropItemNaturally(mob.getLocation(), itemFactory.buildSpellComboBook());
        }

        // ── Lore Book drops — only from kills by a magic staff ────────────────
        if (mob.hasMetadata(MagicStaffListener.META_STAFF_HIT)) {
            ItemStack book = getLoreBookDrop(mob);
            if (book != null) {
                mob.getWorld().dropItemNaturally(mob.getLocation(), book);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LORE BOOK LOOT TABLE
    // ══════════════════════════════════════════════════════════════════════════

    private ItemStack getLoreBookDrop(LivingEntity mob) {
        return switch (mob.getType()) {

            // ── Novice Magic Primer (10%) — common mobs ───────────────────────
            case ZOMBIE, SKELETON, SPIDER -> {
                if (rand.nextDouble() < 0.10) yield itemFactory.buildNoviceMagicPrimer();
                yield null;
            }

            // ── Mage's Primer (5%) — medium mobs ─────────────────────────────
            case PILLAGER, HUSK, CAVE_SPIDER, VINDICATOR, DROWNED -> {
                if (rand.nextDouble() < 0.05) yield itemFactory.buildMagesPrimerBook();
                yield null;
            }

            // ── Elemental Theory (3%) — harder mobs ──────────────────────────
            case BLAZE, EVOKER, ELDER_GUARDIAN, PHANTOM, WITCH -> {
                if (rand.nextDouble() < 0.03) yield itemFactory.buildElementalTheoryBook();
                yield null;
            }

            // ── Hidden Arts (1%) — boss/rare mobs ────────────────────────────
            case WARDEN, RAVAGER, WITHER_SKELETON, BREEZE -> {
                if (rand.nextDouble() < 0.01) yield itemFactory.buildHiddenArtsBook();
                yield null;
            }

            default -> null;
        };
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  RUNE DUST LOOT TABLE
    // ══════════════════════════════════════════════════════════════════════════

    private DropResult getRuneDropResult(LivingEntity mob) {
        return switch (mob.getType()) {

            // ── FIRE ──────────────────────────────────────────────────────────
            case BLAZE           -> drop(MagicElement.FIRE, 0.40, 2,  5);
            case MAGMA_CUBE      -> magmaCubeDrop(mob);
            case WITHER_SKELETON -> drop(MagicElement.FIRE, 0.25, 1,  3);
            case PIGLIN_BRUTE    -> drop(MagicElement.FIRE, 0.20, 2,  4);
            case GHAST           -> drop(MagicElement.FIRE, 0.35, 3,  6);
            case STRIDER         -> drop(MagicElement.FIRE, 0.10, 1,  1);
            case WITHER          -> drop(MagicElement.FIRE, 1.00, 20, 30);

            // ── WATER ─────────────────────────────────────────────────────────
            case DROWNED         -> drop(MagicElement.WATER, 0.20, 1,  2);
            case GUARDIAN        -> drop(MagicElement.WATER, 0.40, 2,  5);
            case ELDER_GUARDIAN  -> drop(MagicElement.WATER, 1.00, 15, 25);
            case SQUID           -> drop(MagicElement.WATER, 0.08, 1,  1);
            case GLOW_SQUID      -> drop(MagicElement.WATER, 0.15, 1,  2);
            case AXOLOTL         -> drop(MagicElement.WATER, 0.10, 1,  1);

            // ── EARTH ─────────────────────────────────────────────────────────
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

            // ── AIR ───────────────────────────────────────────────────────────
            case PHANTOM         -> drop(MagicElement.AIR, 0.35, 2,  4);
            case VEX             -> drop(MagicElement.AIR, 0.30, 2,  3);
            case EVOKER          -> drop(MagicElement.AIR, 0.70, 5,  10);
            case BAT             -> drop(MagicElement.AIR, 0.05, 1,  1);
            case BREEZE          -> drop(MagicElement.AIR, 0.60, 4,  8);

            default              -> null;
        };
    }

    private DropResult magmaCubeDrop(LivingEntity mob) {
        int size = (mob instanceof Slime s) ? s.getSize() : 1;
        if (size >= 4) return drop(MagicElement.FIRE, 0.30, 2, 4);
        if (size >= 2) return drop(MagicElement.FIRE, 0.20, 1, 2);
        return           drop(MagicElement.FIRE, 0.10, 1, 1);
    }

    private static DropResult drop(MagicElement el, double chance, int min, int max) {
        return new DropResult(el, chance, min, max);
    }

    private record DropResult(MagicElement element, double chance, int min, int max) {}
}
