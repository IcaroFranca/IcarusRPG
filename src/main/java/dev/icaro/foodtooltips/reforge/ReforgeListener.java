package dev.icaro.foodtooltips.reforge;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Opens the reforge menu from the Blacksmith and protects its virtual inventory. */
public final class ReforgeListener implements Listener {
    private static final String BLACKSMITH_NAME = "Blacksmith";
    private final ReforgeMenuService menu;

    public ReforgeListener(ReforgeMenuService menu) {
        this.menu = menu;
    }

    @EventHandler
    public void rightClick(NPCRightClickEvent event) {
        if (this.isBlacksmith(event.getNPC())) {
            event.setCancelled(true);
            this.menu.open(event.getClicker());
        }
    }

    /**
     * Left-clicking (attacking) the Blacksmith should also open the reforge menu, but
     * Citizens' own {@code NPCLeftClickEvent} only fires when the NPC's "protected" trait
     * is on - it derives the event from whether Citizens itself ends up cancelling the
     * underlying damage, so an unprotected NPC never raises it. Hooking the raw damage
     * event ourselves instead works regardless of that toggle. Deliberately NOT {@code
     * ignoreCancelled} - Citizens' own listener runs at LOWEST and cancels this same event
     * the moment it sees a protected NPC (that's how it stops the NPC taking damage), so
     * an ignoreCancelled handler here would never even run for the default, protected case.
     */
    @EventHandler
    public void leftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        NPC npc = CitizensAPI.getNPCRegistry().getNPC(event.getEntity());
        if (npc == null || !this.isBlacksmith(npc)) {
            return;
        }
        event.setCancelled(true);
        this.menu.open(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void inventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !this.menu.viewing(player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot >= topSize) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }
        if (rawSlot == ReforgeMenuService.ITEM_SLOT) {
            return;
        }

        event.setCancelled(true);
        if (rawSlot == ReforgeMenuService.REFORGE_SLOT) {
            this.menu.reforge(player);
        } else if (rawSlot == ReforgeMenuService.CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void inventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !this.menu.viewing(player)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && rawSlot != ReforgeMenuService.ITEM_SLOT) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void inventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !this.menu.viewing(player)) {
            return;
        }
        this.menu.returnItem(player, event.getInventory());
        this.menu.close(player);
    }

    private boolean isBlacksmith(NPC npc) {
        String name = ChatColor.stripColor(npc.getRawName());
        return name != null && name.equalsIgnoreCase(BLACKSMITH_NAME);
    }
}
