package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Blocks touching a worn piece that's backing an active Wardrobe column - straight from the
 * player's own inventory/character screen, not the Wardrobe board itself (that's {@code
 * WardrobeListener}'s job, and only while the board is actually open). Without this, a player
 * could take the worn clone off through their own inventory (drag it out, shift-click it away,
 * swap it via a hotbar number key...) the instant {@link WardrobeService#select} equips it,
 * then also pull the identical master copy out of that column's own storage - a free
 * duplicate, exactly what {@link WardrobeService#isLockedActiveSlot} exists to prevent from
 * the storage side. This is the body-side half of that same guard, per the player's own
 * explicit "não deve permitir eu tirar a armadura do meu corpo quando ela tá selecionada"
 * spec: the only sanctioned way to take an active set off is {@link WardrobeService#select}
 * itself (clicking that same column's selector again), never a raw inventory edit.
 *
 * <p>Runs independently of whether the Wardrobe screen is open at all - the player's own
 * inventory is a completely different {@code InventoryClickEvent}/{@code InventoryDragEvent}
 * stream from the Wardrobe board's own virtual inventory.
 */
public final class WardrobeArmorLockListener implements Listener {
    /** {@code PlayerInventory}'s own fixed armor-slot indices (boots, leggings, chestplate, helmet) - the same well-known Bukkit convention plenty of other plugins rely on, since there's no direct raw-slot-to-armor-piece API. Only needed for {@link #drag}: {@link InventoryClickEvent} has its own reliable {@code SlotType.ARMOR} for the same purpose. */
    private static final int ARMOR_SLOT_FIRST = 36;
    private static final int ARMOR_SLOT_LAST = 39;

    private final WardrobeService wardrobe;

    public WardrobeArmorLockListener(WardrobeService wardrobe) {
        this.wardrobe = wardrobe;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || e.getSlotType() != InventoryType.SlotType.ARMOR) {
            return;
        }
        if (this.wardrobe.isActiveWornPiece(p, e.getCurrentItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        PlayerInventory pinv = p.getInventory();
        for (int raw : e.getRawSlots()) {
            if (raw < ARMOR_SLOT_FIRST || raw > ARMOR_SLOT_LAST) {
                continue;
            }
            ItemStack current = pinv.getItem(raw);
            if (this.wardrobe.isActiveWornPiece(p, current)) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
