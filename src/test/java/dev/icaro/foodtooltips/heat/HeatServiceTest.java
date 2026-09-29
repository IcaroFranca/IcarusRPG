package dev.icaro.foodtooltips.heat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure tick-math coverage for {@link HeatService#tickIntervalSeconds}/{@link
 * HeatService#accumulate} - neither touches a Bukkit static or a live server (the packet-free
 * "logic" half, same "wiring vs. logic" split {@code creaking.CreakingSightService}'s own
 * {@code diff}/{@code trueSharedFlags} already draw, reused here for the Nether's own Heat
 * mechanic). The packet/scheduler-driving half ({@code tickAll}/{@code tickOne}/the sidebar)
 * is deliberately NOT unit-tested here for the same reason.
 */
final class HeatServiceTest {
    @Test
    void tickIntervalIsOneSecondWithNoResistance() {
        assertEquals(1.0, HeatService.tickIntervalSeconds(0));
    }

    /** {@code y = 1 * (1 + Heat Resistance)} - the player's own formula, exactly. */
    @Test
    void tickIntervalGrowsLinearlyWithResistance() {
        assertEquals(2.0, HeatService.tickIntervalSeconds(1));
        assertEquals(4.0, HeatService.tickIntervalSeconds(3));
        assertEquals(6.0, HeatService.tickIntervalSeconds(5));
    }

    /** A negative Heat Resistance (should never happen, but defensively) never speeds the interval up past the base 1 second. */
    @Test
    void negativeResistanceNeverSpeedsUpTheInterval() {
        assertEquals(1.0, HeatService.tickIntervalSeconds(-5));
    }

    @Test
    void oneSecondElapsedAtOneSecondIntervalGainsExactlyOneTick() {
        HeatService.TickResult result = HeatService.accumulate(0.0, 1.0, 1.0);
        assertEquals(1, result.gained());
        assertEquals(0.0, result.leftoverSeconds());
    }

    /** Heat Resistance stretches the interval to 4s - three 1-second passes gain nothing, only carrying the leftover forward, and the fourth finally completes the tick. */
    @Test
    void aFourSecondIntervalOnlyCompletesOnTheFourthPass() {
        double leftover = 0.0;
        for (int second = 1; second <= 3; second++) {
            HeatService.TickResult result = HeatService.accumulate(leftover, 1.0, 4.0);
            assertEquals(0, result.gained(), "pass " + second + " shouldn't complete a tick yet");
            leftover = result.leftoverSeconds();
        }
        HeatService.TickResult fourth = HeatService.accumulate(leftover, 1.0, 4.0);
        assertEquals(1, fourth.gained());
        assertEquals(0.0, fourth.leftoverSeconds());
    }

    /** More than one interval's worth of elapsed time in a single pass (e.g. after a lag spike) must credit every whole tick it covers, not just one. */
    @Test
    void multipleCompletedTicksInASinglePassAreAllCredited() {
        HeatService.TickResult result = HeatService.accumulate(0.0, 3.5, 1.0);
        assertEquals(3, result.gained());
        assertEquals(0.5, result.leftoverSeconds(), 1e-9);
    }
}
