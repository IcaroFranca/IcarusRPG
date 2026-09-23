package dev.icaro.foodtooltips.brewing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrewingStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

/**
 * Wires {@link BrewingMenuService} into the world: intercepts right-clicking a real Brewing
 * Stand (cancelling it so vanilla's own 5-slot window never opens) and drives its custom
 * GUI's click handling - closing on {@link BrewingMenuService#CLOSE_SLOT}, blocking every
 * decorative filler slot, crediting a taken potion (see {@link
 * BrewingMenuService#creditIfTaken}), and pushing every click's result back into the real
 * stand a tick later (see {@link BrewingMenuService#resync}) so the linked slots and the
 * stand they're proxying never drift apart.
 */
public final class BrewingMenuListener implements Listener {
    private final Plugin plugin;
    private final BrewingMenuService menu;

    public BrewingMenuListener(Plugin plugin, BrewingMenuService menu) {
        this.plugin = plugin;
        this.menu = menu;
    }

    /** Real Brewing Stands always just open their inventory on right-click, no other vanilla behavior to preserve, except one: sneaking while holding an item skips opening the container in favor of using/placing that item (e.g. placing a block against the stand's side) - same rule respected here. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = e.getPlayer();
        if (p.isSneaking() && e.getItem() != null && !e.getItem().getType().isAir()) {
            return;
        }
        Block block = e.getClickedBlock();
        if (block == null || block.getType() != Material.BREWING_STAND) {
            return;
        }
        // getState(false) asks Paper for a LIVE view backed directly by the real tile
        // entity, not a frozen-at-this-instant snapshot - BrewingMenuService keeps this
        // exact object for as long as the menu stays open, so its reads (getBrewingTime())
        // and writes (getInventory().set...) must track the real stand in real time.
        if (!(block.getState(false) instanceof BrewingStand stand)) {
            return;
        }
        e.setCancelled(true);
        this.menu.open(p, stand);
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof BrewingMenuService.BrewingMenuHolder)) {
            return;
        }
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topClicked = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (topClicked) {
            if (raw == BrewingMenuService.CLOSE_SLOT) {
                e.setCancelled(true);
                p.closeInventory();
                return;
            }
            if (!BrewingMenuService.isLinkedSlot(raw)) {
                e.setCancelled(true);
                return;
            }
            this.menu.creditIfTaken(p, raw, e.getCurrentItem(), e.getAction());
        }
        // The click's own effect (if not cancelled above) only actually lands in the
        // inventory after this event finishes - scheduling the resync for next tick, not
        // reading it inline here, is what makes it see the settled result.
        Bukkit.getScheduler().runTask(this.plugin, () -> this.menu.resync(p));
    }

    /** No drag support - the 4 linked slots only need plain single-item clicks, and disallowing drag entirely sidesteps any weirdness dragging across the decorative filler slots could cause. */
    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof BrewingMenuService.BrewingMenuHolder) {
            e.setCancelled(true);
        }
    }
}
