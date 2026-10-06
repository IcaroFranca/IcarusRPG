package dev.icaro.foodtooltips.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure-math coverage for {@link MagicalPowerService#statsMultiplier} - the player's own exact
 * "Stats Multiplier = 29.97 x (ln(0.0019 x Magical Power + 1))^1.2" formula, same "logic vs.
 * wiring" split {@code heat.HeatServiceTest} already uses (the PDC-backed selection/attribute
 * half isn't covered here for the same reason {@code global.LevelColorService} itself has no
 * dedicated test either).
 */
final class MagicalPowerServiceTest {
    @Test
    void zeroMagicalPowerGivesZeroMultiplier() {
        assertEquals(0.0, MagicalPowerService.statsMultiplier(0.0), 1e-9);
    }

    /** The reference image's own worked example: at 10 Magical Power, the header states the multiplier rounds to 0.25. */
    @Test
    void tenMagicalPowerMatchesTheReferenceImagesWorkedExample() {
        assertEquals(0.25, MagicalPowerService.statsMultiplier(10.0), 0.01);
    }

    @Test
    void multiplierGrowsWithMoreMagicalPower() {
        double low = MagicalPowerService.statsMultiplier(10.0);
        double high = MagicalPowerService.statsMultiplier(100.0);
        assertTrue(high > low);
    }

    @Test
    void multiplierNeverNegative() {
        assertTrue(MagicalPowerService.statsMultiplier(0.0) >= 0.0);
        assertTrue(MagicalPowerService.statsMultiplier(1.0) >= 0.0);
    }
}
