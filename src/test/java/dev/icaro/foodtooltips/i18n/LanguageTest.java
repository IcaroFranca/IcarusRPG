package dev.icaro.foodtooltips.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LanguageTest {
    @Test
    void serverAlwaysUsesEnglish() {
        assertEquals(Language.EN, Language.of(null));
        assertEquals("English", Language.of(null).choose("Português", "English"));
    }
}
