package dev.icaro.foodtooltips.island;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Right-click with the island access ticket in hand tries to use it (see {@link IslandAccessService#use}). */
public final class IslandAccessListener implements Listener {
    private final IslandAccessService access;

    public IslandAccessListener(IslandAccessService access) {
        this.access = access;
    }

    @EventHandler(ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = e.getItem();
        if (!this.access.isTicket(item)) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        this.access.use(p, item);
    }
}
