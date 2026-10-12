package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.icaro.foodtooltips.crafting.SlotAmounts;
import org.bukkit.NamespacedKey;
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
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 6, 10, 11, 12, 13, 14, 15}, CobblestoneCollectionService.menuSlots(12));
    }

    private static ItemStack stack(int amount) {
        ItemStack item = Mockito.mock(ItemStack.class);
        Mockito.when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @Test
    void ringNeedsTheAmountInEverySurroundingSlot() {
        NamespacedKey key = new NamespacedKey("test", "ring");
        SlotAmounts.register(key, i -> i == 4 ? 1 : 2);
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = stack(i == 4 ? 1 : 2);
        }
        assertTrue(SlotAmounts.satisfied(key, matrix));
        matrix[7] = stack(1);
        assertFalse(SlotAmounts.satisfied(key, matrix));
        assertEquals(1, SlotAmounts.amount(new NamespacedKey("test", "plain"), 3));
        assertEquals(2, SlotAmounts.amount(key, 3));
        assertEquals(1, SlotAmounts.amount(key, 4));
    }
}
