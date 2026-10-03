package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.LevelColorTheme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;

public final class LevelBadgeRenderer {
    private final Map<Key, List<Component>> cache = new ConcurrentHashMap<Key, List<Component>>();
    /** Same per-frame colors {@link #build} turns into badge {@link Component}s, cached separately (keyed only by theme, not level) since the color sequence itself never depends on the level's digits - {@link #activeColor} reads this directly so the player's name can be tinted with the exact same color as whatever frame the badge is currently showing. */
    private final Map<String, List<TextColor>> colorCache = new ConcurrentHashMap<String, List<TextColor>>();
    private final int smoothness;

    public LevelBadgeRenderer() {
        this(1);
    }

    public LevelBadgeRenderer(int smoothness) {
        this.smoothness = Math.max(1, smoothness);
    }

    public Component frame(long level, LevelColorTheme theme, long tick) {
        List<Component> frames = this.frames(level, theme);
        return frames.get(theme.animated() ? Math.floorMod(tick, frames.size()) : 0);
    }

    public List<Component> frames(long level, LevelColorTheme theme) {
        return this.cache.computeIfAbsent(new Key(level, theme.id()), key -> this.build(level, theme));
    }

    /** The plain {@link TextColor} the badge is showing at {@code tick} - same color, same frame index, as {@link #frame} would render, just without the "[N] " wrapping - so a player's own name can be tinted to match their level color exactly, including mid-animation on a cycling theme. */
    public TextColor activeColor(LevelColorTheme theme, long tick) {
        List<TextColor> colors = this.colorFrames(theme);
        return colors.get(theme.animated() ? Math.floorMod(tick, colors.size()) : 0);
    }

    public void clear() {
        this.cache.clear();
        this.colorCache.clear();
    }

    /**
     * The whole "[N] " badge - brackets included - is always tinted with the theme's own
     * active color(s) (no separate fixed gray for the brackets) per the player's own explicit
     * "as cores dos niveis tem que aplicar... nos colchetes também". For a theme with more
     * than one color, this is a real spatial gradient across the badge's own characters (not
     * just the whole string pulsing through one color at a time) per the player's own
     * follow-up "quero os gradientes das cores que tem mais de uma cor" - see {@link
     * #gradientText}.
     */
    private List<Component> build(long level, LevelColorTheme theme) {
        String text = "[" + Long.toString(Math.max(0L, level)) + "] ";
        List<TextColor> colors = this.colorFrames(theme);
        ArrayList<Component> frames = new ArrayList<Component>();
        for (int tick = 0; tick < colors.size(); tick++) {
            frames.add(gradientText(text, colors, tick));
        }
        return List.copyOf(frames);
    }

    /**
     * Same gradient {@link #build} paints the badge with, for an arbitrary {@code text} (the
     * player's own name in chat/tab list/menu previews) instead of the "[N] " wrapping - so a
     * multi-color theme's name reads as a real left-to-right gradient too, not a single flat
     * color. Exposed (not cached per name, unlike {@link #frames}, since the set of possible
     * names is unbounded) for {@code GlobalPresentationService}/{@code LevelColorMenuService}
     * to call directly; cheap enough for an on-demand per-call build given names are only a
     * few characters long.
     */
    public Component gradientName(String name, LevelColorTheme theme, long tick) {
        List<TextColor> colors = this.colorFrames(theme);
        return gradientText(name, colors, theme.animated() ? Math.floorMod(tick, colors.size()) : 0);
    }

    /**
     * Spreads {@code colors} (one full gradient cycle) across {@code text}'s own characters,
     * each one sampling a different point along the cycle offset by its own position - {@code
     * tick} rotates the whole sampling window, so an animated theme's gradient also slides
     * along the text over time instead of sitting still. Degenerates to a single flat color
     * for a single-color theme (every position samples the same lone entry).
     */
    private static Component gradientText(String text, List<TextColor> colors, int tick) {
        int n = colors.size();
        int len = text.length();
        TextComponent.Builder builder = Component.text();
        for (int i = 0; i < len; i++) {
            int offset = len <= 1 || n <= 1 ? 0 : (int) Math.round((double) i * n / len);
            TextColor color = colors.get(Math.floorMod(tick + offset, n));
            builder.append(Component.text(String.valueOf(text.charAt(i)), color));
        }
        return builder.build();
    }

    private List<TextColor> colorFrames(LevelColorTheme theme) {
        return this.colorCache.computeIfAbsent(theme.id(), key -> this.buildColors(theme));
    }

    private List<TextColor> buildColors(LevelColorTheme theme) {
        int[] colors = theme.palette();
        if (!theme.animated()) {
            return List.of(TextColor.color((int)colors[0]));
        }
        ArrayList<Integer> unique = new ArrayList<Integer>(new LinkedHashSet<Integer>(Arrays.stream(colors).boxed().toList()));
        ArrayList<Integer> bounce = new ArrayList<Integer>(unique);
        for (int i = unique.size() - 2; i > 0; --i) {
            bounce.add((Integer)unique.get(i));
        }
        ArrayList<TextColor> result = new ArrayList<TextColor>();
        int steps = Math.max(1, theme.frameInterval() * this.smoothness);
        for (int i = 0; i < bounce.size(); ++i) {
            int from = (Integer)bounce.get(i);
            int to = (Integer)bounce.get((i + 1) % bounce.size());
            for (int step = 0; step < steps; ++step) {
                result.add(TextColor.color((int)this.interpolate(from, to, (double)step / (double)steps)));
            }
        }
        return List.copyOf(result);
    }

    private int interpolate(int from, int to, double ratio) {
        int r = (int)Math.round((double)(from >> 16 & 0xFF) * (1.0 - ratio) + (double)(to >> 16 & 0xFF) * ratio);
        int g = (int)Math.round((double)(from >> 8 & 0xFF) * (1.0 - ratio) + (double)(to >> 8 & 0xFF) * ratio);
        int b = (int)Math.round((double)(from & 0xFF) * (1.0 - ratio) + (double)(to & 0xFF) * ratio);
        return r << 16 | g << 8 | b;
    }

    private record Key(long level, String theme) {
    }
}

