package dev.icaro.foodtooltips.skills;

/**
 * The one XP-per-level curve every skill uses - {@link GeneralSkillService} (Mining,
 * Farming, Fishing, Foraging, Alchemy, Enchanting...) and {@link CombatSkillService}
 * alike, so Combat's own progression can't silently drift from the others again (it
 * used to run its own exponential formula, {@code xpBase * level^xpExponent + 50},
 * completely unrelated to this table).
 */
final class SkillXpCurve {
    /** XP required for levels 1-30, in order - see {@link #required}. */
    private static final double[] XP_REQUIRED_TABLE = {
            50, 125, 200, 300, 500, 750, 1_000, 1_500, 2_000, 3_500,
            5_000, 7_500, 10_000, 15_000, 20_000, 30_000, 50_000, 75_000, 100_000, 200_000,
            300_000, 400_000, 500_000, 600_000, 700_000, 800_000, 900_000, 1_000_000, 1_100_000, 1_200_000};
    /** Flat XP required for every level past {@link #XP_REQUIRED_TABLE}'s own range - see {@link #required}. */
    private static final double XP_REQUIRED_BEYOND_TABLE = 1_000_000;

    private SkillXpCurve() {
    }

    /**
     * Explicit per-level XP curve (not a formula) for levels 1-30 (see {@link
     * #XP_REQUIRED_TABLE}), given directly rather than computed - every level past
     * that is a flat {@link #XP_REQUIRED_BEYOND_TABLE}, per explicit confirmation
     * (level 30 is genuinely more expensive than every level past it - not a
     * mistake, the table is deliberately a one-time wall right before it flattens
     * out).
     */
    static double required(int level) {
        if (level >= 1 && level <= XP_REQUIRED_TABLE.length) {
            return XP_REQUIRED_TABLE[level - 1];
        }
        return XP_REQUIRED_BEYOND_TABLE;
    }
}
