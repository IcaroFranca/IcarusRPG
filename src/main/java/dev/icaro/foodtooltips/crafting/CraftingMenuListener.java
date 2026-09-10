package dev.icaro.foodtooltips.crafting;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.Plugin;

/**
 * Wires clicks/drags on {@link CraftingMenuService}'s screen: the 3x3 grid slots
 * ({@link CraftingMenuService#MATRIX_SLOTS}) allow normal item placement/pickup like
 * any real inventory (so a recipe's shape can actually be arranged), the output slot
 * is fully custom-handled (see {@link CraftingMenuService#takeOutput}), and
 * everything else (decorative panes, the back button) is a plain button like every
 * other menu in the plugin.
 */
public final class CraftingMenuListener implements Listener {
    private final CraftingMenuService menu;
    private final Plugin plugin;

    public CraftingMenuListener(CraftingMenuService menu, Plugin plugin) {
        this.menu = menu;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            // Player's own inventory - free to reorganize, except shift-clicking an
            // item up into the crafting screen isn't supported yet (see
            // CraftingMenuService's v1 note) - that could otherwise land it in a
            // decorative slot or the output slot unpredictably.
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }
        if (raw == CraftingMenuService.OUTPUT_SLOT) {
            e.setCancelled(true);
            this.menu.takeOutput(p);
            return;
        }
        if (this.isMatrixSlot(raw)) {
            // Not cancelled - let the click's default pickup/place/swap happen, then
            // read the result one tick later, once it's actually landed.
            Bukkit.getScheduler().runTask(this.plugin, () -> this.menu.recompute(p));
            return;
        }
        e.setCancelled(true);
        if (raw == CraftingMenuService.BACK_SLOT) {
            this.menu.back(p);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize && !this.isMatrixSlot(raw)) {
                // Touches a decorative slot or the output slot - not supported yet
                // (see CraftingMenuService's v1 note), so cancel the whole drag
                // rather than let it partially land somewhere it shouldn't.
                e.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> this.menu.recompute(p));
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.menu.close(p);
        }
    }

    private boolean isMatrixSlot(int raw) {
        for (int slot : CraftingMenuService.MATRIX_SLOTS) {
            if (slot == raw) {
                return true;
            }
        }
        return false;
    }
}
