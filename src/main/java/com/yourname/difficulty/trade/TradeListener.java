package com.yourname.difficulty.trade;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * TradeListener — full player-to-player Trade Stone system.
 *
 * ── Trade Stone ───────────────────────────────────────────────────────────────
 *  Right-click → opens a nearby player selector GUI.
 *  Click a player head → sends a trade request (chat message with /trade accept).
 *  /trade accept → opens the shared 54-slot trade GUI for both players.
 *
 * ── Trade GUI layout (54 slots) ──────────────────────────────────────────────
 *  Slots  0-20  : Player A's offer space (21 item slots)
 *  Slot  21     : §c[CANCEL] — Player A cancel button
 *  Slot  26     : §a[CONFIRM] — Player A confirm (grey until ready, green when confirmed)
 *  Slots 27-47  : Player B's offer space (21 item slots)
 *  Slot  48     : §a[CONFIRM] — Player B confirm
 *  Slot  53     : §c[CANCEL] — Player B cancel button
 *
 * When both players click CONFIRM → items swap instantly.
 * Either player closing the GUI or clicking CANCEL → trade aborted.
 */
public class TradeListener implements Listener, org.bukkit.command.CommandExecutor {

    private static final String PDC_STONE_KEY = "trade_stone";
    private static final String SELECTOR_TITLE = "§8Select Player to Trade";
    private static final String TRADE_TITLE    = "§6Trade";

    // Slot constants
    private static final int A_CANCEL  = 21;
    private static final int A_CONFIRM = 26;
    private static final int B_CONFIRM = 27;
    private static final int B_CANCEL  = 53;
    private static final int[] A_SLOTS = buildRange(0,  20);
    private static final int[] B_SLOTS = buildRange(28, 52);

    private final JavaPlugin     plugin;
    private final NamespacedKey  stoneKey;

    /** Pending trade requests: invited UUID → inviter UUID */
    private final Map<UUID, UUID>      pendingRequests = new HashMap<>();
    /** Active trades: UUID of any participant → TradeSession */
    private final Map<UUID, TradeSession> activeTrades  = new HashMap<>();

    // ── Inner class: TradeSession ──────────────────────────────────────────────

    private static class TradeSession {
        final UUID      playerA, playerB;
        final Inventory inventory;
        boolean aConfirmed = false;
        boolean bConfirmed = false;

        TradeSession(UUID a, UUID b, Inventory inv) {
            playerA = a; playerB = b; inventory = inv;
        }

        boolean isPlayerA(UUID uuid) { return uuid.equals(playerA); }
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public TradeListener(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.stoneKey = new NamespacedKey(plugin, PDC_STONE_KEY);
    }

    // ── Trade Stone item ──────────────────────────────────────────────────────

    public ItemStack buildTradeStone() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Trade Stone");
            meta.setLore(List.of(
                "§7Right-click to select a nearby player to trade with.",
                "§8Both players must confirm before items are exchanged.",
                "§8Use /trade accept to accept a trade request."
            ));
            meta.getPersistentDataContainer()
                .set(stoneKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTradeStone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(stoneKey, PersistentDataType.BYTE);
    }

    // ── Right-click: open nearby player selector ──────────────────────────────

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isTradeStone(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);

        if (activeTrades.containsKey(player.getUniqueId())) {
            player.sendMessage("§cYou are already in a trade.");
            return;
        }

        List<Player> nearby = new ArrayList<>();
        for (Entity e : player.getNearbyEntities(10, 10, 10)) {
            if (e instanceof Player p && !p.equals(player)) nearby.add(p);
        }
        if (nearby.isEmpty()) {
            player.sendMessage("§7No players within 10 blocks.");
            return;
        }

        int size = Math.min(27, ((nearby.size() / 9) + 1) * 9);
        Inventory gui = Bukkit.createInventory(null, size, SELECTOR_TITLE);
        for (int i = 0; i < nearby.size() && i < size; i++) {
            Player t = nearby.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(t);
                skull.setDisplayName("§a" + t.getName());
                skull.setLore(List.of("§7Click to send a trade request."));
                head.setItemMeta(skull);
            }
            gui.setItem(i, head);
        }
        player.openInventory(gui);
    }

    // ── Selector GUI click ────────────────────────────────────────────────────

    @EventHandler
    public void onSelectorClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player opener)) return;
        if (!event.getView().getTitle().equals(SELECTOR_TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta skull)) return;
        if (skull.getOwningPlayer() == null) return;

        Player target = Bukkit.getPlayerExact(skull.getOwningPlayer().getName());
        if (target == null || !target.isOnline()) {
            opener.sendMessage("§cPlayer is no longer available.");
            opener.closeInventory();
            return;
        }
        if (activeTrades.containsKey(target.getUniqueId())) {
            opener.sendMessage("§c" + target.getName() + " is already in a trade.");
            opener.closeInventory();
            return;
        }

        pendingRequests.put(target.getUniqueId(), opener.getUniqueId());
        opener.closeInventory();
        opener.sendMessage("§6Trade request sent to §e" + target.getName() + "§6!");
        target.sendMessage("");
        target.sendMessage("§6[Trade] §e" + opener.getName() + " §7wants to trade with you!");
        target.sendMessage("§7Type §a/trade accept §7to begin the trade.");
        target.sendMessage("");
    }

    // ── /trade command ────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command cmd,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("accept")) {
            UUID inviterUid = pendingRequests.remove(player.getUniqueId());
            if (inviterUid == null) {
                player.sendMessage("§cNo pending trade request.");
                return true;
            }
            Player inviter = Bukkit.getPlayer(inviterUid);
            if (inviter == null || !inviter.isOnline()) {
                player.sendMessage("§cThe other player is no longer online.");
                return true;
            }
            openTradeGUI(inviter, player);
        }
        return true;
    }

    // ── Open trade GUI for both players ──────────────────────────────────────

    private void openTradeGUI(Player a, Player b) {
        Inventory inv = Bukkit.createInventory(null, 54, TRADE_TITLE);

        // Fill border/buttons
        ItemStack border = makeGlass(Material.GRAY_STAINED_GLASS_PANE, "§8");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Clear offer slots for placing items
        for (int s : A_SLOTS) inv.setItem(s, null);
        for (int s : B_SLOTS) inv.setItem(s, null);

        // Buttons
        inv.setItem(A_CANCEL,  makeGlass(Material.RED_STAINED_GLASS_PANE,  "§c[A] Cancel Trade"));
        inv.setItem(A_CONFIRM, makeGlass(Material.LIME_STAINED_GLASS_PANE, "§a[A] Confirm Trade (click when ready)"));
        inv.setItem(B_CONFIRM, makeGlass(Material.LIME_STAINED_GLASS_PANE, "§a[B] Confirm Trade (click when ready)"));
        inv.setItem(B_CANCEL,  makeGlass(Material.RED_STAINED_GLASS_PANE,  "§c[B] Cancel Trade"));

        // Labels
        inv.setItem(22, makeGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e" + a.getName() + "'s Offer (top)"));
        inv.setItem(49, makeGlass(Material.YELLOW_STAINED_GLASS_PANE, "§e" + b.getName() + "'s Offer (bottom)"));

        TradeSession session = new TradeSession(a.getUniqueId(), b.getUniqueId(), inv);
        activeTrades.put(a.getUniqueId(), session);
        activeTrades.put(b.getUniqueId(), session);

        a.openInventory(inv);
        b.openInventory(inv);

        a.sendMessage("§6[Trade] §7Trade started with §e" + b.getName() + "§7. Place items and click CONFIRM.");
        b.sendMessage("§6[Trade] §7Trade started with §e" + a.getName() + "§7. Place items and click CONFIRM.");
    }

    // ── Trade GUI click ───────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onTradeClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TRADE_TITLE)) return;

        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        int slot = event.getRawSlot();
        boolean isA = session.isPlayerA(player.getUniqueId());

        // Cancel button
        if ((isA && slot == A_CANCEL) || (!isA && slot == B_CANCEL)) {
            event.setCancelled(true);
            abortTrade(session, player.getName() + " cancelled the trade.");
            return;
        }

        // Confirm button
        if ((isA && slot == A_CONFIRM) || (!isA && slot == B_CONFIRM)) {
            event.setCancelled(true);
            if (isA) session.aConfirmed = true;
            else      session.bConfirmed = true;
            updateConfirmButtons(session);
            notifyConfirmState(session);
            if (session.aConfirmed && session.bConfirmed) executeTrade(session);
            return;
        }

        // Allow clicks only in the player's own offer zone
        int[] mySlots = isA ? A_SLOTS : B_SLOTS;
        boolean allowed = false;
        for (int s : mySlots) { if (s == slot) { allowed = true; break; } }

        if (!allowed) {
            event.setCancelled(true);
            return;
        }

        // Allowed slot — reset confirms when items change
        session.aConfirmed = false;
        session.bConfirmed = false;
        updateConfirmButtons(session);
    }

    // ── Trade GUI close ───────────────────────────────────────────────────────

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(TRADE_TITLE)) return;

        TradeSession session = activeTrades.get(player.getUniqueId());
        if (session == null) return;

        // If trade hasn't executed, abort and return items
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (activeTrades.containsKey(player.getUniqueId())) {
                abortTrade(session, player.getName() + " closed the trade window.");
            }
        }, 1L);
    }

    // ── Execute trade ─────────────────────────────────────────────────────────

    private void executeTrade(TradeSession session) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (a == null || b == null) {
            abortTrade(session, "A player disconnected.");
            return;
        }

        // Collect A's offered items
        List<ItemStack> aItems = new ArrayList<>();
        for (int s : A_SLOTS) {
            ItemStack item = session.inventory.getItem(s);
            if (item != null && !item.getType().isAir()) aItems.add(item);
        }
        // Collect B's offered items
        List<ItemStack> bItems = new ArrayList<>();
        for (int s : B_SLOTS) {
            ItemStack item = session.inventory.getItem(s);
            if (item != null && !item.getType().isAir()) bItems.add(item);
        }

        // Close GUIs first
        a.closeInventory();
        b.closeInventory();

        // Remove from active
        activeTrades.remove(session.playerA);
        activeTrades.remove(session.playerB);

        // Give A's items to B and vice versa
        for (ItemStack item : aItems) b.getInventory().addItem(item);
        for (ItemStack item : bItems) a.getInventory().addItem(item);

        a.sendMessage("§a[Trade] §7Trade completed with §e" + b.getName() + "§7!");
        b.sendMessage("§a[Trade] §7Trade completed with §e" + a.getName() + "§7!");
    }

    private void abortTrade(TradeSession session, String reason) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);

        // Return items to their owners
        for (int s : A_SLOTS) {
            ItemStack item = session.inventory.getItem(s);
            if (item != null && !item.getType().isAir() && a != null)
                a.getInventory().addItem(item);
        }
        for (int s : B_SLOTS) {
            ItemStack item = session.inventory.getItem(s);
            if (item != null && !item.getType().isAir() && b != null)
                b.getInventory().addItem(item);
        }

        activeTrades.remove(session.playerA);
        activeTrades.remove(session.playerB);

        if (a != null) { a.closeInventory(); a.sendMessage("§c[Trade] §7" + reason); }
        if (b != null) { b.closeInventory(); b.sendMessage("§c[Trade] §7" + reason); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateConfirmButtons(TradeSession session) {
        Inventory inv = session.inventory;
        inv.setItem(A_CONFIRM, makeGlass(
            session.aConfirmed ? Material.GREEN_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
            session.aConfirmed ? "§a[A] CONFIRMED!" : "§a[A] Confirm Trade"));
        inv.setItem(B_CONFIRM, makeGlass(
            session.bConfirmed ? Material.GREEN_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
            session.bConfirmed ? "§a[B] CONFIRMED!" : "§a[B] Confirm Trade"));
    }

    private void notifyConfirmState(TradeSession session) {
        Player a = Bukkit.getPlayer(session.playerA);
        Player b = Bukkit.getPlayer(session.playerB);
        if (session.aConfirmed && !session.bConfirmed && b != null)
            b.sendActionBar("§e" + (a != null ? a.getName() : "Player A")
                + " §7has confirmed. §aClick CONFIRM to complete the trade!");
        if (session.bConfirmed && !session.aConfirmed && a != null)
            a.sendActionBar("§e" + (b != null ? b.getName() : "Player B")
                + " §7has confirmed. §aClick CONFIRM to complete the trade!");
    }

    private ItemStack makeGlass(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); item.setItemMeta(meta); }
        return item;
    }

    private static int[] buildRange(int from, int to) {
        int[] arr = new int[to - from + 1];
        for (int i = 0; i < arr.length; i++) arr[i] = from + i;
        return arr;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeSession session = activeTrades.get(event.getPlayer().getUniqueId());
        if (session != null) abortTrade(session, event.getPlayer().getName() + " disconnected.");
        pendingRequests.remove(event.getPlayer().getUniqueId());
        pendingRequests.values().remove(event.getPlayer().getUniqueId());
    }
}
