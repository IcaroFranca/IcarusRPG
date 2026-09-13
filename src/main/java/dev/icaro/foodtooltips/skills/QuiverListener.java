package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Wires the Quiver screen ({@link QuiverService}) - every click and drag just
 * schedules the same uniform arrow-only filter sweep (see {@link
 * QuiverService#scheduleFilterSweep}'s own doc) rather than trying to pre-judge
 * whether a given click/drag will land something illegal, so nothing here needs to
 * be cancelled up front.
 */
public final class QuiverListener implements Listener {
    private final QuiverService quiver;

    public QuiverListener(QuiverService quiver) {
        this.quiver = quiver;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.quiver.viewing(p)) {
            this.quiver.scheduleFilterSweep(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.quiver.viewing(p)) {
            this.quiver.scheduleFilterSweep(p);
        }
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
