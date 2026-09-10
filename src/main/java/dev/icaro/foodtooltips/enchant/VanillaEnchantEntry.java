package dev.icaro.foodtooltips.enchant;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;

/**
 * Wraps a real vanilla {@link Enchantment} as an {@link EnchantEntry}, so the
 * reworked Enchanting Table screen can offer every vanilla enchantment
 * (Sharpness, Protection, Unbreaking, ...) explicitly alongside the plugin's own -
 * see {@code EnchantService#allEntries} (which also excludes a handful: Mending and
 * both curses are hidden from the table entirely, and Flame/Lure/Infinity/Luck of
 * the Sea/Fire Aspect/Protection/Fire Protection/Blast Protection/Projectile
 * Protection/Feather Falling/Respiration/Thorns are replaced by the plugin's own
 * leveled or reimplemented versions - see {@link IcarusEnchant}). Names
 * come straight from Minecraft's own translations (Paper resolves {@link
 * Enchantment#displayName} server-side, so this works the same regardless of client
 * locale) rather than a hand-maintained PT/EN list; descriptions below are
 * hand-written since Bukkit has no API for those.
 */
final class VanillaEnchantEntry implements EnchantEntry {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TextColor ARTHROPOD_COLOR = TextColor.color(0x940204);
    private static final TextColor UNDEAD_COLOR = TextColor.color(0x00AA00);
    private static final TextColor SKELETAL_COLOR = TextColor.color(0xAAAAAA);
    private static final TextColor WITHER_COLOR = TextColor.color(0x555555);
    /** Vanilla's own gold - #FFAA00 - used to label a named stat (Mining Speed, Mining Fortune, Treasure Chance) inline in a description. */
    private static final TextColor LABEL_COLOR = NamedTextColor.GOLD;
    /**
     * Flat XP-level cost per level - fallback for anything not in {@link #COSTS}
     * below (a future/unrecognized enchantment), so this degrades gracefully instead
     * of breaking, same philosophy as {@link #plainDescription}'s own null default.
     */
    private static final int COST_PER_LEVEL = 2;
    /**
     * Explicit per-level XP cost, keyed by the enchantment's plain (unnamespaced)
     * key - not a formula, since the numbers given don't all fit one shape (e.g.
     * Power/Infinity's 10/20/30/40/50 vs. Sharpness/Efficiency/Protection's own
     * 10/15/20/25/30). {@code IcarusEnchant#costAtLevel} holds the equivalent table
     * for the plugin's own custom entries.
     */
    private static final Map<String, int[]> COSTS = Map.ofEntries(
            Map.entry("sharpness", new int[]{10, 15, 20, 25, 30}),
            Map.entry("smite", new int[]{10, 15, 20, 25, 30}),
            Map.entry("bane_of_arthropods", new int[]{10, 15, 20, 25, 30}),
            Map.entry("efficiency", new int[]{10, 15, 20, 25, 30}),
            Map.entry("impaling", new int[]{10, 15, 20, 25, 30}),
            Map.entry("density", new int[]{10, 15, 20, 25, 30}),
            Map.entry("power", new int[]{10, 20, 30, 40, 50}),
            Map.entry("knockback", new int[]{15, 30}),
            Map.entry("punch", new int[]{15, 30}),
            Map.entry("frost_walker", new int[]{15, 30}),
            Map.entry("looting", new int[]{15, 30, 45}),
            Map.entry("fortune", new int[]{15, 30, 45}),
            Map.entry("sweeping_edge", new int[]{15, 30, 45}),
            Map.entry("sweeping", new int[]{15, 30, 45}),
            Map.entry("wind_burst", new int[]{15, 30, 45}),
            Map.entry("silk_touch", new int[]{10}),
            Map.entry("aqua_affinity", new int[]{15}),
            Map.entry("multishot", new int[]{15}),
            Map.entry("channeling", new int[]{15}),
            Map.entry("depth_strider", new int[]{10, 20, 30}),
            Map.entry("swift_sneak", new int[]{10, 20, 30}),
            Map.entry("soul_speed", new int[]{10, 20, 30}),
            Map.entry("unbreaking", new int[]{10, 20, 30}),
            Map.entry("loyalty", new int[]{10, 20, 30}),
            Map.entry("riptide", new int[]{10, 20, 30}),
            Map.entry("quick_charge", new int[]{10, 20, 30}),
            Map.entry("piercing", new int[]{10, 15, 20, 25}),
            Map.entry("breach", new int[]{10, 15, 20, 25}));

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
        int[] costs = COSTS.get(this.enchantment.getKey().getKey());
        if (costs != null) {
            return costs[Math.max(1, Math.min(level, costs.length)) - 1];
        }
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

    /** Vanilla's own real sweep-attack ratio (unchanged, untouched real vanilla mechanic) - level/(level+1) of the main hit's damage, rounded: 50%/67%/75% for I/II/III. See the "sweeping_edge" description. */
    private static int sweepingPercent(int level) {
        return (int) Math.round(100.0 * level / (level + 1));
    }

    /** Linear per-level percentage, except the last level jumps straight to {@code capValue} instead of continuing the line (e.g. Sharpness: 5/10/15/20, then 30 at V, not 25). */
    private static int linearCapped(int level, int maxLevel, int perLevel, int capValue) {
        return level == maxLevel ? capValue : perLevel * level;
    }

    private static List<Component> description(String key, boolean pt, Integer level) {
        List<EnchantText.Token> tokens = tokens(key, pt, level);
        return tokens == null ? List.of() : EnchantText.wrap(tokens);
    }

    private static List<EnchantText.Token> tokens(String key, boolean pt, Integer level) {
        return switch (key) {
            case "bane_of_arthropods" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano causado a mobs"), EnchantText.Token.colored("Ж Artrópodes", ARTHROPOD_COLOR),
                            EnchantText.Token.plain("em"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases damage dealt to"), EnchantText.Token.colored("Ж Arthropod", ARTHROPOD_COLOR),
                            EnchantText.Token.plain("mobs by"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."));
            case "sharpness" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano corpo a corpo causado em"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases melee damage dealt by"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."));
            case "smite" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano causado a mobs"), EnchantText.Token.colored("༕ Mortos-vivos,", UNDEAD_COLOR),
                            EnchantText.Token.colored("☠ Wither", WITHER_COLOR), EnchantText.Token.plain("e"), EnchantText.Token.colored("🦴 Esqueléticos", SKELETAL_COLOR),
                            EnchantText.Token.plain("em"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases damage dealt to"), EnchantText.Token.colored("༕ Undead,", UNDEAD_COLOR),
                            EnchantText.Token.colored("☠ Wither", WITHER_COLOR), EnchantText.Token.plain("and"), EnchantText.Token.colored("🦴 Skeletal", SKELETAL_COLOR),
                            EnchantText.Token.plain("mobs by"), EnchantText.Token.value(level, "%", l -> linearCapped(l, 5, 5, 30)), EnchantText.Token.plain("."));
            case "knockback" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o recuo em"), EnchantText.Token.value(level, " blocos", l -> l * 3), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases knockback by"), EnchantText.Token.value(level, " blocks", l -> l * 3), EnchantText.perLevel(level, false), EnchantText.Token.plain("."));
            case "looting" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta a chance de um monstro dropar um item em"), EnchantText.Token.value(level, "%", l -> l * 15), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases the chance of a monster dropping an item by"), EnchantText.Token.value(level, "%", l -> l * 15), EnchantText.perLevel(level, false), EnchantText.Token.plain("."));
            case "sweeping_edge", "sweeping" -> pt
                    ? List.of(EnchantText.Token.plain("O ataque de varredura passa a causar 1 +"), EnchantText.Token.value(level, "%", VanillaEnchantEntry::sweepingPercent),
                            EnchantText.Token.plain("do dano do golpe principal (já com Sharpness/Smite/Bane of Arthropods) em cada alvo atingido - a fórmula real do vanilla."))
                    : List.of(EnchantText.Token.plain("The sweep attack now deals 1 +"), EnchantText.Token.value(level, "%", VanillaEnchantEntry::sweepingPercent),
                            EnchantText.Token.plain("of the main hit's damage (already including Sharpness/Smite/Bane of Arthropods) to every target it hits - vanilla's own real formula."));
            case "unbreaking" -> pt
                    ? List.of(EnchantText.Token.plain("Chance de não perder durabilidade ao usar:"), EnchantText.Token.value(level, "%", VanillaEnchantEntry::unbreakingChance), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Chance to not lose durability when used:"), EnchantText.Token.value(level, "%", VanillaEnchantEntry::unbreakingChance), EnchantText.Token.plain("."));
            case "efficiency" -> pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> 10 + 20 * l), EnchantText.Token.colored("⸕ Velocidade de Mineração", LABEL_COLOR), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> 10 + 20 * l), EnchantText.Token.colored("⸕ Mining Speed", LABEL_COLOR), EnchantText.Token.plain("."));
            case "fortune" -> pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 10), EnchantText.Token.colored("☘ Sorte de Mineração", LABEL_COLOR), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 10), EnchantText.Token.colored("☘ Mining Fortune", LABEL_COLOR), EnchantText.Token.plain("."));
            case "silk_touch" -> pt
                    ? List.of(EnchantText.Token.plain("Blocos minerados caem como eles mesmos, em vez de seus drops normais."))
                    : List.of(EnchantText.Token.plain("Mined blocks drop themselves instead of their normal drops."));
            case "power" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano do arco em"), EnchantText.Token.value(level, "%", l -> l * 8), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases bow damage by"), EnchantText.Token.value(level, "%", l -> l * 8), EnchantText.Token.plain("."));
            case "punch" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta o recuo das flechas em"), EnchantText.Token.value(level, " blocos", l -> l * 3), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases arrow knockback by"), EnchantText.Token.value(level, " blocks", l -> l * 3), EnchantText.perLevel(level, false), EnchantText.Token.plain("."));
            case "depth_strider" -> pt
                    ? List.of(EnchantText.Token.plain("Reduz a redução de velocidade na água em"), EnchantText.Token.value(level, "%", l -> l * 33), EnchantText.perLevel(level, true),
                            EnchantText.Token.plain(". No nível III, a velocidade de movimento é igual à da terra firme (100%)."))
                    : List.of(EnchantText.Token.plain("Reduces how much you are slowed in the water by"), EnchantText.Token.value(level, "%", l -> l * 33), EnchantText.perLevel(level, false),
                            EnchantText.Token.plain(". At level III, your movement speed is the same as on land (100%)."));
            case "swift_sneak" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta a velocidade ao andar agachado em"), EnchantText.Token.value(level, "%", l -> l * 15), EnchantText.perLevel(level, true),
                            EnchantText.Token.plain("(a velocidade normal agachado é 30% da velocidade andando)."))
                    : List.of(EnchantText.Token.plain("Increases sneaking movement speed by"), EnchantText.Token.value(level, "%", l -> l * 15), EnchantText.perLevel(level, false),
                            EnchantText.Token.plain("(normal sneaking speed is 30% of your walking speed)."));
            case "soul_speed" -> pt
                    ? List.of(EnchantText.Token.plain("Aumenta a velocidade de movimento em areia/solo das almas em"), EnchantText.Token.value(level, "%", l -> l * 35), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases movement speed on soul sand/soil by"), EnchantText.Token.value(level, "%", l -> l * 35), EnchantText.perLevel(level, false), EnchantText.Token.plain("."));
            case "frost_walker" -> pt
                    ? List.of(EnchantText.Token.plain("Congela a água em gelo num raio de"), EnchantText.Token.value(level, " blocos", l -> l * 2 + 1), EnchantText.Token.plain("ao caminhar sobre ela."))
                    : List.of(EnchantText.Token.plain("Freezes water into ice in a radius of"), EnchantText.Token.value(level, " blocks", l -> l * 2 + 1), EnchantText.Token.plain("as you walk over it."));
            default -> {
                String plain = plainDescription(key, pt);
                yield plain == null ? null : List.of(EnchantText.Token.plain(plain));
            }
        };
    }

    /** "+X" for the generic view, or "+" + the resolved number for a specific level. */
    private static EnchantText.Token plusValue(Integer level, java.util.function.IntUnaryOperator formula) {
        return EnchantText.Token.colored(level == null ? "+X" : "+" + formula.applyAsInt(level), EnchantText.VALUE_COLOR);
    }

    /** One-line hand-written descriptions with no numeric value, keyed by the enchantment's plain (unnamespaced) key - null (no line shown) for anything not listed here, so a future/unrecognized enchantment degrades gracefully instead of breaking. */
    private static String plainDescription(String key, boolean pt) {
        return switch (key) {
            case "aqua_affinity" -> pt
                    ? "Aumenta a velocidade de mineração debaixo d'água para o nível normal (minerar na água é normalmente cinco vezes mais lenta)."
                    : "Increases underwater mining rate to normal level mining rate (mining in water is normally five times slower).";
            case "riptide" -> pt ? "Arremessa você junto com o tridente, na água ou na chuva." : "Launches you along with the trident when in water or rain.";
            case "loyalty" -> pt ? "O tridente retorna à sua mão após ser arremessado." : "The trident returns to your hand after being thrown.";
            case "impaling" -> pt ? "Dano bônus contra criaturas aquáticas." : "Bonus damage against aquatic mobs.";
            case "channeling" -> pt ? "Invoca um raio no alvo atingido durante tempestades." : "Summons a lightning bolt on the target hit during a storm.";
            case "multishot" -> pt ? "A besta dispara 3 flechas de uma vez." : "The crossbow fires 3 arrows at once.";
            case "quick_charge" -> pt ? "Diminui o tempo de recarga da besta." : "Decreases the crossbow's reload time.";
            case "piercing" -> pt ? "As flechas atravessam múltiplos alvos." : "Arrows pierce through multiple targets.";
            case "density" -> pt ? "Dano bônus da maça, com base na distância de queda." : "Bonus mace damage based on fall distance.";
            case "breach" -> pt ? "Dano bônus da maça contra alvos com armadura." : "Bonus mace damage against armored targets.";
            case "wind_burst" -> pt ? "Impulsiona você para cima ao acertar com a maça." : "Launches you upward when you hit with the mace.";
            default -> null;
        };
    }
}
