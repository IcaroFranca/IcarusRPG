package dev.icaro.foodtooltips.enchant;

import java.util.Set;
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

/**
 * Right-clicking a real Anvil block (any of the 3 damage-stage materials - they're
 * cosmetically/functionally identical, just how many uses it has left) opens {@link
 * AnvilMenuService}'s own screen instead of vanilla's rename/repair/combine UI (the
 * interact event is cancelled either way, so vanilla's never gets a chance to open) -
 * same "cancel, then open our own" pattern {@code GrindstoneMenuListener}/{@code
 * EnchantMenuListener} already use for their own blocks. The two input slots ({@link
 * AnvilMenuService#MAIN_ITEM_SLOT}/{@link AnvilMenuService#SECONDARY_ITEM_SLOT}) accept
 * real placement/pickup - everything else in the top inventory is a fixed control,
 * always cancelled.
 */
public final class AnvilMenuListener implements Listener {
    private static final Set<Material> ANVILS = Set.of(Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL);

    private final AnvilMenuService menu;

    public AnvilMenuListener(AnvilMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.getClickedBlock() == null || !ANVILS.contains(e.getClickedBlock().getType())) {
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
            // Player's own inventory - always free to reorganize; shift-click could land
            // in one of the (currently empty) input slots, same reasoning as the
            // Grindstone's own listener.
            if (e.isShiftClick()) {
                this.menu.scheduleRefresh(p);
            }
            return;
        }
        boolean inputSlot = raw == AnvilMenuService.MAIN_ITEM_SLOT || raw == AnvilMenuService.SECONDARY_ITEM_SLOT;
        if (inputSlot) {
            // Not cancelled - real placement/pickup, this is one of the two items being combined.
            this.menu.scheduleRefresh(p);
            return;
        }
        e.setCancelled(true);
        if (raw == AnvilMenuService.PREVIEW_SLOT) {
            this.menu.confirm(p);
        } else if (raw == AnvilMenuService.CLOSE_SLOT) {
            p.closeInventory();
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
        this.menu.handleClose(p, e.getInventory());
    }
}
