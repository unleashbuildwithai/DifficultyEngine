package net.yourserver.coreengine.commands;

import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.gui.SettingsForm;
import net.yourserver.coreengine.settings.PlayerSettingsManager.Setting;
import net.yourserver.coreengine.util.PlatformUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /settings} - DonutSMP-style settings panel. Java players get the
 * paginated toggle GUI; Bedrock (Geyser) players get the native chat tap list.
 * {@code /settings <SettingName>} toggles a single setting (used by the tiles).
 */
public class SettingsCommand implements CommandExecutor {

    private final CoreEngine plugin;

    public SettingsCommand(CoreEngine plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /settings.");
            return true;
        }
        if (args.length == 0) {
            switch (PlatformUtil.platformOf(player)) {
                case BEDROCK -> SettingsForm.open(player, plugin);
                case JAVA -> plugin.getSettingsUI().open(player, 0);
            }
            return true;
        }
        Setting setting = resolve(args[0]);
        if (setting == null) {
            player.sendMessage("§cUnknown setting: §e" + args[0] + "§c. Run /settings to see the list.");
            return true;
        }
        boolean on = plugin.getPlayerSettingsManager().toggleSetting(player.getUniqueId(), setting);
        applyEffect(player, setting, on);
        player.sendMessage((on ? "§a● ENABLED: §f" : "§c○ DISABLED: §f") + setting.label());
        return true;
    }

    private static Setting resolve(String raw) {
        try {
            return Setting.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Applies immediate, safe side effects for toggles that have one. */
    private static void applyEffect(Player player, Setting setting, boolean on) {
        if (setting == Setting.NIGHT_VISION) {
            if (on) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                        Integer.MAX_VALUE, 0, false, false, false));
            } else {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }
        }
    }
}