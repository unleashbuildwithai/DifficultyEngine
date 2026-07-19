 package com.yourname.difficulty;

import com.yourname.difficulty.bag.MagicBagGUI;
import com.yourname.difficulty.bag.MagicBagGUIListener;
import com.yourname.difficulty.bag.MagicBagManager;
import com.yourname.difficulty.currency.GoldDropListener;
import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.currency.GoldValueListener;
import com.yourname.difficulty.gui.CapeSlotGUI;
import com.yourname.difficulty.gui.CapeSlotGUIListener;
import com.yourname.difficulty.gui.RegistryGUI;
import com.yourname.difficulty.gui.RegistryGUIListener;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.listeners.BossEventListener;
import com.yourname.difficulty.listeners.CapeVisualTask;
import com.yourname.difficulty.listeners.DarkBowListener;
import com.yourname.difficulty.listeners.GunZSwordListener;
import com.yourname.difficulty.listeners.MagicGlowTask;
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.GroupDifficultyListener;
import com.yourname.difficulty.listeners.LevelProtectionListener;
import com.yourname.difficulty.items.MageGearTier;
import com.yourname.difficulty.listeners.MageGearCraftListener;
import com.yourname.difficulty.listeners.MageGearEquipListener;
import com.yourname.difficulty.listeners.MeleeGearCraftListener;
import com.yourname.difficulty.listeners.MeleeGearEquipListener;
import com.yourname.difficulty.listeners.RangedGearCraftListener;
import com.yourname.difficulty.listeners.RangedGearEquipListener;
import com.yourname.difficulty.listeners.RangedSpeedListener;
import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.listeners.CustomItemCraftListener;
import com.yourname.difficulty.listeners.MagicCauldronCraftListener;
import com.yourname.difficulty.vip.VipShopListener;
import com.yourname.difficulty.listeners.MinecartListener;
import com.yourname.difficulty.listeners.NightmareAggroListener;
import com.yourname.difficulty.listeners.PrayerListener;
import com.yourname.difficulty.listeners.SitListener;
import com.yourname.difficulty.listeners.SoulfurPotionListener;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.magic.MagicStaffListener;
import com.yourname.difficulty.magic.RuneDropListener;
import com.yourname.difficulty.magic.SandstormManager;
import com.yourname.difficulty.skills.CapeDataManager;
import com.yourname.difficulty.magic.SpellBookListener;
import com.yourname.difficulty.magic.SpellBookManager;
import com.yourname.difficulty.party.PartyHudTask;
import com.yourname.difficulty.party.PartyListener;
import com.yourname.difficulty.party.PartyManager;
import com.yourname.difficulty.quests.QuestGUI;
import com.yourname.difficulty.quests.QuestKillListener;
import com.yourname.difficulty.quests.QuestManager;
import com.yourname.difficulty.skills.CapeEquipListener;
import com.yourname.difficulty.skills.ItemLevelListener;
import com.yourname.difficulty.skills.SkillBonusManager;
import com.yourname.difficulty.skills.SkillCombatListener;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.quests.BossQuestCapeTask;
import com.yourname.difficulty.quests.NpcQuestListener;
import com.yourname.difficulty.quests.NpcQuestManager;
import com.yourname.difficulty.quests.NpcQuestSpawner;
import com.yourname.difficulty.skills.SkillCommand;
import com.yourname.difficulty.skills.SkillLvlCommand;
import com.yourname.difficulty.skills.SkillGUI;
import com.yourname.difficulty.skills.SkillGUIListener;
import com.yourname.difficulty.skills.SkillListener;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.trade.TradeListener;
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

    private PlayerDifficultyManager difficultyManager;
    private ItemFactory             itemFactory;
    private RegistryGUI             registryGUI;
    private SitListener             sitListener;
    private SkillManager            skillManager;
    private SkillCapeManager        skillCapeManager;
    private AdminLightCommand       adminLightCommand;
    private CapeVisualTask          capeVisualTask;
    private CapeDataManager         capeDataManager;
    private SandstormManager        sandstormManager;
    private MagicStaffListener      magicStaffListener;
    // ── New systems ────────────────────────────────────────────────────────────
    private SpellBookManager spellBookManager;
    private GoldManager    goldManager;
    private QuestManager   questManager;
    private PartyManager   partyManager;
    private PartyHudTask   partyHudTask;
    private PartyListener  partyListener;
    private QuestGUI       questGUI;
    private TradeListener  tradeListener;
    private VipShopListener    vipShopListener;
    private GunZSwordListener  gunZSwordListener;
    // ── Magic Bag ──────────────────────────────────────────────────────────────
    private MagicBagManager    magicBagManager;
    private MagicBagGUI        magicBagGUI;
    // ── NPC Quest System ───────────────────────────────────────────────────────
    private NpcQuestManager  npcQuestManager;
    private NpcQuestSpawner  npcQuestSpawner;

    /** All crafting recipe keys registered by this plugin — used for recipe-book discovery. */
    private final List<NamespacedKey> allRecipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        getLogger().info("DifficultyEngine: Initializing...");

        // ── Core managers ─────────────────────────────────────────────────────
        this.difficultyManager = new PlayerDifficultyManager(this);
        this.skillManager      = new SkillManager(this);
        this.skillCapeManager  = new SkillCapeManager(this);
        // Cape data stored separately from the chestplate slot (wear both!)
        this.capeDataManager   = new CapeDataManager(this);
        getServer().getPluginManager().registerEvents(capeDataManager, this);

        // ── Item & GUI layer ──────────────────────────────────────────────────
        this.itemFactory = new ItemFactory(this, skillCapeManager);
        this.registryGUI = new RegistryGUI(itemFactory);

        // ── Skill GUI ─────────────────────────────────────────────────────────
        SkillGUI skillGUI = new SkillGUI(skillManager);

        // ── Register listeners ────────────────────────────────────────────────

        getServer().getPluginManager().registerEvents(
                new DifficultyEngine(this, difficultyManager), this);

        getServer().getPluginManager().registerEvents(
                new SoulfurPotionListener(this, itemFactory, difficultyManager), this);

        getServer().getPluginManager().registerEvents(
                new NightmareAggroListener(difficultyManager), this);

        getServer().getPluginManager().registerEvents(
                new RegistryGUIListener(itemFactory, registryGUI), this);

        this.sitListener = new SitListener();
        getServer().getPluginManager().registerEvents(sitListener, this);

        getServer().getPluginManager().registerEvents(
                new MinecartListener(itemFactory), this);

        // SkillListener now receives ItemFactory for Enchanted Shard drops
        getServer().getPluginManager().registerEvents(
                new SkillListener(this, skillManager, skillCapeManager, difficultyManager, itemFactory), this);

        getServer().getPluginManager().registerEvents(
                new SkillGUIListener(), this);

        getServer().getPluginManager().registerEvents(
                new CapeEquipListener(skillManager, skillCapeManager), this);

        getServer().getPluginManager().registerEvents(
                new ItemLevelListener(skillManager), this);

        getServer().getPluginManager().registerEvents(
                new SkillCombatListener(skillManager, this), this);

        // Prayer: bone-burying XP + hit-block chance
        getServer().getPluginManager().registerEvents(
                new PrayerListener(skillManager), this);

        // Magic staffs: casting, rune consumption, crafting validation
        this.magicStaffListener = new MagicStaffListener(itemFactory, skillManager, this);
        getServer().getPluginManager().registerEvents(magicStaffListener, this);
        // Sandstorm: triggered by Air bolt on Earth-on-Water quicksand
        this.sandstormManager = new SandstormManager(this);
        magicStaffListener.setSandstormManager(sandstormManager);
        getServer().getPluginManager().registerEvents(sandstormManager, this);

        // Level protection: OSRS combat-level PvP bracket + passive aura scan
        getServer().getPluginManager().registerEvents(
                new LevelProtectionListener(skillManager, this), this);

        // Cape wardrobe GUI (two-slot: armour + cape independently)
        getServer().getPluginManager().registerEvents(
                new CapeSlotGUIListener(skillCapeManager, skillManager, capeDataManager), this);

        // ── Group Nightmare difficulty (4+ NM players within 50 blocks → ×10) ─
        getServer().getPluginManager().registerEvents(
                new GroupDifficultyListener(difficultyManager, this), this);

        // ── Boss events: double spawn (1 %), boss-fight mobs, boss cape + kill tome ──
        getServer().getPluginManager().registerEvents(
                new BossEventListener(this, skillCapeManager, itemFactory), this);

        // ── Mage Gear crafting: replaces vanilla result with PDC item ──────────
        getServer().getPluginManager().registerEvents(
                new MageGearCraftListener(itemFactory), this);

        // ── Mage Gear equip: enforces Magic level requirements ─────────────────
        getServer().getPluginManager().registerEvents(
                new MageGearEquipListener(itemFactory, skillManager), this);

        // ── Magic Cauldron crafting: replaces placeholder with PDC Rune Dust ───
        getServer().getPluginManager().registerEvents(
                new MagicCauldronCraftListener(itemFactory), this);

        // ── Custom Item crafting: Soulfur Potion, Turbo Minecart, Magic Bag, Earth Pages ─
        // Also handles earth page recipe discovery on first pickup.
        getServer().getPluginManager().registerEvents(
                new CustomItemCraftListener(itemFactory, this), this);

        // ── Melee Gear crafting: replaces vanilla result with PDC item ──────────
        getServer().getPluginManager().registerEvents(
                new MeleeGearCraftListener(itemFactory), this);

        // ── Melee Gear equip: enforces Melee level requirements ─────────────────
        getServer().getPluginManager().registerEvents(
                new MeleeGearEquipListener(itemFactory, skillManager), this);

        // ── Ranged Gear crafting: replaces vanilla result with PDC item ─────────
        getServer().getPluginManager().registerEvents(
                new RangedGearCraftListener(itemFactory), this);

        // ── Ranged Gear equip: enforces Ranged level requirements ───────────────
        getServer().getPluginManager().registerEvents(
                new RangedGearEquipListener(itemFactory, skillManager), this);

        // ── Ranged Speed: scales arrow velocity + damage with Ranged level/gear ─
        // Level 1 → ~70% velocity (slow, sluggish arrows)
        // Level 99 + Dragon gear → ~168% velocity (fast, devastating arrows)
        getServer().getPluginManager().registerEvents(
                new RangedSpeedListener(itemFactory, skillManager), this);

        // ── Gold currency system ───────────────────────────────────────────────
        this.goldManager = new GoldManager(this);
        getServer().getPluginManager().registerEvents(
                new GoldDropListener(goldManager, difficultyManager, this), this);
        getServer().getPluginManager().registerEvents(new GoldValueListener(), this);

        // ── VIP Shop: villager NPC with gold-coin trades + Unicorn Slippers ─────
        // (must come after goldManager is initialized)
        this.vipShopListener = new VipShopListener(this, itemFactory, goldManager);
        getServer().getPluginManager().registerEvents(vipShopListener, this);

        // ── Rune mob drops (Air, Fire, Water, Earth from specific mobs) ────────
        getServer().getPluginManager().registerEvents(
                new RuneDropListener(itemFactory), this);

        // ── GunZ Sword: double-tap WASD dashing (Lv99 Melee) ──────────────────
        this.gunZSwordListener = new GunZSwordListener(itemFactory, skillManager, this);
        getServer().getPluginManager().registerEvents(gunZSwordListener, this);

        // ── Dark Bow: homing arrows + Dragon Arrow drops ───────────────────────
        getServer().getPluginManager().registerEvents(
                new DarkBowListener(itemFactory, skillManager, this), this);

        // ── Spell Book system (Arcane Tome + Spell Pages) ──────────────────────
        this.spellBookManager = new SpellBookManager(this);
        getServer().getPluginManager().registerEvents(
                new SpellBookListener(spellBookManager), this);

        // ── Quest system (legacy QuestType) ───────────────────────────────────
        this.questManager = new QuestManager(this, goldManager, skillManager, itemFactory);
        this.questGUI     = new QuestGUI(questManager);
        getServer().getPluginManager().registerEvents(questGUI, this);

        // ── NPC Quest System (300 quests — main + secret) ─────────────────────
        this.npcQuestManager = new NpcQuestManager(this, goldManager);
        this.npcQuestSpawner = new NpcQuestSpawner(this);
        getServer().getPluginManager().registerEvents(npcQuestSpawner, this);
        getServer().getPluginManager().registerEvents(
                new NpcQuestListener(npcQuestManager, npcQuestSpawner), this);

        // Wire /questnpc command
        org.bukkit.command.PluginCommand questNpcCmd = getCommand("questnpc");
        if (questNpcCmd != null) {
            questNpcCmd.setExecutor(npcQuestSpawner);
            questNpcCmd.setTabCompleter(npcQuestSpawner);
        } else {
            getLogger().warning("Command 'questnpc' not found in plugin.yml — skipping.");
        }

        // Kill tracking feeds both systems
        getServer().getPluginManager().registerEvents(
                new QuestKillListener(questManager, npcQuestManager), this);

        // ── Party system ───────────────────────────────────────────────────────
        this.partyManager  = new PartyManager();
        this.partyListener = new PartyListener(partyManager, difficultyManager, this);
        getServer().getPluginManager().registerEvents(partyListener, this);

        // ── Trade Stone ────────────────────────────────────────────────────────
        this.tradeListener = new TradeListener(this);
        getServer().getPluginManager().registerEvents(tradeListener, this);

        // ── Magic Bag ──────────────────────────────────────────────────────────
        this.magicBagManager = new MagicBagManager(this, itemFactory);
        this.magicBagGUI     = new MagicBagGUI(magicBagManager);
        getServer().getPluginManager().registerEvents(
                new MagicBagGUIListener(magicBagManager, magicBagGUI, this), this);

        // ── Register commands (null-safe) ─────────────────────────────────────

        registerCmd("difficulty", new DifficultyCommand(difficultyManager));
        registerCmd("gear",       new GearCommand());
        registerCmd("hpbar",      new HpBarCommand(difficultyManager));
        registerCmd("sit",        new SitCommand(sitListener));

        registerCmd("registry", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Item Registry.");
                return true;
            }
            registryGUI.open(player);
            return true;
        });

        registerCmd("curecosmetic", new CureCosmeticCommand());

        this.adminLightCommand = new AdminLightCommand(this);
        registerCmd("adminlight", adminLightCommand);

        // ── /skilllvl — Admin skill level editor ──────────────────────────────
        SkillLvlCommand skillLvlCmd = new SkillLvlCommand(skillManager);
        org.bukkit.command.PluginCommand skilllvlPluginCmd = getCommand("skilllvl");
        if (skilllvlPluginCmd != null) {
            skilllvlPluginCmd.setExecutor(skillLvlCmd);
            skilllvlPluginCmd.setTabCompleter(skillLvlCmd);
        } else {
            getLogger().warning("Command 'skilllvl' not found in plugin.yml — skipping.");
        }

        registerCmd("skills", new SkillCommand(skillManager, skillGUI, false));

        SkillCommand guiCmd = new SkillCommand(skillManager, skillGUI, true);
        registerCmd("mystats", guiCmd);
        registerCmd("stats",   guiCmd);

        CapeSlotGUI capeGui = new CapeSlotGUI(skillCapeManager, capeDataManager);
        registerCmd("cape", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Cape Wardrobe.");
                return true;
            }
            capeGui.open(player);
            return true;
        });

        // ── New commands ───────────────────────────────────────────────────────
        registerCmd("mycape", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
            capeGui.open(player);
            return true;
        });

        registerCmd("gold", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /gold.");
                return true;
            }
            long bal = goldManager.getBalance(player.getUniqueId());
            player.sendMessage("§6Your balance: §e" + GoldManager.formatGold(bal) + " gp");
            return true;
        });

        registerCmd("questbook", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Quest Book.");
                return true;
            }
            questGUI.open(player);
            return true;
        });

        registerCmd("party", partyListener);
        registerCmd("trade", tradeListener);

        // ── VIP Shop command ───────────────────────────────────────────────────
        registerCmd("vipshop", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /vipshop.");
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("spawn")) {
                if (!player.hasPermission("difficultyengine.cape.admin")) {
                    player.sendMessage("§cNo permission.");
                    return true;
                }
                vipShopListener.spawnVipKeeper(player.getLocation());
                player.sendMessage("§6✦ §7VIP Shop Keeper spawned at your location!");
                return true;
            }
            player.sendMessage("§6Usage: §e/vipshop spawn §8(Admin) §7— Spawns the VIP Shop villager.");
            player.sendMessage("§7Right-click the VIP Shop Keeper to browse cosmetics.");
            return true;
        });

        registerCmd("spellbook", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Arcane Tome.");
                return true;
            }
            // Admin-only shortcut — regular players craft an Arcane Tome to open theirs
            if (!player.hasPermission("difficultyengine.cape.admin")) {
                player.sendMessage("§c✗ §7/spellbook is admin-only.");
                player.sendMessage("§7Craft an §5Arcane Tome §8(Book + Amethyst Shard + Purple Dye)");
                player.sendMessage("§7to read your spell pages.");
                return true;
            }
            int count = spellBookManager.getUnlockedCount(player.getUniqueId());
            player.openBook(spellBookManager.buildBookForPlayer(player.getUniqueId()));
            player.sendActionBar("§5✦ §dArcane Tome §8— §7" + count + " §8/ §7"
                    + SpellBookManager.TOTAL_PAGES + " §dpages unlocked");
            return true;
        });

        registerCmd("spellpage", (sender, cmd, label, args) -> {
            Player target;
            if (args.length > 0) {
                target = getServer().getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[0]);
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("§cUsage: /spellpage [player]");
                return true;
            }
            target.getInventory().addItem(spellBookManager.buildSpellPageItem());
            target.sendMessage(
                "§d✧ §7You received a §dSpell Page§7! Right-click it to unlock a tome page.");
            if (!target.equals(sender))
                sender.sendMessage("§7Gave a §dSpell Page §7to §d" + target.getName() + "§7.");
            return true;
        });

        // ── Magic Bag commands ─────────────────────────────────────────────────
        registerCmd("magicbag", (sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open their Magic Bag.");
                return true;
            }
            magicBagGUI.open(player, 0);
            return true;
        });

        registerCmd("givebag", (sender, cmd, label, args) -> {
            Player target;
            if (args.length > 0) {
                target = getServer().getPlayerExact(args[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[0]);
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage("§cUsage: /givebag [player]");
                return true;
            }
            target.getInventory().addItem(magicBagManager.buildMagicBag());
            target.sendMessage("§5✦ §7You received a §dMagic Bag§7! Right-click to open it.");
            if (!target.equals(sender))
                sender.sendMessage("§7Gave a §dMagic Bag §7to §d" + target.getName() + "§7.");
            return true;
        });

        // ── Crafting recipes ──────────────────────────────────────────────────
        registerCraftingRecipes();

        // ── Recipe discovery on join (shows recipes in the recipe book) ───────
        registerRecipeDiscovery();

        // ── Scheduled tasks ───────────────────────────────────────────────────

        // Nightmare bonus-spawn — every 15 seconds (6 mobs, 64-128 block range)
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 300L, 300L);

        // Party HUD — update scoreboard sidebar every second
        this.partyHudTask = new PartyHudTask(partyManager, difficultyManager, this);
        partyHudTask.runTaskTimer(this, 20L, 20L);

        // Sweep any orphaned cape hologram stands left from a previous crash/reload
        new CapeVisualTask(skillCapeManager, capeDataManager, this).cleanup();

        // Cape visual particles + floating hologram symbol — every 10 ticks (0.5 s)
        this.capeVisualTask = new CapeVisualTask(skillCapeManager, capeDataManager, this);
        capeVisualTask.runTaskTimer(this, 10L, 10L);

        // Magic Item Glow — Lv99 Magic only — element particles — every 4 ticks
        new MagicGlowTask(itemFactory, skillManager, this).runTaskTimer(this, 5L, 4L);

        // Boss Quest Cape fire ring — every 10 ticks (0.5 s)
        new BossQuestCapeTask(this).runTaskTimer(this, 10L, 10L);

        // Restore any quest NPCs that might be missing after a reload (delayed 3 s)
        getServer().getScheduler().runTaskLater(this,
                () -> npcQuestSpawner.restoreMissingNpcs(), 60L);

        for (Player p : getServer().getOnlinePlayers()) {
            difficultyManager.syncNightmareTag(p, difficultyManager.getDifficulty(p.getUniqueId()));
            // Discover recipes for already-online players (reload scenario)
            p.discoverRecipes(allRecipeKeys);
        }

        getLogger().info("DifficultyEngine: Ready!");
        getLogger().info("  Players : /difficulty  /hpbar  /sit  /registry  /skills  /mystats  /stats  /cape");
        getLogger().info("  Admins  : /gear  /curecosmetic  /adminlight");
        getLogger().info("  Magic   : Right-click elemental staffs to cast spells.");
        getLogger().info("  Spell Book: Find Spell Pages from mobs (4%) or /spellpage. /spellbook to read.");
        getLogger().info("  Prayer  : Right-click bone on dirt to bury it for XP.");
        getLogger().info("  Mage Gear: Craft with LEATHER_PIECE + PURPLE_DYE + BLAZE_POWDER");
        getLogger().info("  Boss Cape: Defeat a Double Boss event without dying.");
        getLogger().info("  Group NM : 4+ nightmare players within 50 blocks → x10 difficulty/rewards");
    }

    @Override
    public void onDisable() {
        if (difficultyManager != null) difficultyManager.saveAll();
        if (skillManager      != null) skillManager.saveAll();
        if (adminLightCommand != null) adminLightCommand.disableAll();
        if (capeVisualTask    != null) capeVisualTask.cleanup();
        if (capeDataManager   != null) capeDataManager.saveAll();
        if (sandstormManager  != null) sandstormManager.shutdown();
        if (partyHudTask      != null) partyHudTask.cleanup();
        if (spellBookManager  != null) spellBookManager.save();
        if (goldManager       != null) goldManager.saveAll();
        if (questManager      != null) questManager.saveAll();
        if (vipShopListener   != null) vipShopListener.shutdown();
        if (gunZSwordListener != null) gunZSwordListener.shutdown();
        if (magicBagManager   != null) magicBagManager.saveAll();
        if (npcQuestManager   != null) npcQuestManager.saveAll();
        for (Player p : getServer().getOnlinePlayers()) {
            SkillBonusManager.removeDefenceHpBonus(p);
        }
        getLogger().info("DifficultyEngine: Data saved. Goodbye.");
    }

    // ── Crafting recipes ──────────────────────────────────────────────────────

    private void registerCraftingRecipes() {
        // ── Elemental Staff recipes ────────────────────────────────────────────
        // Each staff: Enchanted Shard (AMETHYST_SHARD) + element ingredient + STICK
        // Note: CraftItemEvent in MagicStaffListener verifies the shard has PDC.
        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(this, el.staffKey + "_recipe");
            ItemStack staffResult = itemFactory.buildStaff(el);
            ShapelessRecipe recipe = new ShapelessRecipe(key, staffResult);
            recipe.addIngredient(Material.AMETHYST_SHARD);       // Enchanted Shard base
            recipe.addIngredient(el.staffCraftIngredient);        // Element ingredient
            recipe.addIngredient(Material.STICK);                 // Handle
            getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        // ── Rune recipes: 4× base material → 8 runes ─────────────────────────
        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(this, el.runeKey + "_recipe");
            ItemStack runeResult = itemFactory.buildRune(el, 8);
            ShapelessRecipe recipe = new ShapelessRecipe(key, runeResult);
            recipe.addIngredient(4, el.runeCraftIngredient);      // 4× base material
            getServer().addRecipe(recipe);
            allRecipeKeys.add(key);
        }

        // ── Dragon Arrow: 4× Dragon Arrow Tips → 4 Dragon Arrows ─────────────
        NamespacedKey dragonArrowRecipe = new NamespacedKey(this, "dragon_arrow_recipe");
        ItemStack dragonArrowResult = itemFactory.buildDragonArrow(4);
        ShapelessRecipe dragonArrowR = new ShapelessRecipe(dragonArrowRecipe, dragonArrowResult);
        dragonArrowR.addIngredient(4, Material.PRISMARINE_CRYSTALS); // Dragon Arrow Tips by material
        getServer().addRecipe(dragonArrowR);
        allRecipeKeys.add(dragonArrowRecipe);

        // ── Mage Gear recipes: LEATHER_PIECE + PURPLE_DYE + BLAZE_POWDER ──────
        // MageGearCraftListener intercepts PrepareItemCraftEvent to replace the
        // vanilla leather result with the PDC-tagged, coloured Mage Gear item.

        NamespacedKey hoodKey = new NamespacedKey(this, "mage_hood_recipe");
        ShapelessRecipe hoodRecipe = new ShapelessRecipe(hoodKey, new ItemStack(Material.LEATHER_HELMET));
        hoodRecipe.addIngredient(Material.LEATHER_HELMET);
        hoodRecipe.addIngredient(Material.PURPLE_DYE);
        hoodRecipe.addIngredient(Material.BLAZE_POWDER);
        getServer().addRecipe(hoodRecipe);
        allRecipeKeys.add(hoodKey);

        NamespacedKey topKey = new NamespacedKey(this, "mage_robe_top_recipe");
        ShapelessRecipe topRecipe = new ShapelessRecipe(topKey, new ItemStack(Material.LEATHER_CHESTPLATE));
        topRecipe.addIngredient(Material.LEATHER_CHESTPLATE);
        topRecipe.addIngredient(Material.PURPLE_DYE);
        topRecipe.addIngredient(Material.BLAZE_POWDER);
        getServer().addRecipe(topRecipe);
        allRecipeKeys.add(topKey);

        NamespacedKey bottomKey = new NamespacedKey(this, "mage_robe_bottom_recipe");
        ShapelessRecipe bottomRecipe = new ShapelessRecipe(bottomKey, new ItemStack(Material.LEATHER_LEGGINGS));
        bottomRecipe.addIngredient(Material.LEATHER_LEGGINGS);
        bottomRecipe.addIngredient(Material.PURPLE_DYE);
        bottomRecipe.addIngredient(Material.BLAZE_POWDER);
        getServer().addRecipe(bottomRecipe);
        allRecipeKeys.add(bottomKey);

        NamespacedKey bootsKey = new NamespacedKey(this, "mage_boots_recipe");
        ShapelessRecipe bootsRecipe = new ShapelessRecipe(bootsKey, new ItemStack(Material.LEATHER_BOOTS));
        bootsRecipe.addIngredient(Material.LEATHER_BOOTS);
        bootsRecipe.addIngredient(Material.PURPLE_DYE);
        bootsRecipe.addIngredient(Material.BLAZE_POWDER);
        getServer().addRecipe(bootsRecipe);
        allRecipeKeys.add(bootsKey);

        // ── Apprentice Mage Gear: Leather + Purple Dye + String (Lv 1) ────────
        for (Material[] mat : new Material[][]{
            {Material.LEATHER_HELMET,     Material.LEATHER_HELMET},
            {Material.LEATHER_CHESTPLATE, Material.LEATHER_CHESTPLATE},
            {Material.LEATHER_LEGGINGS,   Material.LEATHER_LEGGINGS},
            {Material.LEATHER_BOOTS,      Material.LEATHER_BOOTS}
        }) {
            String suffix = mat[0] == Material.LEATHER_HELMET ? "apprentice_hood_recipe"
                : mat[0] == Material.LEATHER_CHESTPLATE ? "apprentice_top_recipe"
                : mat[0] == Material.LEATHER_LEGGINGS   ? "apprentice_bottom_recipe"
                : "apprentice_boots_recipe";
            NamespacedKey k = new NamespacedKey(this, suffix);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat[0]));
            r.addIngredient(mat[0]);
            r.addIngredient(Material.PURPLE_DYE);
            r.addIngredient(Material.STRING);
            getServer().addRecipe(r);
            allRecipeKeys.add(k);
        }

        // ── Alch Mage Gear: Leather + Blue Dye + Blaze Powder + Eye of Ender (Lv 60) ──
        for (Material mat : new Material[]{
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
        }) {
            String suffix = mat == Material.LEATHER_HELMET ? "alch_hood_recipe"
                : mat == Material.LEATHER_CHESTPLATE ? "alch_top_recipe"
                : mat == Material.LEATHER_LEGGINGS   ? "alch_bottom_recipe"
                : "alch_boots_recipe";
            NamespacedKey k = new NamespacedKey(this, suffix);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat);
            r.addIngredient(Material.BLUE_DYE);
            r.addIngredient(Material.BLAZE_POWDER);
            r.addIngredient(Material.ENDER_EYE);
            getServer().addRecipe(r);
            allRecipeKeys.add(k);
        }

        // ── Master Mage Gear: Leather + Black Dye + Blaze Powder + Shard + Dragon Breath (Lv 90) ──
        for (Material mat : new Material[]{
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS
        }) {
            String suffix = mat == Material.LEATHER_HELMET ? "master_hood_recipe"
                : mat == Material.LEATHER_CHESTPLATE ? "master_top_recipe"
                : mat == Material.LEATHER_LEGGINGS   ? "master_bottom_recipe"
                : "master_boots_recipe";
            NamespacedKey k = new NamespacedKey(this, suffix);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat);
            r.addIngredient(Material.BLACK_DYE);
            r.addIngredient(Material.BLAZE_POWDER);
            r.addIngredient(Material.AMETHYST_SHARD);   // Enchanted Shard (by material)
            r.addIngredient(Material.DRAGON_BREATH);
            getServer().addRecipe(r);
            allRecipeKeys.add(k);
        }

        // ── Magic Cauldron recipes → Rune Dust ────────────────────────────────
        // BASIC  (no diamond):    CAULDRON + element ingredients          → 16 dust
        // PREMIUM (+ 1 Diamond):  CAULDRON + element ingredients + DIAMOND → 80 dust
        // PrepareItemCraftEvent (MagicCauldronCraftListener) replaces the placeholder
        // GLOWSTONE_DUST result with the real PDC-tagged Rune Dust item.

        // ── Fire Rune Dust ──────────────────────────────────────────────────────
        NamespacedKey cfb = new NamespacedKey(this, "cauldron_fire_basic");
        ShapelessRecipe cfbR = new ShapelessRecipe(cfb, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cfbR.addIngredient(Material.CAULDRON);
        cfbR.addIngredient(Material.LAVA_BUCKET);
        cfbR.addIngredient(4, Material.NETHERRACK);
        getServer().addRecipe(cfbR);  allRecipeKeys.add(cfb);

        NamespacedKey cfp = new NamespacedKey(this, "cauldron_fire_premium");
        ShapelessRecipe cfpR = new ShapelessRecipe(cfp, new ItemStack(Material.GLOWSTONE_DUST, 64));
        cfpR.addIngredient(Material.CAULDRON);
        cfpR.addIngredient(Material.LAVA_BUCKET);
        cfpR.addIngredient(4, Material.NETHERRACK);
        cfpR.addIngredient(Material.DIAMOND);
        getServer().addRecipe(cfpR);  allRecipeKeys.add(cfp);

        // ── Water Rune Dust ─────────────────────────────────────────────────────
        NamespacedKey cwb = new NamespacedKey(this, "cauldron_water_basic");
        ShapelessRecipe cwbR = new ShapelessRecipe(cwb, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cwbR.addIngredient(Material.CAULDRON);
        cwbR.addIngredient(2, Material.WATER_BUCKET);
        cwbR.addIngredient(4, Material.PRISMARINE_SHARD);
        getServer().addRecipe(cwbR);  allRecipeKeys.add(cwb);

        NamespacedKey cwp = new NamespacedKey(this, "cauldron_water_premium");
        ShapelessRecipe cwpR = new ShapelessRecipe(cwp, new ItemStack(Material.GLOWSTONE_DUST, 64));
        cwpR.addIngredient(Material.CAULDRON);
        cwpR.addIngredient(2, Material.WATER_BUCKET);
        cwpR.addIngredient(4, Material.PRISMARINE_SHARD);
        cwpR.addIngredient(Material.DIAMOND);
        getServer().addRecipe(cwpR);  allRecipeKeys.add(cwp);

        // ── Earth Rune Dust ─────────────────────────────────────────────────────
        NamespacedKey ceb = new NamespacedKey(this, "cauldron_earth_basic");
        ShapelessRecipe cebR = new ShapelessRecipe(ceb, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cebR.addIngredient(Material.CAULDRON);
        cebR.addIngredient(Material.WATER_BUCKET);
        cebR.addIngredient(4, Material.DIRT);
        getServer().addRecipe(cebR);  allRecipeKeys.add(ceb);

        NamespacedKey cep = new NamespacedKey(this, "cauldron_earth_premium");
        ShapelessRecipe cepR = new ShapelessRecipe(cep, new ItemStack(Material.GLOWSTONE_DUST, 64));
        cepR.addIngredient(Material.CAULDRON);
        cepR.addIngredient(Material.WATER_BUCKET);
        cepR.addIngredient(4, Material.DIRT);
        cepR.addIngredient(Material.DIAMOND);
        getServer().addRecipe(cepR);  allRecipeKeys.add(cep);

        // ── Air Rune Dust ───────────────────────────────────────────────────────
        NamespacedKey cab = new NamespacedKey(this, "cauldron_air_basic");
        ShapelessRecipe cabR = new ShapelessRecipe(cab, new ItemStack(Material.GLOWSTONE_DUST, 16));
        cabR.addIngredient(Material.CAULDRON);
        cabR.addIngredient(Material.PUFFERFISH);
        cabR.addIngredient(Material.WATER_BUCKET);
        getServer().addRecipe(cabR);  allRecipeKeys.add(cab);

        NamespacedKey cap_ = new NamespacedKey(this, "cauldron_air_premium");
        ShapelessRecipe capR = new ShapelessRecipe(cap_, new ItemStack(Material.GLOWSTONE_DUST, 64));
        capR.addIngredient(Material.CAULDRON);
        capR.addIngredient(Material.PUFFERFISH);
        capR.addIngredient(Material.WATER_BUCKET);
        capR.addIngredient(Material.DIAMOND);
        getServer().addRecipe(capR);  allRecipeKeys.add(cap_);

        // ── Melee Gear recipes — 4 tiers × 4 pieces = 16 recipes ─────────────
        // MeleeGearCraftListener intercepts PrepareItemCraftEvent to swap in the
        // PDC-tagged result. Vanilla armour pieces + a rare ingredient = DE melee gear.
        for (String[] entry : new String[][]{
            {"IRON_HELMET",        "melee_iron_helmet"},
            {"IRON_CHESTPLATE",    "melee_iron_chestplate"},
            {"IRON_LEGGINGS",      "melee_iron_leggings"},
            {"IRON_BOOTS",         "melee_iron_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.IRON_INGOT);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"DIAMOND_HELMET",     "melee_diamond_helmet"},
            {"DIAMOND_CHESTPLATE", "melee_diamond_chestplate"},
            {"DIAMOND_LEGGINGS",   "melee_diamond_leggings"},
            {"DIAMOND_BOOTS",      "melee_diamond_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.DIAMOND);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"NETHERITE_HELMET",     "melee_netherite_helmet"},
            {"NETHERITE_CHESTPLATE", "melee_netherite_chestplate"},
            {"NETHERITE_LEGGINGS",   "melee_netherite_leggings"},
            {"NETHERITE_BOOTS",      "melee_netherite_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHERITE_INGOT);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"NETHERITE_HELMET",     "melee_dragon_helmet"},
            {"NETHERITE_CHESTPLATE", "melee_dragon_chestplate"},
            {"NETHERITE_LEGGINGS",   "melee_dragon_leggings"},
            {"NETHERITE_BOOTS",      "melee_dragon_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.DRAGON_BREATH);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        // ── Ranged Gear recipes — 4 tiers × 4 pieces = 16 recipes ────────────
        // RangedGearCraftListener intercepts PrepareItemCraftEvent to swap in PDC item.
        for (String[] entry : new String[][]{
            {"LEATHER_HELMET",     "ranged_leather_helmet"},
            {"LEATHER_CHESTPLATE", "ranged_leather_chestplate"},
            {"LEATHER_LEGGINGS",   "ranged_leather_leggings"},
            {"LEATHER_BOOTS",      "ranged_leather_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.STRING);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"CHAINMAIL_HELMET",     "ranged_chain_helmet"},
            {"CHAINMAIL_CHESTPLATE", "ranged_chain_chestplate"},
            {"CHAINMAIL_LEGGINGS",   "ranged_chain_leggings"},
            {"CHAINMAIL_BOOTS",      "ranged_chain_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.FEATHER); r.addIngredient(Material.LAPIS_LAZULI);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"NETHERITE_HELMET",     "ranged_netherite_helmet"},
            {"NETHERITE_CHESTPLATE", "ranged_netherite_chestplate"},
            {"NETHERITE_LEGGINGS",   "ranged_netherite_leggings"},
            {"NETHERITE_BOOTS",      "ranged_netherite_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHERITE_INGOT); r.addIngredient(Material.FEATHER);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }
        for (String[] entry : new String[][]{
            {"NETHERITE_HELMET",     "ranged_dragon_helmet"},
            {"NETHERITE_CHESTPLATE", "ranged_dragon_chestplate"},
            {"NETHERITE_LEGGINGS",   "ranged_dragon_leggings"},
            {"NETHERITE_BOOTS",      "ranged_dragon_boots"}
        }) {
            Material mat = Material.valueOf(entry[0]);
            NamespacedKey k = new NamespacedKey(this, entry[1]);
            ShapelessRecipe r = new ShapelessRecipe(k, new ItemStack(mat));
            r.addIngredient(mat); r.addIngredient(Material.NETHER_STAR); r.addIngredient(Material.ARROW);
            getServer().addRecipe(r); allRecipeKeys.add(k);
        }

        // ── Arcane Tome recipe: Book + Amethyst Shard + Purple Dye ──────────
        NamespacedKey arcaneTomeRecipe = new NamespacedKey(this, "arcane_tome_recipe");
        ShapelessRecipe arcaneTomeR = new ShapelessRecipe(arcaneTomeRecipe,
                spellBookManager.buildArcaneTomeItem());
        arcaneTomeR.addIngredient(Material.BOOK);
        arcaneTomeR.addIngredient(Material.AMETHYST_SHARD);
        arcaneTomeR.addIngredient(Material.PURPLE_DYE);
        getServer().addRecipe(arcaneTomeR);
        allRecipeKeys.add(arcaneTomeRecipe);

        // ── Soulfur Potion: Glass Bottle + Soul Sand + Blaze Powder + Nether Wart ─
        // CustomItemCraftListener intercepts PrepareItemCraftEvent to swap in the
        // PDC-tagged, colored Soulfur Potion.
        NamespacedKey soulfurPotionRecipeKey = new NamespacedKey(this, "soulfur_potion_recipe");
        ShapelessRecipe soulfurR = new ShapelessRecipe(soulfurPotionRecipeKey, new ItemStack(Material.POTION));
        soulfurR.addIngredient(Material.GLASS_BOTTLE);
        soulfurR.addIngredient(Material.SOUL_SAND);
        soulfurR.addIngredient(Material.BLAZE_POWDER);
        soulfurR.addIngredient(Material.NETHER_WART);
        getServer().addRecipe(soulfurR);
        allRecipeKeys.add(soulfurPotionRecipeKey);

        // ── Turbo Minecart: Minecart + Powered Rail + Redstone + Gold Ingot ────
        // CustomItemCraftListener intercepts PrepareItemCraftEvent to swap in the
        // PDC-tagged Turbo Minecart.
        NamespacedKey turboMinecartRecipeKey = new NamespacedKey(this, "turbo_minecart_recipe");
        ShapelessRecipe turboR = new ShapelessRecipe(turboMinecartRecipeKey, new ItemStack(Material.MINECART));
        turboR.addIngredient(Material.MINECART);
        turboR.addIngredient(Material.POWERED_RAIL);
        turboR.addIngredient(Material.REDSTONE);
        turboR.addIngredient(Material.GOLD_INGOT);
        getServer().addRecipe(turboR);
        allRecipeKeys.add(turboMinecartRecipeKey);

        // ── Magic Bag: Chest + Ender Pearl + Amethyst Shard + Purple Dye + String ─
        // CustomItemCraftListener intercepts PrepareItemCraftEvent to swap in the
        // PDC-tagged Magic Bag (CHEST with arcane storage GUI).
        NamespacedKey magicBagRecipeKey = new NamespacedKey(this, "magic_bag_recipe");
        ShapelessRecipe magicBagR = new ShapelessRecipe(magicBagRecipeKey, new ItemStack(Material.CHEST));
        magicBagR.addIngredient(Material.CHEST);
        magicBagR.addIngredient(Material.ENDER_PEARL);
        magicBagR.addIngredient(Material.AMETHYST_SHARD);
        magicBagR.addIngredient(Material.PURPLE_DYE);
        magicBagR.addIngredient(Material.STRING);
        getServer().addRecipe(magicBagR);
        allRecipeKeys.add(magicBagRecipeKey);

        // ── Earth Magic Pages: Book + Block Material + String (one per EarthBlockTier) ─
        // NOTE: Earth Page recipes are intentionally NOT added to allRecipeKeys.
        // They are only discovered via EntityPickupItemEvent in CustomItemCraftListener
        // when a player picks up their first Earth Magic Page of that tier.
        // This preserves the "fog-of-war" discovery mechanic for higher-tier pages.
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            NamespacedKey pageRecipeKey = new NamespacedKey(this,
                    "de_earth_page_recipe_" + tier.name().toLowerCase());
            ShapelessRecipe pageR = new ShapelessRecipe(pageRecipeKey, new ItemStack(Material.BOOK));
            pageR.addIngredient(Material.BOOK);
            pageR.addIngredient(tier.material);
            pageR.addIngredient(Material.STRING);
            getServer().addRecipe(pageR);
            // ↑ Registered for use but NOT added to allRecipeKeys → not auto-discovered on join
        }

        // ── Informational Magic Books ─────────────────────────────────────────
        // These are pre-written WRITTEN_BOOK items — the full BookMeta is embedded
        // in the recipe result directly (no PDC interceptor needed).

        // Novice Magic Primer: Book + Paper + Feather
        NamespacedKey novicePrimerKey = new NamespacedKey(this, "novice_magic_primer_recipe");
        ShapelessRecipe novicePrimerR = new ShapelessRecipe(novicePrimerKey,
                itemFactory.buildNoviceMagicPrimer());
        novicePrimerR.addIngredient(Material.BOOK);
        novicePrimerR.addIngredient(Material.PAPER);
        novicePrimerR.addIngredient(Material.FEATHER);
        getServer().addRecipe(novicePrimerR);
        allRecipeKeys.add(novicePrimerKey);

        // Mage's Primer: Book + Paper + Blaze Powder
        NamespacedKey magesPrimerKey = new NamespacedKey(this, "mages_primer_recipe");
        ShapelessRecipe magesPrimerR = new ShapelessRecipe(magesPrimerKey,
                itemFactory.buildMagesPrimerBook());
        magesPrimerR.addIngredient(Material.BOOK);
        magesPrimerR.addIngredient(Material.PAPER);
        magesPrimerR.addIngredient(Material.BLAZE_POWDER);
        getServer().addRecipe(magesPrimerR);
        allRecipeKeys.add(magesPrimerKey);

        // Elemental Theory Book: Book + Amethyst Shard + Paper
        NamespacedKey elementalTheoryKey = new NamespacedKey(this, "elemental_theory_recipe");
        ShapelessRecipe elementalTheoryR = new ShapelessRecipe(elementalTheoryKey,
                itemFactory.buildElementalTheoryBook());
        elementalTheoryR.addIngredient(Material.BOOK);
        elementalTheoryR.addIngredient(Material.AMETHYST_SHARD);
        elementalTheoryR.addIngredient(Material.PAPER);
        getServer().addRecipe(elementalTheoryR);
        allRecipeKeys.add(elementalTheoryKey);

        // Hidden Arts Book: Book + Nether Star + Paper  (rare — Nether Star gate)
        NamespacedKey hiddenArtsKey = new NamespacedKey(this, "hidden_arts_recipe");
        ShapelessRecipe hiddenArtsR = new ShapelessRecipe(hiddenArtsKey,
                itemFactory.buildHiddenArtsBook());
        hiddenArtsR.addIngredient(Material.BOOK);
        hiddenArtsR.addIngredient(Material.NETHER_STAR);
        hiddenArtsR.addIngredient(Material.PAPER);
        getServer().addRecipe(hiddenArtsR);
        allRecipeKeys.add(hiddenArtsKey);

        // Mage Gear Guide: Book + Leather + Purple Dye
        NamespacedKey mageGearGuideKey = new NamespacedKey(this, "mage_gear_guide_recipe");
        ShapelessRecipe mageGearGuideR = new ShapelessRecipe(mageGearGuideKey,
                itemFactory.buildMageGearGuide());
        mageGearGuideR.addIngredient(Material.BOOK);
        mageGearGuideR.addIngredient(Material.LEATHER);
        mageGearGuideR.addIngredient(Material.PURPLE_DYE);
        getServer().addRecipe(mageGearGuideR);
        allRecipeKeys.add(mageGearGuideKey);

        int totalRecipes = allRecipeKeys.size();
        getLogger().info("DifficultyEngine: Registered " + totalRecipes + " crafting recipes.");
        getLogger().info("  Mage Gear:   LEATHER_PIECE + PURPLE_DYE + BLAZE_POWDER (4 tiers)");
        getLogger().info("  Melee Gear:  ARMOUR_PIECE + tier ingredient (Iron/Diamond/Netherite/Dragon)");
        getLogger().info("  Ranged Gear: ARMOUR_PIECE + tier ingredient (Leather/Chain/Netherite/Dragon)");
        getLogger().info("  Soulfur Potion: GLASS_BOTTLE + SOUL_SAND + BLAZE_POWDER + NETHER_WART");
        getLogger().info("  Turbo Minecart: MINECART + POWERED_RAIL + REDSTONE + GOLD_INGOT");
        getLogger().info("  Magic Bag: CHEST + ENDER_PEARL + AMETHYST_SHARD + PURPLE_DYE + STRING");
        getLogger().info("  Earth Pages: BOOK + <block material> + STRING (8 tiers, Lv10-90)");
        getLogger().info("  Open crafting table → recipe book to search for them.");
    }

    // ── Recipe discovery ──────────────────────────────────────────────────────

    /**
     * Registers a PlayerJoinEvent listener that automatically "unlocks" all
     * DifficultyEngine crafting recipes in the recipe book for joining players.
     *
     * This makes the items searchable in the green recipe book icon that appears
     * in the crafting table — players can click it and see exactly what ingredients
     * are required for every craftable item.
     */
    private void registerRecipeDiscovery() {
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                Player player = event.getPlayer();
                // Slight delay so the player fully loads before receiving discovery packets
                getServer().getScheduler().runTaskLater(Main.this, () -> {
                    if (player.isOnline()) {
                        player.discoverRecipes(allRecipeKeys);
                    }
                }, 10L);
            }
        }, this);
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public ItemFactory getItemFactory() { return itemFactory; }

    /**
     * Null-safe command executor registration.
     * Logs a warning if the command is missing from plugin.yml rather than NPE.
     */
    private void registerCmd(String name, org.bukkit.command.CommandExecutor exec) {
        org.bukkit.command.PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(exec);
        } else {
            getLogger().warning("Command '" + name + "' not found in plugin.yml — skipping.");
        }
    }
}
