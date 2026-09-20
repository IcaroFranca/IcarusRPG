package dev.icaro.foodtooltips.potion;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Wires the Potion Guide/Milestones screens ({@link PotionGuideMenuService}) - both are
 * plain read-only paginated lists (no item slots, no search), so every top-inventory click
 * just routes to pagination/back/close; a click anywhere else in the top inventory (the
 * decorative filler, a catalog icon) is simply cancelled with no effect.
 *
 * <p>{@link #take} is the actual potion-brewing hook: fires for ANY {@link BrewerInventory},
 * whether or not this player currently has either screen open - a brewed potion should be
 * credited the moment it's collected regardless of what menu (if any) they're looking at.
 */
public final class PotionGuideMenuListener implements Listener {
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_SLOT = 48;
    private static final int CLOSE_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    /** A {@link BrewerInventory}'s 3 potion output slots always sit first (0-2), followed by the ingredient (3) and fuel (4) - real vanilla inventory layout, not something this plugin defines. */
    private static final int BREWER_OUTPUT_SLOTS = 3;

    private final PotionGuideMenuService menu;

    public PotionGuideMenuListener(PotionGuideMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (e.getClickedInventory() instanceof BrewerInventory && e.getRawSlot() >= 0 && e.getRawSlot() < BREWER_OUTPUT_SLOTS) {
            this.take(e);
        }
        if (!this.menu.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            return;
        }
        e.setCancelled(true);
        PotionGuideMenuService.View view = this.menu.view(p);
        if (view == null) {
            return;
        }
        if (raw == BACK_SLOT) {
            this.menu.back(p);
        } else if (raw == CLOSE_SLOT) {
            this.menu.close(p);
            p.closeInventory();
        } else if (raw == PREV_PAGE_SLOT && view.page() > 0) {
            this.open(p, view.type(), view.page() - 1);
        } else if (raw == NEXT_PAGE_SLOT) {
            this.open(p, view.type(), view.page() + 1);
        }
    }

    private void open(Player p, PotionGuideMenuService.Type type, int page) {
        if (type == PotionGuideMenuService.Type.GUIDE) {
            this.menu.openGuide(p, page);
        } else {
            this.menu.openMilestones(p, page);
        }
    }

    /** Credits whoever took the item out of the output slot - see {@link PotionGuideMenuService#creditBrew}'s own doc on why this, not {@link org.bukkit.event.inventory.BrewEvent}, is the right hook (that one fires with no player at all). */
    private void take(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        ItemStack taken = e.getCurrentItem();
        if (taken == null || taken.isEmpty()) {
            return;
        }
        this.menu.creditBrew(p, taken);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.menu.close(p);
        }
    }
}
