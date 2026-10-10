package dev.icaro.foodtooltips.util;

/** Durations shown to players as {@code dd:hh:mm:ss} (the player's own spec for the Mushroom Soup flight timer) - always all four fields, two digits each (days can grow past 99). */
public final class DurationFormat {
    private DurationFormat() {
    }

    public static String ddhhmmss(long totalSeconds) {
        long s = Math.max(0L, totalSeconds);
        long days = s / 86_400L;
        long hours = s % 86_400L / 3_600L;
        long minutes = s % 3_600L / 60L;
        long seconds = s % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d:%02d", days, hours, minutes, seconds);
    }
}
