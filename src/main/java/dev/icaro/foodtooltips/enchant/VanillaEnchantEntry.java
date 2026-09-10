package dev.icaro.foodtooltips.enchant;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;

/**
 * Wraps a real vanilla {@link Enchantment} as an {@link EnchantEntry}, so the
 * reworked Enchanting Table screen can offer every vanilla enchantment
 * (Sharpness, Protection, Unbreaking, ...) explicitly alongside the plugin's own -
 * see {@code EnchantService#allEntries}. Names come straight from Minecraft's own
 * translations (Paper resolves {@link Enchantment#displayName} server-side, so this
 * works the same regardless of client locale) rather than a hand-maintained PT/EN
 * list; descriptions below are hand-written since Bukkit has no API for those.
 */
final class VanillaEnchantEntry implements EnchantEntry {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    /** Bright green for a description's numeric value AND its unit (%, s, blocks...) together. */
    private static final TextColor VALUE_COLOR = TextColor.color(0x30F04E);
    private static final TextColor ARTHROPOD_COLOR = TextColor.color(0x940204);
    private static final TextColor UNDEAD_COLOR = TextColor.color(0x00AA00);
    private static final TextColor SKELETAL_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor WITHER_COLOR = TextColor.color(0x555555);
    /** Roughly how many characters fit one lore line before it reads too wide - not pixel-exact, just a practical wrap point. */
    private static final int WRAP_WIDTH = 40;
    /**
     * Flat XP-level cost per level, matching {@link IcarusEnchant}'s own
     * "costPerLevel * level" shape - Bukkit's {@link Enchantment} doesn't carry a
     * per-enchant cost of its own, so every vanilla entry shares this one default.
     * Deliberately cheap relative to the custom enchants (3-5): there are far more
     * vanilla entries competing for the same tier-based slots.
     */
    private static final int COST_PER_LEVEL = 2;

    private final Enchantment enchantment;

    VanillaEnchantEntry(Enchantment enchantment) {
        this.enchantment = enchantment;
    }

    Enchantment enchantment() {
        return this.enchantment;
    }

    @Override
    public String id() {
        return "vanilla:" + this.enchantment.getKey();
    }

    @Override
    public String catalogName(boolean pt) {
        // No "bare" (level-independent) name in Bukkit's API - level 1's leveled name is
        // the closest fit, and already omits the numeral entirely for maxLevel-1 entries.
        return PLAIN.serialize(this.enchantment.displayName(1));
    }

    @Override
    public String leveledName(boolean pt, int level) {
        return PLAIN.serialize(this.enchantment.displayName(level));
    }

    @Override
    public int maxLevel() {
        return this.enchantment.getMaxLevel();
    }

    @Override
    public int costAtLevel(int level) {
        return COST_PER_LEVEL * level;
    }

    @Override
    public List<Component> genericDescription(boolean pt) {
        return description(this.enchantment.getKey().getKey(), pt, null);
    }

    @Override
    public List<Component> resolvedDescription(boolean pt, int level) {
        return description(this.enchantment.getKey().getKey(), pt, level);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VanillaEnchantEntry other && other.enchantment.equals(this.enchantment);
    }

    @Override
    public int hashCode() {
        return this.enchantment.hashCode();
    }

    /** Real vanilla Unbreaking odds (level / (level + 1), rounded down) - I/II/III = 50/66/75%. */
    private static int unbreakingChance(int level) {
        return (int) (100.0 * level / (level + 1));
    }

    /** Linear per-level percentage, except the last level jumps straight to {@code capValue} instead of continuing the line (e.g. Sharpness: 5/10/15/20, then 30 at V, not 25). */
    private static int linearCapped(int level, int maxLevel, int perLevel, int capValue) {
        return level == maxLevel ? capValue : perLevel * level;
    }

    private static List<Component> description(String key, boolean pt, Integer level) {
        List<Token> tokens = tokens(key, pt, level);
        return tokens == null ? List.of() : wrap(tokens);
    }

    private static List<Token> tokens(String key, boolean pt, Integer level) {
        return switch (key) {
            case "bane_of_arthropods" -> pt
                    ? List.of(Token.plain("Aumenta o dano causado a mobs"), Token.colored("Ж Artrópodes", ARTHROPOD_COLOR),
                            Token.plain("em"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."))
                    : List.of(Token.plain("Increases damage dealt to"), Token.colored("Ж Arthropod", ARTHROPOD_COLOR),
                            Token.plain("mobs by"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."));
            case "sharpness" -> pt
                    ? List.of(Token.plain("Aumenta o dano corpo a corpo causado em"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."))
                    : List.of(Token.plain("Increases melee damage dealt by"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."));
            case "smite" -> pt
                    ? List.of(Token.plain("Aumenta o dano causado a mobs"), Token.colored("༕ Mortos-vivos,", UNDEAD_COLOR),
                            Token.colored("☠ Wither", WITHER_COLOR), Token.plain("e"), Token.colored("🦴 Esqueléticos", SKELETAL_COLOR),
                            Token.plain("em"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."))
                    : List.of(Token.plain("Increases damage dealt to"), Token.colored("༕ Undead,", UNDEAD_COLOR),
                            Token.colored("☠ Wither", WITHER_COLOR), Token.plain("and"), Token.colored("🦴 Skeletal", SKELETAL_COLOR),
                            Token.plain("mobs by"), value(level, "%", l -> linearCapped(l, 5, 5, 30)), Token.plain("."));
            case "fire_aspect" -> pt
                    ? List.of(Token.plain("Incendeia seus inimigos por"), value(level, "s,", l -> l * 3),
                            Token.plain("causando"), Token.colored("3%", VALUE_COLOR), Token.plain("do seu dano por nível por segundo."))
                    : List.of(Token.plain("Ignites your enemies for"), value(level, "s,", l -> l * 3),
                            Token.plain("dealing"), Token.colored("3%", VALUE_COLOR), Token.plain("of your damage per level per second."));
            case "knockback" -> pt
                    ? List.of(Token.plain("Aumenta o recuo em"), value(level, " blocos", l -> 3), Token.plain("por nível."))
                    : List.of(Token.plain("Increases knockback by"), value(level, " blocks", l -> 3), Token.plain("per level."));
            case "looting" -> pt
                    ? List.of(Token.plain("Aumenta a chance de um monstro dropar um item em"), value(level, "%", l -> 15), Token.plain("por nível."))
                    : List.of(Token.plain("Increases the chance of a monster dropping an item by"), value(level, "%", l -> 15), Token.plain("per level."));
            case "sweeping_edge", "sweeping" -> pt
                    ? List.of(Token.plain("Aumenta o dano do ataque de varredura em"), value(level, "%", l -> l * 10), Token.plain("."))
                    : List.of(Token.plain("Increases sweep attack damage by"), value(level, "%", l -> l * 10), Token.plain("."));
            case "unbreaking" -> pt
                    ? List.of(Token.plain("Chance de não perder durabilidade ao usar:"), value(level, "%", VanillaEnchantEntry::unbreakingChance), Token.plain("."))
                    : List.of(Token.plain("Chance to not lose durability when used:"), value(level, "%", VanillaEnchantEntry::unbreakingChance), Token.plain("."));
            default -> {
                String plain = plainDescription(key, pt);
                yield plain == null ? null : List.of(Token.plain(plain));
            }
        };
    }

    /** The colored value token: the literal "X" placeholder plus {@code unit} when {@code level} is null (generic view), or the real computed number plus {@code unit} (resolved view). */
    private static Token value(Integer level, String unit, IntUnaryOperator formula) {
        String number = level == null ? "X" : String.valueOf(formula.applyAsInt(level));
        return Token.colored(number + unit, VALUE_COLOR);
    }

    /** One word/phrase with a color, either free to wrap internally at spaces ({@link Token#plain}) or kept as one unbreakable unit ({@link Token#colored}, for a value+unit or a named creature type). */
    private record Token(String text, TextColor color, boolean atomic) {
        static Token plain(String s) {
            return new Token(s, NamedTextColor.GRAY, false);
        }

        static Token colored(String s, TextColor color) {
            return new Token(s, color, true);
        }
    }

    /** Packs {@code tokens} into lore lines no wider than {@link #WRAP_WIDTH} characters, breaking only between words (atomic tokens never split) and never leaving a leading space before punctuation-only words. */
    private static List<Component> wrap(List<Token> tokens) {
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

    /** One-line hand-written descriptions with no numeric value, keyed by the enchantment's plain (unnamespaced) key - null (no line shown) for anything not listed here, so a future/unrecognized enchantment degrades gracefully instead of breaking. */
    private static String plainDescription(String key, boolean pt) {
        return switch (key) {
            case "protection" -> pt ? "Reduz o dano da maioria das fontes." : "Reduces damage from most sources.";
            case "fire_protection" -> pt ? "Reduz dano de fogo e diminui o tempo em chamas." : "Reduces fire damage and burn duration.";
            case "feather_falling" -> pt ? "Reduz o dano de queda." : "Reduces fall damage.";
            case "blast_protection" -> pt ? "Reduz dano e recuo de explosões." : "Reduces explosion damage and knockback.";
            case "projectile_protection" -> pt ? "Reduz dano de projéteis." : "Reduces projectile damage.";
            case "respiration" -> pt ? "Aumenta o tempo de respiração debaixo d'água e a visão." : "Extends underwater breathing time and improves underwater visibility.";
            case "aqua_affinity" -> pt ? "Aumenta a velocidade de mineração debaixo d'água." : "Increases underwater mining speed.";
            case "thorns" -> pt ? "Chance de refletir parte do dano recebido no atacante." : "Chance to reflect some damage back at the attacker.";
            case "depth_strider" -> pt ? "Aumenta a velocidade de movimento na água." : "Increases underwater movement speed.";
            case "frost_walker" -> pt ? "Congela a água em gelo ao caminhar sobre ela." : "Freezes water into ice as you walk over it.";
            case "binding_curse" -> pt ? "Impede remover o item depois de equipado." : "Prevents removing the item once equipped.";
            case "soul_speed" -> pt ? "Aumenta a velocidade ao caminhar sobre areia das almas." : "Increases movement speed on soul sand/soil.";
            case "swift_sneak" -> pt ? "Aumenta a velocidade ao andar agachado." : "Increases movement speed while sneaking.";
            case "efficiency" -> pt ? "Aumenta a velocidade de mineração." : "Increases mining speed.";
            case "silk_touch" -> pt ? "Blocos minerados caem como eles mesmos." : "Mined blocks drop themselves.";
            case "fortune" -> pt ? "Aumenta a quantidade de drops de blocos minerados." : "Increases block drop quantity when mined.";
            case "power" -> pt ? "Aumenta o dano das flechas." : "Increases arrow damage.";
            case "punch" -> pt ? "Aumenta o recuo causado pelas flechas." : "Increases knockback dealt by arrows.";
            case "flame" -> pt ? "Incendeia as flechas disparadas." : "Sets fired arrows on fire.";
            case "infinity" -> pt ? "Disparar não consome flechas normais (precisa de ao menos 1 na mochila)." : "Shooting doesn't consume regular arrows (still needs at least one in your inventory).";
            case "luck_of_the_sea" -> pt ? "Aumenta a chance de itens raros ao pescar." : "Increases the chance of rare items while fishing.";
            case "lure" -> pt ? "Diminui o tempo de espera até um peixe morder." : "Decreases the wait time until a fish bites.";
            case "loyalty" -> pt ? "O tridente retorna à sua mão após ser arremessado." : "The trident returns to your hand after being thrown.";
            case "impaling" -> pt ? "Dano bônus contra criaturas aquáticas." : "Bonus damage against aquatic mobs.";
            case "riptide" -> pt ? "Arremessa você junto com o tridente, na água ou na chuva." : "Launches you along with the trident when in water or rain.";
            case "channeling" -> pt ? "Invoca um raio no alvo atingido durante tempestades." : "Summons a lightning bolt on the target hit during a storm.";
            case "multishot" -> pt ? "A besta dispara 3 flechas de uma vez." : "The crossbow fires 3 arrows at once.";
            case "quick_charge" -> pt ? "Diminui o tempo de recarga da besta." : "Decreases the crossbow's reload time.";
            case "piercing" -> pt ? "As flechas atravessam múltiplos alvos." : "Arrows pierce through multiple targets.";
            case "mending" -> pt ? "Usa XP coletado para reparar o item equipado/segurado." : "Uses collected XP to repair the equipped/held item.";
            case "vanishing_curse" -> pt ? "O item desaparece ao morrer, em vez de cair no chão." : "The item disappears on death instead of dropping.";
            case "density" -> pt ? "Dano bônus da maça, com base na distância de queda." : "Bonus mace damage based on fall distance.";
            case "breach" -> pt ? "Dano bônus da maça contra alvos com armadura." : "Bonus mace damage against armored targets.";
            case "wind_burst" -> pt ? "Impulsiona você para cima ao acertar com a maça." : "Launches you upward when you hit with the mace.";
            default -> null;
        };
    }
}
