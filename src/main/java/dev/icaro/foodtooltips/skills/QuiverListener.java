package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Wires the Quiver screen ({@link QuiverService}) - a click/drag that touches only
 * the storage rows (or the player's own inventory) just schedules the same uniform
 * arrow-only filter sweep (see {@link QuiverService#scheduleFilterSweep}'s own doc)
 * rather than trying to pre-judge whether it will land something illegal; the
 * decorative back-button row is the one place that does need pre-judging, since
 * nothing should ever be pulled out of or dropped into it.
 */
public final class QuiverListener implements Listener {
    private final QuiverService quiver;

    public QuiverListener(QuiverService quiver) {
        this.quiver = quiver;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.quiver.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (topInventory && QuiverService.isBackSlot(raw)) {
            e.setCancelled(true);
            this.quiver.back(p);
            return;
        }
        if (topInventory && !QuiverService.isStorageSlot(raw)) {
            // Decorative filler in the back-button row - never a real slot.
            e.setCancelled(true);
            return;
        }
        this.quiver.scheduleFilterSweep(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.quiver.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesDecorativeRow = e.getRawSlots().stream().anyMatch(slot -> slot < topSize && !QuiverService.isStorageSlot(slot));
        if (touchesDecorativeRow) {
            e.setCancelled(true);
            return;
        }
        this.quiver.scheduleFilterSweep(p);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.quiver.close(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.quiver.handleQuit(e.getPlayer());
    }
}
