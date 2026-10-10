package dev.icaro.foodtooltips.placeholder;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI expansion exposing IcarusRPG's own stats to any other plugin that
 * reads placeholders - TAB in particular, for sorting the tab list/nametags by Global
 * Level (highest first: TAB's own config sorts descending on a numeric placeholder).
 * Registered from {@code FoodTooltipsPlugin} only when PlaceholderAPI is actually
 * installed (same soft-depend pattern as WorldGuard/Citizens elsewhere in this
 * plugin) - PlaceholderAPI itself is a "provided" compile-time dependency, nothing
 * here ships it.
 *
 * <p>%icarusrpg_globallevel% - the player's current Global Level (see {@link
 * GlobalLevelService#snapshot}), as a plain integer string.
 *
 * <p>The sidebar values this plugin used to put on its own temporary board (which hid a
 * scoreboard plugin's whole board while up - see {@code util.SidebarMode}), for TAB to show
 * instead: %icarusrpg_heat% (current Nether Heat, 0 outside it), %icarusrpg_nether% ("true"
 * in the Nether - for a Nether-only TAB board's display-condition, regardless of world name),
 * %icarusrpg_flight% (Mushroom Soup flight seconds left, 0 if none - for conditions),
 * %icarusrpg_flight_time% (that same time as dd:hh:mm:ss) and %icarusrpg_flight_display% (a
 * ready-made "✦ Flight: 00:00:00:30" line, empty when not flying).
 */
public final class IcarusPlaceholders extends PlaceholderExpansion {
    private final GlobalLevelService global;
    private final java.util.function.ToIntFunction<Player> heat;
    private final java.util.function.ToIntFunction<Player> flightSeconds;

    public IcarusPlaceholders(GlobalLevelService global, java.util.function.ToIntFunction<Player> heat, java.util.function.ToIntFunction<Player> flightSeconds) {
        this.global = global;
        this.heat = heat;
        this.flightSeconds = flightSeconds;
    }

    @Override
    public String getIdentifier() {
        return "icarusrpg";
    }

    @Override
    public String getAuthor() {
        return "IcarusRPG";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    /** Keeps the expansion registered across a PlaceholderAPI reload, instead of needing `/papi reload` to bring it back every time. */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }
        switch (identifier) {
            case "globallevel":
                return String.valueOf(this.global.snapshot(player).level());
            case "heat":
                return String.valueOf(this.heat.applyAsInt(player));
            case "nether":
                return String.valueOf(player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER);
            case "flight":
                return String.valueOf(this.flightSeconds.applyAsInt(player));
            case "flight_time":
                return dev.icaro.foodtooltips.util.DurationFormat.ddhhmmss(this.flightSeconds.applyAsInt(player));
            case "flight_display": {
                int seconds = this.flightSeconds.applyAsInt(player);
                return seconds > 0 ? "§d✦ Flight: §f" + dev.icaro.foodtooltips.util.DurationFormat.ddhhmmss(seconds) : "";
            }
            default:
                return null;
        }
    }
}
