package dev.icaro.foodtooltips.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CompactNumberTest {
    @Test
    void upToFiveDigitsStaysAsIs() {
        assertEquals("0", CompactNumber.format(0));
        assertEquals("4500", CompactNumber.format(4500));
        assertEquals("99999", CompactNumber.format(99_999));
    }

    @Test
    void sixDigitsBecomeK() {
        assertEquals("100K", CompactNumber.format(100_000));
        assertEquals("750K", CompactNumber.format(750_000));
        assertEquals("150K", CompactNumber.format(150_000), "End Enderman's level");
        assertEquals("749.3K", CompactNumber.format(749_312));
    }

    @Test
    void millionsAndBillions() {
        assertEquals("15M", CompactNumber.format(15_000_000), "Ender Dragon");
        assertEquals("3M", CompactNumber.format(3_000_000), "Ender Dragon's level");
        assertEquals("1.2M", CompactNumber.format(1_250_000));
        assertEquals("2B", CompactNumber.format(2_000_000_000L));
    }

    /** Never rounds up into the next step - a mob 1 HP short of full must not read as full. */
    @Test
    void alwaysRoundsDown() {
        assertEquals("999.9K", CompactNumber.format(999_999));
        assertEquals("14.9M", CompactNumber.format(14_999_999));
    }
}
