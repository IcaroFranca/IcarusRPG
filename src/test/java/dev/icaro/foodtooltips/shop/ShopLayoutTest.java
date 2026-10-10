package dev.icaro.foodtooltips.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ShopService#centeredSlots}: a shop's items sit in rows of 7, centered, inside the 4 middle rows - never on the header (row 0) or the Close/Back row (row 5). */
final class ShopLayoutTest {
    @Test
    void combatMerchantsElevenItemsFillOneFullRowAndACenteredSecond() {
        List<Integer> slots = ShopService.centeredSlots(11);

        assertEquals(11, slots.size());
        assertEquals(List.of(19, 20, 21, 22, 23, 24, 25, 29, 30, 31, 32), slots);
    }

    @Test
    void slotsAreUniqueAndStayInsideTheMiddleRows() {
        for (int count = 1; count <= 28; count++) {
            List<Integer> slots = ShopService.centeredSlots(count);
            assertEquals(count, slots.size());
            assertEquals(count, new HashSet<>(slots).size());
            for (int slot : slots) {
                assertTrue(slot >= 9 && slot < 45, "slot " + slot + " for " + count + " items");
                assertTrue(slot % 9 >= 1 && slot % 9 <= 7, "slot " + slot + " touches the border");
            }
        }
    }
}
