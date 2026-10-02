package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for {@link TillingHoeService#radiusFor} - the Tilling Hoe's own
 * milestone-to-area ladder (3x3 at M1 through 9x9 at M7), per the player's own "uma enxada que
 * ara 3x3, 5x5, 7x7 e 9x9" spec. No Bukkit static touched, same "wiring vs logic" split
 * {@code enchant.AnvilMenuServiceTest}/{@code item.AnimalCrystalService} already established,
 * so this needs no live server.
 */
final class TillingHoeServiceTest {
    @Test
    void belowMilestoneThreeStaysAtThreeByThree() {
        assertEquals(1, TillingHoeService.radiusFor(0));
        assertEquals(1, TillingHoeService.radiusFor(1));
        assertEquals(1, TillingHoeService.radiusFor(2));
    }

    @Test
    void milestoneThreeGrowsToFiveByFive() {
        assertEquals(2, TillingHoeService.radiusFor(3));
        assertEquals(2, TillingHoeService.radiusFor(4));
    }

    @Test
    void milestoneFiveGrowsToSevenBySeven() {
        assertEquals(3, TillingHoeService.radiusFor(5));
        assertEquals(3, TillingHoeService.radiusFor(6));
    }

    @Test
    void milestoneSevenGrowsToNineByNine() {
        assertEquals(4, TillingHoeService.radiusFor(7));
    }

    @Test
    void furtherMilestonesStayAtNineByNine() {
        assertEquals(4, TillingHoeService.radiusFor(8));
        assertEquals(4, TillingHoeService.radiusFor(9));
        assertEquals(4, TillingHoeService.radiusFor(100));
    }
}
