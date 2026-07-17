package com.yourname.difficulty;

import com.yourname.difficulty.gui.RegistryGUI;
import com.yourname.difficulty.gui.RegistryGUIListener;
import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.listeners.DifficultyEngine;
import com.yourname.difficulty.listeners.MinecartListener;
import com.yourname.difficulty.listeners.NightmareAggroListener;
import com.yourname.difficulty.listeners.SitListener;
import com.yourname.difficulty.listeners.SoulfurPotionListener;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlayerDifficultyManager difficultyManager;
    private ItemFactory             itemFactory;
    private RegistryGUI             registryGUI;
    private SitListener             sitListener;

    @Override
    public void onEnable() {
        getLogger().info("DifficultyEngine: Initializing...");

        // ── Core managers ─────────────────────────────────────────────────────
        this.difficultyManager = new PlayerDifficultyManager(this);

        // ── Item & GUI layer ──────────────────────────────────────────────────
        this.itemFactory = new ItemFactory(this);
        this.registryGUI = new RegistryGUI(itemFactory);

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

        // ── Scheduled tasks ───────────────────────────────────────────────────

        // Nightmare bonus-spawn — every 30 seconds
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 600L, 600L);

        // ── Sync NIGHTMARE PDC tags for already-online players ─────────────────
        // (handles /reload or late-enable scenarios)
        for (Player p : getServer().getOnlinePlayers()) {
            difficultyManager.syncNightmareTag(p, difficultyManager.getDifficulty(p.getUniqueId()));
        }

        getLogger().info("DifficultyEngine: Ready!");
        getLogger().info("  Players : /difficulty  /hpbar  /sit  /registry");
        getLogger().info("  Admins  : /gear");
    }

    @Override
    public void onDisable() {
        if (difficultyManager != null) difficultyManager.saveAll();
        getLogger().info("DifficultyEngine: Data saved. Goodbye.");
    }
}
