package dev.icaro.foodtooltips.island;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Cancels any non-plugin spawn inside an {@link IslandMobService}'s zone, so
 * only its own hand-placed mobs exist there, and schedules a respawn whenever
 * one of those mobs dies.
 */
public final class IslandMobListener implements Listener {
    private final IslandMobService island;

    public IslandMobListener(IslandMobService island) {
        this.island = island;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        // CUSTOM is the reason World#spawn(...)/spawnEntity(...) uses - i.e. our own
        // IslandMobService.spawnOne(), which must never be cancelled by its own guard.
        if (!this.island.suppressNaturalSpawns() || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }
        if (this.island.zone().contains(e.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String defId = this.island.islandMobId(entity);
        if (defId != null) {
            this.island.scheduleRespawn(defId, entity.getLocation());
        }
    }
}
