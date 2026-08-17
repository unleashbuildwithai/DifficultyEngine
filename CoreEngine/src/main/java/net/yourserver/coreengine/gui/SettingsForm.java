package net.yourserver.coreengine.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.yourserver.coreengine.CoreEngine;
import net.yourserver.coreengine.settings.PlayerSettingsManager;
import net.yourserver.coreengine.settings.PlayerSettingsManager.Setting;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Native interaction surface for Bedrock (Geyser) players. Instead of forcing a
 * chest inventory, /settings prints a categorized, click-to-toggle list using
 * chat click events, which Bedrock renders natively as tappable text.
 * Every row runs {@code /settings <SettingName>} to flip the toggle.
 */
public final class SettingsForm {

    private SettingsForm() {
    }

    /** Sends the full toggle list to the given player as interactive chat tiles. */
    public static void open(Player player, CoreEngine plugin) {
        PlayerSettingsManager.PlayerSettings ps = plugin.getPlayerSettingsManager().get(player.getUniqueId());

        player.sendMessage(Component.text("══════════ Settings ══════════").color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        player.sendMessage(Component.text("Tap any toggle to flip it. Turned ON = §a●§r, OFF = §8○§r.")
                .color(NamedTextColor.GRAY));

        Set<String> categories = new LinkedHashSet<>();
        for (Setting s : Setting.values()) {
            categories.add(s.category());
        }
        for (String category : categories) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.text("  §e" + category).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
            for (Setting s : Setting.values()) {
                if (!s.category().equals(category)) {
                    continue;
                }
                boolean on = ps.get(s);
                Component cmd = Component.text("/settings " + s.name());
                lines.add(Component.text("   " + (on ? "● " : "○ "))
                        .color(on ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)
                        .append(Component.text(s.label()).color(on ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                        .clickEvent(ClickEvent.runCommand(cmd))
                        .hoverEvent(HoverEvent.showText(
                                Component.text((on ? "Disable" : "Enable") + " " + s.label()
                                        + "\n" + s.category()))));
            }
            player.sendMessage(lines.toArray(new Component[0]));
        }
        player.sendMessage(Component.text("══════════════════════════════").color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
    }
}
