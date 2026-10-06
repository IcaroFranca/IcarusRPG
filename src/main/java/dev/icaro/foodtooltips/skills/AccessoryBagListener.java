package dev.icaro.foodtooltips.skills;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;

/**
 * Wires {@link AccessoryBagService} into the world: the equip screen's own click/drag/close
 * handling (same "resolve first, sweep after" shape {@code PotionBagListener} already uses -
 * see {@link AccessoryBagService#scheduleFilterSweep}'s own doc), the fall-damage (see
 * {@link #fall}, the Feather line) and poison-damage (see {@link #poison}, the Vaccine line)
 * reduction every equipped accessory grants, and the potion-duration extension the Potion
 * Affinity line grants (see {@link #potionEffect}). The Farmer Orb's crop-growth aura and the
 * Night Vision Charm's permanent effect live in {@link AccessoryBagService} itself instead
 * ({@link AccessoryBagService#pulseFarmerOrbs}/{@link AccessoryBagService#refreshStandingEffects}) -
 * both are periodic maintenance, not a reaction to a Bukkit event the way every effect here is.
 */
public final class AccessoryBagListener implements Listener {
    private final AccessoryBagService bag;
    /** Re-entrancy guard for {@link #potionEffect} - see that method's own doc on why. */
    private final Set<UUID> extendingPotionDuration = new HashSet<>();

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
        if (topInventory && this.bag.isPowersSlot(raw)) {
            e.setCancelled(true);
            this.bag.powersButtonClicked(p);
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

    /**
     * Every equipped Vaccine-line accessory's own {@code DamageCause.POISON} reduction,
     * summed and applied as a straight percentage cut - same "sum every stored accessory's
     * own bonus" shape as {@link #fall}, just a single multiplicative step instead of a flat
     * subtraction first (poison damage has no "free buffer" equivalent to fall height).
     * {@link EventPriority#HIGHEST} for the same reason {@link #fall} uses it: apply on top of
     * whatever else already touched this damage instance, never before it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void poison(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.POISON || !(e.getEntity() instanceof Player p)) {
            return;
        }
        double reductionPercent = this.bag.totalPoisonReductionPercent(p);
        if (reductionPercent <= 0.0) {
            return;
        }
        double damage = e.getDamage() * (1.0 - reductionPercent / 100.0);
        if (damage <= 0.0) {
            e.setCancelled(true);
            return;
        }
        e.setDamage(damage);
    }

    /**
     * Extends the duration of any effect gained by actually drinking a potion ({@code
     * Cause.POTION_DRINK}) by every equipped Potion Affinity accessory's own bonus percent,
     * summed. Only {@code Action.ADDED} (a fresh effect, not already affected) - extending an
     * upgrade/refresh ({@code Action.CHANGED}, e.g. drinking a second potion of the same type
     * before the first wears off) is a known v1 simplification, left for later rather than
     * guessing at how "extend an already-extended, already-ticking-down duration" should even
     * compose. Bukkit gives no way to just change a {@link PotionEffect}'s own duration in
     * place, so this cancels the original and re-applies an extended clone instead - which
     * would otherwise fire this exact same handler again for the extended effect, so {@link
     * #extendingPotionDuration} guards against re-extending what's already been extended once.
     */
    @EventHandler(ignoreCancelled = true)
    public void potionEffect(EntityPotionEffectEvent e) {
        if (e.getCause() != EntityPotionEffectEvent.Cause.POTION_DRINK
                || e.getAction() != EntityPotionEffectEvent.Action.ADDED
                || !(e.getEntity() instanceof Player p)
                || this.extendingPotionDuration.contains(p.getUniqueId())) {
            return;
        }
        double bonusPercent = this.bag.totalPotionDurationBonusPercent(p);
        PotionEffect original = e.getNewEffect();
        if (bonusPercent <= 0.0 || original == null) {
            return;
        }
        int extendedDuration = (int) Math.round(original.getDuration() * (1.0 + bonusPercent / 100.0));
        PotionEffect extended = new PotionEffect(original.getType(), extendedDuration, original.getAmplifier(),
                original.isAmbient(), original.hasParticles(), original.hasIcon());
        e.setCancelled(true);
        this.extendingPotionDuration.add(p.getUniqueId());
        try {
            p.addPotionEffect(extended);
        } finally {
            this.extendingPotionDuration.remove(p.getUniqueId());
        }
    }
}
