package dev.icaro.foodtooltips.food;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class FoodTooltipServiceTest {
    @Test
    void hungerUsesFullAndHalfIcons() {
        assertEquals("", FoodTooltipService.hungerIcons(0));
        assertEquals(String.valueOf(FoodTooltipService.HUNGER_HALF), FoodTooltipService.hungerIcons(1));
        assertEquals(String.valueOf(FoodTooltipService.HUNGER_FULL).repeat(2), FoodTooltipService.hungerIcons(4));
        assertEquals(String.valueOf(FoodTooltipService.HUNGER_FULL).repeat(2)
                + FoodTooltipService.HUNGER_HALF, FoodTooltipService.hungerIcons(5));
    }

    @Test
    void saturationMatchesAppleSkinQuarterSprites() {
        assertEquals("", FoodTooltipService.saturationIcons(0.0));
        assertEquals(String.valueOf(FoodTooltipService.SATURATION_25), FoodTooltipService.saturationIcons(0.4));
        assertEquals(String.valueOf(FoodTooltipService.SATURATION_FULL)
                + FoodTooltipService.SATURATION_25, FoodTooltipService.saturationIcons(2.4));
        assertEquals(String.valueOf(FoodTooltipService.SATURATION_FULL)
                + FoodTooltipService.SATURATION_50, FoodTooltipService.saturationIcons(3.0));
        assertEquals(String.valueOf(FoodTooltipService.SATURATION_FULL)
                + FoodTooltipService.SATURATION_75, FoodTooltipService.saturationIcons(3.2));
        assertEquals(String.valueOf(FoodTooltipService.SATURATION_FULL)
                + FoodTooltipService.SATURATION_75, FoodTooltipService.saturationIcons(3.99));
    }
}
