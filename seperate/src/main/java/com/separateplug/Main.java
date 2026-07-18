package com.separateplug;

import com.separateplug.combat.CombatHitBar;
import com.separateplug.combat.StunSwordListener;
import com.separateplug.spirit.SpiritItems;
import com.separateplug.spirit.SpiritListener;
import com.separateplug.spirit.SpiritManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * SeparatePlug — Main plugin class.
 *
 * ── Features ───────────────────────────────────────────────────────────────────
 *  • Spirit system: random spirit binding on first join (FIRE/WATER/EARTH/AIR).
 *  • Spirit staves: no runes — right-click to cast elemental bolt.
 *  • 5 % staff drop on death; 5 % duplicate gain on kill.
 *  • Combat hit-bar: 10 hits → STUN READY (BossBar).
 *  • Ghost Sword: Craft NETHER_STAR + QUARTZ_BLOCK.
 *    On hit → 3s stun (Slowness 255 + flashing Blindness).
 *    Charged stun (bar full) → 4.5s stun + bonus damage.
 *  • Resource pack auto-send on join (configure URL in config.yml).
 *
 * ── ZERO dependency on DifficultyEngine ────────────────────────────────────────
 *  This plugin is 100 % self-contained.  Do NOT add it as a soft/hard depend.
 *
 * ── Texture pack CustomModelData reference ────────────────────────────────────
 *  IRON_SWORD        CMD 1001  → Ghost Sword blade (grey ghost body)
 *  BLAZE_ROD         CMD 2001  → Fire Spirit Staff
 *  PRISMARINE_SHARD  CMD 2002  → Water Spirit Staff
 *  STICK             CMD 2003  → Earth Spirit Staff
 *  FEATHER           CMD 2004  → Air Spirit Staff
 */
public class Main extends JavaPlugin {

    private SpiritManager  spiritManager;
    private SpiritItems    spiritItems;
    private CombatHitBar   combatHitBar;

    @Override
    public void onEnable() {
        getLogger().info("SeparatePlug: Initializing…");

        saveDefaultConfig();

        // ── Core systems ──────────────────────────────────────────────────────
        this.spiritManager = new SpiritManager(this);
        this.spiritItems   = new SpiritItems(this);
        this.combatHitBar  = new CombatHitBar(this);

        // ── Listeners ─────────────────────────────────────────────────────────
        SpiritListener spiritListener = new SpiritListener(
            this, spiritManager, spiritItems, combatHitBar);
        getServer().getPluginManager().registerEvents(spiritListener, this);

        StunSwordListener stunListener = new StunSwordListener(
            this, spiritItems, combatHitBar);
        getServer().getPluginManager().registerEvents(stunListener, this);

        // ── Resource pack send on join ─────────────────────────────────────────
        String packUrl = getConfig().getString("resource-pack.url", "");
        if (!packUrl.isEmpty()) {
            String hash = getConfig().getString("resource-pack.sha1", "");
            getServer().getPluginManager().registerEvents(new Listener() {
                @EventHandler
                public void onJoin(PlayerJoinEvent event) {
                    Player p = event.getPlayer();
                    getServer().getScheduler().runTaskLater(Main.this, () -> {
                        if (p.isOnline()) {
                            p.setResourcePack(packUrl, hash, true,
                                net.kyori.adventure.text.Component.text(
                                    "§5SeparatePlug requires a resource pack for ghost-sword models."));
                        }
                    }, 40L);
                }
            }, this);
            getLogger().info("SeparatePlug: Resource pack configured — " + packUrl);
        } else {
            getLogger().info("SeparatePlug: No resource pack URL set in config.yml.");
            getLogger().info("  Ghost-sword models will use vanilla item textures until a pack is added.");
        }

        // ── Crafting recipe: NETHER_STAR + QUARTZ_BLOCK → Ghost Sword ─────────
        NamespacedKey stunKey = new NamespacedKey(this, "ghost_sword_recipe");
        ItemStack stunSword = spiritItems.buildStunSword();
        ShapelessRecipe stunRecipe = new ShapelessRecipe(stunKey, stunSword);
        stunRecipe.addIngredient(Material.NETHER_STAR);
        stunRecipe.addIngredient(Material.QUARTZ_BLOCK);
        getServer().addRecipe(stunRecipe);

        // ── Commands ──────────────────────────────────────────────────────────
        getCommand("spiritinfo").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /spiritinfo.");
                return true;
            }
            var type = spiritManager.getBoundSpirit(player.getUniqueId());
            if (type == null) {
                player.sendMessage("§7You have no bound spirit yet. Rejoin to be assigned one.");
            } else {
                player.sendMessage(type.color + "✦ §7Your bound spirit: §r" + type.displayName);
                player.sendMessage("§8  " + type.color + type.description);
            }
            return true;
        });

        getCommand("givestunblade").setExecutor((sender, cmd, label, args) -> {
            if (!sender.hasPermission("separateplug.admin")) {
                sender.sendMessage("§cNo permission.");
                return true;
            }
            Player target = args.length > 0
                ? getServer().getPlayerExact(args[0])
                : (sender instanceof Player p ? p : null);
            if (target == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            target.getInventory().addItem(spiritItems.buildStunSword());
            target.sendMessage("§f★ §7You received a §fGhost Sword§7!");
            if (!target.equals(sender))
                sender.sendMessage("§7Gave Ghost Sword to §f" + target.getName() + "§7.");
            return true;
        });

        getLogger().info("SeparatePlug: Ready! Spirits active. Ghost Sword recipe registered.");
        getLogger().info("  /spiritinfo  — see your bound spirit");
        getLogger().info("  /givestunblade [player]  — admin: give Ghost Sword");
        getLogger().info("  Craft Ghost Sword: NETHER_STAR + QUARTZ_BLOCK");
    }

    @Override
    public void onDisable() {
        if (spiritManager != null) spiritManager.saveAll();
        if (combatHitBar  != null) combatHitBar.shutdown();
        getLogger().info("SeparatePlug: Data saved. Goodbye.");
    }
}
