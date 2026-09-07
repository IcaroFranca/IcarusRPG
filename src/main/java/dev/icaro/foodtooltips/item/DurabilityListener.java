package dev.icaro.foodtooltips.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Applies the durability multiplier on join; the periodic HUD tick in {@code
 * FoodTooltipsPlugin} re-applies it every cycle afterwards (same join+tick pattern
 * {@code ItemTierListener}/{@code ArmorDefenseListener} use), which is enough to
 * reach any item the player picks up, buys, mines, crafts or is given, without
 * needing a dedicated pickup/inventory-click/craft hook.
 */
public final class DurabilityListener implements Listener {
    private final DurabilityService durability;

    public DurabilityListener(DurabilityService durability) {
        this.durability = durability;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.durability.applyDurability(e.getPlayer());
    }
}
