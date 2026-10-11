package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LavaFishingServiceTest {
    @Test
    void infernoChanceGrowsWithLuckOfTheSea() {
        assertEquals(1.0, LavaFishingService.infernoChancePercent(0));
        assertEquals(1.5, LavaFishingService.infernoChancePercent(1));
        assertEquals(3.5, LavaFishingService.infernoChancePercent(5));
    }

    @Test
    void lureShortensTheLavaWait() {
        assertEquals(600, LavaFishingService.lavaWaitTicks(600, 0, 0));
        assertEquals(450, LavaFishingService.lavaWaitTicks(600, 5, 0));
        assertEquals(500, LavaFishingService.lavaWaitTicks(600, 0, 1));
        assertEquals(20, LavaFishingService.lavaWaitTicks(100, 0, 3));
    }
}
