package net.yourserver.coreengine.settings;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.BiConsumer;

/**
 * In-memory per-player settings/toggles (DonutSMP-style). Every toggle shown on
 * the /settings menu maps to one {@link Setting}. Transient per session.
 */
public class PlayerSettingsManager {

    /** Who is allowed to teleport to this player. */
    public enum TpPrivacy {
        EVERYONE,
        PARTY,
        NOBODY
    }

    /** Master registry of every toggle shown in /settings. */
    public enum Setting {
        PRIVATE_MESSAGES("Private Messages", "Social", 0, true),
        SERVER_CHAT_MESSAGES("Server Chat Messages", "Social", 1, true),
        SERVER_HOTBAR_MESSAGES("Server Hotbar Messages", "Social", 2, true),
        DEATH_MESSAGES("Death Messages", "Social", 3, true),
        ADVANCEMENT_MESSAGES("Advancement Messages", "Social", 4, true),
        JOIN_LEAVE_MESSAGES("Join/Leave Messages", "Social", 5, true),
        FRIEND_FOLLOW_ALERTS("Friends / Follow Alerts", "Social", 6, true),
        PAY_ALERTS("Pay Alerts", "Alerts", 0, true),
        TELEPORT_ALERTS("Teleport Alerts", "Alerts", 1, true),
        BOUNTY_ALERTS("Bounty Alerts", "Alerts", 2, true),
        ORDER_ALERTS("Order Alerts", "Alerts", 3, true),
        AUCTION_ALERTS("Auction Alerts", "Alerts", 4, true),
        SERVER_SOUNDS("Server Sounds", "Alerts", 5, true),
        FOLLOWER_ALERTS("Follower Alerts", "Alerts", 6, true),
        FAST_CRYSTALS("Fast Crystals", "Gameplay", 0, false),
        TOTEM_PARTICLES("Totem Particles", "Gameplay", 1, true),
        EXPLOSION_PARTICLES("Explosion Particles", "Gameplay", 2, true),
        EXPLOSION_SOUNDS("Explosion Sounds", "Gameplay", 3, true),
        COMBAT_TIMER_DISPLAY("Combat Timer Display", "Gameplay", 4, true),
        MEMBER_PLUS_MONEY("Member+ Money", "Gameplay", 5, true),
        PLAYER_NAMETAGS("Player Nametags", "Gameplay", 6, true),
        ITEM_WORTH_LORE("Item Worth Lore", "Gameplay", 7, true),
        TELEPORT_CONFIRM_MENU("Teleport Confirm Menu", "Teleport", 0, true),
        TP_REQUEST_ANYONE("Teleport Request: Anyone", "Teleport", 1, true),
        TP_HERE_REQUEST_ANYONE("Teleport-Here Request: Anyone", "Teleport", 2, true),
        ALLOW_PAYMENTS_ANYONE("Allow Payments: Anyone", "Teleport", 3, true),
        RANDOMIZED_COORDS("Randomized Coords", "Privacy", 0, false),
        PRIVATE_TRANSACTIONS("Private Transactions", "Privacy", 1, false),
        SHOW_MONEY("Scoreboard: Money", "Scoreboard", 0, true),
        SHOW_SHARDS("Scoreboard: Shards", "Scoreboard", 1, true),
        SHOW_KILLS("Scoreboard: Kills", "Scoreboard", 2, true),
        SHOW_DEATHS("Scoreboard: Deaths", "Scoreboard", 3, true),
        SHOW_PLAYTIME("Scoreboard: Playtime", "Scoreboard", 4, true),
        AUCTION_QUICK_BUY("Auction Quick Buy", "Auction", 0, false),
        AUCTION_QUICK_SELL("Auction Quick Sell", "Auction", 1, false),
        MOB_SPAWNS("Mob Spawns", "World", 0, true),
        PHANTOM_SPAWNS("Phantom Spawns", "World", 1, true),
        NIGHT_VISION("Night Vision", "World", 2, false),
        DESTROY_PEARL_ON_DEATH("Destroy Pearl on Death", "World", 3, false);

        private final String label;
        private final String category;
        private final int order;
        private final boolean defaultValue;

        Setting(String label, String category, int order, boolean defaultValue) {
            this.label = label;
            this.category = category;
            this.order = order;
            this.defaultValue = defaultValue;
        }

        public String label() { return label; }
        public String category() { return category; }
        public int order() { return order; }
        public boolean defaultValue() { return defaultValue; }
    }
    /** Per-player toggle state, keyed by {@link Setting} NAME. */
    public static final class PlayerSettings {
        // Legacy flat fields kept for compatibility with existing callers.
        public boolean ghostMode;
        public boolean nightvision;
        public boolean removeMonsters;
        public boolean tpAuto;
        public boolean hudStats;
        public boolean quickBuy = true;
        public TpPrivacy tpPrivacy = TpPrivacy.EVERYONE;
        /** The full, defaulted toggle set for this player. */
        public final Map<String, Boolean> toggles = new LinkedHashMap<>();

        PlayerSettings() {
            for (Setting s : Setting.values()) {
                toggles.put(s.name(), s.defaultValue());
            }
            // Mirror a few toggles onto the legacy fields for existing consumers.
            nightvision = get(Setting.NIGHT_VISION);
            quickBuy = get(Setting.AUCTION_QUICK_BUY);
            refreshHudFromScoreboard();
        }

        public boolean get(Setting s) {
            Boolean b = toggles.get(s.name());
            return b == null ? s.defaultValue() : b;
        }

        public void set(Setting s, boolean value) {
            toggles.put(s.name(), value);
        }

        public boolean toggle(Setting s) {
            boolean next = !get(s);
            set(s, next);
            switch (s) {
                case NIGHT_VISION -> nightvision = next;
                case AUCTION_QUICK_BUY -> quickBuy = next;
                case SHOW_MONEY, SHOW_SHARDS, SHOW_KILLS, SHOW_DEATHS, SHOW_PLAYTIME -> refreshHudFromScoreboard();
                default -> { }
            }
            return next;
        }

        private void refreshHudFromScoreboard() {
            hudStats = get(Setting.SHOW_MONEY) || get(Setting.SHOW_SHARDS)
                    || get(Setting.SHOW_KILLS) || get(Setting.SHOW_DEATHS)
                    || get(Setting.SHOW_PLAYTIME);
        }
    }

    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();

    public PlayerSettings get(UUID uuid) {
        return settings.computeIfAbsent(uuid, k -> new PlayerSettings());
    }

    public boolean toggle(UUID uuid, Function<PlayerSettings, Boolean> getter,
                          BiConsumer<PlayerSettings, Boolean> setter) {
        PlayerSettings s = get(uuid);
        boolean current = getter.apply(s);
        boolean next = !current;
        setter.accept(s, next);
        return next;
    }

    public boolean toggleSetting(UUID uuid, Setting setting) {
        return get(uuid).toggle(setting);
    }

    public boolean isEnabled(UUID uuid, Setting setting) {
        return get(uuid).get(setting);
    }

    public void setEnabled(UUID uuid, Setting setting, boolean value) {
        get(uuid).set(setting, value);
    }

    public boolean toggleGhost(UUID uuid) {
        return toggle(uuid, s -> s.ghostMode, (s, v) -> s.ghostMode = v);
    }

    public boolean toggleNightvision(UUID uuid) {
        return toggle(uuid, s -> s.nightvision, (s, v) -> s.nightvision = v);
    }

    public boolean toggleRemoveMonsters(UUID uuid) {
        return toggle(uuid, s -> s.removeMonsters, (s, v) -> s.removeMonsters = v);
    }

    public boolean toggleTpAuto(UUID uuid) {
        return toggle(uuid, s -> s.tpAuto, (s, v) -> s.tpAuto = v);
    }

    public boolean toggleHudStats(UUID uuid) {
        return toggle(uuid, s -> s.hudStats, (s, v) -> s.hudStats = v);
    }

    public boolean toggleQuickBuy(UUID uuid) {
        return toggle(uuid, s -> s.quickBuy, (s, v) -> s.quickBuy = v);
    }

    /** Cycles the TP privacy level EVERYONE -> PARTY -> NOBODY -> EVERYONE. */
    public TpPrivacy cycleTpPrivacy(UUID uuid) {
        PlayerSettings s = get(uuid);
        s.tpPrivacy = switch (s.tpPrivacy) {
            case EVERYONE -> TpPrivacy.PARTY;
            case PARTY -> TpPrivacy.NOBODY;
            case NOBODY -> TpPrivacy.EVERYONE;
        };
        return s.tpPrivacy;
    }

    public void remove(UUID uuid) {
        settings.remove(uuid);
    }
}
