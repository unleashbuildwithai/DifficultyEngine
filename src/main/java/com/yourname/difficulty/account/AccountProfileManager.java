package com.yourname.difficulty.account;

import com.yourname.difficulty.DifficultyLevel;
import com.yourname.difficulty.PlayerDifficultyManager;
import com.yourname.difficulty.bag.MagicBagManager;
import com.yourname.difficulty.currency.GoldManager;
import com.yourname.difficulty.skills.SkillManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * AccountProfileManager — "shared account" system.
 *
 * Lets two (or more) real people share ONE Minecraft login/account while
 * keeping their own separate difficulty, skills, gold, and inventory.
 *
 * ── Setup ─────────────────────────────────────────────────────────────────
 *   /share <name1> <name2> [more...]
 *     Registers the given profile names as valid "identities" for this
 *     Minecraft account (keyed by the player's UUID). Can be re-run later
 *     to add more names. Names are case-insensitive, stored lower-case.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────
 *   /profile <name>   (aliases: /useaccount, /switchaccount)
 *     Switches the "active identity" on this login:
 *       1. Snapshots the CURRENTLY active profile's live state (skills,
 *          gold, difficulty, HP-bar toggle, full inventory + armor + offhand,
 *          Magic Bag contents) to disk.
 *       2. Loads the target profile's saved snapshot (or creates a fresh
 *          blank profile if this is the first time switching to that name)
 *          back into the live managers + the player's actual inventory.
 *       3. Records the new active profile name.
 *
 * ── Persistence ───────────────────────────────────────────────────────────
 *   plugins/DifficultyEngine/account_profiles/<uuid>.yml
 *     registered: [name1, name2, ...]
 *     active: <current profile name>
 *     profiles.<name>.gold: long
 *     profiles.<name>.difficulty: STRING
 *     profiles.<name>.hpbar: boolean
 *     profiles.<name>.skills.<SKILL>: long
 *     profiles.<name>.inventory.slot.<i>: ItemStack
 *     profiles.<name>.armor.<slot>: ItemStack   (helmet/chest/legs/boots)
 *     profiles.<name>.offhand: ItemStack
 *     profiles.<name>.magicbag.slot.<i>: ItemStack
 */
public class AccountProfileManager {

    private static final String DEFAULT_PROFILE = "default";

    private final JavaPlugin              plugin;
    private final PlayerDifficultyManager difficultyManager;
    private final SkillManager            skillManager;
    private final GoldManager             goldManager;
    private final MagicBagManager         magicBagManager;
    private final File                    dir;

    /** uuid -> currently active profile name (in-memory cache; also persisted). */
    private final Map<UUID, String> activeProfile = new HashMap<>();
    /** uuid -> set of registered profile names for that account. */
    private final Map<UUID, Set<String>> registeredNames = new HashMap<>();

    public AccountProfileManager(JavaPlugin plugin,
                                  PlayerDifficultyManager difficultyManager,
                                  SkillManager skillManager,
                                  GoldManager goldManager,
                                  MagicBagManager magicBagManager) {
        this.plugin            = plugin;
        this.difficultyManager = difficultyManager;
        this.skillManager      = skillManager;
        this.goldManager       = goldManager;
        this.magicBagManager   = magicBagManager;
        this.dir = new File(plugin.getDataFolder(), "account_profiles");
        if (!dir.exists()) dir.mkdirs();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the currently active profile name for this player (defaults to "default"). */
    public String getActiveProfile(UUID uuid) {
        return activeProfile.computeIfAbsent(uuid, id -> {
            YamlConfiguration cfg = loadYaml(id);
            return cfg.getString("active", DEFAULT_PROFILE);
        });
    }

    /** Returns the set of profile names registered for this account (via /share). */
    public Set<String> getRegisteredNames(UUID uuid) {
        return registeredNames.computeIfAbsent(uuid, id -> {
            YamlConfiguration cfg = loadYaml(id);
            Set<String> names = new LinkedHashSet<>(cfg.getStringList("registered"));
            names.add(DEFAULT_PROFILE);
            return names;
        });
    }

    /**
     * Registers one or more profile names as valid identities for this
     * player's Minecraft account. Idempotent — re-adding an existing name
     * is a no-op for that name.
     */
    public void registerNames(Player player, List<String> names) {
        UUID uuid = player.getUniqueId();
        Set<String> set = getRegisteredNames(uuid);
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;
            set.add(raw.trim().toLowerCase());
        }
        YamlConfiguration cfg = loadYaml(uuid);
        cfg.set("registered", new ArrayList<>(set));
        saveYaml(uuid, cfg);
    }

    /**
     * Switches the active profile for this player's login session.
     * Returns {@code true} on success, {@code false} if the name is not
     * registered (caller should show an error / usage hint in that case).
     */
    public boolean switchProfile(Player player, String targetNameRaw) {
        UUID uuid = player.getUniqueId();
        String targetName = targetNameRaw.trim().toLowerCase();

        Set<String> registered = getRegisteredNames(uuid);
        if (!registered.contains(targetName)) {
            return false;
        }

        String currentName = getActiveProfile(uuid);
        if (currentName.equals(targetName)) {
            return true; // already active — nothing to do
        }

        YamlConfiguration cfg = loadYaml(uuid);

        // 1. Snapshot current live state → save under currentName
        saveProfileSnapshot(player, cfg, currentName);

        // 2. Load targetName's saved snapshot (or blank defaults) → live state
        loadProfileSnapshot(player, cfg, targetName);

        // 3. Persist new active name
        cfg.set("active", targetName);
        saveYaml(uuid, cfg);
        activeProfile.put(uuid, targetName);

        return true;
    }

    // ── Snapshot: live → disk ─────────────────────────────────────────────────

    private void saveProfileSnapshot(Player player, YamlConfiguration cfg, String profileName) {
        UUID uuid = player.getUniqueId();
        String base = "profiles." + profileName + ".";

        // Gold
        cfg.set(base + "gold", goldManager.getBalance(uuid));

        // Difficulty
        DifficultyLevel level = difficultyManager.getDifficulty(uuid);
        cfg.set(base + "difficulty", level != null ? level.name() : DifficultyLevel.EASY.name());

        // HP bar toggle
        cfg.set(base + "hpbar", difficultyManager.isHpDisplayEnabled(uuid));

        // Skills
        Map<SkillType, Long> xp = skillManager.getAllXp(uuid);
        for (SkillType skill : SkillType.values()) {
            long amount = xp.getOrDefault(skill, 0L);
            cfg.set(base + "skills." + skill.name(), amount);
        }

        // Inventory (36 main slots)
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            String path = base + "inventory.slot." + i;
            if (contents[i] != null && !contents[i].getType().isAir()) {
                cfg.set(path, contents[i]);
            } else {
                cfg.set(path, null);
            }
        }

        // Armor + offhand
        setOrClear(cfg, base + "armor.helmet", inv.getHelmet());
        setOrClear(cfg, base + "armor.chest",  inv.getChestplate());
        setOrClear(cfg, base + "armor.legs",   inv.getLeggings());
        setOrClear(cfg, base + "armor.boots",  inv.getBoots());
        setOrClear(cfg, base + "offhand",      inv.getItemInOffHand());

        // Magic Bag
        if (magicBagManager != null) {
            ItemStack[] bag = magicBagManager.getBag(uuid);
            for (int i = 0; i < bag.length; i++) {
                String path = base + "magicbag.slot." + i;
                if (bag[i] != null && !bag[i].getType().isAir()) {
                    cfg.set(path, bag[i]);
                } else {
                    cfg.set(path, null);
                }
            }
        }
    }

    private void setOrClear(YamlConfiguration cfg, String path, ItemStack item) {
        if (item != null && !item.getType().isAir()) cfg.set(path, item);
        else cfg.set(path, null);
    }

    // ── Snapshot: disk → live ─────────────────────────────────────────────────

    private void loadProfileSnapshot(Player player, YamlConfiguration cfg, String profileName) {
        UUID uuid = player.getUniqueId();
        String base = "profiles." + profileName + ".";
        boolean isNewProfile = !cfg.isConfigurationSection("profiles." + profileName);

        // Gold
        long gold = cfg.getLong(base + "gold", 0L);
        goldManager.setBalance(uuid, gold);

        // Difficulty
        String diffName = cfg.getString(base + "difficulty", DifficultyLevel.EASY.name());
        DifficultyLevel level;
        try { level = DifficultyLevel.valueOf(diffName); }
        catch (IllegalArgumentException ex) { level = DifficultyLevel.EASY; }
        difficultyManager.setDifficulty(uuid, level);

        // Skills
        Map<SkillType, Long> xp = new EnumMap<>(SkillType.class);
        for (SkillType skill : SkillType.values()) {
            long amount = cfg.getLong(base + "skills." + skill.name(), 0L);
            if (amount > 0) xp.put(skill, amount);
        }
        skillManager.setAllXp(uuid, xp);

        // Inventory
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ItemStack[] contents = new ItemStack[inv.getStorageContents().length];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = cfg.getItemStack(base + "inventory.slot." + i);
        }
        inv.setStorageContents(contents);

        inv.setHelmet(cfg.getItemStack(base + "armor.helmet"));
        inv.setChestplate(cfg.getItemStack(base + "armor.chest"));
        inv.setLeggings(cfg.getItemStack(base + "armor.legs"));
        inv.setBoots(cfg.getItemStack(base + "armor.boots"));
        inv.setItemInOffHand(cfg.getItemStack(base + "offhand"));

        // Magic Bag
        if (magicBagManager != null) {
            ItemStack[] bag = magicBagManager.getBag(uuid);
            for (int i = 0; i < bag.length; i++) {
                bag[i] = cfg.getItemStack(base + "magicbag.slot." + i);
            }
            magicBagManager.saveAsync(uuid);
        }

        if (isNewProfile) {
            player.sendMessage("§5✦ §7This is a §dbrand-new§7 profile — starting fresh!");
        }
    }

    // ── YAML I/O ───────────────────────────────────────────────────────────────

    private File fileFor(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }

    private YamlConfiguration loadYaml(UUID uuid) {
        File f = fileFor(uuid);
        return YamlConfiguration.loadConfiguration(f);
    }

    private void saveYaml(UUID uuid, YamlConfiguration cfg) {
        try {
            cfg.save(fileFor(uuid));
        } catch (IOException e) {
            plugin.getLogger().warning("[AccountProfileManager] Failed to save profile for "
                    + uuid + ": " + e.getMessage());
        }
    }
}
