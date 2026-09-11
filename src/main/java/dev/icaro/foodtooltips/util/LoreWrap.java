package dev.icaro.foodtooltips.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared lore line-wrapping for every inventory menu screen. Minecraft item lore never
 * auto-wraps within a single line - if a line's text is built from dynamic data (which
 * items an enchant applies to, which bonus sources currently feed a stat...) instead of
 * a fixed short literal, it can grow past what's comfortable and stretch the tooltip
 * wide instead of wrapping, unless the class building it wraps it itself. This is the
 * one shared implementation every menu class should call for that, instead of each
 * duplicating its own copy (unlike the small per-class item()/text() view builders,
 * which stay duplicated by convention - this is real algorithmic logic, not glue).
 *
 * Two flavors:
 * - {@link #wrapText}: plain sentence, breaks on spaces, never mid-word.
 * - {@link #wrapList}/{@link #wrapItems}: a list of short atomic pieces joined by a
 *   separator, never splits a single piece across lines.
 *
 * Pure String in/out - no Component/color dependency - so any class can wrap first and
 * then turn each returned line into a Component with its own text()/color helper.
 */
public final class LoreWrap {
    /** Default max characters per lore line - past this a line reads fine as text but stretches the tooltip uncomfortably wide. */
    public static final int DEFAULT_WIDTH = 40;

    private LoreWrap() {
    }

    /** Greedily word-wraps a plain sentence into lines no wider than {@code maxWidth}, breaking only on spaces. */
    public static List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * Greedily packs a comma-separated item list into lines no wider than {@code maxWidth},
     * never splitting a single item across two lines. The first line carries {@code prefix}
     * (e.g. "Applies to: "); wrapped continuation lines are indented to line up under it.
     */
    public static List<String> wrapList(String prefix, List<String> items, int maxWidth) {
        return wrapItems(prefix, items, ", ", maxWidth);
    }

    /** Same as {@link #wrapList}, but with a caller-chosen separator (e.g. " + " for a stat-source join) instead of ", ". */
    public static List<String> wrapItems(String prefix, List<String> items, String separator, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (items.isEmpty()) {
            lines.add(prefix + "-");
            return lines;
        }
        String indent = " ".repeat(prefix.length());
        StringBuilder line = new StringBuilder(prefix);
        boolean lineHasItem = false;
        for (String item : items) {
            String candidate = lineHasItem ? line + separator + item : line.toString() + item;
            if (lineHasItem && candidate.length() > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(indent).append(item);
            } else {
                line = new StringBuilder(candidate);
            }
            lineHasItem = true;
        }
        lines.add(line.toString());
        return lines;
    }
}
