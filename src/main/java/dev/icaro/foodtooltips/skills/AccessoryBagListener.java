package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Wires {@link AccessoryBagService} into the world: the equip screen's own click/drag/close
 * handling (same "resolve first, sweep after" shape {@code PotionBagListener} already uses -
 * see {@link AccessoryBagService#scheduleFilterSweep}'s own doc), and the actual fall-damage
 * reduction every equipped accessory grants (see {@link #fall}).
 */
public final class AccessoryBagListener implements Listener {
    private final AccessoryBagService bag;

    public AccessoryBagListener(AccessoryBagService bag) {
        this.bag = bag;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.bag.viewing(p)) {
            return;
        }
        int raw = e.getRawSlot();
        boolean topInventory = raw >= 0 && raw < e.getView().getTopInventory().getSize();
        if (topInventory && this.bag.isCloseSlot(raw)) {
            e.setCancelled(true);
            this.bag.backButtonClicked(p);
            return;
        }
        if (topInventory && !this.bag.isStorageSlot(raw)) {
            e.setCancelled(true);
            return;
        }
        this.bag.scheduleFilterSweep(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !this.bag.viewing(p)) {
            return;
        }
        int topSize = e.getView().getTopInventory().getSize();
        boolean touchesLockedOrDecorative = e.getRawSlots().stream().anyMatch(slot -> slot < topSize && !this.bag.isStorageSlot(slot));
        if (touchesLockedOrDecorative) {
            e.setCancelled(true);
            return;
        }
        this.bag.scheduleFilterSweep(p);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            this.bag.close(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.bag.handleQuit(e.getPlayer());
    }

    /**
     * Every equipped accessory's own fall-height buffer (flat subtraction) and damage
     * reduction (percentage, applied after) - same two-step shape {@code
     * enchant.ArmorEnchantEffectListener#fall}'s own Feather Falling math already uses
     * ({@code Math.max(0, damage - heightBonus) * (1 - reduction)}), and deliberately
     * {@link EventPriority#HIGHEST} (that listener's own {@link EventPriority#HIGH} already
     * applied the real vanilla fall-damage multiplier and Feather Falling's own reduction by
     * the time this one runs) so an accessory's own buffer/reduction always applies on top
     * of the final number every other fall-damage source already agreed on, never before it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) {
            return;
        }
        int bonusHeight = this.bag.totalFallHeightBonus(p);
        double reductionPercent = this.bag.totalFallDamageReductionPercent(p);
        if (bonusHeight <= 0 && reductionPercent <= 0.0) {
            return;
        }
        double damage = Math.max(0.0, e.getDamage() - bonusHeight) * (1.0 - reductionPercent / 100.0);
        if (damage <= 0.0) {
            e.setCancelled(true);
            return;
        }
        e.setDamage(damage);
    }
}
