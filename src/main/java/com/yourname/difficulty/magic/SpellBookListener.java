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
 *  Opens the §dCombo Favorites GUI§r (chest inventory) so the player can
 *  star/un-star which combo hints they want to see during combat.
 *  Inside the GUI there is a "Read Full Tome" button to open the written book.
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
    private       FavoritesGUI     favoritesGUI = null;
    private       com.yourname.difficulty.items.ItemFactory itemFactory = null;

    public SpellBookListener(SpellBookManager manager) {
        this.manager = manager;
    }

    /** Wires in the FavoritesGUI — called from Main after construction. */
    public void setFavoritesGUI(FavoritesGUI gui) {
        this.favoritesGUI = gui;
    }

    /** Wires in the ItemFactory — called from Main after construction. */
    public void setItemFactory(com.yourname.difficulty.items.ItemFactory factory) {
        this.itemFactory = factory;
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

        // ── Spell Combo Book → open written guide ───────────────────────────
        if (itemFactory != null && itemFactory.isSpellComboBook(hand)) {
            event.setCancelled(true);
            player.openBook(buildWrittenComboBook());
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.3f);
            return;
        }

        // ── Support Pages/Books ──────────────────────────────────────────────
        if (isSupportPageItem(hand)) {
            event.setCancelled(true);
            openSupportPageAsBook(player, hand);
            return;
        }

        // ── Spell Tome → open Favorites GUI ──────────────────────────────────
        if (manager.isSpellTome(hand)) {
            event.setCancelled(true);
            if (favoritesGUI != null) {
                favoritesGUI.open(player);
                player.playSound(player.getLocation(),
                        Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.3f);
            } else {
                // Fallback: open written book directly (favoritesGUI not yet wired)
                int count = manager.getUnlockedCount(player.getUniqueId());
                player.openBook(manager.buildBookForPlayer(player.getUniqueId()));
                player.sendActionBar(
                    "§5✦ §dArcane Tome §8— §7" + count + " §8/ §7"
                    + SpellBookManager.TOTAL_PAGES + " §dpages unlocked");
            }
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
                + " pages discovered.  §7Right-click your §dArcane Tome§7 to open the §dFavorites§7 menu.");
            player.sendActionBar(
                "§5✦ §dPage " + newPage + " §7unlocked§8! §8("
                + total + "/" + SpellBookManager.TOTAL_PAGES + ")");
        }
    }

    private boolean isSupportPageItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        org.bukkit.persistence.PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
            if (key.getKey().startsWith("de_support_page_")) {
                return true;
            }
        }
        return false;
    }

    private void openSupportPageAsBook(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        org.bukkit.inventory.meta.ItemMeta itemMeta = item.getItemMeta();
        String title = itemMeta.getDisplayName();
        java.util.List<String> lore = itemMeta.getLore();
        
        ItemStack writtenBook = new ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) writtenBook.getItemMeta();
        if (bookMeta != null) {
            bookMeta.setTitle("Support Spell Guide");
            bookMeta.setAuthor("Support Archmage");
            bookMeta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
            
            StringBuilder sb = new StringBuilder();
            sb.append("§5§l" + title.replace("§5✦ Support Page: §d", "").replace("§5✦ ", "") + "§r\n\n");
            if (lore != null) {
                for (String line : lore) {
                    if (line.contains("[DifficultyEngine")) continue;
                    sb.append(line).append("\n");
                }
            }
            sb.append("\n§d✦ §8[Right-click Support Staff to cast once unlocked]");
            
            bookMeta.addPage(sb.toString());
            writtenBook.setItemMeta(bookMeta);
            player.openBook(writtenBook);
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.3f);
        }
    }

    private ItemStack buildWrittenComboBook() {
        ItemStack book = new ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("Spell Combo Book");
            meta.setAuthor("Archmage Guide");
            meta.setGeneration(org.bukkit.inventory.meta.BookMeta.Generation.ORIGINAL);
            
            // Page 1: Introduction
            meta.addPage("§d§lSPELL COMBOS§r\n\n§7Cast spells in quick succession to trigger high-impact elemental combos!\n\n§5Carry this book§7 to see action bar hints in combat.\n\n§dPages ahead detail all recipes...");
            
            // Page 2: Fire & Water Combos
            meta.addPage("§c§lFIRE & WATER§r\n\n" +
                "§cFire §7+ §cFire\n§8→ §c§lInferno Burst§r (AoE blast)\n\n" +
                "§bWater §7+ §cFire §r/ §cFire §7+ §bWater\n§8→ §e§lSteam Blast§r (Armor shred)\n\n" +
                "§bWater §7+ §bWater\n§8→ §b§lTidal Surge§r (Pushback wave)");
                
            // Page 3: Earth & Air Combos
            meta.addPage("§2§lEARTH & AIR§r\n\n" +
                "§aAir §7+ §aAir\n§8→ §f§lGale Force§r (Launch targets)\n\n" +
                "§aAir §7+ §cFire §r/ §cFire §7+ §aAir\n§8→ §c§lTornado Flame§r (Blazing dash)\n\n" +
                "§eEarth §7+ §eEarth\n§8→ §e§lStone Skin§r (Heavy resistance)");

            // Page 4: Advanced Combos
            meta.addPage("§d§lADVANCED COMBOS§r\n\n" +
                "§eEarth §7+ §bWater\n§8→ §6§lQuicksand§r (AoE slowdown)\n\n" +
                "§bWater §7+ §eEarth\n§8→ §8§lMud Wall§r (Block attacks)\n\n" +
                "§aAir §7+ §bWater\n§8→ §d§lMist Veil§r (Total stealth)");

            // Page 5: More Advanced Combos
            meta.addPage("§d§lADVANCED COMBOS§r\n\n" +
                "§bWater §7+ §aAir\n§8→ §b§lCleanse§r (Clear debuffs)\n\n" +
                "§eEarth §7+ §aAir §r/ §aAir §7+ §eEarth\n§8→ §7§lSandstorm§r (Blind enemies)");

            // Page 6: Grand Harmony
            meta.addPage("§d§lGRAND HARMONY§r\n\n" +
                "§cFire §7+ §bWater §7+ §eEarth §7+ §aAir\n" +
                "§8(Or any 4-element sequence)\n\n" +
                "§5§lGRAND HARMONY§r\n" +
                "§d✦ Absolute Shielding\n" +
                "§d✦ Heavy frontliner boost\n" +
                "§d✦ Ultimate support aura");

            book.setItemMeta(meta);
        }
        return book;
    }

    // ── Hostile mob death: 4% chance to drop a Spell Page ────────────────────
    //
    // Rules:
    //   1. Only from monsters killed by a PLAYER (not environmental, not other mobs).
    //   2. Player must NOT already have an unabsorbed Spell Page in inventory.
    //   3. Player must NOT have all pages already unlocked.
    //   4. Lore books (WRITTEN_BOOK) never drop from monsters — only Spell Pages.

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player       killer = entity.getKiller();
        if (killer == null) return; // not killed by a player

        // Only from hostile monsters
        if (!(entity instanceof Monster)) return;

        // Suppress drop if player already has an unabsorbed Spell Page in inventory
        if (playerHasSpellPage(killer)) return;

        // Suppress drop if player has already unlocked all pages
        if (manager.allUnlocked(killer.getUniqueId())) return;

        if (RAND.nextDouble() < PAGE_DROP_CHANCE) {
            event.getDrops().add(manager.buildSpellPageItem());
        }
    }

    /** Returns true if the player has an unabsorbed Spell Page anywhere in their inventory. */
    private boolean playerHasSpellPage(Player player) {
        for (org.bukkit.inventory.ItemStack s : player.getInventory().getContents()) {
            if (manager.isSpellPage(s)) return true;
        }
        return false;
    }
}
