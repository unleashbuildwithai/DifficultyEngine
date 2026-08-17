package net.yourserver.coreengine.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.settings.PlayerSettingsManager.Setting;
import net.yourserver.coreengine.util.PDCKeys;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * DonutSMP-style /settings menu: a flat, paginated list of toggles rendered as
 * clean tiles (no chest "slots" look). Clicking a tile toggles it and re-opens
 * the same page. If there are more toggles than a page fits, next/prev arrows
 * appear. Used for Java players; Bedrock players get a chat-based list routed
 * through {@code SettingsForm}.
 */
public class SettingsUI {

    public static final String GUI_TYPE = "SETTINGS_TOGGLES";
    private static final int PER_PAGE = 14;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final CoreEngine plugin;
    private final PlayerSettingsManager settings;

    public SettingsUI(CoreEngine plugin, PlayerSettingsManager settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public int pageCount() {
        int total = Setting.values().length;
        return (total + PER_PAGE - 1) / PER_PAGE;
    }

    public void open(Player player, int page) {
        Setting[] all = Setting.values();
        int pages = pageCount();
        int index = Math.max(0, Math.min(page, pages - 1));

        Inventory inv = Bukkit.createInventory(
                new CustomGUIHolder(GUI_TYPE), 54,
                LEGACY.deserialize("§8[§aSettings§8] §fPage " + (index + 1) + "/" + pages));

        // Background panes so it reads as a clean panel, not a storage grid.
        ItemStack pane = pane();
        for (int s = 0; s < 54; s++) {
            inv.setItem(s, pane);
        }

        // Header: current setting category focus (aggregate count).
        inv.setItem(4, header(index + 1, pages));

        PlayerSettingsManager.PlayerSettings ps = settings.get(player.getUniqueId());
        int start = index * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < all.length; i++) {
            Setting s = all[start + i];
            inv.setItem(slotFor(i), tile(ps, s));
        }

        // Pagination arrows (only appear when there are more pages).
        if (pages > 1) arrow(inv, 45, index - 1, "§e◀ §7Prev", index > 0);
        if (pages > 1) arrow(inv, 53, index + 1, "§7Next §e▶", index < pages - 1);

        player.openInventory(inv);
    }

    /** Translates a within-page index (0..13) into a 9-wide inventory slot. */
    private static int slotFor(int i) {
        int row = i / 7;
        int col = i % 7;
        return (19 + row * 9) + col;
    }

    private ItemStack header(int page, int pages) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        var meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize("§eSettings"));
        meta.lore(List.of(
                LEGACY.deserialize("§7Click a tile to enable / disable."),
                LEGACY.deserialize("§7Page §f" + page + "§7/§f" + pages)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pane() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack tile(PlayerSettingsManager.PlayerSettings ps, Setting setting) {
        boolean on = ps.get(setting);
        Material mat = on ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize((on ? "§a" : "§7") + setting.label()));
        List<Component> lore = new ArrayList<>();
        lore.add(LEGACY.deserialize("§8" + setting.category()));
        lore.add(LEGACY.deserialize(on ? "§2● ENABLED" : "§c○ DISABLED"));
        lore.add(LEGACY.deserialize("§7Click to toggle"));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(PDCKeys.settingKey(), PersistentDataType.STRING, setting.name());
        item.setItemMeta(meta);
        return item;
    }

    private void arrow(Inventory inv, int slot, int targetPage, String name, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.ARROW : Material.BARRIER);
        var meta = item.getItemMeta();
        meta.displayName(LEGACY.deserialize(name));
        meta.getPersistentDataContainer().set(PDCKeys.settingsPage(), PersistentDataType.INTEGER, targetPage);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }
}
