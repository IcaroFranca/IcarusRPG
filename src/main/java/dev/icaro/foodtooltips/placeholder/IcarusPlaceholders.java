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
 */
public final class IcarusPlaceholders extends PlaceholderExpansion {
    private final GlobalLevelService global;

    public IcarusPlaceholders(GlobalLevelService global) {
        this.global = global;
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
        if (identifier.equals("globallevel")) {
            return String.valueOf(this.global.snapshot(player).level());
        }
        return null;
    }
}
