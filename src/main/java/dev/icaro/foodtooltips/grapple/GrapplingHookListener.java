package dev.icaro.foodtooltips.grapple;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Wires {@link GrapplingHookService#interact}/{@link GrapplingHookService#cancel} into real events - same "service holds the ability, listener only wires the event" split as {@code sponge.MegaSpongeListener}. */
public final class GrapplingHookListener implements Listener {
    private final GrapplingHookService hook;

    public GrapplingHookListener(GrapplingHookService hook) {
        this.hook = hook;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.hook.isGrapplingHook(e.getItem())) {
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }
        e.setCancelled(true);
        this.hook.interact(e.getPlayer());
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
