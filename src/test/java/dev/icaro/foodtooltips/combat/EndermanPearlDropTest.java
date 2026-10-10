package dev.icaro.foodtooltips.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * {@link CombatListener#rollEnderPearls}: an Enderman's pearls come from this vanilla-shaped
 * roll alone (0-1, plus 0-1 per Looting level), whatever loot table the server's datapacks load -
 * StellarityLite swaps the pearl for Chorus Fruit, which used to leave Endermen dropping nothing.
 */
final class EndermanPearlDropTest {
    private static final int ROLLS = 100_000;

    @Test
    void withoutLootingDropsZeroOrOnePearlHalfTheTime() {
        SplittableRandom random = new SplittableRandom(42);
        int total = 0;
        for (int i = 0; i < ROLLS; i++) {
            int pearls = CombatListener.rollEnderPearls(0, random);
            assertTrue(pearls == 0 || pearls == 1, "got " + pearls);
            total += pearls;
        }
        assertEquals(0.5, (double) total / ROLLS, 0.01);
    }

    @Test
    void eachLootingLevelAddsUpToOneMorePearl() {
        SplittableRandom random = new SplittableRandom(7);
        int total = 0;
        int max = 0;
        for (int i = 0; i < ROLLS; i++) {
            int pearls = CombatListener.rollEnderPearls(3, random);
            assertTrue(pearls >= 0 && pearls <= 4, "got " + pearls);
            total += pearls;
            max = Math.max(max, pearls);
        }
        assertEquals(2.0, (double) total / ROLLS, 0.02);
        assertEquals(4, max);
    }
}
