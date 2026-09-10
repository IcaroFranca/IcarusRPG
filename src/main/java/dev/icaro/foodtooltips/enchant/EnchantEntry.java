package dev.icaro.foodtooltips.enchant;

import java.util.List;
import net.kyori.adventure.text.Component;

/**
 * One catalog entry the Enchanting Table screen can offer - either one of the
 * plugin's own {@link IcarusEnchant}s ({@link CustomEnchantEntry}) or a real vanilla
 * {@code org.bukkit.enchantments.Enchantment} ({@link VanillaEnchantEntry}), unified so
 * {@code EnchantMenuService} doesn't need to know which kind it's looking at. Both
 * kinds share the same flat per-level XP cost shape; only custom entries get a level-independent
 * catalog name for free (vanilla has no API for one - see the per-method docs on
 * {@link VanillaEnchantEntry}).
 */
public sealed interface EnchantEntry permits CustomEnchantEntry, VanillaEnchantEntry {
    /** Stable id, unique across both kinds - not shown to players, just used for lookups. */
    String id();

    /** The catalog/guide icon's own name - level-independent for custom entries, best-effort (level 1's leveled name) for vanilla, since Bukkit has no "bare" enchant name API. */
    String catalogName(boolean pt);

    /** Full name including the level, e.g. "Fúria III" / "Sharpness V" - matches vanilla's own convention of omitting the numeral entirely for a max-level-1 entry. */
    String leveledName(boolean pt, int level);

    int maxLevel();

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. */
    int costAtLevel(int level);

    /**
     * Word-wrapped lore lines explaining what this entry does, with any numeric
     * value shown as a bright green "X" placeholder rather than a real number -
     * shown in the catalog and Guide, where no specific level is selected yet. Empty
     * (never null) if there isn't one.
     */
    List<Component> genericDescription(boolean pt);

    /**
     * Same description as {@link #genericDescription}, but with {@code level}'s real
     * value substituted in place of the "X" placeholder - shown on the level-select
     * screen and in an applied item's own lore. Empty (never null) if there isn't one.
     */
    List<Component> resolvedDescription(boolean pt, int level);
}
