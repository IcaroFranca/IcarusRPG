package dev.icaro.foodtooltips.enchant;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * The plugin's own custom enchantments - entirely separate from (and, since real
 * vanilla enchantments are also offered through the same screen via {@link
 * VanillaEnchantEntry}, sharing the catalog with) vanilla enchantments. Applied
 * only through the reworked Enchanting Table screen (see {@code
 * EnchantMenuService}), never randomly.
 *
 * <p>Each of these three replaces a vanilla enchantment whose real level cap
 * (fixed by Mojang/Bukkit, not overridable) was too low for the leveled effect
 * wanted here - Flame and Infinity are both capped at level 1 vanilla, Lure at
 * level 3 - so vanilla's own version of each is excluded from the table (see
 * {@code EnchantService#allEntries}) in favor of these. Unlike the vanilla
 * entries, these actually change gameplay - see {@link
 * dev.icaro.foodtooltips.enchant.CustomEnchantEffectListener} for the real
 * effect (arrow burn-and-damage-over-time, arrow-save chance, fishing wait time).
 */
public enum IcarusEnchant {
    /** Replaces vanilla Flame (capped at level 1) - see CustomEnchantEffectListener#arrowHit. */
    FLAME("Chama", "Flame", 2, Material.BOW),
    /** Replaces vanilla Lure (capped at level 3) - see CustomEnchantEffectListener#fish. */
    LURE("Chamariz", "Lure", 5, Material.FISHING_ROD),
    /** Replaces vanilla Infinity (capped at level 1, and a plain on/off rather than a chance) - see CustomEnchantEffectListener#bowShoot. */
    INFINITE_QUIVER("Aljava Infinita", "Infinite Quiver", 5, Material.BOW);

    /** Level 1's (duration seconds, damage % per second) pair; level 2's. Doesn't fit a "flat rate * level" formula, so it's a direct lookup instead. */
    private static final double[] FLAME_DURATION = {0, 3.5, 4.0};
    private static final double[] FLAME_PERCENT = {0, 3, 6};

    private final String namePt;
    private final String nameEn;
    private final int maxLevel;
    private final Material appliesTo;

    IcarusEnchant(String namePt, String nameEn, int maxLevel, Material appliesTo) {
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.maxLevel = maxLevel;
        this.appliesTo = appliesTo;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    /** The one item type this entry can go on - unlike vanilla entries (see {@code Enchantment#canEnchantItem}), a custom entry has exactly one. */
    public boolean canApplyTo(Material item) {
        return item == this.appliesTo;
    }

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. Matches the flat rate every vanilla entry uses (see VanillaEnchantEntry), for consistency. */
    public int costAtLevel(int level) {
        return 2 * level;
    }

    /** Word-wrapped description, colored the same way vanilla entries are - see {@link EnchantText}. {@code level} null shows the generic "X"/"Y" placeholder view; a real level resolves the real numbers. */
    public List<Component> description(boolean pt, Integer level) {
        return switch (this) {
            case FLAME -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("A flecha incendeia seus inimigos por"), flameDuration(level), EnchantText.Token.plain("s, causando"),
                            flamePercent(level), EnchantText.Token.plain("% do seu dano por segundo."))
                    : List.of(EnchantText.Token.plain("Arrow ignites your enemies for"), flameDuration(level), EnchantText.Token.plain("s, dealing"),
                            flamePercent(level), EnchantText.Token.plain("% of your damage per second.")));
            case LURE -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Diminui o tempo máximo para fisgar algo em"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("por nível."))
                    : List.of(EnchantText.Token.plain("Shortens the maximum time to catch something by"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("per level.")));
            case INFINITE_QUIVER -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Economiza flechas"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.Token.plain("por nível das vezes que você atira com o arco."))
                    : List.of(EnchantText.Token.plain("Saves arrows"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.Token.plain("per level of the time when you fire your bow.")));
        };
    }

    /** Flame's duration lookup (not a formula - see {@link #FLAME_DURATION}), formatted without a trailing ".0" for level 2's whole number. */
    private static EnchantText.Token flameDuration(Integer level) {
        if (level == null) {
            return EnchantText.Token.colored("X", EnchantText.VALUE_COLOR);
        }
        double v = FLAME_DURATION[Math.min(level, FLAME_DURATION.length - 1)];
        String text = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return EnchantText.Token.colored(text, EnchantText.VALUE_COLOR);
    }

    private static EnchantText.Token flamePercent(Integer level) {
        if (level == null) {
            return EnchantText.Token.colored("Y", EnchantText.VALUE_COLOR);
        }
        return EnchantText.Token.colored(String.valueOf((long) FLAME_PERCENT[Math.min(level, FLAME_PERCENT.length - 1)]), EnchantText.VALUE_COLOR);
    }
}
