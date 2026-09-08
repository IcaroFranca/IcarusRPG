package dev.icaro.foodtooltips.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Applies the custom tool attack-damage/speed pair on join; the periodic HUD tick in
 * {@code FoodTooltipsPlugin} re-applies it every cycle afterwards, same join+tick
 * pattern {@link SwordDamageListener} uses - enough to reach any axe/pickaxe/shovel/hoe
 * the player picks up, buys, mines or is given, without needing a dedicated
 * pickup/inventory-click hook.
 */
public final class ToolDamageListener implements Listener {
    private final ToolDamageService toolDamage;

    public ToolDamageListener(ToolDamageService toolDamage) {
        this.toolDamage = toolDamage;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.toolDamage.applyToolDamage(e.getPlayer());
    }
}
