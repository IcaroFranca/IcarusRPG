package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BedrockSwordThrowListener
implements Listener {
    private final SwordThrowListener throwsService;

    public BedrockSwordThrowListener(SwordThrowListener service) {
        this.throwsService = service;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !p.isSneaking() || e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK || !BedrockPlayers.isBedrock(p)) {
            return;
        }
        if (this.throwsService.attemptThrow(p)) {
            e.setCancelled(true);
        }
    }
}

