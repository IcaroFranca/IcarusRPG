package dev.icaro.foodtooltips.reforge;

import dev.icaro.foodtooltips.item.ItemTier;
import java.util.Locale;

/**
 * Every reforge a sword can roll in the Blacksmith's screen ({@link ReforgeMenuService}) -
 * one random pick per {@code ReforgeService#reforge} roll, each with its own five-tier stat
 * table (Tier D through Tier S) matching the player's own spec exactly. Sword-only for now;
 * other equipment kinds get their own tables later.
 *
 * <p>Kept as a fixed English proper noun in every language (like every other reforge word
 * in this family) - same precedent as {@link ItemTier#label()}'s "TIER S" badge, which is
 * also never translated.
 */
public enum ReforgePrefix {
    GENTLE(stats(3, 0, 0, 0, 8), stats(5, 0, 0, 0, 10), stats(7, 0, 0, 0, 15), stats(10, 0, 0, 0, 20), stats(15, 0, 0, 0, 25)),
    ODD(stats(0, 12, 10, -5, 0), stats(0, 15, 15, -10, 0), stats(0, 15, 15, -18, 0), stats(0, 20, 22, -32, 0), stats(0, 25, 30, -50, 0)),
    FAST(stats(0, 0, 0, 0, 10), stats(0, 0, 0, 0, 20), stats(0, 0, 0, 0, 30), stats(0, 0, 0, 0, 40), stats(0, 0, 0, 0, 50)),
    FAIR(stats(2, 2, 2, 2, 2), stats(3, 3, 3, 3, 3), stats(4, 4, 4, 4, 4), stats(7, 7, 7, 7, 7), stats(10, 10, 10, 10, 10)),
    EPIC(stats(15, 0, 10, 0, 1), stats(20, 0, 15, 0, 2), stats(25, 0, 20, 0, 4), stats(32, 0, 27, 0, 7), stats(40, 0, 35, 0, 10)),
    SHARP(stats(0, 10, 20, 0, 0), stats(0, 12, 30, 0, 0), stats(0, 14, 40, 0, 0), stats(0, 17, 55, 0, 0), stats(0, 20, 75, 0, 0)),
    HEROIC(stats(15, 0, 0, 40, 1), stats(20, 0, 0, 50, 2), stats(25, 0, 0, 65, 2), stats(32, 0, 0, 80, 3), stats(40, 0, 0, 100, 5)),
    SPICY(stats(2, 1, 25, 0, 1), stats(3, 1, 35, 0, 2), stats(4, 1, 45, 0, 4), stats(7, 1, 60, 0, 7), stats(10, 1, 80, 0, 10)),
    LEGENDARY(stats(3, 5, 5, 5, 2), stats(7, 7, 10, 8, 3), stats(12, 9, 15, 12, 5), stats(18, 12, 22, 18, 7), stats(25, 15, 28, 25, 10));

    private final ReforgeStats tierD;
    private final ReforgeStats tierC;
    private final ReforgeStats tierB;
    private final ReforgeStats tierA;
    private final ReforgeStats tierS;

    ReforgePrefix(ReforgeStats tierD, ReforgeStats tierC, ReforgeStats tierB, ReforgeStats tierA, ReforgeStats tierS) {
        this.tierD = tierD;
        this.tierC = tierC;
        this.tierB = tierB;
        this.tierA = tierA;
        this.tierS = tierS;
    }

    private static ReforgeStats stats(double strength, double critChance, double critDamage, double intelligence, double attackSpeed) {
        return new ReforgeStats(strength, critChance, critDamage, intelligence, attackSpeed);
    }

    public ReforgeStats stats(ItemTier tier) {
        return switch (tier) {
            case D -> this.tierD;
            case C -> this.tierC;
            case B -> this.tierB;
            case A -> this.tierA;
            case S -> this.tierS;
            case E -> ReforgeStats.NONE;
        };
    }

    /** "Gentle" / "Sharp" / "Legendary"... - see the class doc on why this is never translated. */
    public String displayWord() {
        String n = this.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }
}
