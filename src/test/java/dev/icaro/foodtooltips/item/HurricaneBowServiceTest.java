package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for {@link HurricaneBowService#arrowCountFor} - Tempest's own
 * kill-to-arrow-count ladder (1 arrow at 0 kills through 5 at {@value
 * HurricaneBowService#KILL_THRESHOLDS}[4] kills), per the player's own kill thresholds
 * (20/75/150/250). No Bukkit static touched, same "wiring vs logic" split {@code
 * TillingHoeServiceTest} already established, so this needs no live server.
 */
final class HurricaneBowServiceTest {
    @Test
    void belowTwentyKillsStaysAtOneArrow() {
        assertEquals(1, HurricaneBowService.arrowCountFor(0));
        assertEquals(1, HurricaneBowService.arrowCountFor(19));
    }

    @Test
    void twentyKillsGrowsToTwoArrows() {
        assertEquals(2, HurricaneBowService.arrowCountFor(20));
        assertEquals(2, HurricaneBowService.arrowCountFor(74));
    }

    @Test
    void seventyFiveKillsGrowsToThreeArrows() {
        assertEquals(3, HurricaneBowService.arrowCountFor(75));
        assertEquals(3, HurricaneBowService.arrowCountFor(149));
    }

    @Test
    void oneHundredFiftyKillsGrowsToFourArrows() {
        assertEquals(4, HurricaneBowService.arrowCountFor(150));
        assertEquals(4, HurricaneBowService.arrowCountFor(249));
    }

    @Test
    void twoHundredFiftyKillsReachesFullPotential() {
        assertEquals(5, HurricaneBowService.arrowCountFor(250));
        assertEquals(5, HurricaneBowService.arrowCountFor(1000));
    }
}
