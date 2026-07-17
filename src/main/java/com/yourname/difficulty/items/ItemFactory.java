package com.yourname.difficulty.items;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemFactory — Central registry for all DifficultyEngine custom items.
 *
 * Identity is determined exclusively through PersistentDataContainer tags.
 * Display names are cosmetic only and never used for logic checks.
 */
public class ItemFactory {

    // ── PDC key strings ───────────────────────────────────────────────────────
    public static final String SOULFUR_POTION_KEY  = "soulfur_potion";
    public static final String TURBO_MINECART_KEY  = "turbo_minecart";

    // ── NamespacedKeys ────────────────────────────────────────────────────────
    private final NamespacedKey soulfurPotionKey;
    private final NamespacedKey turboMinecartKey;

    // ── Internal registry ─────────────────────────────────────────────────────
    private final List<ItemStack> registry = new ArrayList<>();

    public ItemFactory(JavaPlugin plugin) {
        this.soulfurPotionKey = new NamespacedKey(plugin, SOULFUR_POTION_KEY);
        this.turboMinecartKey = new NamespacedKey(plugin, TURBO_MINECART_KEY);
        register();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    private void register() {
        registry.add(buildSoulfurPotion());
        registry.add(buildTurboMinecart());
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

    // ── Registry access ───────────────────────────────────────────────────────

    public List<ItemStack> getAll() {
        List<ItemStack> copies = new ArrayList<>(registry.size());
        for (ItemStack item : registry) copies.add(item.clone());
        return copies;
    }

    public NamespacedKey getSoulfurPotionKey() { return soulfurPotionKey; }
    public NamespacedKey getTurboMinecartKey()  { return turboMinecartKey; }
}
