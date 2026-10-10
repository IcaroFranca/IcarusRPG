package dev.icaro.foodtooltips.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class DurationFormatTest {
    @Test
    void alwaysShowsAllFourFields() {
        assertEquals("00:00:00:00", DurationFormat.ddhhmmss(0));
        assertEquals("00:00:00:30", DurationFormat.ddhhmmss(30));
        assertEquals("00:00:05:00", DurationFormat.ddhhmmss(300));
        assertEquals("00:01:01:01", DurationFormat.ddhhmmss(3661));
        assertEquals("02:03:04:05", DurationFormat.ddhhmmss(2 * 86_400 + 3 * 3_600 + 4 * 60 + 5));
    }

    @Test
    void negativeReadsAsZero() {
        assertEquals("00:00:00:00", DurationFormat.ddhhmmss(-5));
    }
}
