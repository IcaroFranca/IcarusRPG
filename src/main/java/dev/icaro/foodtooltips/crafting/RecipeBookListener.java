package dev.icaro.foodtooltips.crafting;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Wires clicks/drags on {@link RecipeBookMenuService}'s screen - unlike {@code
 * CraftingMenuService}, every slot here is a read-only preview (nothing is ever placed,
 * taken or crafted), so every click in the top inventory is cancelled and just dispatched
 * to {@link RecipeBookMenuService#click}.
 */
public final class RecipeBookListener implements Listener {
    private final RecipeBookMenuService menu;

    public RecipeBookListener(RecipeBookMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }
        e.setCancelled(true);
        this.menu.click(p, raw);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p && this.menu.viewing(p)) {
            this.menu.close(p);
        }
    }
}
