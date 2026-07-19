package com.yourname.difficulty.party;

import com.yourname.difficulty.PlayerDifficultyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * PartyListener — handles Party Stone right-click (nearby player invite GUI),
 * damage tracking for rolling DPS, and player disconnect handling.
 *
 * ── /party sub-commands ───────────────────────────────────────────────────
 *   invite <player>   — invite a player to your party
 *   accept            — accept a pending invite
 *   leave             — leave your party
 *   list              — show party members (offline shown in grey)
 *   info [player]     — show detailed info for your or another player's party
 *
 * ── Offline handling ──────────────────────────────────────────────────────
 *   When a party member goes offline they are NOT removed from the party.
 *   They are marked offline (PartyManager.markOffline) so:
 *     • /party list shows them in §8 grey.
 *     • PartyHudTask skips them (they're not online to show a bar to).
 *   When they reconnect, markOnline() restores them and they get a welcome
 *   message. They must /party leave to permanently quit.
 */
public class PartyListener implements Listener, org.bukkit.command.CommandExecutor {

    private static final String PDC_KEY   = "party_stone";
    private static final String GUI_TITLE = "§8Invite a Player";

    private final PartyManager            partyManager;
    private final PlayerDifficultyManager diffManager;
    private final JavaPlugin              plugin;
    private final NamespacedKey           stoneKey;

    public PartyListener(PartyManager partyManager,
                         PlayerDifficultyManager diffManager,
                         JavaPlugin plugin) {
        this.partyManager = partyManager;
        this.diffManager  = diffManager;
        this.plugin       = plugin;
        this.stoneKey     = new NamespacedKey(plugin, PDC_KEY);
    }

    // ── Party Stone item builder ──────────────────────────────────────────────

    public ItemStack buildPartyStone() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Party Stone");
            meta.setLore(List.of(
                "§7Right-click to invite nearby players.",
                "§8Use §7/party leave §8to leave your party.",
                "§8Use §7/party list §8to see members.",
                "§8Use §7/party info §8for party details."
            ));
            meta.getPersistentDataContainer()
                .set(stoneKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isPartyStone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
            .has(stoneKey, PersistentDataType.BYTE);
    }

    // ── Right-click Party Stone → Nearby Player Selector GUI ─────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
            && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!isPartyStone(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        openNearbyGUI(player);
    }

    private void openNearbyGUI(Player opener) {
        List<Player> nearby = new ArrayList<>();
        for (Entity e : opener.getNearbyEntities(15, 15, 15)) {
            if (e instanceof Player p && !p.equals(opener)) nearby.add(p);
        }
        if (nearby.isEmpty()) {
            opener.sendMessage("§7No players within 15 blocks to invite.");
            return;
        }
        int size = Math.min(27, ((nearby.size() / 9) + 1) * 9);
        Inventory gui = Bukkit.createInventory(null, size, GUI_TITLE);

        for (int i = 0; i < nearby.size() && i < size; i++) {
            Player target = nearby.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skull = (SkullMeta) head.getItemMeta();
            if (skull != null) {
                skull.setOwningPlayer(target);
                skull.setDisplayName("§a" + target.getName());
                double hp    = Math.round(target.getHealth() * 10.0) / 10.0;
                var maxAttr  = target.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
                double maxHp = maxAttr != null ? maxAttr.getValue() : 20.0;
                skull.setLore(List.of(
                    "§7HP: §c" + hp + " / " + (int) maxHp,
                    "§7Click to invite to your party."
                ));
                head.setItemMeta(skull);
            }
            gui.setItem(i, head);
        }
        opener.openInventory(gui);
    }

    // ── GUI Click: Select player to invite ────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL)
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player opener)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
        if (!(clicked.getItemMeta() instanceof SkullMeta skull)) return;

        String targetName = skull.getOwningPlayer() != null
            ? skull.getOwningPlayer().getName() : null;
        if (targetName == null) return;

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            opener.sendMessage("§cThat player is no longer available.");
            opener.closeInventory();
            return;
        }

        partyManager.sendInvite(opener.getUniqueId(), target.getUniqueId());
        opener.closeInventory();
        opener.sendMessage("§6Party invite sent to §e" + target.getName() + "§6!");
        target.sendMessage("");
        target.sendMessage("§6[Party] §e" + opener.getName() + " §7invited you to their party!");
        target.sendMessage("§7Type §a/party accept §7or §c/party leave §7to decline.");
        target.sendMessage("");
    }

    // ── /party command ────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /party.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§6/party §8<invite|accept|leave|list|info>");
            return true;
        }
        switch (args[0].toLowerCase()) {

            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("§c Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cPlayer not found or not online: §e" + args[1]);
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("§cYou cannot invite yourself.");
                    return true;
                }
                if (partyManager.isInParty(target.getUniqueId())
                        && partyManager.getPartyMembers(target.getUniqueId())
                                       .contains(player.getUniqueId())) {
                    player.sendMessage("§e" + target.getName() + " §cis already in your party.");
                    return true;
                }
                partyManager.sendInvite(player.getUniqueId(), target.getUniqueId());
                player.sendMessage("§6Party invite sent to §e" + target.getName() + "§6!");
                target.sendMessage("");
                target.sendMessage("§6┌─ §e[Party Invite] §6────────────────────");
                target.sendMessage("§6│ §e" + player.getName() + " §7has invited you to their party!");
                target.sendMessage("§6│ §aType: §f/party accept §7to join");
                target.sendMessage("§6│ §cType: §f/party leave §7to decline");
                target.sendMessage("§6└────────────────────────────────────");
                target.sendMessage("");
                target.playSound(target.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            }

            case "accept" -> {
                if (!partyManager.hasPendingInvite(player.getUniqueId())) {
                    player.sendMessage("§cYou have no pending party invite.");
                    return true;
                }
                UUID inviterUuid = partyManager.getInviter(player.getUniqueId());
                partyManager.acceptInvite(player.getUniqueId());
                player.sendMessage("§aYou joined the party!");
                Player inviter = Bukkit.getPlayer(inviterUuid);
                if (inviter != null)
                    inviter.sendMessage("§a" + player.getName() + " §7joined the party!");
                for (UUID m : partyManager.getPartyMembers(player.getUniqueId())) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null && !mp.equals(player))
                        mp.sendMessage("§a" + player.getName() + " §7has joined the party.");
                }
            }

            case "leave" -> {
                if (!partyManager.isInParty(player.getUniqueId())) {
                    if (partyManager.hasPendingInvite(player.getUniqueId())) {
                        partyManager.declineInvite(player.getUniqueId());
                        player.sendMessage("§7Party invite declined.");
                    } else {
                        player.sendMessage("§cYou are not in a party.");
                    }
                    return true;
                }
                List<UUID> remaining = partyManager.leaveParty(player.getUniqueId());
                player.sendMessage("§7You left the party.");
                for (UUID m : remaining) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null)
                        mp.sendMessage("§c" + player.getName() + " §7left the party.");
                }
            }

            case "list" -> {
                if (!partyManager.isInParty(player.getUniqueId())) {
                    player.sendMessage("§cYou are not in a party.");
                    return true;
                }
                player.sendMessage("§6=== Party Members ===");
                for (UUID m : partyManager.getPartyMembers(player.getUniqueId())) {
                    Player mp      = Bukkit.getPlayer(m);
                    boolean online = (mp != null && mp.isOnline() && !partyManager.isOffline(m));
                    String  name   = mp != null ? mp.getName()
                            : Bukkit.getOfflinePlayer(m).getName();
                    if (name == null) name = m.toString().substring(0, 8);
                    String leader  = partyManager.isLeader(m) ? " §6[Leader]" : "";
                    String status  = online ? "§f" : "§8[offline] §7";
                    player.sendMessage("  " + status + name + leader);
                }
            }

            case "info" -> {
                // /party info [player] — show another (or your own) party's details
                String lookupName = (args.length >= 2) ? args[1] : player.getName();
                Player target = Bukkit.getPlayerExact(lookupName);
                UUID targetUid = (target != null)
                        ? target.getUniqueId()
                        : Bukkit.getOfflinePlayer(lookupName).getUniqueId();

                if (!partyManager.isInParty(targetUid)) {
                    player.sendMessage("§e" + lookupName + " §cis not in a party.");
                    return true;
                }
                player.sendMessage("§6=== " + lookupName + "'s Party ===");
                for (UUID m : partyManager.getPartyMembers(targetUid)) {
                    Player  mp     = Bukkit.getPlayer(m);
                    boolean online = (mp != null && mp.isOnline() && !partyManager.isOffline(m));
                    String  mName  = mp != null ? mp.getName()
                            : Bukkit.getOfflinePlayer(m).getName();
                    if (mName == null) mName = m.toString().substring(0, 8);
                    String  hp     = online
                            ? "§c❤ " + (int) mp.getHealth() + "  " : "";
                    String  ldr    = partyManager.isLeader(m) ? " §6★" : "";
                    String  status = online ? "§a● §f" : "§8● §7";
                    player.sendMessage("  " + status + mName + ldr + "  " + hp);
                }
            }

            default -> player.sendMessage(
                    "§6/party §8<invite|accept|leave|list|info [player]>");
        }
        return true;
    }

    // ── DPS tracking ──────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = getDamagerPlayer(event.getDamager());
        if (damager == null) return;
        if (!partyManager.isInParty(damager.getUniqueId())) return;
        partyManager.recordDamage(damager.getUniqueId(), event.getFinalDamage());
    }

    // ── Offline handling ──────────────────────────────────────────────────────

    /**
     * On disconnect: keep the player in their party but mark them offline.
     * Online party members receive a notice. The player is NOT removed —
     * they will be welcomed back on reconnect.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();
        partyManager.declineInvite(uuid);

        if (partyManager.isInParty(uuid)) {
            partyManager.markOffline(uuid);
            for (UUID m : partyManager.getPartyMembers(uuid)) {
                if (m.equals(uuid)) continue;
                Player mp = Bukkit.getPlayer(m);
                if (mp != null)
                    mp.sendMessage("§8[Party] §7" + player.getName()
                        + " §8went offline — still in party.");
            }
        }
    }

    /**
     * On reconnect: if the player was still in a party from their last session,
     * remove the offline mark and notify everyone.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID   uuid   = player.getUniqueId();

        if (partyManager.isInParty(uuid) && partyManager.isOffline(uuid)) {
            partyManager.markOnline(uuid);
            player.sendMessage("§6[Party] §7Welcome back! You are still in your party.");
            player.sendMessage("§8  Type §c/party leave §8to leave.");
            for (UUID m : partyManager.getPartyMembers(uuid)) {
                if (m.equals(uuid)) continue;
                Player mp = Bukkit.getPlayer(m);
                if (mp != null)
                    mp.sendMessage("§a[Party] §f" + player.getName() + " §7came back online!");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Player getDamagerPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
