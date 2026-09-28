package dev.icaro.foodtooltips.util;

import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Centers a chat line the way Minecraft's own default font actually renders it - character
 * by character pixel width, not character count - since the font isn't monospaced (an "I"
 * and a "W" don't take the same space) and naive space-padding by string length drifts
 * further off-center the longer a line gets. Same well-known "DefaultFontInfo" per-character
 * width table every Bukkit/Spigot chat-centering plugin has used for years, with {@value
 * #CENTER_PX} as the assumed half-width (in pixels) of the default client's chat box.
 *
 * <p>Colors/click/hover events on {@code content} are untouched - only plain space
 * characters (uncolored) are prepended in front of it, measured off {@link
 * PlainTextComponentSerializer}'s own stripped-down text (so color codes themselves never
 * count towards the width). Bold text renders about 1px wider per character in the real
 * font, which this doesn't account for - none of this plugin's own centered messages use
 * bold, so it isn't worth the extra complexity of tracking it through a {@code Component}
 * tree until something actually needs it.
 */
public final class ChatCenter {
    /** Half the assumed pixel width of the default client's chat box - the other half is implicit (whatever's left after the padding this computes). */
    private static final int CENTER_PX = 154;
    /** Pixel width of a plain space glyph in the default font, plus the 1px gap Minecraft renders between every character. */
    private static final int SPACE_ADVANCE = 4;
    /** Fallback for any character (accented letters, the plugin's own ✦/♦/❤/→/• icons...) not in {@link #WIDTHS} - close to a typical letter's own width in the default font. */
    private static final int DEFAULT_WIDTH = 6;

    private static final Map<Character, Integer> WIDTHS = new HashMap<>();

    static {
        put(3, ' ');
        put(1, '.', ',', ':', ';', '\'', '|', 'i', 'l', '!');
        put(2, '`', '[', ']', 't');
        put(3, '"', '(', ')', '{', '}', 'k', 'I');
        put(4, '<', '>', '*', '~', 'f');
        put(5, "abcdeghnopqrsuvxyzABCDEFGHJKLMNOPQRSTUVWXYZ0123456789-_=+?/\\%$#^&");
        put(6, '@', 'w', 'm');
        for (char c = 'a'; c <= 'z'; c++) {
            WIDTHS.putIfAbsent(c, 5);
        }
    }

    private static void put(int width, char... chars) {
        for (char c : chars) {
            WIDTHS.put(c, width);
        }
    }

    private static void put(int width, String chars) {
        put(width, chars.toCharArray());
    }

    private ChatCenter() {
    }

    /** {@code content}, prefixed with just enough plain spaces to render centered in the default client's chat box. Falls back to returning {@code content} unchanged if it's already wide enough that no padding would fit. */
    public static Component center(Component content) {
        String plain = PlainTextComponentSerializer.plainText().serialize(content);
        int width = pixelWidth(plain);
        int toCompensate = CENTER_PX - width / 2;
        int spaces = toCompensate / SPACE_ADVANCE;
        if (spaces <= 0) {
            return content;
        }
        return Component.text(" ".repeat(spaces)).append(content);
    }

    private static int pixelWidth(String text) {
        int size = 0;
        for (int i = 0; i < text.length(); i++) {
            size += WIDTHS.getOrDefault(text.charAt(i), DEFAULT_WIDTH) + 1;
        }
        return size;
    }
}
