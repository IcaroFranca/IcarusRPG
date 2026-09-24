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
 * Double-crouch (two sneak presses within {@value #DOUBLE_TAP_WINDOW_MILLIS}ms of each
 * other) throws the Spruce Axe for a Bedrock/Geyser player instead of Swap Hands (F).
 *
 * <p>Sneak + right-click ({@code PlayerInteractEvent}) was tried first and didn't work:
 * Geyser doesn't reliably translate a Bedrock right-click into that event when nothing is
 * within normal interact reach (a known Geyser limitation, GeyserMC/Geyser#2346) - and
 * "nothing within reach" is exactly the normal case for this ability, meant to hit a tree
 * well beyond melee range. A crouch toggle ({@code PlayerToggleSneakEvent}) has no such
 * caveat - it's a plain state change, not an attempt to interact with whatever's in front
 * of the player - and every platform, console controllers included, has a crouch button,
 * unlike Swap Hands. Requiring a double-tap (not a single crouch) keeps holding the axe
 * while sneaking normally - to walk along a ledge without falling, say - from launching it
 * by accident.
 */
public final class BedrockSpruceAxeThrowListener implements Listener {
    private static final long DOUBLE_TAP_WINDOW_MILLIS = 400L;

    private final SpruceAxeListener throwsService;
    private final Map<UUID, Long> lastSneakStart = new HashMap<>();

    public BedrockSpruceAxeThrowListener(SpruceAxeListener service) {
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
