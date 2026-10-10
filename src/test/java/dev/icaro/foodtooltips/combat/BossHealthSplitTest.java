package dev.icaro.foodtooltips.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link MobDifficultyService#proportionalShare}: a Boss (Ender Dragon, Wither) with a bonus HP
 * pool keeps its real health - the only thing its vanilla boss bar reads - the same fraction of
 * its real max as its true total is of the total max, so the bar drops smoothly through a 15M HP
 * fight instead of sitting at 100% until 14,999,000 pool HP are gone.
 */
final class BossHealthSplitTest {
    private static final double REAL_MAX = 1000.0;
    private static final double POOL_MAX = 15_000_000.0 - REAL_MAX;

    @Test
    void halfTheTotalGoneMeansHalfTheBossBarGone() {
        double[] split = MobDifficultyService.proportionalShare(REAL_MAX, REAL_MAX, POOL_MAX, POOL_MAX, 7_500_000.0);

        double newReal = REAL_MAX - split[0];
        assertEquals(500.0, newReal, 1e-6);
        assertEquals(7_500_000.0, newReal + split[1], 1e-3);
    }

    @Test
    void aSingleHitMovesTheBarByItsShareOfTheTotal() {
        double[] split = MobDifficultyService.proportionalShare(REAL_MAX, REAL_MAX, POOL_MAX, POOL_MAX, 2200.0);

        assertEquals(2200.0 * REAL_MAX / 15_000_000.0, split[0], 1e-9);
        assertEquals(15_000_000.0 - 2200.0, (REAL_MAX - split[0]) + split[1], 1e-3);
    }

    @Test
    void killingBlowEmptiesBoth() {
        double[] split = MobDifficultyService.proportionalShare(10.0, REAL_MAX, 100.0, POOL_MAX, 1_000.0);

        assertEquals(10.0, split[0], 1e-9);
        assertEquals(0.0, split[1], 1e-9);
    }
}
