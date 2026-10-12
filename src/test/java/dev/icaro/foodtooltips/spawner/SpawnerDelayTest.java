package dev.icaro.foodtooltips.spawner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.CreatureSpawner;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** {@link SpawnerUpgradeService#apply}'s spawn delay math - both delays bottom out at {@value SpawnerUpgradeService#MIN_DELAY_FLOOR}. */
final class SpawnerDelayTest {
    private static int[] delays(int sugar, int clock) {
        CreatureSpawner state = Mockito.mock(CreatureSpawner.class);
        SpawnerData data = new SpawnerData();
        data.base = new int[]{200, 800, 4, 6, 16, 4};
        data.set(SpawnerModifier.SUGAR, sugar);
        data.set(SpawnerModifier.CLOCK, clock);
        SpawnerUpgradeService.apply(state, data);
        var min = org.mockito.ArgumentCaptor.forClass(Integer.class);
        var max = org.mockito.ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(state).setMinSpawnDelay(min.capture());
        Mockito.verify(state).setMaxSpawnDelay(max.capture());
        return new int[]{min.getValue(), max.getValue()};
    }

    @Test
    void fullStacksReachForty() {
        assertEquals(40, delays(32, 38)[0]);
        assertEquals(40, delays(32, 38)[1]);
    }

    @Test
    void perItemSteps() {
        assertEquals(150, delays(10, 0)[0]);
        assertEquals(600, delays(0, 10)[1]);
    }

    @Test
    void maxNeverBelowMin() {
        // 38 Clocks alone would make it 40, but the max can't go under the untouched 200 min.
        assertEquals(200, delays(0, 38)[1]);
    }
}
