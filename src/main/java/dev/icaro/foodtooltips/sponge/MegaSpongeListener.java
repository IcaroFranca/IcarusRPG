package dev.icaro.foodtooltips.sponge;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Fires the Mega Sponge's own effect ({@link MegaSpongeService#drain}) on right-click, air or block alike - the raytrace inside {@link MegaSpongeService#drain} finds the target water itself, so this listener doesn't need a clicked block at all. */
public final class MegaSpongeListener implements Listener {
    private final MegaSpongeService sponge;

    public MegaSpongeListener(MegaSpongeService sponge) {
        this.sponge = sponge;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.sponge.isSponge(e.getItem())) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        this.sponge.drain(p);
    }
}
