package dev.icaro.foodtooltips.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

/**
 * Plain data test - {@link Component} is pure Java (no Bukkit registry involved), same
 * reasoning {@code reforge.BowReforgePrefixTest} gives for why this needs no live server.
 */
final class ChatCenterTest {
    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static int leadingSpaces(Component c) {
        String s = plain(c);
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    @Test
    void padsAShortLineWithLeadingSpaces() {
        Component line = Component.text("Hi", NamedTextColor.GOLD);
        Component centered = ChatCenter.center(line);
        assertTrue(leadingSpaces(centered) > 0, "a short line should gain leading padding");
        assertTrue(plain(centered).endsWith("Hi"));
    }

    @Test
    void aLongerLineGetsLessPaddingThanAShorterOne() {
        Component shortLine = Component.text("Hi");
        Component longLine = Component.text("A much, much longer line of text than the short one");
        int shortPadding = leadingSpaces(ChatCenter.center(shortLine));
        int longPadding = leadingSpaces(ChatCenter.center(longLine));
        assertTrue(longPadding < shortPadding, "a wider line needs less padding to center");
    }

    @Test
    void aLineAlreadyWiderThanTheChatBoxIsReturnedUnchanged() {
        Component huge = Component.text("X".repeat(200));
        Component centered = ChatCenter.center(huge);
        assertEquals(0, leadingSpaces(centered));
        assertEquals(huge, centered);
    }

    @Test
    void colorAndStructureSurviveCentering() {
        Component line = Component.text("Reward", NamedTextColor.GREEN);
        Component centered = ChatCenter.center(line);
        assertTrue(centered.children().contains(line) || centered.equals(line),
                "the original component must still be reachable (as itself or as a child) after centering");
    }
}
