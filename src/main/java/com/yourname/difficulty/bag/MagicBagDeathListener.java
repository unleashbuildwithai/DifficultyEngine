package com.yourname.difficulty.bag;

import com.yourname.difficulty.items.ItemFactory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicBagDeathListener — Prevents the Magic Bag ITEM from dropping on death,
 * and handles spilling/protecting the bag's virtual CONTENTS.
 *
 * ── Behaviour ─────────────────────────────────────────────────────────────
 *  • On death   : removes the Magic Bag item from the drop list and holds it.
 *                 Also handles the bag's virtual 144-slot contents:
 *                   - Books/Pages (lore books, spell pages, earth pages,
 *                     support pages, water/earth book, spell combo book,
 *                     ancient kill tome) are ALWAYS kept safe in the bag.
 *                   - If the player carries a Bag Ward in their main
 *                     inventory, it is consumed and NOTHING else drops either.
 *                   - Otherwise, everything else (runes, potions, staves,
 *                     gear, misc) is dropped on the ground at the death
 *                     location, and removed from the bag's virtual storage.
 *  • On respawn : restores the bag to hotbar slot 8 (top-right).
 *
 *  The bag is NOT locked to any slot — players can move it freely after
 *  it is restored.  Right-clicking it in hand always opens the GUI.
 */
public class MagicBagDeathListener implements Listener {

    private final MagicBagManager bagManager;
    private final ItemFactory     itemFactory;
    private final JavaPlugin      plugin;

    /**
     * Temporarily holds each player's Magic Bag item between death and respawn.
     * Cleaned up immediately on respawn (or if the player never respawns in
     * the same session, the entry is harmless).
     */
    private final Map<UUID, ItemStack> heldBags = new HashMap<>();

    public MagicBagDeathListener(MagicBagManager bagManager, ItemFactory itemFactory, JavaPlugin plugin) {
        this.bagManager  = bagManager;
        this.itemFactory = itemFactory;
        this.plugin      = plugin;
    }

    /** Legacy 2-arg constructor kept for compatibility — uses no book-protection filter. */
    public MagicBagDeathListener(MagicBagManager bagManager, JavaPlugin plugin) {
        this(bagManager, null, plugin);
    }

    // ── Death — pull the bag item out of the drop list + handle contents ──────

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID   uuid   = player.getUniqueId();

        // Remove bag ITEM from drops
        var iter = event.getDrops().iterator();
        while (iter.hasNext()) {
            ItemStack drop = iter.next();
            if (bagManager.isMagicBag(drop)) {
                heldBags.put(uuid, drop.clone());
                iter.remove();
                break;
            }
        }

        // If bag wasn't in drops (keepInventory on, etc.) still track it
        // so we know to restore it on respawn.
        if (!heldBags.containsKey(uuid)) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && bagManager.isMagicBag(item)) {
                    heldBags.put(uuid, item.clone());
                    break;
                }
            }
        }

        // ── Handle the bag's virtual CONTENTS ─────────────────────────────
        if (itemFactory == null) return; // legacy construction — skip content logic

        ItemStack[] bag = bagManager.getBag(uuid);
        boolean hasAnyContents = false;
        for (ItemStack it : bag) {
            if (it != null && !it.getType().isAir()) { hasAnyContents = true; break; }
        }
        if (!hasAnyContents) return;

        // Check for + consume a Bag Ward from the player's MAIN inventory
        // (not from inside the bag itself, to avoid a chicken-and-egg problem).
        boolean warded = consumeBagWard(player);

        if (warded) {
            player.sendMessage("§d✦ §7Your §5Bag Ward §7shattered, protecting your §dMagic Bag's §7contents from spilling!");
            return; // nothing drops — Ward fully protects this death
        }

        // No Ward — books/pages stay safe in the bag, everything else drops.
        int dropped = 0;
        for (int i = 0; i < bag.length; i++) {
            ItemStack it = bag[i];
            if (it == null || it.getType().isAir()) continue;

            if (isProtectedBookItem(it)) continue; // stays in the bag

            player.getWorld().dropItemNaturally(player.getLocation(), it.clone());
            bag[i] = null;
            dropped++;
        }

        if (dropped > 0) {
            bagManager.saveAsync(uuid);
            player.sendMessage("§c☠ §7Your §dMagic Bag's §7contents spilled out! §8("
                    + dropped + " item stack(s) dropped — books/pages stayed safe)");
        }
    }

    /** Returns true if the item should be excluded from the death-spill (books/pages). */
    private boolean isProtectedBookItem(ItemStack item) {
        if (itemFactory.isLoreBook(item)) return true;
        if (itemFactory.isMageGearGuideBook(item)) return true;
        if (itemFactory.isWaterBook(item)) return true;
        if (itemFactory.isEarthBook(item)) return true;
        if (itemFactory.isSpellComboBook(item)) return true;
        if (itemFactory.isAncientKillTome(item)) return true;
        if (itemFactory.isSandstormBook(item)) return true;
        if (itemFactory.isSupportBook(item)) return true;
        // Any Support Page, Earth Magic Page, or Spell Page — these are all
        // WRITTEN_BOOK/PAPER "reference" items, not consumables.
        var pdc = item.hasItemMeta() ? item.getItemMeta().getPersistentDataContainer() : null;
        if (pdc != null) {
            for (var key : pdc.getKeys()) {
                String k = key.getKey();
                if (k.startsWith("de_support_page_") || k.startsWith(ItemFactory.EARTH_PAGE_KEY_PREFIX)
                        || k.equals(ItemFactory.SPELL_PAGE_DISPLAY_KEY) || k.equals(ItemFactory.SPELL_TOME_DISPLAY_KEY)
                        || k.equals(ItemFactory.QUEST_OVERVIEW_BOOK_KEY)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Removes 1 Bag Ward from the player's main inventory. Returns true if one was found/consumed. */
    private boolean consumeBagWard(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it != null && itemFactory.isBagWard(it)) {
                if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
                else player.getInventory().setItem(i, null);
                return true;
            }
        }
        return false;
    }

    // ── Respawn — put the bag back in slot 8 (top-right) ─────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        ItemStack savedBag = heldBags.remove(uuid);

        // Run 5 ticks later so inventory is fully loaded after respawn
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            // If the player already has a bag (e.g. keepInventory), no need to re-add
            for (ItemStack it : player.getInventory().getContents()) {
                if (it != null && bagManager.isMagicBag(it)) return;
            }

            ItemStack bagToRestore = (savedBag != null) ? savedBag : bagManager.buildMagicBag();

            // Place in slot 8 (top-right hotbar), displacing anything there
            ItemStack displaced = player.getInventory().getItem(MagicBagChestInterceptListener.BAG_SLOT);
            player.getInventory().setItem(MagicBagChestInterceptListener.BAG_SLOT, bagToRestore);
            if (displaced != null && !displaced.getType().isAir()) {
                player.getInventory().addItem(displaced);
            }

            player.sendActionBar("§d✦ §7A magical force keeps your Magic Bag — but your items fell out of it!");
        }, 5L);
    }
}
