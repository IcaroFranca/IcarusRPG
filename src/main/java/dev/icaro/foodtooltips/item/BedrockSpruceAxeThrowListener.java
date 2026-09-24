package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.BedrockPlayers;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Sneak + right-click throws the Spruce Axe for a Bedrock/Geyser player instead of Swap
 * Hands (F) - same fix, same reasoning, as {@code skills.BedrockSwordThrowListener}: a
 * Bedrock client doesn't reliably send - and on a console controller (a PS4, say, with no
 * dedicated "swap hands" input at all) simply can't send - the F-key gesture {@link
 * SpruceAxeListener#throwAxe} listens for.
 */
public final class BedrockSpruceAxeThrowListener implements Listener {
    private final SpruceAxeListener throwsService;

    public BedrockSpruceAxeThrowListener(SpruceAxeListener service) {
        this.throwsService = service;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !p.isSneaking()
                || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)
                || !BedrockPlayers.isBedrock(p)) {
            return;
        }
        if (this.throwsService.attemptThrow(p)) {
            e.setCancelled(true);
        }
    }
}
