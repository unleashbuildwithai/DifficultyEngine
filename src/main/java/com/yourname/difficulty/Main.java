package com.yourname.difficulty;

import com.yourname.difficulty.bag.MagicBagDeathListener;
import com.yourname.difficulty.bag.MagicBagGUI;
import com.yourname.difficulty.bag.MagicBagGUIListener;
import com.yourname.difficulty.bag.MagicBagManager;
import com.yourname.difficulty.bag.MagicBagChestInterceptListener;
import com.yourname.difficulty.boss.BossEffectListener;
import com.yourname.difficulty.boss.BossEffectTask;
import com.yourname.difficulty.boss.BossSpawnerCommand;
import com.yourname.difficulty.boss.CrimsonBossManager;
import com.yourname.difficulty.boss.EffectRegistry;
import com.yourname.difficulty.casting.BuffLogic;
import com.yourname.difficulty.casting.CastingEngine;
import com.yourname.difficulty.casting.CastingQueueManager;
import com.yourname.difficulty.currency.GoldDropListener;
import com.yourname.difficulty.currency.GoldInventoryGUI;
import com.yourname.difficulty.currency.GoldInventoryListener;
import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.currency.GoldValueListener;
import com.yourname.difficulty.gui.CapeSlotGUI;
import com.yourname.difficulty.gui.CapeSlotGUIListener;
import com.yourname.difficulty.gui.RegistryGUI;
import com.yourname.difficulty.gui.RegistryGUIListener;
import com.yourname.difficulty.hardcore.NightmareHardcoreListener;
import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.listeners.BossEventListener;
import com.yourname.difficulty.listeners.CapeVisualTask;
import com.yourname.difficulty.listeners.CustomItemCraftListener;
import com.yourname.difficulty.listeners.DarkBowListener;
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.GroupDifficultyListener;
import com.yourname.difficulty.listeners.GunZSwordListener;
import com.yourname.difficulty.listeners.LevelProtectionListener;
import com.yourname.difficulty.listeners.MageGearCraftListener;
import com.yourname.difficulty.listeners.MageGearEquipListener;
import com.yourname.difficulty.listeners.MagicCauldronCraftListener;
import com.yourname.difficulty.listeners.MagicGlowTask;
import com.yourname.difficulty.listeners.MeleeGearCraftListener;
import com.yourname.difficulty.listeners.MeleeGearEquipListener;
import com.yourname.difficulty.listeners.MinecartListener;
import com.yourname.difficulty.listeners.NightmareAggroListener;
import com.yourname.difficulty.listeners.NightSpawnBoostListener;
import com.yourname.difficulty.listeners.PrayerListener;
import com.yourname.difficulty.listeners.RangedGearCraftListener;
import com.yourname.difficulty.listeners.RangedGearEquipListener;
import com.yourname.difficulty.listeners.RangedSpeedListener;
import com.yourname.difficulty.listeners.SitListener;
import com.yourname.difficulty.listeners.SoulfurPotionListener;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.magic.CatchingBlockListener;
import com.yourname.difficulty.magic.MagicBottleManager;
import com.yourname.difficulty.magic.MagicStaffListener;
import com.yourname.difficulty.magic.RuneDropListener;
import com.yourname.difficulty.magic.SandstormManager;
import com.yourname.difficulty.magic.ComboFavoritesManager;
import com.yourname.difficulty.magic.FavoritesGUI;
import com.yourname.difficulty.magic.FavoritesGUIListener;
import com.yourname.difficulty.magic.SpellBookListener;
import com.yourname.difficulty.magic.SpellBookManager;
import com.yourname.difficulty.monsters.CustomMonsterDropListener;
import com.yourname.difficulty.monsters.CustomMonsterManager;
import com.yourname.difficulty.party.PartyHudTask;
import com.yourname.difficulty.party.PartyListener;
import com.yourname.difficulty.party.PartyManager;
import com.yourname.difficulty.quests.BossQuestCapeTask;
import com.yourname.difficulty.quests.NpcQuestListener;
import com.yourname.difficulty.quests.NpcQuestManager;
import com.yourname.difficulty.quests.NpcQuestSpawner;
import com.yourname.difficulty.quests.QuestGUI;
import com.yourname.difficulty.quests.QuestKillListener;
import com.yourname.difficulty.quests.QuestManager;
import com.yourname.difficulty.realm.AncientDebrisPortalListener;
import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.skills.CapeEquipListener;
import com.yourname.difficulty.skills.ItemLevelListener;
import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillCombatListener;
import com.yourname.difficulty.skills.SkillCommand;
import com.yourname.difficulty.skills.SkillGUI;
import com.yourname.difficulty.skills.SkillGUIListener;
import com.yourname.difficulty.skills.SkillListener;
import com.yourname.difficulty.skills.SkillLvlCommand;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.trade.TradeListener;
import com.yourname.difficulty.vip.VipShopListener;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class Main extends JavaPlugin {

    // ── Core managers ─────────────────────────────────────────────────────────
    private PlayerDifficultyManager difficultyManager;
    private ItemFactory             itemFactory;
    private RegistryGUI             registryGUI;
    private SitListener             sitListener;
    private SkillManager            skillManager;
    private SkillCapeManager        skillCapeManager;
    private AdminLightCommand       adminLightCommand;
    private LightningAdminCommand   lightningAdminCommand;
    private MagicBottleManager      magicBottleManager;
    private com.yourname.difficulty.magic.LightningChargeManager lightningChargeManager;
    private CapeVisualTask          capeVisualTask;
    private CapeDataManager         capeDataManager;
    private SandstormManager        sandstormManager;
    private MagicStaffListener      magicStaffListener;
    // ── Existing systems ───────────────────────────────────────────────────────
    private SpellBookManager    spellBookManager;
    private GoldManager         goldManager;
    private QuestManager        questManager;
    private PartyManager        partyManager;
    private PartyHudTask        partyHudTask;
    private PartyListener       partyListener;
    private QuestGUI            questGUI;
    private TradeListener       tradeListener;
    private VipShopListener     vipShopListener;
    private GunZSwordListener   gunZSwordListener;
    // ── Magic Bag ──────────────────────────────────────────────────────────────
    private MagicBagManager     magicBagManager;
    private MagicBagGUI         magicBagGUI;
    // ── NPC Quest System ───────────────────────────────────────────────────────
    private NpcQuestManager     npcQuestManager;
    private NpcQuestSpawner     npcQuestSpawner;
    // ── Phase 1: Effect Engine ─────────────────────────────────────────────────
    private EffectRegistry      effectRegistry;
    private BossEffectListener  bossEffectListener;
    // ── Phase 2: Combo Casting ─────────────────────────────────────────────────
    private CastingQueueManager castingQueueManager;
    private CastingEngine       castingEngine;
    // ── Phase 3: Gold Inventory ────────────────────────────────────────────────
    private GoldInventoryListener goldInventoryListener;
    // ── Phase 7: Ancient Realm ─────────────────────────────────────────────────
    private AncientDebrisPortalListener ancientPortalListener;
    // ── Phase 8: Nightmare Hardcore ───────────────────────────────────────────
    private NightmareHardcoreListener hardcoreListener;
    // ── Phase 9: Custom Monsters ──────────────────────────────────────────────
    private CustomMonsterManager customMonsterManager;
    // ── Dungeon Bosses ────────────────────────────────────────────────────────
    private CrimsonBossManager   crimsonBossManager;

    /** All crafting recipe keys registered by this plugin — used for recipe-book discovery. */
    private final List<NamespacedKey> allRecipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        getLogger().info("DifficultyEngine: Initializing...");

        // ── Default config ────────────────────────────────────────────────────
        saveDefaultConfig();
        getConfig().addDefault("ancient-realm.spawn-x", -23.320);
        getConfig().addDefault("ancient-realm.spawn-y", 77.0);
        getConfig().addDefault("ancient-realm.spawn-z", 1.450);
        getConfig().options().copyDefaults(true);
        saveConfig();

        // ── Core managers ─────────────────────────────────────────────────────
        this.difficultyManager = new PlayerDifficultyManager(this);
        this.skillManager      = new SkillManager(this);
        this.skillCapeManager  = new SkillCapeManager(this);
        this.capeDataManager   = new CapeDataManager(this);
        getServer().getPluginManager().registerEvents(capeDataManager, this);

        // ── Item & GUI layer ──────────────────────────────────────────────────
        this.itemFactory = new ItemFactory(this, skillCapeManager);
        this.registryGUI = new RegistryGUI(itemFactory);

        // ── Skill GUI ─────────────────────────────────────────────────────────
        SkillGUI skillGUI = new SkillGUI(skillManager);

        // ── Phase 1: Effect Engine ─────────────────────────────────────────────
        this.effectRegistry     = new EffectRegistry();
        this.bossEffectListener = new BossEffectListener(this, effectRegistry, skillManager, itemFactory);
        getServer().getPluginManager().registerEvents(bossEffectListener, this);
        new BossEffectTask(this, effectRegistry, bossEffectListener.trackedBosses)
                .runTaskTimer(this, 1L, 1L);

        // ── Phase 2: Combo Casting System ──────────────────────────────────────
        this.castingQueueManager = new CastingQueueManager();
        BuffLogic buffLogic      = new BuffLogic(this);
        this.castingEngine       = new CastingEngine(this, castingQueueManager, buffLogic, itemFactory);
        getServer().getPluginManager().registerEvents(castingEngine, this);
        getServer().getScheduler().runTaskTimer(this, castingQueueManager::cleanupAll, 100L, 100L);

        // ── Register listeners ────────────────────────────────────────────────
        getServer().getPluginManager().registerEvents(
                new DifficultyEngine(this, difficultyManager), this);
        getServer().getPluginManager().registerEvents(
                new SoulfurPotionListener(this, itemFactory, difficultyManager), this);
        getServer().getPluginManager().registerEvents(
                new NightmareAggroListener(difficultyManager), this);
        getServer().getPluginManager().registerEvents(
                new RegistryGUIListener(itemFactory, registryGUI, this), this);

        this.sitListener = new SitListener();
        getServer().getPluginManager().registerEvents(sitListener, this);

        getServer().getPluginManager().registerEvents(
                new MinecartListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new SkillListener(this, skillManager, skillCapeManager, difficultyManager, itemFactory), this);
        getServer().getPluginManager().registerEvents(new SkillGUIListener(), this);
        getServer().getPluginManager().registerEvents(
                new CapeEquipListener(skillManager, skillCapeManager, this), this);
        getServer().getPluginManager().registerEvents(
                new ItemLevelListener(skillManager), this);
        getServer().getPluginManager().registerEvents(
                new SkillCombatListener(skillManager, this), this);
        getServer().getPluginManager().registerEvents(
                new PrayerListener(skillManager), this);

        this.magicStaffListener = new MagicStaffListener(itemFactory, skillManager, this);
        this.magicStaffListener.setCastingEngine(castingEngine);
        getServer().getPluginManager().registerEvents(magicStaffListener, this);

        // ── Lightning Admin + Catching Block system ────────────────────────────
        this.lightningAdminCommand = new LightningAdminCommand();
        magicStaffListener.setLightningAdminCommand(lightningAdminCommand);
        this.magicBottleManager = new MagicBottleManager();
        getServer().getPluginManager().registerEvents(
            new CatchingBlockListener(itemFactory, magicBottleManager), this);
            
        this.lightningChargeManager = new com.yourname.difficulty.magic.LightningChargeManager(this, itemFactory);
        getServer().getPluginManager().registerEvents(lightningChargeManager, this);

        this.sandstormManager = new SandstormManager(this);
        magicStaffListener.setSandstormManager(sandstormManager);
        getServer().getPluginManager().registerEvents(sandstormManager, this);

        getServer().getPluginManager().registerEvents(
                new LevelProtectionListener(skillManager, this), this);
        getServer().getPluginManager().registerEvents(
                new CapeSlotGUIListener(skillCapeManager, skillManager, capeDataManager), this);
        getServer().getPluginManager().registerEvents(
                new GroupDifficultyListener(difficultyManager, this), this);
        getServer().getPluginManager().registerEvents(
                new BossEventListener(this, skillCapeManager, itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new MageGearCraftListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new MageGearEquipListener(itemFactory, skillManager), this);
        getServer().getPluginManager().registerEvents(
                new MagicCauldronCraftListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new CustomItemCraftListener(itemFactory, this), this);
        getServer().getPluginManager().registerEvents(
                new MeleeGearCraftListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new MeleeGearEquipListener(itemFactory, skillManager), this);
        getServer().getPluginManager().registerEvents(
                new RangedGearCraftListener(itemFactory), this);
        getServer().getPluginManager().registerEvents(
                new RangedGearEquipListener(itemFactory, skillManager), this);
        getServer().getPluginManager().registerEvents(
                new RangedSpeedListener(itemFactory, skillManager), this);

        // ── Gold currency system ───────────────────────────────────────────────
        this.goldManager = new GoldManager(this);
        getServer().getPluginManager().registerEvents(
                new GoldDropListener(goldManager, difficultyManager, this), this);
        getServer().getPluginManager().registerEvents(new GoldValueListener(), this);

        this.goldInventoryListener = new GoldInventoryListener(goldManager);
        getServer().getPluginManager().registerEvents(goldInventoryListener, this);

        this.vipShopListener = new VipShopListener(this, itemFactory, goldManager);
        getServer().getPluginManager().registerEvents(vipShopListener, this);

        getServer().getPluginManager().registerEvents(
                new RuneDropListener(itemFactory), this);

        this.gunZSwordListener = new GunZSwordListener(itemFactory, skillManager, this);
        getServer().getPluginManager().registerEvents(gunZSwordListener, this);

        getServer().getPluginManager().registerEvents(
                new DarkBowListener(itemFactory, skillManager, this), this);

        this.spellBookManager = new SpellBookManager(this);
        // ── Combo Favorites system — gates action bar hints to starred chains ───
        ComboFavoritesManager comboFavoritesManager = new ComboFavoritesManager(this);
        FavoritesGUI          favoritesGUI          = new FavoritesGUI(comboFavoritesManager, spellBookManager);
        SpellBookListener     spellBookListener     = new SpellBookListener(spellBookManager);
        spellBookListener.setFavoritesGUI(favoritesGUI);
        getServer().getPluginManager().registerEvents(spellBookListener, this);
        getServer().getPluginManager().registerEvents(
                new FavoritesGUIListener(favoritesGUI, comboFavoritesManager, spellBookManager, this), this);
        // Wire FavoritesManager into MagicStaffListener (must be wired after both created)
        magicStaffListener.setFavoritesManager(comboFavoritesManager);

        this.questManager = new QuestManager(this, goldManager, skillManager, itemFactory);
        this.questGUI     = new QuestGUI(questManager);
        getServer().getPluginManager().registerEvents(questGUI, this);

        this.npcQuestManager = new NpcQuestManager(this, goldManager);
        this.npcQuestSpawner = new NpcQuestSpawner(this);
        getServer().getPluginManager().registerEvents(npcQuestSpawner, this);
        getServer().getPluginManager().registerEvents(
                new NpcQuestListener(npcQuestManager, npcQuestSpawner), this);

        org.bukkit.command.PluginCommand questNpcCmd = getCommand("questnpc");
        if (questNpcCmd != null) {
            questNpcCmd.setExecutor(npcQuestSpawner);
            questNpcCmd.setTabCompleter(npcQuestSpawner);
        }

        getServer().getPluginManager().registerEvents(
                new QuestKillListener(questManager, npcQuestManager), this);

        this.partyManager  = new PartyManager();
        // Wire PartyManager into CastingEngine for support staff combo-gate
        castingEngine.setPartyManager(partyManager);
        this.partyListener = new PartyListener(partyManager, difficultyManager, this);
        getServer().getPluginManager().registerEvents(partyListener, this);
        // Wire PartyManager into MagicStaffListener for water-splash auto-heal
        magicStaffListener.setPartyManagerRef(partyManager);

        // ── /bring — party portal travel toggle ───────────────────────────────
        BringCommand bringCommand = new BringCommand(this, partyManager);
        getServer().getPluginManager().registerEvents(bringCommand, this);
        registerCmd("bring", bringCommand);

        this.tradeListener = new TradeListener(this);
        getServer().getPluginManager().registerEvents(tradeListener, this);

        // ── Magic Bag — freely moveable, survives death, right-click opens GUI ──
        this.magicBagManager = new MagicBagManager(this, itemFactory);
        this.magicBagGUI     = new MagicBagGUI(magicBagManager);
        // Wire bag manager into staff listener so Spell Combo Book is found even when bagged
        magicStaffListener.setMagicBagManager(magicBagManager);
        getServer().getPluginManager().registerEvents(
                new MagicBagGUIListener(magicBagManager, magicBagGUI, this), this);
        getServer().getPluginManager().registerEvents(
                new MagicBagChestInterceptListener(magicBagManager, this), this);
        // Death-proof: bag is removed from drops on death and restored on respawn
        getServer().getPluginManager().registerEvents(
                new MagicBagDeathListener(magicBagManager, this), this);

        // ── Night spawn boost + Nightmare party ×10 ───────────────────────────
        getServer().getPluginManager().registerEvents(
                new NightSpawnBoostListener(this, difficultyManager, partyManager), this);

        // ── Ancient Debris Portal ──────────────────────────────────────────────
        this.ancientPortalListener = new AncientDebrisPortalListener(this, skillManager, itemFactory);
        getServer().getPluginManager().registerEvents(ancientPortalListener, this);
        this.magicStaffListener.setPortalListener(ancientPortalListener);
        ancientPortalListener.setBringCommand(bringCommand);

        // ── Nightmare Hardcore Mode ────────────────────────────────────────────
        this.hardcoreListener = new NightmareHardcoreListener(
                this, difficultyManager, skillManager, goldManager);
        getServer().getPluginManager().registerEvents(hardcoreListener, this);

        // ── Custom Monster Registry ────────────────────────────────────────────
        this.customMonsterManager = new CustomMonsterManager(this);
        getServer().getPluginManager().registerEvents(
                new CustomMonsterDropListener(this, customMonsterManager), this);

        // ── Dungeon Bosses ─────────────────────────────────────────────────────
        // CrimsonBossManager: The Infernal Blazefiend at Crimson Pit (-108,-26,-14)
        // BossSpawnerCommand: /spawnboss tempest | crimson
        this.crimsonBossManager = new CrimsonBossManager(this, itemFactory, bossEffectListener);
        getServer().getPluginManager().registerEvents(crimsonBossManager, this);

        // ── Register commands ─────────────────────────────────────────────────
        registerCmd("difficulty", new DifficultyCommand(difficultyManager));
        registerCmd("gear",       new GearCommand());
        registerCmd("hpbar",      new HpBarCommand(difficultyManager));
        registerCmd("sit",        new SitCommand(sitListener));

        registerCmd("registry", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Item Registry.");
                return true;
            }
            // Registry is the admin spawn list — requires admin permission
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§8[§6DifficultyEngine§8] §c✗ §7The Item Registry is §4Admin Only§c.");
                player.sendMessage("§8  Permission: §fdifficultyengine.cape.admin");
                return true;
            }
            registryGUI.open(player);
            return true;
        });

        registerCmd("curecosmetic", new CureCosmeticCommand());

        this.adminLightCommand = new AdminLightCommand(this);
        registerCmd("adminlight", adminLightCommand);

        registerCmd("lightningadmin", lightningAdminCommand);

        SkillLvlCommand skillLvlCmd = new SkillLvlCommand(skillManager);
        org.bukkit.command.PluginCommand skilllvlPluginCmd = getCommand("skilllvl");
        if (skilllvlPluginCmd != null) {
            skilllvlPluginCmd.setExecutor(skillLvlCmd);
            skilllvlPluginCmd.setTabCompleter(skillLvlCmd);
        }

        registerCmd("skills", new SkillCommand(skillManager, skillGUI, false));
        SkillCommand guiCmd = new SkillCommand(skillManager, skillGUI, true);
        registerCmd("mystats", guiCmd);
        registerCmd("stats",   guiCmd);

        CapeSlotGUI capeGui = new CapeSlotGUI(skillCapeManager, capeDataManager);
        registerCmd("cape", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return true; }
            capeGui.open(p); return true;
        });
        registerCmd("mycape", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cPlayers only."); return true; }
            capeGui.open(p); return true;
        });

        registerCmd("gold", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /gold."); return true;
            }
            long bal = goldManager.getBalance(player.getUniqueId());
            player.sendMessage("§6✦ §7Balance: §e" + GoldManager.formatGold(bal)
                    + " gp §8— use §6/inventory §8to view your vault.");
            return true;
        });

        registerCmd("inventory", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /inventory."); return true;
            }
            goldInventoryListener.openVault(player);
            return true;
        });

        registerCmd("questbook", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Quest Book."); return true;
            }
            questGUI.open(player); return true;
        });

        registerCmd("party", partyListener);
        registerCmd("trade", tradeListener);

        registerCmd("vipshop", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /vipshop."); return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
                if (!player.hasPermission("difficultyengine.cape.admin")) {
                    player.sendMessage("§cNo permission."); return true;
                }
                vipShopListener.spawnVipKeeper(player.getLocation());
                player.sendMessage("§6✦ §7VIP Shop Keeper spawned!"); return true;
            }
            player.sendMessage("§6Usage: §e/vipshop spawn §8(Admin)");
            return true;
        });

        registerCmd("spellbook", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /spellbook."); return true;
            }
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§c✗ §7Craft an §5Arcane Tome §7to read spells.");
                return true;
            }
            player.openBook(spellBookManager.buildBookForPlayer(player.getUniqueId()));
            return true;
        });

        registerCmd("spellpage", (sender, cmd, label, args) -> {
            Player target;
            if (args.length > 0) {
                target = getServer().getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
            } else if (sender instanceof Player p) { target = p; }
            else { sender.sendMessage("§cUsage: /spellpage [player]"); return true; }
            target.getInventory().addItem(spellBookManager.buildSpellPageItem());
            target.sendMessage("§d✧ §7You received a §dSpell Page§7!");
            return true;
        });

        registerCmd("magicbag", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /magicbag."); return true;
            }
            magicBagGUI.open(player, 0); return true;
        });

        registerCmd("givebag", (sender, cmd, label, args) -> {
            Player target;
            if (args.length > 0) {
                target = getServer().getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
            } else if (sender instanceof Player p) { target = p; }
            else { sender.sendMessage("§cUsage: /givebag [player]"); return true; }
            // Prevent duplicates — one Magic Bag per player
            for (org.bukkit.inventory.ItemStack s : target.getInventory().getContents()) {
                if (itemFactory.isMagicBag(s)) {
                    sender.sendMessage("§c✗ §e" + target.getName() + " §7already has a Magic Bag.");
                    return true;
                }
            }
            target.getInventory().addItem(magicBagManager.buildMagicBag());
            target.sendMessage("§5✦ §7You received a §dMagic Bag§7!");
            return true;
        });

        registerCmd("commands", (sender, cmd, label, args) -> {
            if (sender instanceof Player player) {
                sendWelcomeMessage(player);
            } else {
                sender.sendMessage("§7Use /commands as a player.");
            }
            return true;
        });

        registerCmd("spawnmob", (sender, cmd, label, args) -> {
            if (!sender.hasPermission("difficultyengine.cape.admin")) {
                sender.sendMessage("§cNo permission."); return true;
            }
            if (args.length == 0) {
                sender.sendMessage("§7Available monsters: §e"
                        + String.join("§7, §e", customMonsterManager.getDefinitions().keySet()));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /spawnmob."); return true;
            }
            var mob = customMonsterManager.spawn(args[0], player.getLocation());
            if (mob != null) {
                sender.sendMessage("§a✓ §7Spawned §e" + args[0] + " §7at your location.");
                var def = customMonsterManager.getDefinition(args[0]);
                if (def != null && def.effects().contains("LEACHED_AURA")) {
                    bossEffectListener.registerBoss(mob);
                    bossEffectListener.spawnShriek(mob);
                }
            } else {
                sender.sendMessage("§c✗ §7Unknown monster: §e" + args[0]);
                sender.sendMessage("§7Available: §e"
                        + String.join("§7, §e", customMonsterManager.getDefinitions().keySet()));
            }
            return true;
        });

        // ── /spawnboss — Dungeon boss spawner ─────────────────────────────────
        BossSpawnerCommand bossSpawnerCmd =
                new BossSpawnerCommand(this, crimsonBossManager, bossEffectListener);
        org.bukkit.command.PluginCommand spawnBossPluginCmd = getCommand("spawnboss");
        if (spawnBossPluginCmd != null) {
            spawnBossPluginCmd.setExecutor(bossSpawnerCmd);
            spawnBossPluginCmd.setTabCompleter(bossSpawnerCmd);
        }

        registerCmd("hardcore", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /hardcore."); return true;
            }
            hardcoreListener.confirmActivation(player);
            return true;
        });

        // ── Support Staff recipe ───────────────────────────────────────────────
        NamespacedKey supportStaffKey = new NamespacedKey(this, "support_staff_recipe");
        ShapelessRecipe staffRecipe = new ShapelessRecipe(supportStaffKey,
                castingEngine.buildSupportStaff());
        staffRecipe.addIngredient(Material.BOOK);
        staffRecipe.addIngredient(Material.NETHER_STAR);
        staffRecipe.addIngredient(Material.BLAZE_ROD);
        staffRecipe.addIngredient(Material.PRISMARINE_CRYSTALS);
        staffRecipe.addIngredient(Material.EMERALD);
        staffRecipe.addIngredient(Material.FEATHER);
        getServer().addRecipe(staffRecipe);
        allRecipeKeys.add(supportStaffKey);

        registerCraftingRecipes();
        registerRecipeDiscovery();

        // ── Scheduled tasks ───────────────────────────────────────────────────
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 300L, 300L);

        this.partyHudTask = new PartyHudTask(partyManager, difficultyManager, this);
        partyHudTask.runTaskTimer(this, 20L, 20L);

        new CapeVisualTask(skillCapeManager, capeDataManager, this).cleanup();
        this.capeVisualTask = new CapeVisualTask(skillCapeManager, capeDataManager, this);
        capeVisualTask.runTaskTimer(this, 10L, 10L);

        new MagicGlowTask(itemFactory, skillManager, this).runTaskTimer(this, 5L, 4L);
        new BossQuestCapeTask(this).runTaskTimer(this, 10L, 10L);

        getServer().getScheduler().runTaskLater(this,
                () -> npcQuestSpawner.restoreMissingNpcs(), 60L);

        for (Player p : getServer().getOnlinePlayers()) {
            difficultyManager.syncNightmareTag(p, difficultyManager.getDifficulty(p.getUniqueId()));
            p.discoverRecipes(allRecipeKeys);
        }

        getLogger().info("DifficultyEngine v2.0 Ready!");
        getLogger().info("  Magic Bag: death-proof, freely moveable, right-click to open");
        getLogger().info("  Bosses: /spawnboss tempest | crimson");
        getLogger().info("  Custom Monsters: /spawnmob ("
                + customMonsterManager.getDefinitionCount() + " definitions)");
    }

    @Override
    public void onDisable() {
        if (difficultyManager  != null) difficultyManager.saveAll();
        if (skillManager       != null) skillManager.saveAll();
        if (adminLightCommand      != null) adminLightCommand.disableAll();
        if (lightningAdminCommand  != null) lightningAdminCommand.disableAll();
        if (capeVisualTask     != null) capeVisualTask.cleanup();
        if (capeDataManager    != null) capeDataManager.saveAll();
        if (sandstormManager   != null) sandstormManager.shutdown();
        if (partyHudTask       != null) partyHudTask.cleanup();
        if (spellBookManager   != null) spellBookManager.save();
        if (goldManager        != null) goldManager.saveAll();
        if (questManager       != null) questManager.saveAll();
        if (vipShopListener    != null) vipShopListener.shutdown();
        if (gunZSwordListener  != null) gunZSwordListener.shutdown();
        if (magicBagManager    != null) magicBagManager.saveAll();
        if (npcQuestManager    != null) npcQuestManager.saveAll();
        if (hardcoreListener   != null) hardcoreListener.save();
        if (crimsonBossManager != null) crimsonBossManager.cleanup();
        for (Player p : getServer().getOnlinePlayers()) {
            SkillBonusManager.removeDefenceHpBonus(p);
        }
        getLogger().info("DifficultyEngine: Data saved. Goodbye.");
    }

    // ── Welcome message ───────────────────────────────────────────────────────

    public void sendWelcomeMessage(Player player) {
        player.sendMessage("§6§m════════════════════════════════");
        player.sendMessage("§e✦ §6§lWelcome back, §e" + player.getName() + "§6§l!");
        player.sendMessage("§7Your commands:");
        player.sendMessage("§8 • §e/skills §8— §7View your skill levels");
        player.sendMessage("§8 • §e/mystats §8— §7Open stats GUI");
        player.sendMessage("§8 • §e/questbook §8— §7Open quest journal");
        player.sendMessage("§8 • §e/magicbag §8— §7Open your Magic Bag (or right-click it)");
        player.sendMessage("§8 • §e/inventory §8— §7Open your Gold Vault");
        player.sendMessage("§8 • §e/party §8— §7Manage your party");
        player.sendMessage("§8 • §e/cape §8— §7Open Cape Wardrobe");
        player.sendMessage("§8 • §e/difficulty §8— §7Change difficulty");
        player.sendMessage("§8 • §e/commands §8— §7Show this message again");
        player.sendMessage("");

        TextComponent prefix = new TextComponent("§5§l✦ §7Join our Discord: ");
        TextComponent link   = new TextComponent("§b§l[Click Here to Join]");
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://discord.gg/SreKERPhNB"));
        link.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                new net.md_5.bungee.api.chat.ComponentBuilder(
                        "§7Click to open Discord invite in your browser\n§b§l→ discord.gg/SreKERPhNB"
                ).create()));
        player.spigot().sendMessage(prefix, link);
        player.sendMessage("§6§m════════════════════════════════");
    }

    // ── Crafting recipes ──────────────────────────────────────────────────────

    private void registerCraftingRecipes() {
        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(this, el.staffKey + "_recipe");
            ItemStack staffResult = itemFactory.buildStaff(el);
            ShapelessRecipe recipe = new ShapelessRecipe(key, staffResult);
            recipe.addIngredient(Material.AMETHYST_SHARD);
            recipe.addIngredient(el.staffCraftIngredient);
            recipe.addIngredient(Material.STICK);
            getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(this, el.runeKey + "_recipe");
            ItemStack runeResult = itemFactory.buildRune(el, 8);
            ShapelessRecipe recipe = new ShapelessRecipe(key, runeResult);
            recipe.addIngredient(4, el.runeCraftIngredient);
            getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        NamespacedKey dragonArrowRecipe = new NamespacedKey(this, "dragon_arrow_recipe");
        ItemStack dragonArrowResult = itemFactory.buildDragonArrow(4);
        ShapelessRecipe dragonArrowR = new ShapelessRecipe(dragonArrowRecipe, dragonArrowResult);
        dragonArrowR.addIngredient(4, Material.PRISMARINE_CRYSTALS);
        getServer().addRecipe(dragonArrowR);
        allRecipeKeys.add(dragonArrowRecipe);

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","mage_hood_recipe"},{"LEATHER_CHESTPLATE","mage_robe_top_recipe"},
            {"LEATHER_LEGGINGS","mage_robe_bottom_recipe"},{"LEATHER_BOOTS","mage_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.PURPLE_DYE); r.addIngredient(Material.BLAZE_POWDER);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","apprentice_hood_recipe"},{"LEATHER_CHESTPLATE","apprentice_top_recipe"},
            {"LEATHER_LEGGINGS","apprentice_bottom_recipe"},{"LEATHER_BOOTS","apprentice_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.PURPLE_DYE); r.addIngredient(Material.STRING);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","alch_hood_recipe"},{"LEATHER_CHESTPLATE","alch_top_recipe"},
            {"LEATHER_LEGGINGS","alch_bottom_recipe"},{"LEATHER_BOOTS","alch_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.BLUE_DYE);
            r.addIngredient(Material.BLAZE_POWDER); r.addIngredient(Material.ENDER_EYE);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] entry : new String[][]{
            {"LEATHER_HELMET","master_hood_recipe"},{"LEATHER_CHESTPLATE","master_top_recipe"},
            {"LEATHER_LEGGINGS","master_bottom_recipe"},{"LEATHER_BOOTS","master_boots_recipe"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.BLACK_DYE);
            r.addIngredient(Material.BLAZE_POWDER); r.addIngredient(Material.AMETHYST_SHARD);
            r.addIngredient(Material.DRAGON_BREATH);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        addCauldronRecipe("cauldron_fire_basic",    16, Material.LAVA_BUCKET,  4, Material.NETHERRACK);
        addCauldronRecipe("cauldron_fire_premium",   64, Material.LAVA_BUCKET,  4, Material.NETHERRACK);
        addCauldronRecipe("cauldron_water_basic",   16, Material.WATER_BUCKET, 4, Material.PRISMARINE_SHARD);
        addCauldronRecipe("cauldron_water_premium",  64, Material.WATER_BUCKET, 4, Material.PRISMARINE_SHARD);
        addCauldronRecipe("cauldron_earth_basic",   16, Material.WATER_BUCKET, 4, Material.DIRT);
        addCauldronRecipe("cauldron_earth_premium",  64, Material.WATER_BUCKET, 4, Material.DIRT);

        NamespacedKey cab = new NamespacedKey(this, "cauldron_air_basic");
        ShapelessRecipe cabR = new ShapelessRecipe(cab, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cabR.addIngredient(Material.CAULDRON); cabR.addIngredient(Material.PUFFERFISH); cabR.addIngredient(Material.WATER_BUCKET);
        getServer().addRecipe(cabR); allRecipeKeys.add(cab);

        NamespacedKey capP = new NamespacedKey(this, "cauldron_air_premium");
        ShapelessRecipe capR = new ShapelessRecipe(capP, new ItemStack(Material.GLOWSTONE_DUST, 64));
        capR.addIngredient(Material.CAULDRON); capR.addIngredient(Material.PUFFERFISH);
        capR.addIngredient(Material.WATER_BUCKET); capR.addIngredient(Material.DIAMOND);
        getServer().addRecipe(capR); allRecipeKeys.add(capP);

        for (String[] e : new String[][]{
            {"IRON_HELMET","melee_iron_helmet"},{"IRON_CHESTPLATE","melee_iron_chestplate"},
            {"IRON_LEGGINGS","melee_iron_leggings"},{"IRON_BOOTS","melee_iron_boots"}
        }) { addMeleeRecipe(e[0], e[1], Material.IRON_INGOT); }
        for (String[] e : new String[][]{
            {"DIAMOND_HELMET","melee_diamond_helmet"},{"DIAMOND_CHESTPLATE","melee_diamond_chestplate"},
            {"DIAMOND_LEGGINGS","melee_diamond_leggings"},{"DIAMOND_BOOTS","melee_diamond_boots"}
        }) { addMeleeRecipe(e[0], e[1], Material.DIAMOND); }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","melee_netherite_helmet"},{"NETHERITE_CHESTPLATE","melee_netherite_chestplate"},
            {"NETHERITE_LEGGINGS","melee_netherite_leggings"},{"NETHERITE_BOOTS","melee_netherite_boots"}
        }) { addMeleeRecipe(e[0], e[1], Material.NETHERITE_INGOT); }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","melee_dragon_helmet"},{"NETHERITE_CHESTPLATE","melee_dragon_chestplate"},
            {"NETHERITE_LEGGINGS","melee_dragon_leggings"},{"NETHERITE_BOOTS","melee_dragon_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(this, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.DRAGON_BREATH);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        for (String[] e : new String[][]{
            {"LEATHER_HELMET","ranged_leather_helmet"},{"LEATHER_CHESTPLATE","ranged_leather_chestplate"},
            {"LEATHER_LEGGINGS","ranged_leather_leggings"},{"LEATHER_BOOTS","ranged_leather_boots"}
        }) { addRangedRecipe(e[0], e[1], Material.STRING); }
        for (String[] e : new String[][]{
            {"CHAINMAIL_HELMET","ranged_chain_helmet"},{"CHAINMAIL_CHESTPLATE","ranged_chain_chestplate"},
            {"CHAINMAIL_LEGGINGS","ranged_chain_leggings"},{"CHAINMAIL_BOOTS","ranged_chain_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(this, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.FEATHER); r.addIngredient(Material.LAPIS_LAZULI);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","ranged_netherite_helmet"},{"NETHERITE_CHESTPLATE","ranged_netherite_chestplate"},
            {"NETHERITE_LEGGINGS","ranged_netherite_leggings"},{"NETHERITE_BOOTS","ranged_netherite_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(this, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHERITE_INGOT); r.addIngredient(Material.FEATHER);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] e : new String[][]{
            {"NETHERITE_HELMET","ranged_dragon_helmet"},{"NETHERITE_CHESTPLATE","ranged_dragon_chestplate"},
            {"NETHERITE_LEGGINGS","ranged_dragon_leggings"},{"NETHERITE_BOOTS","ranged_dragon_boots"}
        }) {
            Material mat = Material.valueOf(e[0]); NamespacedKey k = new NamespacedKey(this, e[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.ARROW);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        NamespacedKey atk = new NamespacedKey(this, "arcane_tome_recipe");
        ShapelessRecipe atr = new ShapelessRecipe(atk, spellBookManager.buildArcaneTomeItem());
        atr.addIngredient(Material.BOOK); atr.addIngredient(Material.AMETHYST_SHARD); atr.addIngredient(Material.PURPLE_DYE);
        getServer().addRecipe(atr); allRecipeKeys.add(atk);

        NamespacedKey spk = new NamespacedKey(this, "soulfur_potion_recipe");
        ShapelessRecipe spr = new ShapelessRecipe(spk, new ItemStack(Material.POTION));
        spr.addIngredient(Material.GLASS_BOTTLE); spr.addIngredient(Material.SOUL_SAND);
        spr.addIngredient(Material.BLAZE_POWDER); spr.addIngredient(Material.NETHER_WART);
        getServer().addRecipe(spr); allRecipeKeys.add(spk);

        NamespacedKey tmk = new NamespacedKey(this, "turbo_minecart_recipe");
        ShapelessRecipe tmr = new ShapelessRecipe(tmk, new ItemStack(Material.MINECART));
        tmr.addIngredient(Material.MINECART); tmr.addIngredient(Material.POWERED_RAIL);
        tmr.addIngredient(Material.REDSTONE); tmr.addIngredient(Material.GOLD_INGOT);
        getServer().addRecipe(tmr); allRecipeKeys.add(tmk);

        NamespacedKey mbk = new NamespacedKey(this, "magic_bag_recipe");
        ShapelessRecipe mbr = new ShapelessRecipe(mbk, new ItemStack(Material.CHEST));
        mbr.addIngredient(Material.CHEST); mbr.addIngredient(Material.ENDER_PEARL);
        mbr.addIngredient(Material.AMETHYST_SHARD); mbr.addIngredient(Material.PURPLE_DYE);
        mbr.addIngredient(Material.STRING);
        getServer().addRecipe(mbr); allRecipeKeys.add(mbk);

        for (EarthBlockTier tier : EarthBlockTier.values()) {
            NamespacedKey pk = new NamespacedKey(this, "de_earth_page_recipe_" + tier.name().toLowerCase());
            ShapelessRecipe pr = new ShapelessRecipe(pk, new ItemStack(Material.BOOK));
            pr.addIngredient(Material.BOOK); pr.addIngredient(tier.material); pr.addIngredient(Material.STRING);
            getServer().addRecipe(pr);
        }

        addBookRecipe("novice_magic_primer_recipe", itemFactory.buildNoviceMagicPrimer(),
                Material.BOOK, Material.PAPER, Material.FEATHER);
        addBookRecipe("mages_primer_recipe", itemFactory.buildMagesPrimerBook(),
                Material.BOOK, Material.PAPER, Material.BLAZE_POWDER);
        addBookRecipe("elemental_theory_recipe", itemFactory.buildElementalTheoryBook(),
                Material.BOOK, Material.AMETHYST_SHARD, Material.PAPER);
        addBookRecipe("hidden_arts_recipe", itemFactory.buildHiddenArtsBook(),
                Material.BOOK, Material.NETHER_STAR, Material.PAPER);
        addBookRecipe("mage_gear_guide_recipe", itemFactory.buildMageGearGuide(),
                Material.BOOK, Material.LEATHER, Material.PURPLE_DYE);

        // ── Empty Magic Bottle — lightning capture vessel ──────────────────────
        // Recipe: 4× Glass Pane + Leather + String + Enchanted Book → 1 Empty Magic Bottle
        NamespacedKey emptyBottleKey = new NamespacedKey(this, "empty_magic_bottle_recipe");
        ShapelessRecipe emptyBottleRecipe = new ShapelessRecipe(emptyBottleKey,
                new ItemStack(Material.GLASS_BOTTLE));
        emptyBottleRecipe.addIngredient(4, Material.GLASS_PANE);
        emptyBottleRecipe.addIngredient(Material.LEATHER);
        emptyBottleRecipe.addIngredient(Material.STRING);
        emptyBottleRecipe.addIngredient(Material.ENCHANTED_BOOK);
        getServer().addRecipe(emptyBottleRecipe);
        allRecipeKeys.add(emptyBottleKey);

        getLogger().info("DifficultyEngine: Registered " + allRecipeKeys.size() + " crafting recipes.");
    }

    private void registerRecipeDiscovery() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                getServer().getScheduler().runTaskLater(Main.this, () -> {
                    if (!player.isOnline()) return;
                    player.discoverRecipes(allRecipeKeys);
                    sendWelcomeMessage(player);
                }, 20L);
            }
        }, this);
    }

    // ── Crafting helpers ──────────────────────────────────────────────────────

    private void addCauldronRecipe(String key, int amount, Material bucket, int qty, Material filler) {
        NamespacedKey k = new NamespacedKey(this, key);
        ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(Material.GLOWSTONE_DUST, amount));
        r.addIngredient(Material.CAULDRON); r.addIngredient(bucket); r.addIngredient(qty, filler);
        if (key.contains("premium")) r.addIngredient(Material.DIAMOND);
        getServer().addRecipe(r); allRecipeKeys.add(k);
    }

    private void addMeleeRecipe(String matName, String keyName, Material ingredient) {
        Material mat = Material.valueOf(matName); NamespacedKey k = new NamespacedKey(this, keyName);
        ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
        r.addIngredient(mat); r.addIngredient(ingredient);
        getServer().addRecipe(r); allRecipeKeys.add(k);
    }

    private void addRangedRecipe(String matName, String keyName, Material ingredient) {
        addMeleeRecipe(matName, keyName, ingredient);
    }

    private void addBookRecipe(String keyName, ItemStack result, Material... ingredients) {
        NamespacedKey k = new NamespacedKey(this, keyName);
        ShapelessRecipe r = new ShapelessRecipe(k, result);
        for (Material m : ingredients) r.addIngredient(m);
        getServer().addRecipe(r); allRecipeKeys.add(k);
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public ItemFactory    getItemFactory()    { return itemFactory; }
    public EffectRegistry getEffectRegistry() { return effectRegistry; }
    public CastingEngine  getCastingEngine()  { return castingEngine; }
    public NightmareHardcoreListener getHardcoreListener() { return hardcoreListener; }
    public CustomMonsterManager getCustomMonsterManager()  { return customMonsterManager; }

    // ── Null-safe command registration ────────────────────────────────────────

    private void registerCmd(String name, org.bukkit.command.CommandExecutor exec) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(exec);
        } else {
            getLogger().warning("Command '" + name + "' not found in plugin.yml — skipping.");
        }
    }
}
