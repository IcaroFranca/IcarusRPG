package dev.icaro.foodtooltips.enchant;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

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
    /** The real achievable maximum Bookshelf Power (see {@code EnchantMenuService#bookshelfPower}) - what {@link #requiredBookshelfPower} scales up to, so nothing is ever gated behind a number that can't actually be reached. */
    int MAX_BOOKSHELF_POWER = 24;

    /** Stable id, unique across both kinds - not shown to players, just used for lookups. */
    String id();

    /** The catalog/guide icon's own name - level-independent for custom entries, best-effort (level 1's leveled name) for vanilla, since Bukkit has no "bare" enchant name API. */
    String catalogName(boolean pt);

    /** Full name including the level, e.g. "Fúria III" / "Sharpness V" - matches vanilla's own convention of omitting the numeral entirely for a max-level-1 entry. */
    String leveledName(boolean pt, int level);

    int maxLevel();

    /**
     * Enchanting skill level required for this entry to appear on the real Enchanting
     * Table's own application catalog (see {@code EnchantMenuService}) - 0 (always
     * available) for almost everything; only a handful of entries override this. Does
     * NOT gate the Guide or Milestones screens, which stay full reference lists
     * regardless of the viewer's own Enchanting level.
     */
    default int requiredEnchantingLevel() {
        return 0;
    }

    /**
     * Minimum Bookshelf Power (see {@code EnchantMenuService#bookshelfPower}) needed
     * to apply this entry at {@code level} - 0 for every entry's own level 1 (always
     * free), scaling linearly up to a flat {@value #MAX_BOOKSHELF_POWER} at the
     * entry's own {@link #maxLevel()}, so a stronger level of anything costs more
     * Bookshelf Power than a weaker one of the same entry. A single-level entry
     * ({@code maxLevel() == 1}) is never gated.
     */
    default int requiredBookshelfPower(int level) {
        int max = this.maxLevel();
        if (max <= 1) {
            return 0;
        }
        return (int) Math.round((double) MAX_BOOKSHELF_POWER * (level - 1) / (max - 1));
    }

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. */
    int costAtLevel(int level);

    /**
     * Word-wrapped lore lines explaining what this entry does, with any numeric
     * value shown as level 1's own real number (in bright green) rather than a
     * placeholder letter - shown in the catalog and Guide, where no specific level is
     * selected yet. Empty (never null) if there isn't one. {@code item} is the
     * Material this description is being shown for (the Guide screen, tied to no
     * item, passes null) - only {@link VanillaEnchantEntry}'s own Fortune entry reads
     * it, to show "Foraging Fortune" instead of "Mining Fortune" on an axe.
     */
    List<Component> genericDescription(boolean pt, Material item);

    /**
     * Same description as {@link #genericDescription}, but with {@code level}'s own
     * real value in place of level 1's - shown on the level-select screen and in an
     * applied item's own lore. Empty (never null) if there isn't one. See {@link
     * #genericDescription} for what {@code item} is used for.
     */
    List<Component> resolvedDescription(boolean pt, int level, Material item);
}
