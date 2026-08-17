package net.yourserver.coreengine.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Platform detection so menus can route between custom inventory UIs (Java) and
 * native interactions (Bedrock via Geyser/Floodgate). Uses reflection so
 * CoreEngine never hard-depends on Floodgate/Geyser and still runs if they are
 * absent.
 */
public final class PlatformUtil {

    public enum Platform {
        JAVA,
        BEDROCK
    }

    private PlatformUtil() {
    }

    /**
     * Returns the player's platform. Only reaches out to Floodgate when it is
     * installed; otherwise returns JAVA.
     */
    public static Platform platformOf(Player player) {
        return isBedrock(player) ? Platform.BEDROCK : Platform.JAVA;
    }

    public static boolean isBedrock(Player player) {
        return isFloodgatePlayer(player.getUniqueId());
    }

    private static boolean isFloodgatePlayer(UUID uuid) {
        try {
            // org.geysermc.floodgate.api.FloodgateApi#getInstance().isFloodgatePlayer(UUID)
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            if (api == null) {
                return false;
            }
            Method isPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = isPlayer.invoke(api, uuid);
            return Boolean.TRUE.equals(result);
        } catch (Exception | LinkageError e) {
            // Floodgate absent or API changed -> treat as Java.
            return false;
        }
    }
}
