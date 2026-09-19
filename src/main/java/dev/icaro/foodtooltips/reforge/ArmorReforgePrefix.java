package dev.icaro.foodtooltips.reforge;

import dev.icaro.foodtooltips.item.ItemTier;
import java.util.Locale;

/**
 * Every reforge an armor piece can roll in the Blacksmith's screen ({@link
 * ReforgeMenuService}) - one random pick per {@code ReforgeService#reforge} roll on a
 * helmet/chestplate/leggings/boots, each with its own five-tier stat table (Tier D
 * through Tier S). Sibling of {@link ReforgePrefix} (swords) - separate enum since the
 * two gear kinds roll from completely different tables, but same "fixed English proper
 * noun, never translated" convention (see {@link ReforgePrefix}'s own class doc).
 */
public enum ArmorReforgePrefix {
    CLEAN(stats(5, 5, 0, 2, 0, 0, 0, 0), stats(7, 7, 0, 4, 0, 0, 0, 0), stats(10, 10, 0, 6, 0, 0, 0, 0), stats(15, 15, 0, 8, 0, 0, 0, 0), stats(20, 20, 0, 10, 0, 0, 0, 0)),
    FIERCE(stats(0, 0, 2, 2, 4, 0, 0, 0), stats(0, 0, 4, 3, 7, 0, 0, 0), stats(0, 0, 6, 4, 10, 0, 0, 0), stats(0, 0, 8, 5, 14, 0, 0, 0), stats(0, 0, 10, 6, 18, 0, 0, 0)),
    HEAVY(stats(0, 25, 0, 0, -1, -1, 0, 0), stats(0, 35, 0, 0, -2, -1, 0, 0), stats(0, 50, 0, 0, -2, -1, 0, 0), stats(0, 65, 0, 0, -3, -3, 0, 0), stats(0, 80, 0, 0, -5, -5, 0, 0)),
    LIGHT(stats(5, 1, 0, 1, 1, 1, 1, 0), stats(7, 2, 0, 1, 2, 2, 2, 0), stats(10, 3, 0, 2, 3, 3, 3, 0), stats(15, 4, 0, 2, 4, 4, 4, 0), stats(20, 5, 0, 3, 5, 5, 5, 0)),
    MYTHIC(stats(2, 2, 2, 1, 0, 2, 0, 20), stats(4, 4, 4, 2, 0, 2, 0, 25), stats(6, 6, 6, 3, 0, 2, 0, 30), stats(8, 8, 8, 4, 0, 2, 0, 40), stats(10, 10, 10, 5, 0, 2, 0, 50)),
    PURE(stats(2, 2, 2, 1, 2, 1, 1, 2), stats(3, 3, 3, 4, 3, 1, 1, 3), stats(4, 4, 4, 6, 4, 1, 2, 4), stats(6, 6, 6, 8, 6, 1, 3, 6), stats(8, 8, 8, 10, 8, 1, 4, 8)),
    SMART(stats(4, 4, 0, 0, 0, 0, 0, 20), stats(6, 6, 0, 0, 0, 0, 0, 40), stats(9, 9, 0, 0, 0, 0, 0, 60), stats(12, 12, 0, 0, 0, 0, 0, 80), stats(15, 15, 0, 0, 0, 0, 0, 100)),
    TITANIC(stats(10, 10, 0, 0, 0, 0, 0, 0), stats(15, 15, 0, 0, 0, 0, 0, 0), stats(20, 20, 0, 0, 0, 0, 0, 0), stats(25, 25, 0, 0, 0, 0, 0, 0), stats(35, 35, 0, 0, 0, 0, 0, 0)),
    WISE(stats(6, 0, 0, 0, 0, 1, 0, 25), stats(8, 0, 0, 0, 0, 1, 0, 50), stats(10, 0, 0, 0, 0, 1, 0, 75), stats(12, 0, 0, 0, 0, 2, 0, 100), stats(15, 0, 0, 0, 0, 2, 0, 125));

    private final ArmorReforgeStats tierD;
    private final ArmorReforgeStats tierC;
    private final ArmorReforgeStats tierB;
    private final ArmorReforgeStats tierA;
    private final ArmorReforgeStats tierS;

    ArmorReforgePrefix(ArmorReforgeStats tierD, ArmorReforgeStats tierC, ArmorReforgeStats tierB, ArmorReforgeStats tierA, ArmorReforgeStats tierS) {
        this.tierD = tierD;
        this.tierC = tierC;
        this.tierB = tierB;
        this.tierA = tierA;
        this.tierS = tierS;
    }

    private static ArmorReforgeStats stats(double health, double defense, double strength, double critChance,
                                            double critDamage, double agility, double attackSpeed, double intelligence) {
        return new ArmorReforgeStats(health, defense, strength, critChance, critDamage, agility, attackSpeed, intelligence);
    }

    public ArmorReforgeStats stats(ItemTier tier) {
        return switch (tier) {
            case D -> this.tierD;
            case C -> this.tierC;
            case B -> this.tierB;
            case A -> this.tierA;
            case S -> this.tierS;
            case E -> ArmorReforgeStats.NONE;
        };
    }

    /** "Clean" / "Fierce" / "Titanic"... - never translated, see the class doc. */
    public String displayWord() {
        String n = this.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }
}
