package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.citizens.CitizensIntegrationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Right-clicking another player opens their Status & Equipamento screen (read-only) -
 * the same one {@code SkillsMenuService#openStats} already builds for your own, via
 * the head icon in the Skills menu. Only reacts to the main-hand copy of the event
 * (right-clicking an entity fires once per hand) so the screen doesn't open twice per
 * click. Player-skinned Citizens NPCs (e.g. the Blacksmith) are still {@code instanceof
 * Player} to Bukkit, so they're excluded here - otherwise this would steal their click
 * (and cancel the event) before the NPC's own right-click handler ever runs.
 */
public final class PlayerStatsViewListener implements Listener {
    private final SkillsMenuService menus;

    public PlayerStatsViewListener(SkillsMenuService menus) {
        this.menus = menus;
    }

    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player target) || target.equals(e.getPlayer())
                || CitizensIntegrationService.isNpc(target)) {
            return;
        }
        e.setCancelled(true);
        this.menus.openStats(e.getPlayer(), target);
    }
}
