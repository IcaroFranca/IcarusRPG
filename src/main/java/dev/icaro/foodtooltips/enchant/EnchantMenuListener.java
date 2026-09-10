package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * Right-clicking a real Enchanting Table block opens {@link EnchantMenuService}'s own
 * screen instead of vanilla's random-offer UI (the interact event is cancelled either
 * way, so vanilla's never gets a chance to open). Click routing per screen: the item
 * slot on the main screen accepts normal placement/pickup (there's something real to
 * enchant), catalog/level icons and navigation buttons are plain buttons, and the
 * player's own inventory stays fully usable throughout.
 */
public final class EnchantMenuListener implements Listener {
    private final EnchantMenuService menu;

    public EnchantMenuListener(EnchantMenuService menu) {
        this.menu = menu;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND
                || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENCHANTING_TABLE) {
            return;
        }
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);
        this.menu.open(e.getPlayer(), e.getClickedBlock().getLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.menu.viewing(p)) {
            return;
        }
        EnchantMenuService.View view = this.menu.view(p);
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (!topInventory) {
            // Player's own inventory - always free to reorganize, except shift-click
            // (not supported here - could otherwise land an item on a decorative slot).
            if (e.isShiftClick()) {
                e.setCancelled(true);
            }
            return;
        }
        if (view.type() == EnchantMenuService.Type.MAIN && raw == EnchantMenuService.ITEM_SLOT) {
            // Not cancelled - real placement/pickup, this is the item being enchanted.
            return;
        }
        e.setCancelled(true);
        if (this.menu.handleNav(p, raw)) {
            return;
        }
        switch (view.type()) {
            case MAIN -> {
                EnchantEntry enchant = this.menu.catalogEnchantAt(view.page(), raw);
                if (enchant == null) {
                    return;
                }
                ItemStack item = e.getInventory().getItem(EnchantMenuService.ITEM_SLOT);
                if (item == null || item.isEmpty()) {
                    p.sendMessage(Component.text(
                            Language.of(p).choose("Coloque um item para encantar primeiro.", "Place an item to enchant first."),
                            NamedTextColor.RED));
                    return;
                }
                e.getInventory().setItem(EnchantMenuService.ITEM_SLOT, null);
                this.menu.chooseEnchant(p, item, enchant);
            }
            case LEVEL -> {
                int level = this.menu.levelAt(raw);
                if (level > 0 && view.enchant() != null) {
                    this.menu.applyLevel(p, view.enchant(), level);
                }
            }
            case GUIDE -> {
                // Only navigation slots do anything here, already handled above.
            }
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
        EnchantMenuService.View view = this.menu.view(p);
        ItemStack toReturn = view.type() == EnchantMenuService.Type.MAIN
                ? e.getInventory().getItem(EnchantMenuService.ITEM_SLOT)
                : this.menu.takePendingItem(p);
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
