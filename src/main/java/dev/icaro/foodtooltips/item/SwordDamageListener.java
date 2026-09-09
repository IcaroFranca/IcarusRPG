package dev.icaro.foodtooltips.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Applies the custom sword attack-damage/speed pair on join; the periodic HUD tick in
 * {@code FoodTooltipsPlugin} re-applies it every cycle afterwards, same join+tick
 * pattern {@link ItemTierListener} uses - enough to reach any sword the player picks
 * up, buys, mines or is given, without needing a dedicated pickup/inventory-click hook.
 */
public final class SwordDamageListener implements Listener {
    private final SwordDamageService swordDamage;

    public SwordDamageListener(SwordDamageService swordDamage) {
        this.swordDamage = swordDamage;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.swordDamage.neutralizeBaseAttackDamage(e.getPlayer());
        this.swordDamage.applySwordDamage(e.getPlayer());
    }
}
