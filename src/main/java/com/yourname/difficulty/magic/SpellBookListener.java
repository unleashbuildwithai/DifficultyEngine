package com.yourname.difficulty.magic;

import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * SpellBookListener — Handles all Spell Tome / Spell Page interactions.
 *
 * ── Right-click Spell Tome ──────────────────────────────────────────────────
 *  Opens a read-only WRITTEN_BOOK view showing the player's 37 pages.
 *  Locked pages show "???" until discovered. Uses {@code Player#openBook()} —
 *  the book is never placed into the player's inventory.
 *
 * ── Right-click Spell Page ──────────────────────────────────────────────────
 *  Consumes the item and unlocks one random undiscovered page. Plays a
 *  page-turn + XP sound and sends a message naming the newly unlocked page.
 *
 * ── Hostile mob drops ───────────────────────────────────────────────────────
 *  4 % chance to drop a Spell Page when a player kills any hostile mob
 *  (anything implementing {@link Monster}).
 */
public class SpellBookListener implements Listener {

    /** Drop chance from hostile mob kills. */
    private static final double PAGE_DROP_CHANCE = 0.04;
    private static final Random RAND = new Random();

    private final SpellBookManager manager;

    public SpellBookListener(SpellBookManager manager) {
        this.manager = manager;
    }

    // ── Right-click handler ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        // Only fire once (main hand)
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();

        // ── Spell Tome → open book view ───────────────────────────────────────
        if (manager.isSpellTome(hand)) {
            event.setCancelled(true);
            int count = manager.getUnlockedCount(player.getUniqueId());
            player.openBook(manager.buildBookForPlayer(player.getUniqueId()));
            player.sendActionBar(
                "§5✦ §dArcane Tome §8— §7" + count + " §8/ §7"
                + SpellBookManager.TOTAL_PAGES + " §dpages unlocked");
            return;
        }

        // ── Spell Page → absorb and unlock a random page ──────────────────────
        if (manager.isSpellPage(hand)) {
            event.setCancelled(true);

            if (manager.allUnlocked(player.getUniqueId())) {
                player.sendActionBar(
                    "§5✦ §7Your tome is §d§lcomplete§7! All "
                    + SpellBookManager.TOTAL_PAGES + " pages unlocked.");
                player.playSound(player.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
                // Consume the page anyway (it's a duplicate)
                hand.setAmount(hand.getAmount() - 1);
                return;
            }

            int newPage = manager.discoverRandomPage(player.getUniqueId());
            hand.setAmount(hand.getAmount() - 1);

            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
            player.playSound(player.getLocation(),
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);

            int total = manager.getUnlockedCount(player.getUniqueId());
            player.sendMessage(
                "§5✦ §dSpell Page absorbed! §7Page §d" + newPage
                + " §7is now unlocked in your §dArcane Tome§7.");
            player.sendMessage(
                "§8  " + total + " / " + SpellBookManager.TOTAL_PAGES
                + " pages discovered.  §7Right-click your §dArcane Tome§7, or use §d/spellbook §7to read.");
            player.sendActionBar(
                "§5✦ §dPage " + newPage + " §7unlocked§8! §8("
                + total + "/" + SpellBookManager.TOTAL_PAGES + ")");
        }
    }

    // ── Hostile mob death: 4% chance to drop a Spell Page ────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player       killer = entity.getKiller();
        if (killer == null) return;

        // Only drops from monsters (Zombie, Skeleton, Blaze, Witch, etc.)
        if (!(entity instanceof Monster)) return;

        if (RAND.nextDouble() < PAGE_DROP_CHANCE) {
            event.getDrops().add(manager.buildSpellPageItem());
        }
    }
}
