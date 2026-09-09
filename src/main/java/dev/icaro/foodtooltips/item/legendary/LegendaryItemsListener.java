package dev.icaro.foodtooltips.item.legendary;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Same click/drag/close pattern as {@code SkillsListener} - cancel every click in the menu and delegate by raw slot. */
public final class LegendaryItemsListener implements Listener {
    private final LegendaryItemsMenuService menu;

    public LegendaryItemsListener(LegendaryItemsMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player p && this.menu.viewing(p)) {
            e.setCancelled(true);
            this.menu.handleClick(p, e.getRawSlot());
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
