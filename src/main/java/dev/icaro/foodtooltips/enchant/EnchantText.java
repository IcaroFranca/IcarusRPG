package dev.icaro.foodtooltips.enchant;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Small colored-word-wrap builder shared by {@link VanillaEnchantEntry} and {@link
 * IcarusEnchant} for their descriptions: a description is a list of {@link Token}s
 * (plain gray narrative text, free to wrap at spaces, or an "atomic" colored word/
 * phrase - a value+unit, a named creature type - that never splits across a line),
 * packed into lore lines no wider than {@link #WRAP_WIDTH} characters.
 */
final class EnchantText {
    /** Bright green for a description's numeric value AND its unit (%, s, blocks...) together. */
    static final TextColor VALUE_COLOR = TextColor.color(0x30F04E);
    /** Roughly how many characters fit one lore line before it reads too wide - not pixel-exact, just a practical wrap point. */
    private static final int WRAP_WIDTH = 40;

    private EnchantText() {
    }

    /** One word/phrase with a color, either free to wrap internally at spaces ({@link #plain}) or kept as one unbreakable unit ({@link #colored}, for a value+unit or a named type). */
    record Token(String text, TextColor color, boolean atomic) {
        static Token plain(String s) {
            return new Token(s, NamedTextColor.GRAY, false);
        }

        static Token colored(String s, TextColor color) {
            return new Token(s, color, true);
        }

        /** The colored value token: the literal "X" placeholder plus {@code unit} when {@code level} is null (generic view), or the real computed number plus {@code unit} (resolved view). */
        static Token value(Integer level, String unit, java.util.function.IntUnaryOperator formula) {
            String number = level == null ? "X" : String.valueOf(formula.applyAsInt(level));
            return colored(number + unit, VALUE_COLOR);
        }
    }

    /**
     * "per level"/"por nível" for the generic ("X") view, or an empty (contributes
     * nothing to the wrapped text - see {@link #wrap}) token once resolved to a
     * specific level - a resolved number already reflects that level's total, so
     * restating "per level" next to it would be redundant/misleading (e.g. "Grants
     * +45 Respiration per level" for a single applied level of Respiration III).
     */
    static Token perLevel(Integer level, boolean pt) {
        return Token.plain(level == null ? (pt ? "por nível" : "per level") : "");
    }

    /** Packs {@code tokens} into lore lines no wider than {@link #WRAP_WIDTH} characters, breaking only between words (atomic tokens never split) and never leaving a leading space before punctuation-only words. */
    static List<Component> wrap(List<Token> tokens) {
        List<Token> words = new ArrayList<>();
        for (Token t : tokens) {
            if (t.atomic()) {
                words.add(t);
                continue;
            }
            for (String part : t.text().split(" ")) {
                if (!part.isEmpty()) {
                    words.add(new Token(part, t.color(), false));
                }
            }
        }
        List<Component> lines = new ArrayList<>();
        Component current = null;
        int currentLen = 0;
        for (Token w : words) {
            boolean glue = current == null || isPunctuationOnly(w.text());
            if (!glue && currentLen + 1 + w.text().length() > WRAP_WIDTH) {
                lines.add(current);
                current = null;
                currentLen = 0;
                glue = true;
            }
            String prefix = glue ? "" : " ";
            Component piece = Component.text(prefix + w.text(), w.color()).decoration(TextDecoration.ITALIC, false);
            current = current == null ? piece : current.append(piece);
            currentLen += prefix.length() + w.text().length();
        }
        if (current != null) {
            lines.add(current);
        }
        return lines;
    }

    private static boolean isPunctuationOnly(String s) {
        return s.equals(".") || s.equals(",") || s.equals(":");
    }
}
