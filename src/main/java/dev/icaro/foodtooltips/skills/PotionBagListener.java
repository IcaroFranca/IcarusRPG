package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Wires the Potion Bag screen ({@link PotionBagService}) - same shape as {@code QuiverListener}, just checking the per-player (growing) storage size instead of a fixed one. */
public final class PotionBagListener implements Listener {
    private final PotionBagService potionBag;

    public PotionBagListener(PotionBagService potionBag) {
        this.potionBag = potionBag;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.potionBag.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (topInventory && this.potionBag.isBackSlot(p, raw)) {
            e.setCancelled(true);
            this.potionBag.backButtonClicked(p);
            return;
        }
        if (topInventory && !this.potionBag.isStorageSlot(p, raw)) {
            e.setCancelled(true);
            return;
        }
        this.potionBag.scheduleFilterSweep(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.potionBag.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesDecorativeRow = e.getRawSlots().stream().anyMatch(slot -> slot < topSize && !this.potionBag.isStorageSlot(p, slot));
        if (touchesDecorativeRow) {
            e.setCancelled(true);
            return;
        }
        this.potionBag.scheduleFilterSweep(p);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.potionBag.close(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.potionBag.handleQuit(e.getPlayer());
    }
}
