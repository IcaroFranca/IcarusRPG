package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The Personal Compactor's menu layout and its "N per slot" upgrade recipes - see {@link CobblestoneCollectionService}. */
final class PersonalCompactorTest {

    @Test
    void menuSlotsAreCenteredInTheRow() {
        assertArrayEquals(new int[]{4}, CobblestoneCollectionService.menuSlots(1));
        assertArrayEquals(new int[]{3, 4, 5}, CobblestoneCollectionService.menuSlots(3));
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 7}, CobblestoneCollectionService.menuSlots(7));
    }

    private static ItemStack stack(int amount) {
        ItemStack item = Mockito.mock(ItemStack.class);
        Mockito.when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @Test
    void ringNeedsTheAmountInEverySurroundingSlot() {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = stack(i == 4 ? 1 : 2);
        }
        assertTrue(CobblestoneCollectionService.hasRing(matrix, 2));
        assertFalse(CobblestoneCollectionService.hasRing(matrix, 4));
        matrix[7] = stack(1);
        assertFalse(CobblestoneCollectionService.hasRing(matrix, 2));
        matrix[7] = null;
        assertFalse(CobblestoneCollectionService.hasRing(matrix, 2));
    }
}
