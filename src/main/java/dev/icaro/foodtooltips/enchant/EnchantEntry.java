package dev.icaro.foodtooltips.enchant;

/**
 * One catalog entry the Enchanting Table screen can offer - either one of the
 * plugin's own {@link IcarusEnchant}s ({@link CustomEnchantEntry}) or a real vanilla
 * {@code org.bukkit.enchantments.Enchantment} ({@link VanillaEnchantEntry}), unified so
 * {@code EnchantMenuService} doesn't need to know which kind it's looking at. Both
 * kinds share the same tier-based slot limit (see {@link EnchantService#slotLimit})
 * and the same flat per-level XP cost shape; only custom entries get a written
 * description and a level-independent catalog name (vanilla has no API for either -
 * see the per-method docs on {@link VanillaEnchantEntry}).
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

    /** One line explaining what this entry does, or null if there isn't one (every vanilla entry - Bukkit has no description API, and vanilla's own tooltips don't show one either). */
    String description(boolean pt);

    /** "+N%"/"+N" for custom entries, or "" for vanilla (there's no formula-derived value to show beyond the level itself). */
    String formattedValue(int level);
}
