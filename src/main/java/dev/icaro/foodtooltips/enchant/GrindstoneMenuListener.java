package dev.icaro.foodtooltips.enchant;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-clicking a real Grindstone block opens {@link GrindstoneMenuService}'s own
 * screen instead of vanilla's repair/disenchant UI (the interact event is cancelled
 * either way, so vanilla's never gets a chance to open) - same "cancel, then open our
 * own" pattern as {@code EnchantMenuListener} uses for the Enchanting Table. Only one
 * screen type here (no Guide/level-select), so this is a good deal simpler: the item
 * slot accepts normal placement/pickup (including shift-click, matching the table's
 * own convention - it's the only slot {@code GrindstoneMenuService#fill} leaves
 * genuinely empty), catalog entries route to {@link GrindstoneMenuService#handleEntryClick},
 * and closing the screen hands back whatever item was left in the slot.
 */
public final class GrindstoneMenuListener implements Listener {
    private final GrindstoneMenuService menu;

    public GrindstoneMenuListener(GrindstoneMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.GRINDSTONE) {
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
        this.menu.open(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            // Player's own inventory - always free to reorganize; shift-click moves a
            // held item into the item slot, the only slot #fill leaves genuinely
            // empty, same reasoning as the Enchanting Table's own listener.
            if (e.isShiftClick()) {
                this.menu.scheduleCatalogRefresh(p);
            }
            return;
        }
        if (raw == GrindstoneMenuService.ITEM_SLOT) {
            // Not cancelled - real placement/pickup, this is the item being ground.
            // The catalog depends on what's here, so refresh it once the click's own
            // default pickup/place/swap actually resolves (next tick).
            this.menu.scheduleCatalogRefresh(p);
            return;
        }
        e.setCancelled(true);
        if (this.menu.handleNav(p, raw)) {
            return;
        }
        EnchantEntry entry = this.menu.entryAt(p, raw);
        if (entry != null) {
            this.menu.handleEntryClick(p, entry);
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
        if (!(e.getPlayer() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        ItemStack toReturn = e.getInventory().getItem(GrindstoneMenuService.ITEM_SLOT);
        this.menu.close(p);
        if (toReturn == null || toReturn.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = p.getInventory().addItem(toReturn);
        for (ItemStack over : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), over);
        }
    }
}
