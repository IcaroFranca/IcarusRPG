package dev.icaro.foodtooltips.trash;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Wires clicks on {@link TrashMenuService}'s screen: the player's own inventory stays
 * fully usable (that's how an item gets picked up to drop into the trash in the first
 * place, or shift-clicked to vanish an entire stack at once without needing to drag it
 * over one at a time - see the shift-click handling below), the trash slot ({@link
 * TrashMenuService#TRASH_SLOT}) allows normal item placement/pickup too (so there's
 * something real to vanish - see {@link TrashMenuService#scheduleTrashEmpty}), and the
 * back button/every decorative pane are plain buttons like every other menu. Dragging
 * is always cancelled - a single slot doesn't need it.
 */
public final class TrashMenuListener implements Listener {
    private final TrashMenuService menu;

    public TrashMenuListener(TrashMenuService menu) {
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
            // Player's own inventory - free to reorganize/pick items up normally
            // (that's how an item gets onto the cursor to drop into the trash slot in
            // the first place). Shift-click vanishes the whole clicked stack directly
            // instead of the default "move it to the other inventory" (which would try
            // to land it on a decorative pane, since there's no real space for it up
            // there) - lets a player empty several stacks at once without dragging each
            // one over individually.
            if (e.isShiftClick() && e.getClickedInventory() != null) {
                e.setCancelled(true);
                e.getClickedInventory().setItem(e.getSlot(), null);
            }
            return;
        }
        if (raw == TrashMenuService.TRASH_SLOT) {
            // Not cancelled - let the click's default pickup/place/swap happen, then
            // vanish whatever landed there one tick later.
            this.menu.scheduleTrashEmpty(p);
            return;
        }
        e.setCancelled(true);
        if (raw == TrashMenuService.BACK_SLOT) {
            this.menu.back(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.menu.viewing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.menu.close(p);
        }
    }
}
