package com.yourname.difficulty.items;

import com.yourname.difficulty.items.EarthBlockTier;
import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ItemFactory — Central registry for all DifficultyEngine custom items.
 *
 * Mage Gear Tiers (4):
 *   APPRENTICE (Lv  1) — Purple + String.   -100ms/piece.
 *   MAGE       (Lv 30) — Purple + Blaze.    -250ms/piece. (original)
 *   ALCH       (Lv 60) — Blue + Ender.      -350ms/piece.
 *   MASTER     (Lv 90) — Black + Dragon.    -500ms/piece.
 *
 * Air Gust Multiplier:
 *   Determined by total airPower across all equipped mage gear pieces.
 *   No gear = 0.50×.  Full Master set = 2.00×.
 */
public class ItemFactory {

    // ── PDC key constants ─────────────────────────────────────────────────────
    public static final String SOULFUR_POTION_KEY    = "soulfur_potion";
    public static final String TURBO_MINECART_KEY    = "turbo_minecart";
    public static final String ENCHANTED_SHARD_KEY   = "enchanted_shard";
    public static final String MAGE_GEAR_KEY         = "mage_gear"; // universal tag on ALL tiers
    public static final String SPELL_COMBO_BOOK_KEY  = "spell_combo_book";
    public static final String ANCIENT_KILL_TOME_KEY = "ancient_kill_tome";
    public static final String UNICORN_SLIPPERS_KEY  = "unicorn_slippers";
    /** Prefix for all earth-page PDC keys. */
    public static final String EARTH_PAGE_KEY_PREFIX = "de_earth_page_";
    public static final String GUNZ_SWORD_KEY        = "gunz_sword";
    public static final String DARK_BOW_KEY          = "dark_bow";
    public static final String DRAGON_ARROW_KEY      = "dragon_arrow";
    public static final String DRAGON_ARROW_TIP_KEY  = "dragon_arrow_tip";

    // ── NamespacedKeys ────────────────────────────────────────────────────────
    private final NamespacedKey soulfurPotionKey;
    private final NamespacedKey turboMinecartKey;
    private final NamespacedKey enchantedShardKey;
    private final NamespacedKey mageGearKey;          // universal mage gear tag
    private final NamespacedKey spellComboBookKey;
    private final NamespacedKey ancientKillTomeKey;
    private final NamespacedKey unicornSlippersKey;
    private final NamespacedKey gunZSwordKey;
    private final NamespacedKey darkBowKey;
    private final NamespacedKey dragonArrowKey;
    private final NamespacedKey dragonArrowTipKey;
    /** Per-tier PDC keys (MAGE tier maps to mageGearKey). */
    private final Map<MageGearTier, NamespacedKey> mageGearTierKeys = new EnumMap<>(MageGearTier.class);

    private final Map<MagicElement, NamespacedKey> staffKeys    = new EnumMap<>(MagicElement.class);
    private final Map<MagicElement, NamespacedKey> runeKeys     = new EnumMap<>(MagicElement.class);
    private final Map<MagicElement, NamespacedKey> runeDustKeys = new EnumMap<>(MagicElement.class);

    /** Per-tier Earth Magic Page keys. */
    private final Map<EarthBlockTier, NamespacedKey> earthPageKeys = new EnumMap<>(EarthBlockTier.class);

    private final SkillCapeManager capeManager;

    private final List<ItemStack> registryPage1 = new ArrayList<>();
    private final List<ItemStack> registryPage2 = new ArrayList<>();

    public ItemFactory(JavaPlugin plugin, SkillCapeManager capeManager) {
        this.soulfurPotionKey    = new NamespacedKey(plugin, SOULFUR_POTION_KEY);
        this.turboMinecartKey    = new NamespacedKey(plugin, TURBO_MINECART_KEY);
        this.enchantedShardKey   = new NamespacedKey(plugin, ENCHANTED_SHARD_KEY);
        this.mageGearKey         = new NamespacedKey(plugin, MAGE_GEAR_KEY);
        this.spellComboBookKey   = new NamespacedKey(plugin, SPELL_COMBO_BOOK_KEY);
        this.ancientKillTomeKey  = new NamespacedKey(plugin, ANCIENT_KILL_TOME_KEY);
        this.unicornSlippersKey  = new NamespacedKey(plugin, UNICORN_SLIPPERS_KEY);
        this.gunZSwordKey        = new NamespacedKey(plugin, GUNZ_SWORD_KEY);
        this.darkBowKey          = new NamespacedKey(plugin, DARK_BOW_KEY);
        this.dragonArrowKey      = new NamespacedKey(plugin, DRAGON_ARROW_KEY);
        this.dragonArrowTipKey   = new NamespacedKey(plugin, DRAGON_ARROW_TIP_KEY);

        // Tier-specific keys (MAGE reuses the universal key)
        for (MageGearTier tier : MageGearTier.values()) {
            if (tier == MageGearTier.MAGE) {
                mageGearTierKeys.put(tier, mageGearKey);
            } else {
                mageGearTierKeys.put(tier, new NamespacedKey(plugin, tier.pdcKey));
            }
        }

        for (MagicElement el : MagicElement.values()) {
            staffKeys.put(el,    new NamespacedKey(plugin, el.staffKey));
            runeKeys.put(el,     new NamespacedKey(plugin, el.runeKey));
            runeDustKeys.put(el, new NamespacedKey(plugin, el.runeKey + "_dust"));
        }

        // Earth Magic Page keys — one per throwable block tier
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            earthPageKeys.put(tier, new NamespacedKey(plugin, tier.pageKey));
        }

        this.capeManager = capeManager;
        register();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void register() {
        // Page 1: core magic items
        registryPage1.add(buildSoulfurPotion());
        registryPage1.add(buildTurboMinecart());
        registryPage1.add(buildEnchantedShard());
        for (MagicElement el : MagicElement.values()) registryPage1.add(buildStaff(el));
        for (MagicElement el : MagicElement.values()) registryPage1.add(buildRune(el, 8));
        for (MagicElement el : MagicElement.values()) registryPage1.add(buildRuneDust(el, 1));
        // All 4 tiers × 4 pieces = 16 mage gear items
        for (MageGearTier tier : MageGearTier.values()) {
            registryPage1.add(buildMageGearPiece(tier, Material.LEATHER_HELMET,     "Hood"));
            registryPage1.add(buildMageGearPiece(tier, Material.LEATHER_CHESTPLATE, "Robe Top"));
            registryPage1.add(buildMageGearPiece(tier, Material.LEATHER_LEGGINGS,   "Robe Bottom"));
            registryPage1.add(buildMageGearPiece(tier, Material.LEATHER_BOOTS,      "Boots"));
        }
        registryPage1.add(buildSpellComboBook());
        registryPage1.add(buildAncientKillTome());
        registryPage1.add(buildArcaneTomeDisplay());
        registryPage1.add(buildSpellPageDisplay());

        // Page 1 additions: special weapons
        registryPage1.add(buildGunZSword());
        registryPage1.add(buildDarkBow());
        registryPage1.add(buildDragonArrow(8));
        registryPage1.add(buildDragonArrowTip(4));

        // Page 2: books + capes + cosmetics
        registryPage2.add(buildNoviceMagicPrimer());
        registryPage2.add(buildMagesPrimerBook());
        registryPage2.add(buildElementalTheoryBook());
        registryPage2.add(buildHiddenArtsBook());
        registryPage2.add(buildMageGearGuide());
        registryPage2.add(buildUnicornSlippers());
        registryPage2.addAll(capeManager.buildAllCapes());
        // Earth Magic Pages — one per throwable block tier
        for (EarthBlockTier tier : EarthBlockTier.values()) {
            registryPage2.add(buildEarthMagicPage(tier));
        }
    }

    // ── Soulfur Potion ────────────────────────────────────────────────────────

    public ItemStack buildSoulfurPotion() {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5☠ Soulfur Potion");
            meta.setColor(Color.fromRGB(80, 0, 120));
            meta.setLore(List.of(
                "§7A potion brewed from the depths of the Nether.",
                "§8▶ Causes §5Nausea §8and §5Drunken Sway§8.",
                "§8▶ Repeated sips darken your vision.",
                "§8[DifficultyEngine — Soulfur Potion]"
            ));
            meta.getPersistentDataContainer().set(soulfurPotionKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSoulfurPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(soulfurPotionKey, PersistentDataType.BYTE);
    }

    // ── Turbo Minecart ────────────────────────────────────────────────────────

    public ItemStack buildTurboMinecart() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6⚡ Turbo Minecart");
            meta.setLore(List.of(
                "§7A magnetically-locked high-speed rail cart.",
                "§8▶ §63× faster §8than a normal minecart.",
                "§8[DifficultyEngine — Turbo Minecart]",
                "§8Requires: §cdifficultyengine.turbocart"
            ));
            meta.getPersistentDataContainer().set(turboMinecartKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTurboMinecart(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(turboMinecartKey, PersistentDataType.BYTE);
    }

    // ── Enchanted Shard ───────────────────────────────────────────────────────

    public ItemStack buildEnchantedShard() {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ Enchanted Shard");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§7A crystallised fragment of pure magic.",
                "§6Rare drop ~5% from any hostile mob.",
                "§7Used to craft §delemental staffs §7and §4Master Mage Gear§7.",
                "§8[DifficultyEngine — Enchanted Shard]"
            ));
            meta.getPersistentDataContainer().set(enchantedShardKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isEnchantedShard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(enchantedShardKey, PersistentDataType.BYTE);
    }

    // ── Elemental Staffs ──────────────────────────────────────────────────────

    public ItemStack buildStaff(MagicElement element) {
        ItemStack item = new ItemStack(element.staffMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(element.staffName);
            meta.setCustomModelData(element.staffCMD);
            // Deep, always-visible enchantment glow — guaranteed regardless of held/inventory state
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(buildStaffLore(element));
            meta.getPersistentDataContainer().set(staffKeys.get(element), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildStaffLore(MagicElement element) {
        List<String> lore = new ArrayList<>();
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Element: " + element.color + element.name());
        lore.add("§8" + "─".repeat(26));
        lore.add(switch (element) {
            case FIRE  -> "§7Right-click → §claunches a fireball§7.";
            case WATER -> "§7Right-click on ground (+ bucket) → §b5-block river§7.";
            case EARTH -> "§7Right-click → §2throws a block§7 (Lv10+: use blocks in inventory).";
            case AIR   -> "§7On ground → §7air gust. In air → §7hover (slow fall)§7.";
        });
        lore.add("§8▶ Air gust power scales with §5mage gear §8equipped.");
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Consumes §61× §7" + element.runeName + " §7per cast.");
        lore.add("§6Craft: §7Shard + §7" + element.staffCraftIngredient.name() + " + §7Stick");
        lore.add("§8[DifficultyEngine — Magic Staff]");
        return lore;
    }

    public boolean isStaff(ItemStack item, MagicElement element) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(staffKeys.get(element), PersistentDataType.BYTE);
    }

    public MagicElement getStaffElement(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (MagicElement el : MagicElement.values()) {
            if (pdc.has(staffKeys.get(el), PersistentDataType.BYTE)) return el;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAGE GEAR — All 4 Tiers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Builds a mage gear piece for the given tier, material and piece name.
     * All pieces get the universal {@code mage_gear} PDC key PLUS their
     * tier-specific key (so {@link #getMageGearTier} can identify them).
     */
    public ItemStack buildMageGearPiece(MageGearTier tier, Material mat, String pieceName) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(tier.displayPrefix + " " + pieceName);
            meta.setColor(tier.color);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§7Tier: " + tier.displayPrefix,
                "§8Requires: §aMagic Level §a" + tier.levelRequired,
                "§8" + "─".repeat(26),
                "§d✦ §7Spell cooldown: §a-" + tier.cooldownBonus + "ms §8per piece",
                "§d✦ §7Air gust power: §a+" + (int)(tier.airPower * 12.5) + "% §8per piece",
                "§7Full set (4 pcs): §a-" + (tier.cooldownBonus * 4) + "ms cooldown",
                "§8" + "─".repeat(26),
                "§6Craft: §7" + tier.craftIngredients,
                "§8[DifficultyEngine — " + tier.displayPrefix.replaceAll("§.", "") + " Gear]"
            ));
            // Universal tag (any mage gear)
            meta.getPersistentDataContainer().set(mageGearKey, PersistentDataType.BYTE, (byte) 1);
            // Tier-specific tag
            NamespacedKey tierKey = mageGearTierKeys.get(tier);
            if (tierKey != null && tierKey != mageGearKey) {
                meta.getPersistentDataContainer().set(tierKey, PersistentDataType.BYTE, (byte) 1);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Legacy method — builds original MAGE-tier gear by name+bonus string.
     * Used by {@link com.yourname.difficulty.listeners.MageGearCraftListener}.
     */
    public ItemStack buildMageGear(Material mat, String name, String bonus) {
        // Delegate to tier-aware method with MAGE tier
        return buildMageGearPiece(MageGearTier.MAGE, mat,
            name.replaceAll("§5✦ Mage ", "").replaceAll("§5✦ ", ""));
    }

    /** Returns true if the item is ANY tier of mage gear. */
    public boolean isMageGear(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(mageGearKey, PersistentDataType.BYTE);
    }

    /**
     * Returns the {@link MageGearTier} of the item, or {@code null} if it is
     * not mage gear. Checks from MASTER down to APPRENTICE.
     */
    public MageGearTier getMageGearTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        // Check from highest to lowest — MASTER first avoids ambiguity
        MageGearTier[] reversed = {MageGearTier.MASTER, MageGearTier.ALCH,
                                    MageGearTier.MAGE,   MageGearTier.APPRENTICE};
        for (MageGearTier tier : reversed) {
            NamespacedKey key = mageGearTierKeys.get(tier);
            if (key != null && pdc.has(key, PersistentDataType.BYTE)) {
                return tier;
            }
        }
        return null;
    }

    /**
     * Counts how many mage gear pieces (ANY tier) are currently equipped in
     * the player's armour slots.  Used for Mind Bomb chance calculation.
     */
    public int countMageGearPieces(Player player) {
        int count = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (isMageGear(piece)) count++;
        }
        return count;
    }

    /**
     * Returns the total cooldown reduction (ms) from all mage gear worn.
     * Each tier contributes its own {@link MageGearTier#cooldownBonus} per piece.
     */
    public long getMageGearCooldownBonus(Player player) {
        long bonus = 0L;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            MageGearTier tier = getMageGearTier(piece);
            if (tier != null) bonus += tier.cooldownBonus;
        }
        return bonus;
    }

    /**
     * Returns the total air-gust power from all mage gear worn.
     * Range 0.0 (no gear) to 8.0 (full 4-piece Master set).
     * The Air Staff handler converts this to a velocity multiplier.
     */
    public double getAirGearPower(Player player) {
        double power = 0.0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            MageGearTier tier = getMageGearTier(piece);
            if (tier != null) power += tier.airPower;
        }
        return power;
    }

    /** Returns the NamespacedKey for a specific mage gear tier. */
    public NamespacedKey getMageGearTierKey(MageGearTier tier) {
        return mageGearTierKeys.get(tier);
    }

    // ── Elemental Runes ───────────────────────────────────────────────────────

    public ItemStack buildRune(MagicElement element, int amount) {
        ItemStack item = new ItemStack(element.runeMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(element.runeName);
            meta.setCustomModelData(element.runeCMD);
            // Vibrant enchantment glint — runes glow even without an enchantment tag
            meta.setEnchantmentGlintOverride(true);
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7Consumed when casting with the",
                element.color + element.name() + " Staff§7.",
                "§8" + "─".repeat(22),
                "§6Craft: §74× " + element.runeCraftIngredient.name() + " §8→ §68× Rune",
                "§8[DifficultyEngine — Magic Rune]"
            ));
            meta.getPersistentDataContainer().set(runeKeys.get(element), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Rune Dust ─────────────────────────────────────────────────────────────

    public ItemStack buildRuneDust(MagicElement element, int count) {
        ItemStack item = new ItemStack(element.runeCraftIngredient, Math.max(1, count));
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(element.color + element.name().charAt(0)
                + element.name().substring(1).toLowerCase() + " Rune Dust");
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7Magical dust — drops from " + dustMobSource(element) + "§7.",
                "§6Craft: §74× this §7→ §68× " + element.runeName,
                "§8[DifficultyEngine — Rune Dust]"
            ));
            meta.getPersistentDataContainer().set(runeDustKeys.get(element), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String dustMobSource(MagicElement el) {
        return switch (el) {
            case FIRE  -> "§cBlazes §7& fire mobs";
            case WATER -> "§bDrowned §7& guardians";
            case EARTH -> "§2Zombies §7& spiders";
            case AIR   -> "§7Phantoms §7& ghasts";
        };
    }

    public boolean isRuneDust(ItemStack item, MagicElement element) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(runeDustKeys.get(element), PersistentDataType.BYTE);
    }

    public boolean isAnyRuneDust(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        for (MagicElement el : MagicElement.values()) if (isRuneDust(item, el)) return true;
        return false;
    }

    public boolean isRune(ItemStack item, MagicElement element) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(runeKeys.get(element), PersistentDataType.BYTE);
    }

    // ── Arcane Tome & Spell Page display ──────────────────────────────────────

    public ItemStack buildArcaneTomeDisplay() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ Arcane Tome");
            meta.setLore(List.of(
                "§7Your personal spell combo book.",
                "§7Pages unlock via §dSpell Pages§7.",
                "§7Use §6/spellbook §7to read it.",
                "§8[DifficultyEngine — Arcane Tome]"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack buildSpellPageDisplay() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d✧ Spell Page");
            meta.setLore(List.of(
                "§7Torn from an ancient spell tome.",
                "§7Right-click to unlock a random combo.",
                "§7Drop: §84% from any hostile mob",
                "§8[DifficultyEngine — Spell Page]"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Spell Combo Book ──────────────────────────────────────────────────────

    public ItemStack buildSpellComboBook() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ Spell Combo Book");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§6Passive Effect (carry this):",
                "§7Combo hints appear in the action bar",
                "§7after each spell hits a target.",
                "§8" + "─".repeat(26),
                "§8• §7Lose it — cast blind",
                "§8• §78% drop from any mob killed by staff",
                "§8[DifficultyEngine — Spell Combo Book]"
            ));
            meta.getPersistentDataContainer().set(spellComboBookKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSpellComboBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(spellComboBookKey, PersistentDataType.BYTE);
    }

    public boolean hasSpellComboBook(Player player) {
        for (ItemStack s : player.getInventory().getContents()) if (isSpellComboBook(s)) return true;
        return false;
    }

    // ── Ancient Kill Tome ─────────────────────────────────────────────────────

    public ItemStack buildAncientKillTome() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§cAncient Kill Tome");
            meta.setAuthor("§7Unknown Archmage");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage(
                "§c§l⚠ RESTRICTED ⚠\n\n" +
                "§7This tome reveals\ninstant-death spells.\n\n" +
                "§7From §4§lDOUBLE BOSS\nEVENTS §7only.\n\n" +
                "§8Carry it to unlock\nkill hints in combat."
            );
            meta.addPage(
                "§8§l FROZEN SHATTER \n\n" +
                "§7Target is §b§lFROZEN§7.\nHit with Air bolt.\n\n" +
                "§c§l⚡ INSTANT DEATH\n\n" +
                "§7Their body shatters\non impact."
            );
            meta.addPage(
                "§8§l STATUE CRUMBLE \n\n" +
                "§7Target is §6§lSTATUE§7.\nHit with Air bolt.\n\n" +
                "§c§l⚡ INSTANT DEATH\n\n" +
                "§7The mud explodes\noutward violently."
            );
            meta.getPersistentDataContainer().set(ancientKillTomeKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isAncientKillTome(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(ancientKillTomeKey, PersistentDataType.BYTE);
    }

    public boolean hasAncientKillTome(Player player) {
        for (ItemStack s : player.getInventory().getContents()) if (isAncientKillTome(s)) return true;
        return false;
    }

    // ── Mage Gear Guide (first-wand gift) ────────────────────────────────────

    public ItemStack buildMageGearGuide() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§5Mage Gear Guide");
            meta.setAuthor("§7DifficultyEngine");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage(
                "§5§l─ Mage Gear Guide ─\n\n" +
                "§7Welcome, mage!\n\n" +
                "§7Mage gear amplifies\nyour elemental power\nand reduces spell\ncooldowns.\n\n" +
                "§7There are §54 tiers§7,\neach gated by your\nMagic level."
            );
            meta.addPage(
                "§9§l─ Apprentice Gear ─\n" +
                "§8Magic Lv §a1§8+\n\n" +
                "§6Ingredients:\n" +
                "§7Leather piece\n" +
                "§7+ Purple Dye\n" +
                "§7+ String\n\n" +
                "§d✦ §7-100ms/piece\n" +
                "§d✦ §7Air power x0.75"
            );
            meta.addPage(
                "§5§l─ Mage Gear ─\n" +
                "§8Magic Lv §a30§8+\n\n" +
                "§6Ingredients:\n" +
                "§7Leather piece\n" +
                "§7+ Purple Dye\n" +
                "§7+ Blaze Powder\n\n" +
                "§d✦ §7-250ms/piece\n" +
                "§d✦ §7Air power x1.25"
            );
            meta.addPage(
                "§b§l─ Alch Mage Gear ─\n" +
                "§8Magic Lv §a60§8+\n\n" +
                "§6Ingredients:\n" +
                "§7Leather piece\n" +
                "§7+ Blue Dye\n" +
                "§7+ Blaze Powder\n" +
                "§7+ Eye of Ender\n\n" +
                "§d✦ §7-350ms/piece\n" +
                "§d✦ §7Air power x1.625"
            );
            meta.addPage(
                "§4§l─ Master Mage Gear ─\n" +
                "§8Magic Lv §a90§8+\n\n" +
                "§6Ingredients:\n" +
                "§7Leather piece\n" +
                "§7+ Black Dye\n" +
                "§7+ Blaze Powder\n" +
                "§7+ Enchanted Shard\n" +
                "§7+ Dragon Breath\n\n" +
                "§d✦ §7-500ms/piece\n" +
                "§d✦ §7Air power x2.0!"
            );
            meta.addPage(
                "§5§l─ Air Gust Power ─\n\n" +
                "§7No gear: §c0.5x§7 speed\n" +
                "§7(50% nerf without gear)\n\n" +
                "§7Each piece adds to\nyour air power score.\n\n" +
                "§7Full Master set:\n§a2.0x §7air knockback!\n\n" +
                "§8Wear 4 pieces for\nmaximum power."
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Novice Magic Primer ───────────────────────────────────────────────────

    public ItemStack buildNoviceMagicPrimer() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§aNovice's Spell Guide");
            meta.setAuthor("§7Wandering Apprentice");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage(
                "§8§l─ Novice Spell Guide ─\n\n" +
                "§7Welcome, apprentice!\n\n" +
                "§7This guide teaches\nyou the basics of\nelemental magic.\n\n" +
                "§74 elements exist:\n§cFire §bWater §2Earth §7Air\n\n" +
                "§8Read all pages!"
            );
            meta.addPage(
                "§8§l─ Crafting a Staff ─\n\n" +
                "§7You need:\n\n" +
                "§6• 1 Enchanted Shard\n§7  (~5% drop from mobs)\n" +
                "§6• 1 Element ingredient\n" +
                "§6• 1 Stick\n\n" +
                "§7Craft at a §6crafting table§7!"
            );
            meta.addPage(
                "§8§l─ Getting Runes ─\n\n" +
                "§7Each cast uses 1 rune.\n\n" +
                "§6Craft runes:\n§74× ingredient → §68 runes\n\n" +
                "§cFire§7: Nether Brick\n" +
                "§bWater§7: Ice\n" +
                "§2Earth§7: Clay Ball\n" +
                "§7Air§7: Feather"
            );
            meta.addPage(
                "§8§l─ Basic Spells ─\n\n" +
                "§cFire§7: Fireball. Scorches.\n\n" +
                "§bWater§7: Bolt. Soaks (WET).\n\n" +
                "§2Earth§7: Bolt. Slows.\n§72 hits = suffocate!\n§7Lv10+ throws blocks!\n\n" +
                "§7Air§7: Gust knocks back.\n§7Hold in air = hover!"
            );
            meta.addPage(
                "§8§l─ Earth Block Throwing ─\n\n" +
                "§7At §aMagic Lv 10§7+:\n\n" +
                "§7Carry a §2throwable block\n§7AND an §2Earth Magic Page\n" +
                "§7in your inventory.\n\n" +
                "§71st hit: §6Trap §7(block under feet)\n" +
                "§72nd hit: §cSuffocate!\n\n" +
                "§8Higher blocks = more damage."
            );
            meta.addPage(
                "§8§l─ Your First Combo ─\n\n" +
                "§7Hit with §cFire §7twice:\n\n" +
                "§71. §cFire §7→ §cScorched §7(3s)\n" +
                "§72. §cFire §7again → §c§lBLAZING!\n\n" +
                "§7Find a §5Spell Combo Book§7\nfor all hints!\n\n" +
                "§8Good luck, mage."
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Mage's Primer ─────────────────────────────────────────────────────────

    public ItemStack buildMagesPrimerBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§5The Mage's Primer");
            meta.setAuthor("§7Master Aldric");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage("§8§l─ The Mage's Primer ─\n\n§7Status effects are the\ncore of combo magic.\n\nHit targets to apply\na status, then follow\nup with the right\nelement to combo!");
            meta.addPage("§8§l─ Fire Combos ─\n\n§cFire§7→§cScorched (3s)\n§7+§cFire§7→§c§lBlazing!\n§7+§7Air§7→§6Fanned Flames\n§7+§bWater§7→§6Steam Burst\n\n§c§lBlazing§7 targets:\n§7+§7Air§7→§c§lInferno Blast\n§7+§bWater§7→§6Steam Explosion\n§7+§cFire§7→§c§lInferno Vortex");
            meta.addPage("§8§l─ Water Combos ─\n\n§bWater§7→§bWet (10s)\n\n§bWet§7 targets:\n§7+§2Earth§7→§6Muddy\n§7+§7Air§7→§b❄Chilled (2.5s!)\n§7+§bWater§7→§6Flood Wash\n\n§bChilled§7 targets:\n§7+§7Air§7→§b§lFROZEN (5s!)\n§7+§2Earth§7→§6Cracked Ice");
            meta.addPage("§8§l─ Earth Combos ─\n\n§2Earth§7→Trap+Slowness\n§2Earth+Earth§7→§2Suffocate!\n\n§6Muddy§7 targets:\n§7+§cFire§7→§6§lSTATUE (8s!)\n§7+§7Air§7→§6Mud Launch\n§7+§bWater§7→§6Flood Wash\n\n§6§lStatue§7:\n§7+§2Earth§7→§6Crumble\n§7+§7Air§7→§c§l??? (seek tome)");
            meta.addPage("§8§l─ Air Combos ─\n\n§7On§bWet§7→§b❄Chilled\n§7On§b❄Chilled§7→§b§lFrozen\n§7On§6Muddy§7→§6Mud Launch\n§7On§c§lBlazing§7→§c§lInferno Blast\n§7On§cScorched§7→§6Fanned\n§7On§b§lFrozen§7→§c§l???(seek tome)\n\n§8§lHOVER:§7Hold Air staff\nin air to float!");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Elemental Theory Book ─────────────────────────────────────────────────

    public ItemStack buildElementalTheoryBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§9Advanced Elemental Theory");
            meta.setAuthor("§7Archmage Vethis");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage("§8§l─ Advanced Theory ─\n\n§7You have mastered the\nbasics. Now learn the\ndeadly chains.\n\n§7The freeze chain and\nstatue trap can end\nfights instantly.\n\n§8Seek the §cAncient\nKill Tome §8for the\nfinal steps.");
            meta.addPage("§8§l─ The Freeze Chain ─\n\n§71. §bWater§7→§bWet\n§72. §7Air on Wet§7→§b❄Chilled\n§73. §7Air on Chilled\n§7   →§b§lFROZEN (5s!)\n§74. ??? on Frozen\n§7   →§c§lINSTANT DEATH\n\n§8High Magic level\nlets you cast faster.");
            meta.addPage("§8§l─ The Statue Trap ─\n\n§71. §bWater§7→§bWet\n§72. §2Earth on Wet§7→§6Muddy\n§73. §cFire on Muddy\n§7   →§6§lSTATUE (8s!)\n§74. ??? on Statue\n§7   →§c§lINSTANT DEATH\n\n§88 seconds is your\nwindow. Make it count.");
            meta.addPage("§8§l─ Thaw Explosion ─\n\n§7A §b§lFROZEN §7target hit\nwith §cFire §7creates a\nmassive steam burst.\n\n§c🔥 Fire + §b§lFrozen\n→§c§lTHAW EXPLOSION!\n\nAoE damage. Great\nvs groups!\n\n§8Also: §bWater§7 on §b§lFrozen\n→§6Slush §7(slow+blind)");
            meta.addPage("§8§l─ Mind Bomb ─\n\n§7Wear §d2+ Mage Gear\n§7for a §d5% §7chance on\nany combo hit:\n\n§d§lMIND BOMB!\n§7Nausea+Blindness 5s\n\n§730% chance→§c§lFALLEN§7.\nPress SPACE to get up.");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Hidden Arts Book ──────────────────────────────────────────────────────

    public ItemStack buildHiddenArtsBook() {
        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) item.getItemMeta();
        if (meta != null) {
            meta.setTitle("§4The Hidden Arts");
            meta.setAuthor("§7Unknown");
            meta.setGeneration(BookMeta.Generation.ORIGINAL);
            meta.addPage("§8§l─ The Hidden Arts ─\n\n§7Few mages reach this\nlevel of mastery.\n\n§7These combos are not\ntaught — they must be\ndiscovered.\n\n§4Guard this knowledge.");
            meta.addPage("§8§l§n Slush \n\n§bWater §7on §b§lFROZEN\n\n§b§lSlush effects:\n§7• §cSlowness III (3s)\n§7• §cBlindness (3s)\n§7• Bonus damage burst");
            meta.addPage("§8§l§n Smothered \n\n§2Earth §7on §c§lBLAZING\n\n§2§lSmothered:\n§7• Extinguish fire\n§7• Heavy bonus damage\n§7• Dirt burst particles");
            meta.addPage("§8§l§n Inferno Vortex \n\n§cFire §7on §c§lBLAZING\n\n§c§lInferno Vortex:\n§7• Massive fire ticks\n§7• Double damage burst");
            meta.addPage("§8§l§n Ground Magic \n\n§cFire §7on solid blocks\n→§c§lLAVA pool (30s)\n\n§bWater §7on §c§lLAVA\n→§8§lOBSIDIAN TRAP!\n\n§8Converts all touching\nlava to obsidian.");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Unicorn Slippers ──────────────────────────────────────────────────────

    public ItemStack buildUnicornSlippers() {
        ItemStack item = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§d🦄 Unicorn Slippers");
            meta.setColor(Color.fromRGB(255, 220, 250));
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§7Mystical slippers from the",
                "§7realm of the §dUnicorns§7.",
                "§8" + "─".repeat(26),
                "§d✦ §dRainbow Aura§7:",
                "§7Wearing these creates a",
                "§7§d§orainbow particle trail§7",
                "§7at your feet while worn.",
                "§8" + "─".repeat(26),
                "§6VIP Shop exclusive §8— §65,000 gp",
                "§8[DifficultyEngine — Unicorn Slippers]"
            ));
            meta.getPersistentDataContainer().set(unicornSlippersKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isUnicornSlippers(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(unicornSlippersKey, PersistentDataType.BYTE);
    }

    // ── GunZ Sword ────────────────────────────────────────────────────────────

    /**
     * The GunZ Sword — admin-only Level 99 Melee weapon with GunZ: The Duel
     * style dashing. Double-tap WASD while holding to dash in that direction.
     */
    @SuppressWarnings("deprecation")
    public ItemStack buildGunZSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4⚔ §lGunZ Sword");
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.SHARPNESS,        5, true);
            meta.addEnchant(Enchantment.UNBREAKING,       3, true);
            meta.addEnchant(Enchantment.LOOTING,          3, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT,      2, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§4⚔ §cGunZ Sword §8— §4Admin Spawn Only",
                "§8Requires: §aMelee Level 99",
                "§8" + "─".repeat(26),
                "§c✦ §7GunZ Dashing:",
                "§7Double-tap §fWW §7→ Forward dash",
                "§7Double-tap §fAA §7→ Left dash",
                "§7Double-tap §fDD §7→ Right dash",
                "§7Double-tap §fSS §7→ Backward dash",
                "§8" + "─".repeat(26),
                "§8Dash cooldown: 0.8s",
                "§8[DifficultyEngine — GunZ Sword]"
            ));
            meta.getPersistentDataContainer().set(gunZSwordKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isGunZSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(gunZSwordKey, PersistentDataType.BYTE);
    }

    // ── Dark Bow ──────────────────────────────────────────────────────────────

    /**
     * The Dark Bow — Level 70 Ranged bow. 1% drop from the Warden.
     * Normal shot: single arrow. With Dragon Arrows: purple trail effect.
     * Special (SNEAK + right-click): 2 homing arrows at −35% damage each.
     * Requires 2 Dragon Arrows for the special shot.
     */
    public ItemStack buildDarkBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§4🏹 Dark Bow");
            meta.setUnbreakable(true);
            meta.addEnchant(Enchantment.POWER,     5, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.PUNCH,      2, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§4🏹 §cDark Bow",
                "§8Requires: §aRanged Level 70",
                "§8Drop: §c1% §8from Warden",
                "§8" + "─".repeat(26),
                "§7Normal shot: single arrow",
                "§7With Dragon Arrows: §5purple trail",
                "§8" + "─".repeat(26),
                "§c✦ §4Special §8(§fSNEAK§8 + §fRight-click§8):",
                "§7Fires §c2 homing arrows§7 instantly.",
                "§7Each at §c-35% §7damage — but dual-hit!",
                "§7Costs 2 Dragon Arrows. §83s cooldown.",
                "§8" + "─".repeat(26),
                "§8Combo: Normal → Special → Normal",
                "§8[DifficultyEngine — Dark Bow]"
            ));
            meta.getPersistentDataContainer().set(darkBowKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isDarkBow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(darkBowKey, PersistentDataType.BYTE);
    }

    // ── Dragon Arrow ──────────────────────────────────────────────────────────

    /**
     * Dragon Arrow — crafted from Dragon Arrow Tips (dropped by Ender Dragon).
     * Used with the Dark Bow for enhanced effects and homing special shots.
     */
    public ItemStack buildDragonArrow(int count) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5✦ Dragon Arrow");
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7An arrow tipped with dragon essence.",
                "§5✦ §7Used with the §4Dark Bow§7.",
                "§8" + "─".repeat(22),
                "§7Normal shot: §5purple trail §7+ glow on hit",
                "§7Special shot: §c2× homing §8(needs 2)",
                "§8" + "─".repeat(22),
                "§6Craft: §74× Dragon Arrow Tips §8→ §54 Dragon Arrows",
                "§8[DifficultyEngine — Dragon Arrow]"
            ));
            meta.getPersistentDataContainer().set(dragonArrowKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isDragonArrow(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(dragonArrowKey, PersistentDataType.BYTE);
    }

    // ── Dragon Arrow Tip ──────────────────────────────────────────────────────

    /**
     * Dragon Arrow Tip — drops from the Ender Dragon (8-16 per kill).
     * Craft 4 tips → 4 Dragon Arrows at any crafting table.
     */
    public ItemStack buildDragonArrowTip(int count) {
        ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS, Math.max(1, count));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5⚡ Dragon Arrow Tip");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7A crystallised dragon scale,",
                "§7forged into an arrow tip.",
                "§8" + "─".repeat(22),
                "§8Drop: §5Ender Dragon §8(8–16 per kill)",
                "§6Craft: §74× Tips §8→ §54× Dragon Arrows",
                "§8[DifficultyEngine — Dragon Arrow Tip]"
            ));
            meta.getPersistentDataContainer().set(dragonArrowTipKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isDragonArrowTip(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(dragonArrowTipKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getDragonArrowKey()    { return dragonArrowKey; }
    public NamespacedKey getDragonArrowTipKey() { return dragonArrowTipKey; }
    public NamespacedKey getGunZSwordKey()      { return gunZSwordKey; }
    public NamespacedKey getDarkBowKey()        { return darkBowKey; }

    // ── Earth Magic Pages ─────────────────────────────────────────────────────

    /**
     * Builds an Earth Magic Page item for the given block tier.
     * Players must carry this page in their inventory to throw the corresponding block.
     * The page is permanent — it is NOT consumed on use.
     */
    public ItemStack buildEarthMagicPage(EarthBlockTier tier) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§2✦ Earth Page: " + tier.displayName);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.setLore(List.of(
                "§8" + "─".repeat(26),
                "§7Earth Magic Page §8— §aTier " + tier.levelRequired,
                "§8Requires: §aMagic Lv §a" + tier.levelRequired,
                "§8" + "─".repeat(26),
                "§7Carry this to throw " + tier.displayName + "§7.",
                "§7Trap damage:       §c" + (int)(tier.trapDamage / 2) + " ❤",
                "§7Suffocate damage:  §c" + (int)(tier.suffocateDamage / 2) + " ❤",
                "§8" + "─".repeat(26),
                "§8• Keep in inventory — not consumed.",
                "§8• Page in a chest = won't activate.",
                "§8[DifficultyEngine — Earth Magic Page]"
            ));
            meta.getPersistentDataContainer().set(earthPageKeys.get(tier), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Returns true if the player has the Earth Magic Page for the given tier
     * anywhere in their inventory (not a chest — must be carried).
     */
    public boolean hasEarthPage(Player player, EarthBlockTier tier) {
        NamespacedKey key = earthPageKeys.get(tier);
        if (key == null) return false;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.hasItemMeta()
                    && s.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the NamespacedKey for an earth page tier. */
    public NamespacedKey getEarthPageKey(EarthBlockTier tier) {
        return earthPageKeys.get(tier);
    }

    // ── Cape delegation ───────────────────────────────────────────────────────

    public SkillType getCapeSkill(ItemStack item)  { return capeManager.getCapeSkill(item); }
    public boolean   isAnyCape(ItemStack item)     { return capeManager.isAnyCape(item); }
    public boolean   isMaxCape(ItemStack item)     { return capeManager.isMaxCape(item); }

    // ── Registry access ───────────────────────────────────────────────────────

    public List<ItemStack> getPage1() {
        List<ItemStack> out = new ArrayList<>(registryPage1.size());
        for (ItemStack i : registryPage1) out.add(i.clone());
        return out;
    }

    public List<ItemStack> getPage2() {
        List<ItemStack> out = new ArrayList<>(registryPage2.size());
        for (ItemStack i : registryPage2) out.add(i.clone());
        return out;
    }

    @Deprecated
    public List<ItemStack> getAll() {
        List<ItemStack> all = new ArrayList<>();
        all.addAll(getPage1());
        all.addAll(getPage2());
        return all;
    }

    // ── Key accessors ─────────────────────────────────────────────────────────

    public NamespacedKey getSoulfurPotionKey()        { return soulfurPotionKey; }
    public NamespacedKey getTurboMinecartKey()         { return turboMinecartKey; }
    public NamespacedKey getEnchantedShardKey()        { return enchantedShardKey; }
    public NamespacedKey getStaffKey(MagicElement el)  { return staffKeys.get(el); }
    public NamespacedKey getRuneKey(MagicElement el)   { return runeKeys.get(el); }
    public NamespacedKey getSpellComboBookKey()        { return spellComboBookKey; }
    public NamespacedKey getAncientKillTomeKey()       { return ancientKillTomeKey; }
    public NamespacedKey getUnicornSlippersKey()       { return unicornSlippersKey; }
    public NamespacedKey getMageGearKey()              { return mageGearKey; }
}
