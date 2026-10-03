package dev.icaro.foodtooltips.global;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Pure coverage for {@link LevelBadgeRenderer}'s own spatial gradient - per the player's own
 * explicit "quero os gradientes das cores que tem mais de uma cor", a theme with more than one
 * palette color must paint each character of the badge/name differently (a real left-to-right
 * gradient), not the whole string pulsing through a single shared color over time. No live
 * Bukkit server touched - {@link Material}/Adventure {@link Component} both work standalone,
 * same "wiring vs logic" split {@code HurricaneBowServiceTest} already established.
 */
final class LevelBadgeRendererTest {
    private static final LevelColorTheme SINGLE = new LevelColorTheme("solo", "Solo", 1, Material.WHITE_DYE, 0, new int[]{0xFFFFFF});
    private static final LevelColorTheme DUAL = new LevelColorTheme("dual", "Dual", 1, Material.RED_DYE, 0, new int[]{0xFF0000, 0x0000FF});

    private static TextColor colorAt(Component component, int index) {
        return ((TextComponent) component.children().get(index)).color();
    }

    @Test
    void singleColorThemeStaysFlatAcrossTheWholeName() {
        LevelBadgeRenderer renderer = new LevelBadgeRenderer();
        Component name = renderer.gradientName("AB", SINGLE, 0L);
        assertEquals(colorAt(name, 0), colorAt(name, 1));
    }

    @Test
    void multiColorThemePaintsDifferentCharactersDifferently() {
        LevelBadgeRenderer renderer = new LevelBadgeRenderer();
        Component name = renderer.gradientName("AB", DUAL, 0L);
        assertNotEquals(colorAt(name, 0), colorAt(name, 1));
    }

    @Test
    void multiColorThemeGradientSlidesOverTime() {
        LevelBadgeRenderer renderer = new LevelBadgeRenderer();
        Component atZero = renderer.gradientName("A", DUAL, 0L);
        Component later = renderer.gradientName("A", DUAL, 7L);
        assertNotEquals(colorAt(atZero, 0), colorAt(later, 0));
    }

    @Test
    void badgeBracketsShareTheSameGradientAsTheDigits() {
        LevelBadgeRenderer renderer = new LevelBadgeRenderer();
        Component badge = renderer.frame(18L, SINGLE, 0L);
        // "[18] " - the opening bracket (index 0) and the first digit (index 1) must match
        // for a solid theme, proving the bracket is no longer a separate fixed gray.
        assertEquals(colorAt(badge, 0), colorAt(badge, 1));
    }
}
