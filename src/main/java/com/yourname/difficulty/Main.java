package com.yourname.difficulty;

import com.yourname.difficulty.gui.CapeSlotGUI;
import com.yourname.difficulty.gui.CapeSlotGUIListener;
import com.yourname.difficulty.gui.RegistryGUI;
import com.yourname.difficulty.gui.RegistryGUIListener;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.LevelProtectionListener;
import com.yourname.difficulty.listeners.MinecartListener;
import com.yourname.difficulty.listeners.NightmareAggroListener;
import com.yourname.difficulty.listeners.PrayerListener;
import com.yourname.difficulty.listeners.SitListener;
import com.yourname.difficulty.listeners.SoulfurPotionListener;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.magic.MagicStaffListener;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlayerDifficultyManager difficultyManager;
    private ItemFactory             itemFactory;
    private RegistryGUI             registryGUI;
    private SitListener             sitListener;
    private SkillManager            skillManager;
    private SkillCapeManager        skillCapeManager;
    private AdminLightCommand       adminLightCommand;

    @Override
    public void onEnable() {
        getLogger().info("DifficultyEngine: Initializing...");

        // ── Core managers ─────────────────────────────────────────────────────
        this.difficultyManager = new PlayerDifficultyManager(this);
        this.skillManager      = new SkillManager(this);
        this.skillCapeManager  = new SkillCapeManager(this);

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
        getServer().getPluginManager().registerEvents(
                new MagicStaffListener(itemFactory, skillManager, this), this);

        // Level protection: OSRS combat-level PvP bracket + passive aura scan
        getServer().getPluginManager().registerEvents(
                new LevelProtectionListener(skillManager, this), this);

        // Cape wardrobe GUI
        getServer().getPluginManager().registerEvents(
                new CapeSlotGUIListener(skillCapeManager, skillManager), this);

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

        CapeSlotGUI capeGui = new CapeSlotGUI(skillCapeManager);
        getCommand("cape").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cOnly players can open the Cape Wardrobe.");
                return true;
            }
            capeGui.open(player);
            return true;
        });

        // ── Crafting recipes ──────────────────────────────────────────────────
        registerCraftingRecipes();

        // ── Scheduled tasks ───────────────────────────────────────────────────
        // Nightmare bonus-spawn — every 15 seconds (doubled frequency, 6 mobs, 16-48 block range)
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 300L, 300L);

        for (Player p : getServer().getOnlinePlayers()) {
            difficultyManager.syncNightmareTag(p, difficultyManager.getDifficulty(p.getUniqueId()));
        }

        getLogger().info("DifficultyEngine: Ready!");
        getLogger().info("  Players : /difficulty  /hpbar  /sit  /registry  /skills  /mystats  /stats  /cape");
        getLogger().info("  Admins  : /gear  /curecosmetic  /adminlight");
        getLogger().info("  Magic   : Right-click elemental staffs to cast spells.");
        getLogger().info("  Prayer  : Right-click bone on dirt to bury it for XP.");
    }

    @Override
    public void onDisable() {
        if (difficultyManager != null) difficultyManager.saveAll();
        if (skillManager      != null) skillManager.saveAll();
        if (adminLightCommand != null) adminLightCommand.disableAll();
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
        }

        // ── Rune recipes: 4× base material → 8 runes ─────────────────────────
        for (MagicElement el : MagicElement.values()) {
            NamespacedKey key = new NamespacedKey(this, el.runeKey + "_recipe");
            ItemStack runeResult = itemFactory.buildRune(el, 8);
            ShapelessRecipe recipe = new ShapelessRecipe(key, runeResult);
            recipe.addIngredient(4, el.runeCraftIngredient);      // 4× base material
            getServer().addRecipe(recipe);
        }

        getLogger().info("DifficultyEngine: Registered " + (MagicElement.values().length * 2)
                + " crafting recipes (4 staffs + 4 rune batches).");
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    public ItemFactory getItemFactory() { return itemFactory; }
}
