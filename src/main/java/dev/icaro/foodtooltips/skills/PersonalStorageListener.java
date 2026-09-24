package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Wires the Personal Storage screen ({@link PersonalStorageService}) - same shape as {@code PotionBagListener} (a size-dependent close/back slot right below the currently unlocked rows), just with no item-type filter (any item is allowed here). */
public final class PersonalStorageListener implements Listener {
    private final PersonalStorageService storage;

    public PersonalStorageListener(PersonalStorageService storage) {
        this.storage = storage;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.storage.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (topInventory && this.storage.isCloseSlot(p, raw)) {
            e.setCancelled(true);
            p.closeInventory();
            return;
        }
        if (topInventory && !this.storage.isStorageSlot(p, raw)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.storage.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesLockedOrDecorative = e.getRawSlots().stream().anyMatch(slot -> slot < topSize && !this.storage.isStorageSlot(p, slot));
        if (touchesLockedOrDecorative) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.storage.close(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.storage.handleQuit(e.getPlayer());
    }
}
