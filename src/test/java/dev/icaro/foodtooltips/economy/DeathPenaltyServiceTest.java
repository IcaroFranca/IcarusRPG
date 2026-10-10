package dev.icaro.foodtooltips.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link DeathPenaltyService#moneyLost}: dying takes half the balance (by default), rounded down to whole coins. */
final class DeathPenaltyServiceTest {
    @Test
    void halfOfTheBalance() {
        assertEquals(500L, DeathPenaltyService.moneyLost(1000.0, 50.0));
        assertEquals(2L, DeathPenaltyService.moneyLost(5.0, 50.0), "rounds down");
    }

    @Test
    void nothingToLose() {
        assertEquals(0L, DeathPenaltyService.moneyLost(0.0, 50.0));
        assertEquals(0L, DeathPenaltyService.moneyLost(1.0, 50.0), "half a coin rounds down to nothing");
        assertEquals(0L, DeathPenaltyService.moneyLost(1000.0, 0.0));
    }
}
