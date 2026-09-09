package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.i18n.Language;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

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

    /**
     * MONITOR - and specifically registered after CombatListener's own MONITOR-priority
     * death handler (Bukkit runs same-priority handlers in registration order) - so a
     * dropped legendary weapon is added after CombatListener's Bestiary loot-bonus pass
     * already ran on the original drops, never getting duplicated by it.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof LivingEntity entity)) {
            return;
        }
        String defId = this.island.islandMobId(entity);
        if (defId == null) {
            return;
        }
        this.island.scheduleRespawn(defId, entity.getLocation());
        Player killer = entity.getKiller();
        ItemStack drop = this.island.rollDrop(defId, killer != null ? Language.of(killer) : Language.PT);
        if (drop != null) {
            e.getDrops().add(drop);
        }
    }
}
