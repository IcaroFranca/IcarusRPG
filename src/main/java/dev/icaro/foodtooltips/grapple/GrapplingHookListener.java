package dev.icaro.foodtooltips.grapple;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Wires {@link GrapplingHookService#interact}/{@link GrapplingHookService#cancel} into
 * real events - same "service holds the ability, listener only wires the event" split as
 * {@code sponge.MegaSpongeListener}. Swap Hands (F), not right-click - see {@code
 * GrapplingHookService}'s own class doc for why {@code PlayerInteractEvent}'s {@code
 * RIGHT_CLICK_AIR} can't reliably drive a ranged ability, and {@code priority = HIGH,
 * ignoreCancelled = true} matching every other "must always fire" item-ability listener
 * in this plugin ({@code SpruceAxeListener}, {@code SwordThrowListener}, {@code
 * BuilderWandListener}...) so a protection plugin's own cancellation can't silently
 * swallow this ability either.
 */
public final class GrapplingHookListener implements Listener {
    private final GrapplingHookService hook;

    public GrapplingHookListener(GrapplingHookService hook) {
        this.hook = hook;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!this.hook.isGrapplingHook(p.getInventory().getItemInMainHand())) {
            return;
        }
        e.setCancelled(true);
        this.hook.interact(p);
    }

    /** "Agachar cancela" - fires on beginning to sneak, ignored on standing back up. */
    @EventHandler
    public void sneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking()) {
            this.hook.cancel(e.getPlayer());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.hook.cancel(e.getPlayer());
    }
}
