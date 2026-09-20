package dev.icaro.foodtooltips.skills;

import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Wires the Wardrobe screen ({@link WardrobeService}) - a locked-column cell or the
 * decorative back-button cell never accepts any interaction, a selector-row cell in an
 * unlocked column always triggers {@link WardrobeService#select} instead of letting an item
 * be placed there, and an unlocked armor-row cell behaves like plain chest storage (see
 * {@link WardrobeService}'s own doc on why no item-type filter applies here, unlike {@code
 * QuiverListener}'s arrow-only sweep) - EXCEPT the currently-equipped column's own armor-row
 * cells ({@link WardrobeService#isLockedActiveSlot}), which stay locked against every
 * interaction the whole time that column is worn, so the master copy backing what's on the
 * player's body can never be pulled out for a free duplicate.
 */
public final class WardrobeListener implements Listener {
    private final WardrobeService wardrobe;
    private final Consumer<Player> back;

    public WardrobeListener(WardrobeService wardrobe, Consumer<Player> back) {
        this.wardrobe = wardrobe;
        this.back = back;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.wardrobe.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            return;
        }
        if (WardrobeService.isBackSlot(raw)) {
            e.setCancelled(true);
            this.wardrobe.back(p);
            this.back.accept(p);
            return;
        }
        if (WardrobeService.isSelectorSlot(raw) && this.wardrobe.isUsableSlot(p, raw)) {
            e.setCancelled(true);
            this.wardrobe.select(p, raw % 9);
            return;
        }
        if (!this.wardrobe.isUsableSlot(p, raw) || this.wardrobe.isLockedActiveSlot(p, raw)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.wardrobe.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesRestrictedSlot = e.getRawSlots().stream().anyMatch(slot ->
                slot < topSize && (!this.wardrobe.isUsableSlot(p, slot) || WardrobeService.isSelectorSlot(slot) || this.wardrobe.isLockedActiveSlot(p, slot)));
        if (touchesRestrictedSlot) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.wardrobe.close(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.wardrobe.handleQuit(e.getPlayer());
    }
}
