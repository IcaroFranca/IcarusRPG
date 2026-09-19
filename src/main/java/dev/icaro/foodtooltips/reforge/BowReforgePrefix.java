package dev.icaro.foodtooltips.reforge;

import dev.icaro.foodtooltips.item.ItemTier;
import java.util.Locale;

/**
 * Every reforge a bow can roll in the Blacksmith's screen ({@link ReforgeMenuService}) - one
 * random pick per {@code ReforgeService#reforge} roll, each with its own six-tier stat table
 * (Tier D through {@link ItemTier#MYTHIC}) matching the player's own spec exactly - one tier
 * more than swords/armor get, since this is the only catalog {@link ItemTier#MYTHIC} exists
 * for (see its own class doc). Sibling of {@link ReforgePrefix} (swords) - separate enum since
 * the two gear kinds roll from completely different tables, but reuses {@link ReforgeStats}'s
 * own record shape as-is rather than a dedicated "BowReforgeStats" (a bow's own table only
 * ever grants Strength/Crit Chance/Crit Damage/Intelligence, the exact same fields a sword's
 * table already has - {@code attackSpeed} is simply always 0 here, unused, same as an armor
 * piece never populating a sword-only field would be). Same "fixed English proper noun, never
 * translated" convention as {@link ReforgePrefix}'s own class doc.
 */
public enum BowReforgePrefix {
    DEADLY(stats(0, 10, 5, 0), stats(0, 13, 10, 0), stats(0, 16, 18, 0), stats(0, 19, 32, 0), stats(0, 22, 50, 0), stats(0, 25, 78, 0)),
    FINE(stats(3, 5, 2, 0), stats(7, 7, 4, 0), stats(12, 9, 7, 0), stats(18, 12, 10, 0), stats(25, 15, 15, 0), stats(33, 18, 20, 0)),
    GRAND(stats(25, 0, 0, 0), stats(32, 0, 0, 0), stats(40, 0, 0, 0), stats(50, 0, 0, 0), stats(60, 0, 0, 0), stats(75, 0, 0, 0)),
    HASTY(stats(3, 20, 0, 0), stats(5, 25, 0, 0), stats(7, 30, 0, 0), stats(10, 40, 0, 0), stats(15, 50, 0, 0), stats(20, 60, 0, 0)),
    NEAT(stats(0, 10, 4, 3), stats(0, 12, 8, 6), stats(0, 14, 14, 10), stats(0, 17, 20, 15), stats(0, 20, 30, 20), stats(0, 25, 40, 25)),
    RAPID(stats(2, 0, 35, 0), stats(3, 0, 45, 0), stats(4, 0, 55, 0), stats(7, 0, 65, 0), stats(10, 0, 75, 0), stats(15, 0, 90, 0)),
    UNREAL(stats(3, 8, 5, 0), stats(7, 9, 10, 0), stats(12, 10, 18, 0), stats(18, 11, 32, 0), stats(25, 13, 50, 0), stats(34, 15, 70, 0)),
    AWKWARD(stats(0, 10, 5, -5), stats(0, 12, 10, -10), stats(0, 15, 18, -18), stats(0, 20, 22, -32), stats(0, 25, 30, -50), stats(0, 30, 35, -72)),
    RICH(stats(2, 10, 1, 3), stats(3, 12, 2, 6), stats(4, 14, 4, 10), stats(7, 17, 7, 15), stats(10, 20, 15, 20), stats(15, 25, 25, 25));

    private final ReforgeStats tierD;
    private final ReforgeStats tierC;
    private final ReforgeStats tierB;
    private final ReforgeStats tierA;
    private final ReforgeStats tierS;
    private final ReforgeStats tierMythic;

    BowReforgePrefix(ReforgeStats tierD, ReforgeStats tierC, ReforgeStats tierB, ReforgeStats tierA,
                      ReforgeStats tierS, ReforgeStats tierMythic) {
        this.tierD = tierD;
        this.tierC = tierC;
        this.tierB = tierB;
        this.tierA = tierA;
        this.tierS = tierS;
        this.tierMythic = tierMythic;
    }

    private static ReforgeStats stats(double strength, double critChance, double critDamage, double intelligence) {
        return new ReforgeStats(strength, critChance, critDamage, intelligence, 0.0);
    }

    public ReforgeStats stats(ItemTier tier) {
        return switch (tier) {
            case D -> this.tierD;
            case C -> this.tierC;
            case B -> this.tierB;
            case A -> this.tierA;
            case S -> this.tierS;
            case MYTHIC -> this.tierMythic;
            case E -> ReforgeStats.NONE;
        };
    }

    /** "Deadly" / "Grand" / "Awkward"... - never translated, see the class doc. */
    public String displayWord() {
        String n = this.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }
}
