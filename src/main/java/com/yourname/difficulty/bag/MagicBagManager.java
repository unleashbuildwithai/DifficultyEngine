package com.yourname.difficulty.bag;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MagicBagManager — builds and persists each player's Magic Bag.
 *
 * The Magic Bag is a virtual 32-slot storage split into 4 sections:
 *   Section 0 (slots  0-7 )  🔮 Runes & Rune Dust
 *   Section 1 (slots  8-15)  ⚗️  Staffs & Mage Gear
 *   Section 2 (slots 16-23)  📜 Spell Books & Pages
 *   Section 3 (slots 24-31)  🌿 Ingredients & Misc
 *
 * The physical bag item is built by {@link ItemFactory#buildMagicBag()} and
 * identified by {@link ItemFactory#isMagicBag(ItemStack)}.  Right-clicking it
 * opens the custom GUI via MagicBagGUIListener.
 *
 * Data is persisted to:  plugins/DifficultyEngine/bags/<uuid>.yml
 */
public class MagicBagManager {

    /** Total number of storage slots (4 sections × 8 slots). */
    public static final int TOTAL_SLOTS       = 32;
    public static final int SECTION_COUNT     = 4;
    public static final int SLOTS_PER_SECTION = 8;

    private final JavaPlugin  plugin;
    private final ItemFactory itemFactory;
    private final File        bagDir;

    /** UUID → 32-slot item array (indices 0-31). */
    private final Map<UUID, ItemStack[]> bags = new HashMap<>();

    public MagicBagManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
        this.bagDir      = new File(plugin.getDataFolder(), "bags");
        if (!bagDir.exists()) bagDir.mkdirs();
    }

    // ── Item builder / identifier (delegated to ItemFactory) ──────────────────

    /** Builds the physical Magic Bag item (delegates to ItemFactory). */
    public ItemStack buildMagicBag() {
        return itemFactory.buildMagicBag();
    }

    /** Returns {@code true} if the item is a Magic Bag (delegates to ItemFactory). */
    public boolean isMagicBag(ItemStack item) {
        return itemFactory.isMagicBag(item);
    }

    // ── Bag data access ───────────────────────────────────────────────────────

    /** Gets (or lazily creates) the 32-slot array for a player. */
    public ItemStack[] getBag(UUID uuid) {
        return bags.computeIfAbsent(uuid, k -> new ItemStack[TOTAL_SLOTS]);
    }

    /** Returns true if the bag has been loaded (or created) for this player. */
    public boolean hasBag(UUID uuid) {
        return bags.containsKey(uuid);
    }

    /**
     * Attempts to add an item to the correct section of the bag.
     *
     * @return {@code true} if the item was placed; {@code false} if the section is full
     *         or the item is not a recognisable magic item.
     */
    public boolean addToBag(UUID uuid, ItemStack item) {
        int section = classifyItem(item);
        if (section < 0) return false;

        ItemStack[] bag = getBag(uuid);
        int start = section * SLOTS_PER_SECTION;
        int end   = start  + SLOTS_PER_SECTION;

        for (int i = start; i < end; i++) {
            if (bag[i] == null || bag[i].getType().isAir()) {
                bag[i] = item.clone();
                saveAsync(uuid);
                return true;
            }
            // Stack onto existing compatible slot
            if (bag[i].isSimilar(item)) {
                int space = bag[i].getMaxStackSize() - bag[i].getAmount();
                if (space > 0) {
                    int give = Math.min(space, item.getAmount());
                    bag[i].setAmount(bag[i].getAmount() + give);
                    item.setAmount(item.getAmount() - give);
                    if (item.getAmount() <= 0) {
                        saveAsync(uuid);
                        return true;
                    }
                }
            }
        }
        return false; // section full
    }

    // ── Item classification ───────────────────────────────────────────────────

    /**
     * Returns the section index (0-3) for a recognised magic item,
     * or -1 if the item is not handled by this bag.
     *
     *   Section 0 — Runes & Rune Dust
     *   Section 1 — Staffs, Mage Gear, Weapons
     *   Section 2 — Spell Books & Pages
     *   Section 3 — Ingredients & Misc
     */
    public int classifyItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;

        // Section 0: Runes & Rune Dust
        for (MagicElement el : MagicElement.values()) {
            if (itemFactory.isRune(item, el))     return 0;
            if (itemFactory.isRuneDust(item, el)) return 0;
        }

        // Section 1: Staffs, Mage Gear & Magic Weapons
        if (itemFactory.getStaffElement(item) != null) return 1;
        if (itemFactory.isMageGear(item))               return 1;
        if (itemFactory.isGunZSword(item))              return 1;
        if (itemFactory.isDarkBow(item))                return 1;
        if (itemFactory.isDragonArrow(item))            return 1;
        if (itemFactory.isDragonArrowTip(item))         return 1;

        // Section 2: Spell Books & Pages
        if (itemFactory.isSpellComboBook(item))  return 2;
        if (itemFactory.isAncientKillTome(item)) return 2;
        if (item.getType() == Material.WRITTEN_BOOK
                || item.getType() == Material.ENCHANTED_BOOK
                || item.getType() == Material.WRITABLE_BOOK) {
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String name = item.getItemMeta().getDisplayName();
                if (name.contains("§") || name.contains("Mage") || name.contains("Spell")
                        || name.contains("Rune") || name.contains("Arcane")
                        || name.contains("Magic") || name.contains("Earth Page")) {
                    return 2;
                }
            }
        }

        // Section 3: Ingredients & Misc
        if (itemFactory.isEnchantedShard(item)) return 3;
        if (itemFactory.isSoulfurPotion(item))  return 3;
        if (item.getType() == Material.BOOK && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("Earth Page")) return 3;

        return -1; // not a magic item
    }

    /** Section display name with colour + emoji. */
    public static String sectionLabel(int section) {
        return switch (section) {
            case 0  -> "§5🔮 Runes & Dust";
            case 1  -> "§9⚗ Staffs & Gear";
            case 2  -> "§b📜 Spell Books";
            case 3  -> "§2🌿 Ingredients";
            default -> "§7Unknown";
        };
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void loadPlayer(UUID uuid) {
        File f = new File(bagDir, uuid + ".yml");
        if (!f.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ItemStack[] bag = new ItemStack[TOTAL_SLOTS];
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            bag[i] = cfg.getItemStack("slot." + i);
        }
        bags.put(uuid, bag);
    }

    public void saveAsync(UUID uuid) {
        ItemStack[] snapshot = Arrays.copyOf(getBag(uuid), TOTAL_SLOTS);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File f = new File(bagDir, uuid + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (snapshot[i] != null) cfg.set("slot." + i, snapshot[i]);
            }
            try { cfg.save(f); }
            catch (IOException e) {
                plugin.getLogger().warning("[MagicBag] Failed to save bag for "
                        + uuid + ": " + e.getMessage());
            }
        });
    }

    public void saveAll() {
        for (UUID uuid : bags.keySet()) {
            File f = new File(bagDir, uuid + ".yml");
            YamlConfiguration cfg = new YamlConfiguration();
            ItemStack[] bag = bags.get(uuid);
            for (int i = 0; i < TOTAL_SLOTS; i++) {
                if (bag[i] != null) cfg.set("slot." + i, bag[i]);
            }
            try { cfg.save(f); }
            catch (IOException e) {
                plugin.getLogger().warning("[MagicBag] Failed to save bag for "
                        + uuid + ": " + e.getMessage());
            }
        }
    }
}
