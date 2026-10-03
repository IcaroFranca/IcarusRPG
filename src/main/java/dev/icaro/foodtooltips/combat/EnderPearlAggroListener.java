package dev.icaro.foodtooltips.combat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

/**
 * Per the player's own explicit "ao arremessar ender pearl, o jogador se teleporte como de
 * costume, mas lá vai ter um enderman com raiva dele o atacando" - a real Enderman spawns right
 * where the pearl lands and is immediately hostile toward whoever threw it ({@link
 * Enderman#setTarget}, bypassing vanilla's own "neutral until stared at" default), one tick
 * after the teleport actually resolves (so the player is already standing at the real
 * destination when it appears, not the old spot). The teleport itself is untouched - this only
 * adds the ambush on top of it.
 *
 * <p>Spawned via a plain {@link World#spawn}, same {@code SpawnReason.CUSTOM} path every other
 * plugin-spawned mob in this project already goes through - {@code
 * combat.CombatListener#spawn}'s own {@code CreatureSpawnEvent} handler picks it up for free, so
 * this Enderman gets the same mob-difficulty scaling as anything else, no extra wiring needed.
 */
public final class EnderPearlAggroListener implements Listener {
    private final Plugin plugin;

    public EnderPearlAggroListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void teleport(PlayerTeleportEvent e) {
        if (e.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        Location to = e.getTo();
        World world = to == null ? null : to.getWorld();
        if (world == null) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Enderman enderman = world.spawn(to, Enderman.class);
            enderman.setTarget(p);
        });
    }
}
