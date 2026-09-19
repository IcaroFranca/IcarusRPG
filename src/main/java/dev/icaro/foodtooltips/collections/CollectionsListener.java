package dev.icaro.foodtooltips.collections;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Protects the Collections screen's virtual inventory - same click-cancel-and-forward shape
 * every custom menu in this plugin uses (see {@code bestiary.BestiaryListener}) - and syncs
 * a joining player's real vanilla recipe discovery to their current Collections progress
 * (see {@link CollectionsService#syncDiscoveredRecipes}), so a recipe unlocked in a past
 * session (or one added to the catalog after they'd already qualify) shows up in the
 * crafting table's own recipe book without needing a fresh milestone crossing first.
 */
public final class CollectionsListener implements Listener {
    private final CollectionsMenuService menus;
    private final CollectionsService collections;

    public CollectionsListener(CollectionsMenuService menus, CollectionsService collections) {
        this.menus = menus;
        this.collections = collections;
    }

    @EventHandler
    public void join(PlayerJoinEvent event) {
        this.collections.syncDiscoveredRecipes(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (human instanceof Player player && this.menus.viewing(player)) {
            event.setCancelled(true);
            this.menus.click(player, event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (human instanceof Player player && this.menus.viewing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            this.menus.close(player);
        }
    }
}
