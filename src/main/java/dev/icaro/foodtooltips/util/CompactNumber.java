package dev.icaro.foodtooltips.util;

/**
 * Short form for big numbers shown in-world (mob name tags, floating damage numbers): anything
 * with 6+ digits becomes K/M/B with at most one decimal - 750000 -> "750K", 749312 -> "749.3K",
 * 15000000 -> "15M" (the player's own spec: "números com 6 dígitos ... 750K ao invés de 750000").
 * Below 100000 the number is left as-is, since 5 digits still fit fine. Always rounds DOWN, so a
 * value just under a whole step never reads as the step itself (999999 is "999.9K", not "1000K",
 * and a mob 1 HP short of full never shows as full).
 */
public final class CompactNumber {
    private static final long COMPACT_FROM = 100_000L;

    private CompactNumber() {
    }

    public static String format(long value) {
        if (value < 0) {
            return "-" + format(-value);
        }
        if (value < COMPACT_FROM) {
            return Long.toString(value);
        }
        if (value >= 1_000_000_000L) {
            return scaled(value, 1_000_000_000L, "B");
        }
        if (value >= 1_000_000L) {
            return scaled(value, 1_000_000L, "M");
        }
        return scaled(value, 1_000L, "K");
    }

    /** {@code value / unit}, floored to one decimal, ".0" dropped. */
    private static String scaled(long value, long unit, String suffix) {
        long tenths = value * 10L / unit;
        long whole = tenths / 10L;
        long decimal = tenths % 10L;
        return (decimal == 0L ? Long.toString(whole) : whole + "." + decimal) + suffix;
    }
}
