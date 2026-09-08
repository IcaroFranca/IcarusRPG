package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.citizens.CitizensIntegrationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/** Cancels any non-Citizens spawn inside an {@link IslandMobService}'s zone, so only its own hand-placed mobs exist there. */
public final class IslandMobListener implements Listener {
    private final IslandMobService island;

    public IslandMobListener(IslandMobService island) {
        this.island = island;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!this.island.suppressNaturalSpawns() || CitizensIntegrationService.isNpc(e.getEntity())) {
            return;
        }
        if (this.island.zone().contains(e.getLocation())) {
            e.setCancelled(true);
        }
    }
}
