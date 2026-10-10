package dev.icaro.foodtooltips.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link CoinRewardService#coinsFor}: a kill pays the mob's own Blood Points value, scaled by {@code economy.coins-per-blood-point}. */
final class CoinRewardServiceTest {
    @Test
    void defaultRatePaysTheBloodPointsValue() {
        assertEquals(5L, CoinRewardService.coinsFor(5L, 1.0), "Zombie");
        assertEquals(24L, CoinRewardService.coinsFor(24L, 1.0), "Zombie Miner");
    }

    @Test
    void rateScalesAndRoundsToWholeCoins() {
        assertEquals(10L, CoinRewardService.coinsFor(5L, 2.0));
        assertEquals(3L, CoinRewardService.coinsFor(5L, 0.5));
    }

    @Test
    void nothingForWorthlessKillsOrAZeroRate() {
        assertEquals(0L, CoinRewardService.coinsFor(0L, 1.0));
        assertEquals(0L, CoinRewardService.coinsFor(24L, 0.0));
    }
}
