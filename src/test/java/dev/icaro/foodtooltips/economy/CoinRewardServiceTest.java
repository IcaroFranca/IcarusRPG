package dev.icaro.foodtooltips.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** {@link CoinRewardService#coinsFor}: a kill pays by the mob's total Max Health, per the player's own anchors (common 1, Miner 4, Nether 20). */
final class CoinRewardServiceTest {
    private static final NavigableMap<Double, Long> TABLE = CoinRewardService.defaultBrackets();

    @Test
    void playersOwnAnchors() {
        assertEquals(1L, CoinRewardService.coinsFor(106.0, TABLE), "surface Zombie");
        assertEquals(1L, CoinRewardService.coinsFor(183.0, TABLE), "deepest Overworld Zombie");
        assertEquals(4L, CoinRewardService.coinsFor(300.0, TABLE), "Zombie/Skeleton Miner");
        assertEquals(20L, CoinRewardService.coinsFor(4500.0, TABLE), "any Nether mob");
        assertEquals(400L, CoinRewardService.coinsFor(750_000.0, TABLE), "End Enderman");
    }

    @Test
    void eachThresholdStartsItsOwnBracket() {
        assertEquals(1L, CoinRewardService.coinsFor(249.9, TABLE));
        assertEquals(4L, CoinRewardService.coinsFor(250.0, TABLE));
        assertEquals(10L, CoinRewardService.coinsFor(2499.0, TABLE));
        assertEquals(20L, CoinRewardService.coinsFor(2500.0, TABLE));
    }

    /** Scavenger: 0.3/0.6/0.9/1.2/1.5 coins per mob level (mob level = HP / 5), the player's own spec - no cap. */
    @Test
    void scavengerPaysPerMobLevel() {
        assertEquals(0.3, dev.icaro.foodtooltips.enchant.IcarusEnchant.scavengerCoinsPerMobLevel(1), 1e-9);
        assertEquals(1.5, dev.icaro.foodtooltips.enchant.IcarusEnchant.scavengerCoinsPerMobLevel(5), 1e-9);
        assertEquals(6L, CoinRewardService.scavengerBonus(0.3, 21), "Scavenger I on a level 21 Zombie");
        assertEquals(32L, CoinRewardService.scavengerBonus(1.5, 21), "Scavenger V on a level 21 Zombie");
        assertEquals(1350L, CoinRewardService.scavengerBonus(1.5, 900), "Scavenger V on a level 900 Nether mob");
        assertEquals(0L, CoinRewardService.scavengerBonus(1.5, 0));
    }

    @Test
    void belowTheLowestThresholdPaysNothing() {
        NavigableMap<Double, Long> custom = new TreeMap<>();
        custom.put(100.0, 2L);

        assertEquals(0L, CoinRewardService.coinsFor(50.0, custom));
        assertEquals(2L, CoinRewardService.coinsFor(100.0, custom));
    }
}
