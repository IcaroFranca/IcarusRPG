package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.BedrockPlayers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Double-crouch throws the Treecapitator for a Bedrock/Geyser player instead of Swap Hands
 * (F) - same fix, same reasoning, as {@code BedrockSpruceAxeThrowListener}.
 */
public final class BedrockTreecapitatorThrowListener implements Listener {
    private static final long DOUBLE_TAP_WINDOW_MILLIS = 400L;

    private final TreecapitatorListener throwsService;
    private final Map<UUID, Long> lastSneakStart = new HashMap<>();

    public BedrockTreecapitatorThrowListener(TreecapitatorListener service) {
        this.throwsService = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) {
            return;
        }
        Player p = e.getPlayer();
        if (!BedrockPlayers.isBedrock(p)) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = this.lastSneakStart.getOrDefault(p.getUniqueId(), 0L);
        this.lastSneakStart.put(p.getUniqueId(), now);
        if (now - last > DOUBLE_TAP_WINDOW_MILLIS) {
            return;
        }
        this.lastSneakStart.remove(p.getUniqueId());
        this.throwsService.attemptThrow(p);
    }

    /** Same reason as every other per-player map in this plugin - see {@code skills.SwordThrowListener#quit}. */
    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.lastSneakStart.remove(e.getPlayer().getUniqueId());
    }
}
