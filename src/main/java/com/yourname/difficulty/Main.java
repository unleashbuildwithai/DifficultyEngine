package com.yourname.difficulty;

import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private PlayerDifficultyManager difficultyManager;

    @Override
    public void onEnable() {
        getLogger().info("DifficultyEngine: Initializing personal difficulty system...");

        // Load saved player difficulty choices
        this.difficultyManager = new PlayerDifficultyManager(this);

        // Register event listener
        DifficultyEngine engine = new DifficultyEngine(this, difficultyManager);
        getServer().getPluginManager().registerEvents(engine, this);

        // Register /difficulty command (all players)
        getCommand("difficulty").setExecutor(new DifficultyCommand(difficultyManager));

        // Register /gear command (admins only — enforced via permission)
        getCommand("gear").setExecutor(new GearCommand());

        // Start the nightmare bonus-spawn scheduler (every 30 seconds = 600 ticks)
        new NightmareSpawnTask(difficultyManager).runTaskTimer(this, 600L, 600L);

        getLogger().info("DifficultyEngine: Ready! Players: /difficulty  |  Admins: /gear");
    }

    @Override
    public void onDisable() {
        if (difficultyManager != null) {
            difficultyManager.saveAll();
        }
        getLogger().info("DifficultyEngine: Player difficulty data saved. Goodbye.");
    }
}
