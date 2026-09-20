package dev.icaro.foodtooltips.combat;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * An End Crystal's own explosion (attacked and destroyed, whether in the End fight or "crystal
 * PvP" elsewhere) never breaks blocks on this server - only its damage to nearby entities still
 * applies. Clearing {@link EntityExplodeEvent#blockList()} rather than cancelling the whole
 * event keeps that damage intact, same "let the event through, just drop the block list"
 * technique {@code mining.GemService#entityExplode} already uses to protect its own Gem
 * blocks specifically - this one is unconditional for every End Crystal explosion instead.
 */
public final class EnderCrystalExplosionListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void explode(EntityExplodeEvent e) {
        if (e.getEntityType() == EntityType.END_CRYSTAL) {
            e.blockList().clear();
        }
    }
}
