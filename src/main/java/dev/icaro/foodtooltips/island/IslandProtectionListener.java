package dev.icaro.foodtooltips.island;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

/**
 * Blocks placing/breaking blocks in the combat island world for anyone not in Creative
 * mode - keeps survival players from griefing or farming the hand-built graveyard. OPs
 * always bypass (config island-mobs.world/protect-blocks), so admin touch-ups don't
 * require switching gamemode.
 */
public final class IslandProtectionListener implements Listener {
    private final String world;
    private final boolean enabled;

    public IslandProtectionListener(Plugin plugin) {
        this.world = plugin.getConfig().getString("island-mobs.world", "combat_island");
        this.enabled = plugin.getConfig().getBoolean("island-mobs.protect-blocks", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (this.blocked(e.getPlayer(), e.getBlock().getWorld().getName())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (this.blocked(e.getPlayer(), e.getBlock().getWorld().getName())) {
            e.setCancelled(true);
        }
    }

    private boolean blocked(Player p, String worldName) {
        return this.enabled && worldName.equals(this.world) && p.getGameMode() != GameMode.CREATIVE && !p.isOp();
    }
}
