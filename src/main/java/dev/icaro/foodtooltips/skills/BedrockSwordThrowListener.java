package dev.icaro.foodtooltips.skills;

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
 * Double-crouch (two sneak presses within {@value #DOUBLE_TAP_WINDOW_MILLIS}ms of each
 * other) throws the sword for a Bedrock/Geyser player instead of Swap Hands (F).
 *
 * <p>This used to be sneak + right-click ({@code PlayerInteractEvent}), which turned out not
 * reliable either: Geyser doesn't always translate a Bedrock right-click into that event
 * when nothing is within normal interact reach (GeyserMC/Geyser#2346) - exactly what happens
 * when the intended target is further away than melee range, the whole point of a thrown
 * weapon. A crouch toggle ({@code PlayerToggleSneakEvent}) has no such caveat - it's a plain
 * state change, not an attempt to interact with whatever's in front of the player - and
 * every platform, console controllers included, has a crouch button, unlike Swap Hands.
 * Requiring a double-tap (not a single crouch) keeps sneaking normally while holding the
 * sword - to sneak up on a mob, say - from launching it by accident. {@code
 * item.BedrockSpruceAxeThrowListener}/{@code item.BedrockTreecapitatorThrowListener} use the
 * exact same fix for their own throw abilities.
 */
public final class BedrockSwordThrowListener implements Listener {
    private static final long DOUBLE_TAP_WINDOW_MILLIS = 400L;

    private final SwordThrowListener throwsService;
    private final Map<UUID, Long> lastSneakStart = new HashMap<>();

    public BedrockSwordThrowListener(SwordThrowListener service) {
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

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.lastSneakStart.remove(e.getPlayer().getUniqueId());
    }
}
