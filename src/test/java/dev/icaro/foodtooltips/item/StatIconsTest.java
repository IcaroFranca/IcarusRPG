package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

final class StatIconsTest {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void replacesLegacySymbolWithoutLosingValue() {
        assertEquals(StatIcons.HEALTH + " Vida: +120",
                PLAIN.serialize(StatIcons.text("❤ Vida: +120", NamedTextColor.RED)));
    }

    @Test
    void recognizesValueBeforeStatName() {
        assertEquals(StatIcons.INTELLIGENCE + " +25 Inteligência",
                PLAIN.serialize(StatIcons.text("+25 Inteligência", NamedTextColor.AQUA)));
    }

    @Test
    void leavesDescriptionsUntouched() {
        Component description = Component.text("Aumenta a Vida do jogador.", NamedTextColor.GRAY);
        assertEquals(description, StatIcons.decorate(description));
    }

    @Test
    void decorationIsIdempotent() {
        Component once = StatIcons.text("Mining Speed: +40", NamedTextColor.YELLOW);
        assertEquals(once, StatIcons.decorate(once));
    }
}
