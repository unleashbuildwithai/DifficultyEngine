package com.yourname.difficulty.items;

import com.yourname.difficulty.magic.MagicElement;
import com.yourname.difficulty.skills.SkillCapeManager;
import com.yourname.difficulty.skills.SkillType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
 * Identity is determined exclusively through PersistentDataContainer tags.
 * Display names are cosmetic only and never used for logic checks.
 *
 * Registry order (left-to-right in /registry):
 *   1.  Soulfur Potion
 *   2.  Turbo Minecart
 *   3.  Enchanted Shard  (rare mob drop; required to craft staffs)
 *   4–7.  Elemental Staffs (Fire, Water, Earth, Air)
 *   8–11. Elemental Runes  (Fire, Water, Earth, Air)
 *  12–19. Skill Capes (Melee, Ranged, Defence, Prayer, Magic, WC, Fish, Farm)
 *  20.   Max Cape
 */
public class ItemFactory {

    // ── PDC key constants ─────────────────────────────────────────────────────
    public static final String SOULFUR_POTION_KEY  = "soulfur_potion";
    public static final String TURBO_MINECART_KEY  = "turbo_minecart";
    public static final String ENCHANTED_SHARD_KEY = "enchanted_shard";

    // ── NamespacedKeys ────────────────────────────────────────────────────────
    private final NamespacedKey soulfurPotionKey;
    private final NamespacedKey turboMinecartKey;
    private final NamespacedKey enchantedShardKey;
    private final Map<MagicElement, NamespacedKey> staffKeys = new EnumMap<>(MagicElement.class);
    private final Map<MagicElement, NamespacedKey> runeKeys  = new EnumMap<>(MagicElement.class);

    // ── Cape manager reference ────────────────────────────────────────────────
    private final SkillCapeManager capeManager;

    // ── Internal registry ─────────────────────────────────────────────────────
    private final List<ItemStack> registry = new ArrayList<>();

    public ItemFactory(JavaPlugin plugin, SkillCapeManager capeManager) {
        this.soulfurPotionKey  = new NamespacedKey(plugin, SOULFUR_POTION_KEY);
        this.turboMinecartKey  = new NamespacedKey(plugin, TURBO_MINECART_KEY);
        this.enchantedShardKey = new NamespacedKey(plugin, ENCHANTED_SHARD_KEY);
        for (MagicElement el : MagicElement.values()) {
            staffKeys.put(el, new NamespacedKey(plugin, el.staffKey));
            runeKeys.put(el,  new NamespacedKey(plugin, el.runeKey));
        }
        this.capeManager = capeManager;
        register();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void register() {
        registry.add(buildSoulfurPotion());
        registry.add(buildTurboMinecart());
        registry.add(buildEnchantedShard());
        for (MagicElement el : MagicElement.values()) registry.add(buildStaff(el));
        for (MagicElement el : MagicElement.values()) registry.add(buildRune(el, 8));
        registry.addAll(capeManager.buildAllCapes());
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
                    "§7Consuming it will drive you to madness.",
                    "§8▶ Causes §5Nausea §8and §5Drunken Sway§8.",
                    "§8▶ Repeated sips darken your vision — §4drink too much and die§8.",
                    "§8▶ Cleansed by §bwater §8or §esleep§8.",
                    "",
                    "§8[DifficultyEngine — Soulfur Potion]"
            ));
            meta.getPersistentDataContainer()
                .set(soulfurPotionKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isSoulfurPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(soulfurPotionKey, PersistentDataType.BYTE);
    }

    // ── Turbo Minecart ────────────────────────────────────────────────────────

    public ItemStack buildTurboMinecart() {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6⚡ Turbo Minecart");
            meta.setLore(List.of(
                    "§7A magnetically-locked high-speed rail cart.",
                    "§7Place on any rail to deploy.",
                    "§8▶ §63× faster §8than a normal minecart.",
                    "§8▶ Magnetically §6locks to rails §8— never derails.",
                    "",
                    "§8[DifficultyEngine — Turbo Minecart]",
                    "§8Requires: §cdifficultyengine.turbocart"
            ));
            meta.getPersistentDataContainer()
                .set(turboMinecartKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isTurboMinecart(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(turboMinecartKey, PersistentDataType.BYTE);
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
                "§7A crystallised fragment of pure magic,",
                "§7extracted from fallen monsters.",
                "§8" + "─".repeat(26),
                "§6Rare drop from any hostile mob.",
                "§7Chance: §e~5% per kill",
                "§8" + "─".repeat(26),
                "§7Used to craft §delemenetal staffs§7:",
                "§8  Enchanted Shard + Element + Stick",
                "§8" + "─".repeat(26),
                "§8[DifficultyEngine — Enchanted Shard]"
            ));
            meta.getPersistentDataContainer()
                .set(enchantedShardKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isEnchantedShard(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(enchantedShardKey, PersistentDataType.BYTE);
    }

    // ── Elemental Staffs ──────────────────────────────────────────────────────

    public ItemStack buildStaff(MagicElement element) {
        ItemStack item = new ItemStack(element.staffMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(element.staffName);
            meta.setCustomModelData(element.staffCMD);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(buildStaffLore(element));
            meta.getPersistentDataContainer()
                .set(staffKeys.get(element), PersistentDataType.BYTE, (byte) 1);
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
            case EARTH -> "§7Right-click → §2throws a dirt ball§7.";
            case AIR   -> "§7Right-click → §7air gust knocks enemies back§7.";
        });
        lore.add(switch (element) {
            case FIRE  -> "§8  Hits set target on §cfire for 2s§8.";
            case WATER -> "§8  Aimed at entity (≤7 blocks): 1 heart damage.";
            case EARTH -> "§8  Hits enemy within 7 blocks: 1 heart damage.";
            case AIR   -> "§8  Sends enemy 10 blocks away + 0.5 hearts.";
        });
        lore.add("§8" + "─".repeat(26));
        lore.add("§7Consumes §e1× §7" + element.runeName + " §7per cast.");
        lore.add("§7Cooldown: §e3 seconds");
        lore.add("§8" + "─".repeat(26));
        lore.add("§6Craft: §fShard §8+ §f" + element.staffCraftIngredient.name() + " §8+ §fStick");
        lore.add("§8Custom Model: §7" + element.staffCMD);
        lore.add("§8[DifficultyEngine — Magic Staff]");
        return lore;
    }

    /** Returns true if the item is a staff of the given element. */
    public boolean isStaff(ItemStack item, MagicElement element) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(staffKeys.get(element), PersistentDataType.BYTE);
    }

    /** Returns the element of a staff item, or null if not a staff. */
    public MagicElement getStaffElement(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        for (MagicElement el : MagicElement.values()) {
            if (pdc.has(staffKeys.get(el), PersistentDataType.BYTE)) return el;
        }
        return null;
    }

    // ── Elemental Runes ───────────────────────────────────────────────────────

    public ItemStack buildRune(MagicElement element, int amount) {
        ItemStack item = new ItemStack(element.runeMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(element.runeName);
            meta.setCustomModelData(element.runeCMD);
            meta.setLore(List.of(
                "§8" + "─".repeat(22),
                "§7Consumed when casting with the",
                element.color + element.name() + " Staff§7.",
                "§8" + "─".repeat(22),
                "§6Craft: §f4× " + element.runeCraftIngredient.name() + " §8→ §f8× Rune",
                "§8[DifficultyEngine — Magic Rune]"
            ));
            meta.getPersistentDataContainer()
                .set(runeKeys.get(element), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Returns true if the item is a rune of the given element. */
    public boolean isRune(ItemStack item, MagicElement element) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                   .has(runeKeys.get(element), PersistentDataType.BYTE);
    }

    // ── Cape delegation ───────────────────────────────────────────────────────

    public SkillType getCapeSkill(ItemStack item)    { return capeManager.getCapeSkill(item); }
    public boolean   isAnyCape(ItemStack item)       { return capeManager.isAnyCape(item); }
    public boolean   isMaxCape(ItemStack item)       { return capeManager.isMaxCape(item); }

    // ── Registry access ───────────────────────────────────────────────────────

    public List<ItemStack> getAll() {
        List<ItemStack> copies = new ArrayList<>(registry.size());
        for (ItemStack item : registry) copies.add(item.clone());
        return copies;
    }

    public NamespacedKey getSoulfurPotionKey()  { return soulfurPotionKey; }
    public NamespacedKey getTurboMinecartKey()   { return turboMinecartKey; }
    public NamespacedKey getEnchantedShardKey()  { return enchantedShardKey; }
    public NamespacedKey getStaffKey(MagicElement el) { return staffKeys.get(el); }
    public NamespacedKey getRuneKey(MagicElement el)  { return runeKeys.get(el); }
}
