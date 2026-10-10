package dev.icaro.foodtooltips.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Whether this plugin may take over a player's sidebar with its own temporary boards (Nether
 * Heat, Mushroom Soup flight time). A scoreboard plugin like TAB owns the sidebar itself, and
 * swapping the player onto a personal Bukkit scoreboard hid TAB's whole board for as long as
 * ours was up ("as moedas somem no nether por causa do heat") - with one installed, the same
 * numbers are exposed as PlaceholderAPI placeholders instead ({@code placeholder.IcarusPlaceholders})
 * for that plugin's own board to show alongside everything else.
 *
 * <p>{@code hud.own-sidebar}: {@code auto} (default - only when TAB isn't running), {@code true}
 * (always), {@code false} (never).
 */
public final class SidebarMode {
    private SidebarMode() {
    }

    public static boolean useOwnSidebar(Plugin plugin) {
        String mode = plugin.getConfig().getString("hud.own-sidebar", "auto");
        if ("true".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("false".equalsIgnoreCase(mode)) {
            return false;
        }
        return !Bukkit.getPluginManager().isPluginEnabled("TAB");
    }
}
