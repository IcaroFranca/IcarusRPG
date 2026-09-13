package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/** Wires clicks on {@link PassiveAbilityMenuService}'s screen - every slot is a plain button (toggle or back), same shape as {@code GrindstoneMenuListener}/{@code LevelColorMenuService}'s own click handling. */
public final class PassiveAbilityListener implements Listener {
    private final PassiveAbilityMenuService menu;

    public PassiveAbilityListener(PassiveAbilityMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        e.setCancelled(true);
        int raw = e.getRawSlot();
        if (raw >= 0 && raw < e.getView().getTopInventory().getSize()) {
            this.menu.handleClick(p, raw);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.menu.close(p);
        }
    }
}
