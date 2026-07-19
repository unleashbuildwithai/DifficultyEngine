package com.yourname.difficulty.bag;

import com.yourname.difficulty.items.ItemFactory;
import com.yourname.difficulty.magic.MagicElement;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * MagicBagManager — builds and persists each player's Magic Bag.
 *
 * The Magic Bag is a 4-page virtual storage, each page keyed to a magic element:
 *   Page 0 — 🔥 Fire   (also acts as the universal inbox)
 *   Page 1 — 💧 Water
 *   Page 2 — 🌍 Earth
 *   Page 3 — 💨 Air
 *
 * Each page holds 36 item slots (4 rows × 9 columns).
 * Total storage: 4 × 36 = 144 slots.
 *
 * ── Auto-sort ──────────────────────────────────────────────────────────────
 *  autoSort() collects all items from all pages, clears the bag, then
 *  re-inserts each item into the page that matches its magic element.
 *  Items with no element classification land on page 0 (Fire/inbox).
 *  Placement within each page is random (shuffled empty-slot selection).
 *
 * ── Persistence ────────────────────────────────────────────────────────────
 *  Data written to:  plugins/DifficultyEngine/bags/<uuid>.yml
 *  Keys: slot.0 … slot.143.
 *  Old 32-slot saves load naturally into the first 32 slots of page 0.
 */
public class MagicBagManager {

    /** Pages (elements). */
    public static final int PAGES          = 4;
    /** Item slots per page (4 rows × 9 cols). */
    public static final int SLOTS_PER_PAGE = 36;
    /** Total virtual storage slots. */
    public static final int TOTAL_SLOTS    = PAGES * SLOTS_PER_PAGE; // 144

    // ── Legacy compat ─────────────────────────────────────────────────────────
    /** @deprecated use {@link #PAGES} */
    @Deprecated public static final int SECTION_COUNT     = PAGES;
    /** @deprecated use {@link #SLOTS_PER_PAGE} */
    @Deprecated public static final int SLOTS_PER_SECTION = SLOTS_PER_PAGE;

    private static final Random RAND = new Random();

    private final JavaPlugin  plugin;
    private final ItemFactory itemFactory;
    private final File        bagDir;

    /** UUID → 144-slot item array. */
    private final Map<UUID, ItemStack[]> bags = new HashMap<>();

    public MagicBagManager(JavaPlugin plugin, ItemFactory itemFactory) {
        this.plugin      = plugin;
        this.itemFactory = itemFactory;
        this.bagDir      = new File(plugin.getDataFolder(), "bags");
        if (!bagDir.exists()) bagDir.mkdirs();
    }

    // ── Item builder / identifier ─────────────────────────────────────────────

    public ItemStack buildMagicBag() { return itemFactory.buildMagicBag(); }

    public boolean isMagicBag(ItemStack item) { return itemFactory.isMagicBag(item); }

    // ── Page labels ───────────────────────────────────────────────────────────

    /** Returns the coloured display label for a given page index. */
    public static String pageLabel(int page) {
        return switch (page) {
            case 0  -> "§c🔥 Fire";
            case 1  -> "§b💧 Water";
            case 2  -> "§2🌍 Earth";
            case 3  -> "§7💨 Air";
            default -> "§5✦ Magic";
        };
    }

    /** @deprecated use {@link #pageLabel(int)} */
    @Deprecated
    public static String sectionLabel(int section) { return pageLabel(section); }

    // ── Bag data access ───────────────────────────────────────────────────────

    public ItemStack[] getBag(UUID uuid) {
        return bags.computeIfAbsent(uuid, k -> new ItemStack[TOTAL_SLOTS]);
    }

    public boolean hasBag(UUID uuid) { return bags.containsKey(uuid); }

    // ── Item classification ───────────────────────────────────────────────────

    /**
     * Maps an item to a page (0-3) based on its magic element.
     *
     * <p>The mapping follows the ordinal order of {@link MagicElement}:
     * ordinal 0 → page 0 (Fire), ordinal 1 → page 1 (Water), etc.
     *
     * <p>Items that don't match any element return {@code 0} (Fire / inbox),
     * so anything can be placed on the first page.
     */
    public int classifyItemToPage(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;

        MagicElement[] elements = MagicElement.values();

        // Check runes / rune-dust per element
        for (int i = 0; i < Math.min(elements.length, PAGES); i++) {
            MagicElement el = elements[i];
            if (itemFactory.isRune(item, el))     return i;
            if (itemFactory.isRuneDust(item, el)) return i;
        }

        // Check staff element
        MagicElement staffEl = itemFactory.getStaffElement(item);
        if (staffEl != null) {
            for (int i = 0; i < Math.min(elements.length, PAGES); i++) {
                if (elements[i] == staffEl) return i;
            }
        }

        // Everything else (gear, books, weapons, misc) → page 0 (inbox)
        return 0;
    }

    /**
     * @deprecated use {@link #classifyItemToPage(ItemStack)}
     */
    @Deprecated
    public int classifyItem(ItemStack item) {
        return classifyItemToPage(item);
    }

    // ── Add to bag ────────────────────────────────────────────────────────────

    /**
     * Adds {@code item} to a specific {@code page}, using random empty-slot
     * selection within that page.  Stacks onto existing compatible slots first.
     *
     * @return {@code true} if fully absorbed; {@code false} if page is full.
     */
    public boolean addToBag(UUID uuid, ItemStack item, int page) {
        if (item == null || item.getType().isAir()) return false;
        ItemStack[] bag = getBag(uuid);
        int start = page * SLOTS_PER_PAGE;
        int end   = start + SLOTS_PER_PAGE;

        // Phase 1: stack onto matching slots
        for (int i = start; i < end; i++) {
            if (bag[i] == null || !bag[i].isSimilar(item)) continue;
            int space = bag[i].getMaxStackSize() - bag[i].getAmount();
            if (space <= 0) continue;
            int give = Math.min(space, item.getAmount());
            bag[i].setAmount(bag[i].getAmount() + give);
            item.setAmount(item.getAmount() - give);
            if (item.getAmount() <= 0) { saveAsync(uuid); return true; }
        }

        // Phase 2: random empty slot
        List<Integer> empty = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (bag[i] == null || bag[i].getType().isAir()) empty.add(i);
        }
        if (!empty.isEmpty()) {
            int slot = empty.get(RAND.nextInt(empty.size()));
            bag[slot] = item.clone();
            saveAsync(uuid);
            return true;
        }
        return false; // page full
    }

    /**
     * Classifies the item then inserts it into the correct page with random
     * slot placement.
     */
    public boolean addToBag(UUID uuid, ItemStack item) {
        return addToBag(uuid, item, classifyItemToPage(item));
    }

    // ── Auto-sort ─────────────────────────────────────────────────────────────

    /**
     * Redistributes all items across pages according to their element
     * classification.  Placement within each page is randomised.
     *
     * <p>Call this when the player clicks the Auto-Sort button.
     */
    public void autoSort(UUID uuid) {
        ItemStack[] bag = getBag(uuid);

        // Collect everything
        List<ItemStack> all = new ArrayList<>();
        for (ItemStack it : bag) {
            if (it != null && !it.getType().isAir()) all.add(it.clone());
        }

        // Wipe and re-insert in shuffled order (equal chance for all slots)
        Arrays.fill(bag, null);
        Collections.shuffle(all, RAND);
        for (ItemStack it : all) {
            addToBag(uuid, it); // classifies → random slot in correct page
        }
        saveAsync(uuid);
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
