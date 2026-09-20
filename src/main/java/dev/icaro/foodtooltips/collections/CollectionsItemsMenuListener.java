package dev.icaro.foodtooltips.collections;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Same click/drag/close pattern as {@code item.legendary.LegendaryItemsListener} - cancel every click in the menu and delegate by raw slot. */
public final class CollectionsItemsMenuListener implements Listener {
    private final CollectionsItemsMenuService menu;

    public CollectionsItemsMenuListener(CollectionsItemsMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player p && this.menu.viewing(p)) {
            e.setCancelled(true);
            this.menu.click(p, e.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player p && this.menu.viewing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player p) {
            this.menu.close(p);
        }
    }
}
