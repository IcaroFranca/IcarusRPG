package dev.icaro.foodtooltips.protect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionService {
    private static final Logger LOGGER = Logger.getLogger(ProtectionService.class.getName());

    private final boolean worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    private final boolean griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention") != null;

    public boolean canBuild(Player player, Block block) {
        return this.canBuild(player, block.getLocation());
    }

    /**
     * Fails CLOSED (denies) whenever a hook throws, rather than silently skipping it
     * and continuing to allow - the previous empty {@code catch (Throwable) {}}
     * swallowed a broken reflection call (a WorldGuard/GriefPrevention API version
     * mismatch, say) with no log line at all, so a region's protection could stop
     * working entirely with nothing in the console to say why: griefing went through
     * in a "protected" area as if nothing were wrong. Logged at SEVERE (with the full
     * stack trace) every time, deliberately not deduplicated/rate-limited - an admin
     * seeing this at all means the integration needs attention regardless of how
     * often it repeats, and it should never repeat quietly for long once someone's
     * watching the console.
     */
    public boolean canBuild(Player player, Location location) {
        if (this.worldGuard) {
            try {
                if (!WorldGuardHook.canBuild(player, location)) {
                    return false;
                }
            } catch (Throwable throwable) {
                LOGGER.log(Level.SEVERE, "WorldGuard build-permission check failed - denying "
                        + player.getName() + " at " + location + " until this is fixed.", throwable);
                return false;
            }
        }
        if (this.griefPrevention) {
            try {
                if (!GriefPreventionHook.canBuild(player, location)) {
                    return false;
                }
            } catch (Throwable throwable) {
                LOGGER.log(Level.SEVERE, "GriefPrevention build-permission check failed - denying "
                        + player.getName() + " at " + location + " until this is fixed.", throwable);
                return false;
            }
        }
        return true;
    }

    private static final class WorldGuardHook {
        private WorldGuardHook() {
        }

        static boolean canBuild(Player player, Location location) {
            LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            World world = BukkitAdapter.adapt((org.bukkit.World)location.getWorld());
            if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, world)) {
                return true;
            }
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return query.testState(BukkitAdapter.adapt((Location)location), local, new StateFlag[]{Flags.BUILD});
        }
    }

    private static final class GriefPreventionHook {
        private GriefPreventionHook() {
        }

        static boolean canBuild(Player player, Location location) throws Exception {
            Object reason;
            Object gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention").getField("instance").get(null);
            try {
                reason = gp.getClass().getMethod("allowBuild", Player.class, Location.class, Material.class).invoke(gp, player, location, location.getBlock().getType());
            }
            catch (NoSuchMethodException legacy) {
                reason = gp.getClass().getMethod("allowBuild", Player.class, Location.class).invoke(gp, player, location);
            }
            return reason == null;
        }
    }
}

