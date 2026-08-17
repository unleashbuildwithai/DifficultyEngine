package com.yourname.difficulty.magic;

import com.yourname.difficulty.items.EarthBlockTier;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * EarthBookManager — per-player unlocked Earth Book tiers.
 *
 * The Earth Book is a single item whose pages are the {@link EarthBlockTier}
 * tiers. Players find Earth Pages as mob drops (gated by dimension) and
 * right-click them to unlock the matching tier in their book. Unlocked tiers
 * are persisted per-player to {@code plugins/DifficultyEngine/earthbook_data.yml}.
 *
 * Casting with the Earth Staff falls back from the highest unlocked tier the
 * player qualifies for (level + block in inventory) down to the lowest.
 */
public class EarthBookManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Set<EarthBlockTier>> unlockedTiers = new HashMap<>();

    private File dataFile;
    private YamlConfiguration dataCfg;

    public EarthBookManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    // ── Unlock state ──────────────────────────────────────────────────────────

    /** Returns true if the player has unlocked the given tier in their Earth Book. */
    public boolean hasTier(UUID uuid, EarthBlockTier tier) {
        return unlockedTiers.getOrDefault(uuid, Set.of()).contains(tier);
    }

    /** Returns true if the player has unlocked at least one tier. */
    public boolean hasAnyTier(UUID uuid) {
        return !unlockedTiers.getOrDefault(uuid, Set.of()).isEmpty();
    }

    /** Returns the player's unlocked tiers (empty set if none). */
    public Set<EarthBlockTier> getUnlockedTiers(UUID uuid) {
        return unlockedTiers.getOrDefault(uuid, Collections.emptySet());
    }

    /**
     * Unlocks a tier for the player.
     *
     * @return true if newly unlocked, false if it was already unlocked.
     */
    public boolean unlockTier(UUID uuid, EarthBlockTier tier) {
        Set<EarthBlockTier> set = unlockedTiers.computeIfAbsent(uuid, k -> new HashSet<>());
        if (!set.add(tier)) return false;
        save();
        return true;
    }

    // ── Book rendering ────────────────────────────────────────────────────────

    /**
     * Builds a {@link Material#WRITTEN_BOOK} showing every tier as a page.
     * Unlocked tiers show their block, level and damage; locked tiers show "???".
     * Pass to {@link org.bukkit.entity.Player#openBook(ItemStack)} — never given
     * to the player, only shown as a UI.
     */
    public ItemStack buildBookForPlayer(UUID uuid) {
        Set<EarthBlockTier> unlocked = unlockedTiers.getOrDefault(uuid, Collections.emptySet());

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta == null) return book;

        meta.setTitle("The Earth Book");
        meta.setAuthor("Earth Archmage");
        meta.setGeneration(BookMeta.Generation.ORIGINAL);

        for (EarthBlockTier tier : EarthBlockTier.values()) {
            if (unlocked.contains(tier)) {
                meta.addPage(
                    tier.displayName + "\n" +
                    "§8──────────────\n" +
                    "§7Requires: §aMagic Lv " + tier.levelRequired + "\n\n" +
                    "§7Throw " + tier.displayName + "§7 blocks\n" +
                    "§7with the §2Earth Staff§7.\n\n" +
                    "§7Damage: §c" + (int)(tier.trapDamage / 2) + " ❤\n" +
                    "§7Heavy hit: §c" + (int)(tier.suffocateDamage / 2) + " ❤\n\n" +
                    "§8Dimension: " + dimensionName(tier)
                );
            } else {
                meta.addPage(
                    "§8[Page " + (tier.ordinal() + 1) + " / " + EarthBlockTier.values().length + "]\n\n" +
                    "§7???\n\n" +
                    "§8This page has not\n" +
                    "§8been discovered yet.\n\n" +
                    "§8Find an §2Earth Page\n" +
                    "§8dropped by mobs in\n" +
                    "§8" + dimensionName(tier) + "§8 to unlock it."
                );
            }
        }

        book.setItemMeta(meta);
        return book;
    }

    private String dimensionName(EarthBlockTier tier) {
        return switch (tier.dimension) {
            case NORMAL  -> "§aOverworld";
            case NETHER  -> "§cNether";
            case THE_END -> "§dThe End";
            default      -> "§7Unknown";
        };
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void load() {
        plugin.getDataFolder().mkdirs();
        dataFile = new File(plugin.getDataFolder(), "earthbook_data.yml");
        dataCfg  = YamlConfiguration.loadConfiguration(dataFile);

        if (dataCfg.isConfigurationSection("unlocked")) {
            for (String uuidStr : dataCfg.getConfigurationSection("unlocked").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String raw = dataCfg.getString("unlocked." + uuidStr, "");
                    Set<EarthBlockTier> set = new HashSet<>();
                    if (!raw.isEmpty()) {
                        for (String s : raw.split(",")) {
                            try {
                                set.add(EarthBlockTier.valueOf(s.trim()));
                            } catch (IllegalArgumentException ignored) {}
                        }
                    }
                    unlockedTiers.put(uuid, set);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        plugin.getLogger().info("[EarthBookManager] Loaded " + unlockedTiers.size() + " player Earth Book records.");
    }

    public void save() {
        dataCfg = new YamlConfiguration();
        for (var entry : unlockedTiers.entrySet()) {
            String ids = entry.getValue().stream()
                    .map(Enum::name)
                    .reduce((a, b) -> a + "," + b).orElse("");
            dataCfg.set("unlocked." + entry.getKey(), ids);
        }
        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[EarthBookManager] Save failed: " + e.getMessage(), e);
        }
    }
}

