package com.yourname.difficulty;

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
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.GroupDifficultyListener;
import com.yourname.difficulty.listeners.LevelProtectionListener;
import com.yourname.difficulty.listeners.MageGearCraftListener;
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
import com.yourname.difficulty.skills.SkillCommand;
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
                new RegistryGUIListener(itemFactory), this);

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

        // ── Boss events: double spawn (1 %), boss-fight mobs, boss cape ────────
        getServer().getPluginManager().registerEvents(
                new BossEventListener(this, skillCapeManager), this);

        // ── Mage Gear crafting: replaces vanilla result with PDC item ──────────
        getServer().getPluginManager().registerEvents(
                new MageGearCraftListener(itemFactory), this);

        // ── Gold currency system ───────────────────────────────────────────────
        this.goldManager = new GoldManager(this);
        getServer().getPluginManager().registerEvents(
                new GoldDropListener(goldManager, difficultyManager, this), this);
        getServer().getPluginManager().registerEvents(new GoldValueListener(), this);

        // ── Rune mob drops (Air, Fire, Water, Earth from specific mobs) ────────
        getServer().getPluginManager().registerEvents(
                new RuneDropListener(itemFactory), this);

        // ── Spell Book system (Arcane Tome + Spell Pages) ──────────────────────
        this.spellBookManager = new SpellBookManager(this);
        getServer().getPluginManager().registerEvents(
                new SpellBookListener(spellBookManager), this);

        // ── Quest system ───────────────────────────────────────────────────────
        this.questManager = new QuestManager(this, goldManager, skillManager, itemFactory);
        this.questGUI     = new QuestGUI(questManager);
        getServer().getPluginManager().registerEvents(questGUI, this);
        getServer().getPluginManager().registerEvents(
                new QuestKillListener(questManager), this);

        // ── Party system ───────────────────────────────────────────────────────
        this.partyManager  = new PartyManager();
        this.partyListener = new PartyListener(partyManager, difficultyManager, this);
        getServer().getPluginManager().registerEvents(partyListener, this);

        // ── Trade Stone ────────────────────────────────────────────────────────
        this.tradeListener = new TradeListener(this);
        getServer().getPluginManager().registerEvents(tradeListener, this);

        // ── Register commands ─────────────────────────────────────────────────

        getCommand("difficulty").setExecutor(new DifficultyCommand(difficultyManager));
        getCommand("gear").setExecutor(new GearCommand());
        getCommand("hpbar").setExecutor(new HpBarCommand(difficultyManager));
        getCommand("sit").setExecutor(new SitCommand(sitListener));

        getCommand("registry").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Item Registry.");
                return true;
            }
            registryGUI.open(player);
            return true;
        });

        getCommand("curecosmetic").setExecutor(new CureCosmeticCommand());

        this.adminLightCommand = new AdminLightCommand(this);
        getCommand("adminlight").setExecutor(adminLightCommand);

        getCommand("skills").setExecutor(
                new SkillCommand(skillManager, skillGUI, false));

        SkillCommand guiCmd = new SkillCommand(skillManager, skillGUI, true);
        getCommand("mystats").setExecutor(guiCmd);
        getCommand("stats").setExecutor(guiCmd);

        CapeSlotGUI capeGui = new CapeSlotGUI(skillCapeManager, capeDataManager);
        getCommand("cape").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Cape Wardrobe.");
                return true;
            }
            capeGui.open(player);
            return true;
        });

        // ── New commands ───────────────────────────────────────────────────────
        getCommand("gold").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can use /gold.");
                return true;
            }
            long bal = goldManager.getBalance(player.getUniqueId());
            player.sendMessage("§6Your balance: §e" + GoldManager.formatGold(bal) + " gp");
            return true;
        });

        getCommand("questbook").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Quest Book.");
                return true;
            }
            questGUI.open(player);
            return true;
        });

        getCommand("party").setExecutor(partyListener);
        getCommand("trade").setExecutor(tradeListener);

        getCommand("spellbook").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Arcane Tome.");
                return true;
            }
            int count = spellBookManager.getUnlockedCount(player.getUniqueId());
            player.openBook(spellBookManager.buildBookForPlayer(player.getUniqueId()));
            player.sendActionBar("§5✦ §dArcane Tome §8— §7" + count + " §8/ §7"
                    + SpellBookManager.TOTAL_PAGES + " §dpages unlocked");
            return true;
        });

        getCommand("spellpage").setExecutor((sender, cmd, label, args) -> {
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

        int totalRecipes = allRecipeKeys.size();
        getLogger().info("DifficultyEngine: Registered " + totalRecipes
                + " crafting recipes (4 staffs + 4 rune batches + 4 mage gear pieces).");
        getLogger().info("  Mage Gear recipe: LEATHER_PIECE + PURPLE_DYE + BLAZE_POWDER");
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
}
