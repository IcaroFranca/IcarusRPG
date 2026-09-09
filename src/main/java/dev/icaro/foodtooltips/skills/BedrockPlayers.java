package dev.icaro.foodtooltips.skills;

import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Detects whether a player is connected via Geyser/Floodgate (Bedrock), for the few
 * spots where a Java-only input gesture doesn't translate reliably through Geyser and
 * needs an alternate trigger - see {@link BedrockSwordThrowListener} (original home of
 * this logic) and {@link SkillsStarListener}.
 *
 * <p>Tries every detection path that doesn't require a hard compile-time dependency on
 * Geyser/Floodgate, since a server may run either, both, or neither jar:
 * 1) the Floodgate-UUID signature - Floodgate derives a player's UUID purely from
 *    their 64-bit Xbox Live XUID, always zeroing the upper 64 bits, so this works
 *    even when Floodgate's own API jar isn't on this server's classpath (e.g. it's
 *    only installed on a proxy) - this alone covers the overwhelmingly common setup;
 * 2) FloodgateApi#isFloodgatePlayer, if the backend Floodgate plugin is installed here;
 * 3) GeyserApi#connectionByUuid, if the backend Geyser-Spigot plugin is installed here
 *    (works even without Floodgate, e.g. offline/cracked Geyser setups).
 */
final class BedrockPlayers {
    private BedrockPlayers() {
    }

    static boolean isBedrock(Player p) {
        UUID id = p.getUniqueId();
        if (id.getMostSignificantBits() == 0L) {
            return true;
        }
        return isFloodgatePlayer(id) || isGeyserConnection(id);
    }

    private static boolean isFloodgatePlayer(UUID id) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, id));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isGeyserConnection(UUID id) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            Method method = apiClass.getMethod("connectionByUuid", UUID.class);
            return method.invoke(api, id) != null;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
