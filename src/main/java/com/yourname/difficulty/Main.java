package com.yourname.difficulty;

import com.yourname.difficulty.gui.RegistryGUI;
import com.yourname.difficulty.gui.RegistryGUIListener;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.MinecartListener;
import com.yourname.difficulty.listeners.NightmareAggroListener;
import com.yourname.difficulty.listeners.SitListener;
import com.yourname.difficulty.listeners.SoulfurPotionListener;
import com.yourname.difficulty.skills.CapeEquipListener;
import com.yourname.difficulty.skills.ItemLevelListener;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillCommand;
import com.yourname.difficulty.skills.SkillGUI;
import com.yourname.difficulty.skills.SkillGUIListener;
import com.yourname.difficulty.skills.SkillListener;
import com.yourname.difficulty.skills.SkillManager;
import org.bukkit.entity.Player;
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
        // ItemFactory now takes SkillCapeManager so capes appear in /registry
        this.itemFactory = new ItemFactory(this, skillCapeManager);
        this.registryGUI = new RegistryGUI(itemFactory);

        // ── Skill GUI ─────────────────────────────────────────────────────────
        SkillGUI skillGUI = new SkillGUI(skillManager);

        // ── Register listeners ────────────────────────────────────────────────

        // Core difficulty scaling / HP bar / death cleanup / base aggro
        getServer().getPluginManager().registerEvents(
                new DifficultyEngine(this, difficultyManager), this);

        // Soulfur Potion: sip counter, escalating darkness/damage, 50-sip curse
        getServer().getPluginManager().registerEvents(
                new SoulfurPotionListener(this, itemFactory, difficultyManager), this);

        // Nightmare party threat-aggregation (PDC-gated)
        getServer().getPluginManager().registerEvents(
                new NightmareAggroListener(difficultyManager), this);

        // Registry GUI: permission-checked item distribution
        getServer().getPluginManager().registerEvents(
                new RegistryGUIListener(itemFactory), this);

        // Sit system: directional edge-sitting on slabs/stairs
        this.sitListener = new SitListener();
        getServer().getPluginManager().registerEvents(sitListener, this);

        // Turbo Minecart: PDC-gated placement, 3× speed, rail magnetization
        getServer().getPluginManager().registerEvents(
                new MinecartListener(itemFactory), this);

        // Skill system: XP tracking for melee, ranged, woodcutting, fishing, farming
        // difficultyManager injected so kill XP scales with the player's difficulty tier
        getServer().getPluginManager().registerEvents(
                new SkillListener(this, skillManager, skillCapeManager, difficultyManager), this);

        // Skill GUI: prevent item theft from /mystats inventory
        getServer().getPluginManager().registerEvents(
                new SkillGUIListener(), this);

        // Cape equip: admin equipping a skill cape gets instant Level 99
        getServer().getPluginManager().registerEvents(
                new CapeEquipListener(skillManager, skillCapeManager), this);

        // Item level requirements: block using weapons/seeds/rods below skill level
        getServer().getPluginManager().registerEvents(
                new ItemLevelListener(skillManager), this);

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

        // /curecosmetic — removes blindness, nausea, darkness, etc.
        getCommand("curecosmetic").setExecutor(new CureCosmeticCommand());

        // /adminlight — toggles personal Night Vision for admins
        this.adminLightCommand = new AdminLightCommand(this);
        getCommand("adminlight").setExecutor(adminLightCommand);

        // /skills — text summary of skill levels
        getCommand("skills").setExecutor(
                new SkillCommand(skillManager, skillGUI, false));

        // /mystats — RuneScape-style skill tree GUI
        getCommand("mystats").setExecutor(
                new SkillCommand(skillManager, skillGUI, true));

        // ── Scheduled tasks ───────────────────────────────────────────────────

        // Nightmare bonus-spawn — every 30 seconds
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 600L, 600L);

        // ── Sync NIGHTMARE PDC tags for already-online players ─────────────────
        for (Player p : getServer().getOnlinePlayers()) {
            difficultyManager.syncNightmareTag(p, difficultyManager.getDifficulty(p.getUniqueId()));
        }

        getLogger().info("DifficultyEngine: Ready!");
        getLogger().info("  Players : /difficulty  /hpbar  /sit  /registry  /skills  /mystats");
        getLogger().info("  Admins  : /gear  /curecosmetic  /adminlight");
    }

    @Override
    public void onDisable() {
        if (difficultyManager != null) difficultyManager.saveAll();
        if (skillManager      != null) skillManager.saveAll();
        if (adminLightCommand != null) adminLightCommand.disableAll();
        getLogger().info("DifficultyEngine: Data saved. Goodbye.");
    }
}
