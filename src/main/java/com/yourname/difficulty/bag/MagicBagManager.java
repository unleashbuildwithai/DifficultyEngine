package com.yourname.difficulty.bag;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MagicBagManager — builds and persists each player's Magic Bag.
 *
 * The Magic Bag is a virtual 32-slot storage split into 4 sections:
 *   Section 0 (slots 0-7 )  🔮 Runes & Rune Dust
 *   Section 1 (slots 8-15)  ⚗️ Staffs & Mage Gear
 *   Section 2 (slots 16-23) 📜 Spell Books & Pages
 *   Section 3 (slots 24-31) 🌿 Ingredients & Misc
 *
 * The physical bag item is a CHEST with a PDC tag.  Right-clicking it
 * opens the custom GUI via MagicBagGUIListener.
 *
 * Data is persisted to:  plugins/DifficultyEngine/bags/<uuid>.yml
 */
public class MagicBagManager {

    /** Total number of storage slots (4 sections × 8 slots). */
    public static final int TOTAL_SLOTS    = 32;
    public static final int SECTION_COUNT  = 4;
    public static final int SLOTS_PER_SECTION = 8;

    private static final String PDC_KEY = "magic_bag";

    private final JavaPlugin   plugin;
    private final ItemFactory  itemFactory;
    private final NamespacedKey bagKey;
    private final File          bagDir;

    /** UUID → 32-slot item array (indices 0-31). */
    private final Map<UUID, ItemStack[]> bags = new HashMap<>();

    public MagicBagManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
        this.bagKey      = new NamespacedKey(plugin, PDC_KEY);
        this.bagDir      = new File(plugin.getDataFolder(), "bags");
        if (!bagDir.exists()) bagDir.mkdirs();
    }

    // ── Item builder ──────────────────────────────────────────────────────

    /** Builds the physical Magic Bag item (a CHEST with PDC tag). */
    public ItemStack buildMagicBag() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ §dMagic Bag §5✦");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                "§8" + "─".repeat(28),
                "§7A bag woven from arcane threads.",
                "§7Stores up to §d32 §7magic items",
                "§7across §54 §7organised sections.",
                "§8" + "─".repeat(28),
                "§5🔮 §7Section 1: §dRunes & Dust",
                "§9⚗ §7Section 2: §dStaffs & Mage Gear",
                "§b📜 §7Section 3: §dSpell Books & Pages",
                "§2🌿 §7Section 4: §dIngredients & Misc",
                "§8" + "─".repeat(28),
                "§7Right-click to open. §eShift-click magic",
                "§7items from chests to auto-sort into bag.",
                "§8[DifficultyEngine — Magic Bag]"
            ));
            meta.getPersistentDataContainer().set(bagKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Returns {@code true} if the item is a Magic Bag. */
    public boolean isMagicBag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(bagKey, PersistentDataType.BYTE);
    }

    // ── Bag data access ───────────────────────────────────────────────────

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
     * @return {@code true} if the item was successfully placed; {@code false} if the section is full.
     */
    public boolean addToBag(UUID uuid, ItemStack item) {
        int section = classifyItem(item);
        if (section < 0) return false; // not a magic item

        ItemStack[] bag = getBag(uuid);
        int start = section * SLOTS_PER_SECTION;
        int end   = start + SLOTS_PER_SECTION;

        for (int i = start; i < end; i++) {
            if (bag[i] == null || bag[i].getType().isAir()) {
                bag[i] = item.clone();
                saveAsync(uuid);
                return true;
            }
            // Stack if same type + same meta
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

    // ── Item classification ───────────────────────────────────────────────

    /**
     * Returns the section index (0-3) for a recognised magic item,
     * or -1 if the item is not a magic item handled by this bag.
     *
     * Section 0 — Runes & Dust
     * Section 1 — Staffs & Mage Gear
     * Section 2 — Spell Books & Pages
     * Section 3 — Ingredients & Misc magic items
     */
    public int classifyItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return -1;

        // Section 0: Runes & Rune Dust
        for (MagicElement el : MagicElement.values()) {
            if (itemFactory.isRune(item, el))     return 0;
            if (itemFactory.isRuneDust(item, el)) return 0;
        }

        // Section 1: Staffs & Mage Gear
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
            // Only classify books that have DifficultyEngine lore/name
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
        if (itemFactory.isEnchantedShard(item))  return 3;
        if (itemFactory.isSoulfurPotion(item))   return 3;
        // Earth magic pages
        if (item.getType() == Material.BOOK && item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("Earth Page")) return 3;

        return -1; // not a magic item
    }

    /** Section display name with colour + emoji. */
    public static String sectionLabel(int section) {
        return switch (section) {
            case 0 -> "§5🔮 Runes & Dust";
            case 1 -> "§9⚗ Staffs & Gear";
            case 2 -> "§b📜 Spell Books";
            case 3 -> "§2🌿 Ingredients";
            default -> "§7Unknown";
        };
    }

    // ── Persistence ───────────────────────────────────────────────────────

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
