package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * EarthBookListener — handles Earth Book / Earth Page interactions.
 *
 * ── Right-click Earth Page ──────────────────────────────────────────────────
 *  Consumes the page and unlocks its tier in the player's Earth Book. If the
 *  tier is already unlocked, the page is not consumed.
 *
 * ── Right-click Earth Book ─────────────────────────────────────────────────
 *  Opens the dynamic written-book view showing unlocked vs locked tiers.
 */
public class EarthBookListener implements Listener {

    private final ItemFactory     itemFactory;
    private final EarthBookManager earthBookManager;

    public EarthBookListener(ItemFactory itemFactory, EarthBookManager earthBookManager) {
        this.itemFactory      = itemFactory;
        this.earthBookManager = earthBookManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();

        // ── Earth Page → unlock tier ─────────────────────────────────────────
        EarthBlockTier tier = itemFactory.getEarthPageTier(hand);
        if (tier != null) {
            event.setCancelled(true);
            if (earthBookManager.hasTier(player.getUniqueId(), tier)) {
                player.sendActionBar("§c✗ §7You already know the " + tier.displayName + " §7page!");
                return;
            }
            consumeOne(player, hand);
            earthBookManager.unlockTier(player.getUniqueId(), tier);
            player.sendMessage("§2✦ §7Unlocked the " + tier.displayName + " §7page in your §2Earth Book§7!");
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.4f);
            return;
        }

        // ── Earth Book → open dynamic view ───────────────────────────────────
        if (itemFactory.isEarthBook(hand)) {
            event.setCancelled(true);
            player.openBook(earthBookManager.buildBookForPlayer(player.getUniqueId()));
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        }
    }

    /** Decrements the held stack by one (drops nothing — page is simply consumed). */
    private void consumeOne(Player player, ItemStack hand) {
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
    }
}
