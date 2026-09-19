package dev.icaro.foodtooltips.reforge;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

    @EventHandler
    public void leftClick(NPCLeftClickEvent event) {
        if (this.isBlacksmith(event.getNPC())) {
            event.setCancelled(true);
            this.menu.open(event.getClicker());
        }
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
