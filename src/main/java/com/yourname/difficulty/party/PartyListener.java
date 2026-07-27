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
 * PartyListener â€” handles Party Stone right-click (nearby player invite GUI),
 * damage tracking for rolling DPS, and player disconnect handling.
 *
 * â”€â”€ /party sub-commands â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *   invite <player>   â€” invite a player to your party
 *   accept            â€” accept a pending invite
 *   leave             â€” leave your party
 *   list              â€” show party members (offline shown in grey)
 *   info [player]     â€” show detailed info for your or another player's party
 *
 * â”€â”€ Offline handling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
 *   When a party member goes offline they are NOT removed from the party.
 *   They are marked offline (PartyManager.markOffline) so:
 *     â€¢ /party list shows them in Â§8 grey.
 *     â€¢ PartyHudTask skips them (they're not online to show a bar to).
 *   When they reconnect, markOnline() restores them and they get a welcome
 *   message. They must /party leave to permanently quit.
 */
public class PartyListener implements Listener, org.bukkit.command.CommandExecutor {

    private static final String PDC_KEY   = "party_stone";
    private static final String GUI_TITLE = "Â§8Invite a Player";

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

    // â”€â”€ Party Stone item builder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public ItemStack buildPartyStone() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("Â§6Party Stone");
            meta.setLore(List.of(
                "Â§7Right-click to invite nearby players.",
                "Â§8Use Â§7/party leave Â§8to leave your party.",
                "Â§8Use Â§7/party list Â§8to see members.",
                "Â§8Use Â§7/party info Â§8for party details."
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

    // â”€â”€ Right-click Party Stone â†’ Nearby Player Selector GUI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            opener.sendMessage("Â§7No players within 15 blocks to invite.");
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
                skull.setDisplayName("Â§a" + target.getName());
                double hp    = Math.round(target.getHealth() * 10.0) / 10.0;
                var maxAttr  = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                double maxHp = maxAttr != null ? maxAttr.getValue() : 20.0;
                skull.setLore(List.of(
                    "Â§7HP: Â§c" + hp + " / " + (int) maxHp,
                    "Â§7Click to invite to your party."
                ));
                head.setItemMeta(skull);
            }
            gui.setItem(i, head);
        }
        opener.openInventory(gui);
    }

    // â”€â”€ GUI Click: Select player to invite â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
            opener.sendMessage("Â§cThat player is no longer available.");
            opener.closeInventory();
            return;
        }

        partyManager.sendInvite(opener.getUniqueId(), target.getUniqueId());
        opener.closeInventory();
        opener.sendMessage("Â§6Party invite sent to Â§e" + target.getName() + "Â§6!");
        target.sendMessage("");
        target.sendMessage("Â§6[Party] Â§e" + opener.getName() + " Â§7invited you to their party!");
        target.sendMessage("Â§7Type Â§a/party accept Â§7or Â§c/party leave Â§7to decline.");
        target.sendMessage("");
    }

    // â”€â”€ /party command â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender sender,
                             org.bukkit.command.Command command,
                             String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Â§cOnly players can use /party.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("Â§6/party Â§8<invite|accept|leave|list|info>");
            return true;
        }
        switch (args[0].toLowerCase()) {

            case "invite" -> {
                if (args.length < 2) {
                    player.sendMessage("Â§c Usage: /party invite <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("Â§cPlayer not found or not online: Â§e" + args[1]);
                    return true;
                }
                if (target.equals(player)) {
                    player.sendMessage("Â§cYou cannot invite yourself.");
                    return true;
                }
                if (partyManager.isInParty(target.getUniqueId())
                        && partyManager.getPartyMembers(target.getUniqueId())
                                       .contains(player.getUniqueId())) {
                    player.sendMessage("Â§e" + target.getName() + " Â§cis already in your party.");
                    return true;
                }
                partyManager.sendInvite(player.getUniqueId(), target.getUniqueId());
                player.sendMessage("Â§6Party invite sent to Â§e" + target.getName() + "Â§6!");
                target.sendMessage("");
                target.sendMessage("Â§6â”Œâ”€ Â§e[Party Invite] Â§6â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€");
                target.sendMessage("Â§6â”‚ Â§e" + player.getName() + " Â§7has invited you to their party!");
                target.sendMessage("Â§6â”‚ Â§aType: Â§f/party accept Â§7to join");
                target.sendMessage("Â§6â”‚ Â§cType: Â§f/party leave Â§7to decline");
                target.sendMessage("Â§6â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€");
                target.sendMessage("");
                target.playSound(target.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
            }

            case "accept" -> {
                if (!partyManager.hasPendingInvite(player.getUniqueId())) {
                    player.sendMessage("Â§cYou have no pending party invite.");
                    return true;
                }
                UUID inviterUuid = partyManager.getInviter(player.getUniqueId());
                partyManager.acceptInvite(player.getUniqueId());
                player.sendMessage("Â§aYou joined the party!");
                Player inviter = Bukkit.getPlayer(inviterUuid);
                if (inviter != null)
                    inviter.sendMessage("Â§a" + player.getName() + " Â§7joined the party!");
                for (UUID m : partyManager.getPartyMembers(player.getUniqueId())) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null && !mp.equals(player))
                        mp.sendMessage("Â§a" + player.getName() + " Â§7has joined the party.");
                }
            }

            case "leave" -> {
                if (!partyManager.isInParty(player.getUniqueId())) {
                    if (partyManager.hasPendingInvite(player.getUniqueId())) {
                        partyManager.declineInvite(player.getUniqueId());
                        player.sendMessage("Â§7Party invite declined.");
                    } else {
                        player.sendMessage("Â§cYou are not in a party.");
                    }
                    return true;
                }
                List<UUID> remaining = partyManager.leaveParty(player.getUniqueId());
                player.sendMessage("Â§7You left the party.");
                for (UUID m : remaining) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null)
                        mp.sendMessage("Â§c" + player.getName() + " Â§7left the party.");
                }
            }

            case "list" -> {
                if (!partyManager.isInParty(player.getUniqueId())) {
                    player.sendMessage("Â§cYou are not in a party.");
                    return true;
                }
                player.sendMessage("Â§6=== Party Members ===");
                for (UUID m : partyManager.getPartyMembers(player.getUniqueId())) {
                    Player mp      = Bukkit.getPlayer(m);
                    boolean online = (mp != null && mp.isOnline() && !partyManager.isOffline(m));
                    String  name   = mp != null ? mp.getName()
                            : Bukkit.getOfflinePlayer(m).getName();
                    if (name == null) name = m.toString().substring(0, 8);
                    String leader  = partyManager.isLeader(m) ? " Â§6[Leader]" : "";
                    String status  = online ? "Â§f" : "Â§8[offline] Â§7";
                    player.sendMessage("  " + status + name + leader);
                }
            }

            case "info" -> {
                // /party info [player] â€” show another (or your own) party's details
                String lookupName = (args.length >= 2) ? args[1] : player.getName();
                Player target = Bukkit.getPlayerExact(lookupName);
                UUID targetUid = (target != null)
                        ? target.getUniqueId()
                        : Bukkit.getOfflinePlayer(lookupName).getUniqueId();

                if (!partyManager.isInParty(targetUid)) {
                    player.sendMessage("Â§e" + lookupName + " Â§cis not in a party.");
                    return true;
                }
                player.sendMessage("Â§6=== " + lookupName + "'s Party ===");
                for (UUID m : partyManager.getPartyMembers(targetUid)) {
                    Player  mp     = Bukkit.getPlayer(m);
                    boolean online = (mp != null && mp.isOnline() && !partyManager.isOffline(m));
                    String  mName  = mp != null ? mp.getName()
                            : Bukkit.getOfflinePlayer(m).getName();
                    if (mName == null) mName = m.toString().substring(0, 8);
                    String  hp     = online
                            ? "Â§câ¤ " + (int) mp.getHealth() + "  " : "";
                    String  ldr    = partyManager.isLeader(m) ? " Â§6â˜…" : "";
                    String  status = online ? "Â§aâ— Â§f" : "Â§8â— Â§7";
                    player.sendMessage("  " + status + mName + ldr + "  " + hp);
                }
            }

            default -> player.sendMessage(
                    "Â§6/party Â§8<invite|accept|leave|list|info [player]>");
        }
        return true;
    }

    // â”€â”€ DPS tracking â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = getDamagerPlayer(event.getDamager());
        if (damager == null) return;
        if (!partyManager.isInParty(damager.getUniqueId())) return;
        partyManager.recordDamage(damager.getUniqueId(), event.getFinalDamage());
    }

    // â”€â”€ Offline handling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * On disconnect: keep the player in their party but mark them offline.
     * Online party members receive a notice. The player is NOT removed â€”
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
                    mp.sendMessage("Â§8[Party] Â§7" + player.getName()
                        + " Â§8went offline â€” still in party.");
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
            player.sendMessage("Â§6[Party] Â§7Welcome back! You are still in your party.");
            player.sendMessage("Â§8  Type Â§c/party leave Â§8to leave.");
            for (UUID m : partyManager.getPartyMembers(uuid)) {
                if (m.equals(uuid)) continue;
                Player mp = Bukkit.getPlayer(m);
                if (mp != null)
                    mp.sendMessage("Â§a[Party] Â§f" + player.getName() + " Â§7came back online!");
            }
        }
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private Player getDamagerPlayer(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
