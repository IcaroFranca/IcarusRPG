package dev.icaro.foodtooltips.enchant;

import java.util.List;
import java.util.function.IntUnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;

/**
 * The plugin's own custom enchantments - entirely separate from (and, since real
 * vanilla enchantments are also offered through the same screen via {@link
 * VanillaEnchantEntry}, sharing the catalog with) vanilla enchantments. Applied
 * only through the reworked Enchanting Table screen (see {@code
 * EnchantMenuService}), never randomly.
 *
 * <p>Most of these replace a vanilla enchantment whose real level cap (fixed by
 * Mojang/Bukkit, not overridable) was too low for the leveled effect wanted here -
 * Flame and Infinity are both capped at level 1 vanilla, Lure/Luck of the Sea at
 * level 3, Fire Aspect at level 2, Protection/Fire Protection/Blast Protection/
 * Projectile Protection/Feather Falling at level 4 - so vanilla's own version of
 * each is excluded from the table (see {@code EnchantService#allEntries}) in favor
 * of these. Respiration and Thorns are custom too even though their level cap isn't
 * changing, since the effect wanted for them (a brand new "Respiration" stat;
 * Thorns' own chance/amount formula) doesn't match real vanilla's own mechanic
 * either. Unlike the vanilla entries, these actually change gameplay - see {@link
 * CustomEnchantEffectListener} (held-item effects: arrow burn, arrow-save chance,
 * fishing wait time/treasure re-roll) and {@link ArmorEnchantEffectListener}
 * (armor-slot effects: Defense/True Defense, fall damage, underwater breath,
 * Thorns' reflect).
 */
public enum IcarusEnchant {
    /** Replaces vanilla Flame (capped at level 1) - see CustomEnchantEffectListener#arrowHit. */
    FLAME("Chama", "Flame", 2, Material.BOW),
    /** Replaces vanilla Lure (capped at level 3) - see CustomEnchantEffectListener#fish. */
    LURE("Chamariz", "Lure", 5, Material.FISHING_ROD),
    /** Replaces vanilla Infinity (capped at level 1, and a plain on/off rather than a chance) - see CustomEnchantEffectListener#bowShoot. */
    INFINITE_QUIVER("Aljava Infinita", "Infinite Quiver", 5, Material.BOW),
    /** Replaces vanilla Luck of the Sea (capped at level 3) - see CustomEnchantEffectListener#fishCatch. */
    LUCK_OF_THE_SEA("Sorte do Mar", "Luck of the Sea", 5, Material.FISHING_ROD),
    /** Replaces vanilla Fire Aspect (capped at level 2) - see CustomEnchantEffectListener#fireAspectHit. */
    FIRE_ASPECT("Aspecto Ígneo", "Fire Aspect", 3, Category.SWORD),
    /** Replaces vanilla Protection (capped at level 4) - see ArmorEnchantEffectListener#protection. */
    PROTECTION("Proteção", "Protection", 5, Category.ARMOR),
    /** Replaces vanilla Fire Protection (capped at level 4) - see ArmorEnchantEffectListener#protection. */
    FIRE_PROTECTION("Proteção contra Fogo", "Fire Protection", 5, Category.ARMOR),
    /** Replaces vanilla Blast Protection (capped at level 4) - see ArmorEnchantEffectListener#protection. */
    BLAST_PROTECTION("Proteção contra Explosão", "Blast Protection", 5, Category.ARMOR),
    /** Replaces vanilla Projectile Protection (capped at level 4) - see ArmorEnchantEffectListener#protection. */
    PROJECTILE_PROTECTION("Proteção contra Projétil", "Projectile Protection", 5, Category.ARMOR),
    /** Replaces vanilla Feather Falling (capped at level 4) - see ArmorEnchantEffectListener#fall. */
    FEATHER_FALLING("Queda de Pena", "Feather Falling", 5, Category.BOOTS),
    /** Replaces vanilla Respiration - same level cap (3), but a brand new stat rather than vanilla's own air-loss-chance mechanic - see ArmorEnchantEffectListener#applyRespiration. */
    RESPIRATION("Respiração", "Respiration", 3, Category.HELMET),
    /** Replaces vanilla Thorns - same level cap (3), but a flat chance/per-level amount rather than vanilla's own formula - see ArmorEnchantEffectListener#thorns. */
    THORNS("Espinhos", "Thorns", 3, Category.ARMOR);

    /** An entry's item-type restriction - a single material for a held-item entry (a specific bow/rod), or a whole category otherwise, since one {@link Material} can't express "any sword"/"any armor piece". Kept as a nested enum (rather than e.g. a {@code Predicate<Material>} field) so the constant list above - which Java requires to come first in an enum body - never has to forward-reference a same-class static field. */
    private enum Category {
        SINGLE, SWORD, ARMOR, BOOTS, HELMET
    }

    /** Level 1's (duration seconds, damage % per second) pair; level 2's. Doesn't fit a "flat rate * level" formula, so it's a direct lookup instead. */
    private static final double[] FLAME_DURATION = {0, 3.5, 4.0};
    private static final double[] FLAME_PERCENT = {0, 3, 6};
    /** Fire Aspect's own (duration seconds, damage % per second) lookup per level - see {@link #FLAME_DURATION} for why this is a lookup, not a formula. */
    private static final double[] FIRE_ASPECT_DURATION = {0, 3, 4, 4};
    private static final double[] FIRE_ASPECT_PERCENT = {0, 3, 6, 9};
    /** Vanilla's own gold - #FFAA00 - used to label a named stat (Defense, True Defense) inline, same as Fortune/Efficiency's own labels in VanillaEnchantEntry. */
    private static final TextColor LABEL_COLOR = NamedTextColor.GOLD;
    /** Respiration's own label color, given as #00AAAA rather than the usual gold. */
    private static final TextColor RESPIRATION_COLOR = TextColor.color(0x00AAAA);

    private final String namePt;
    private final String nameEn;
    private final int maxLevel;
    private final Category category;
    private final Material singleMaterial;

    IcarusEnchant(String namePt, String nameEn, int maxLevel, Material singleMaterial) {
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.maxLevel = maxLevel;
        this.category = Category.SINGLE;
        this.singleMaterial = singleMaterial;
    }

    IcarusEnchant(String namePt, String nameEn, int maxLevel, Category category) {
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.maxLevel = maxLevel;
        this.category = category;
        this.singleMaterial = null;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    /** Whether {@code item} is in this entry's item category - unlike vanilla entries, which delegate this straight to {@code Enchantment#canEnchantItem}. */
    public boolean canApplyTo(Material item) {
        String n = item.name();
        return switch (this.category) {
            case SINGLE -> item == this.singleMaterial;
            case SWORD -> n.endsWith("_SWORD");
            case ARMOR -> n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
            case BOOTS -> n.endsWith("_BOOTS");
            case HELMET -> n.endsWith("_HELMET");
        };
    }

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. Matches the flat rate every vanilla entry uses (see VanillaEnchantEntry), for consistency. */
    public int costAtLevel(int level) {
        return 2 * level;
    }

    /** Word-wrapped description, colored the same way vanilla entries are - see {@link EnchantText}. {@code level} null shows the generic "X"/"Y" placeholder view; a real level resolves the real numbers. */
    public List<Component> description(boolean pt, Integer level) {
        return switch (this) {
            case FLAME -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("A flecha incendeia seus inimigos por"), lookup(FLAME_DURATION, "X", level), EnchantText.Token.plain("s, causando"),
                            lookup(FLAME_PERCENT, "Y", level), EnchantText.Token.plain("% do seu dano por segundo."))
                    : List.of(EnchantText.Token.plain("Arrow ignites your enemies for"), lookup(FLAME_DURATION, "X", level), EnchantText.Token.plain("s, dealing"),
                            lookup(FLAME_PERCENT, "Y", level), EnchantText.Token.plain("% of your damage per second.")));
            case LURE -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Diminui o tempo máximo para fisgar algo em"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("por nível."))
                    : List.of(EnchantText.Token.plain("Shortens the maximum time to catch something by"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("per level.")));
            case INFINITE_QUIVER -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Economiza flechas"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.Token.plain("por nível das vezes que você atira com o arco."))
                    : List.of(EnchantText.Token.plain("Saves arrows"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.Token.plain("per level of the time when you fire your bow.")));
            case LUCK_OF_THE_SEA -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), EnchantText.Token.colored(treasureChanceText(level) + " ⛃ Chance de Tesouro", LABEL_COLOR),
                            EnchantText.Token.plain("por nível, o que aumenta a chance de pescar tesouros."))
                    : List.of(EnchantText.Token.plain("Grants"), EnchantText.Token.colored(treasureChanceText(level) + " ⛃ Treasure Chance", LABEL_COLOR),
                            EnchantText.Token.plain("per level, which increases the chance of fishing treasure.")));
            case FIRE_ASPECT -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Incendeia seus inimigos por"), lookup(FIRE_ASPECT_DURATION, "X", level), EnchantText.Token.plain("s, causando"),
                            lookup(FIRE_ASPECT_PERCENT, "Y", level), EnchantText.Token.plain("% do seu dano por nível por segundo."))
                    : List.of(EnchantText.Token.plain("Ignites your enemies for"), lookup(FIRE_ASPECT_DURATION, "X", level), EnchantText.Token.plain("s, dealing"),
                            lookup(FIRE_ASPECT_PERCENT, "Y", level), EnchantText.Token.plain("% of your damage per level per second.")));
            case PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 5), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR), EnchantText.Token.plain("por nível."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 5), EnchantText.Token.colored("❈ Defense", LABEL_COLOR), EnchantText.Token.plain("per level.")));
            case FIRE_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 2), EnchantText.Token.colored("❂ Defesa Verdadeira", LABEL_COLOR),
                            EnchantText.Token.plain("por nível contra fogo e lava."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 2), EnchantText.Token.colored("❂ True Defense", LABEL_COLOR),
                            EnchantText.Token.plain("per level against fire and lava.")));
            case BLAST_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 30), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR),
                            EnchantText.Token.plain("por nível contra explosões."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 30), EnchantText.Token.colored("❈ Defense", LABEL_COLOR),
                            EnchantText.Token.plain("per level against explosions.")));
            case PROJECTILE_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR),
                            EnchantText.Token.plain("por nível contra projéteis."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defense", LABEL_COLOR),
                            EnchantText.Token.plain("per level against projectiles.")));
            case FEATHER_FALLING -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta em"), EnchantText.Token.value(level, "", l -> l), EnchantText.Token.plain("bloco(s) por nível a altura de queda segura, e reduz o dano de queda em"),
                            EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("por nível."))
                    : List.of(EnchantText.Token.plain("Increases how high you can fall before taking fall damage by"), EnchantText.Token.value(level, "", l -> l),
                            EnchantText.Token.plain("per level and reduces fall damage by"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.Token.plain("per level.")));
            case RESPIRATION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 15), EnchantText.Token.colored("⚶ Respiração", RESPIRATION_COLOR), EnchantText.Token.plain("por nível."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 15), EnchantText.Token.colored("⚶ Respiration", RESPIRATION_COLOR), EnchantText.Token.plain("per level.")));
            case THORNS -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), EnchantText.Token.colored("50%", EnchantText.VALUE_COLOR), EnchantText.Token.plain("de chance de refletir"),
                            EnchantText.Token.value(level, "%", l -> l * 3), EnchantText.Token.plain("do dano por nível de volta ao atacante."))
                    : List.of(EnchantText.Token.plain("Grants a"), EnchantText.Token.colored("50%", EnchantText.VALUE_COLOR), EnchantText.Token.plain("chance to rebound"),
                            EnchantText.Token.value(level, "%", l -> l * 3), EnchantText.Token.plain("of damage per level dealt back at the attacker.")));
        };
    }

    /** A duration/percent lookup value formatted without a trailing ".0" for a whole number, or the literal {@code placeholder} for the generic view. */
    private static EnchantText.Token lookup(double[] table, String placeholder, Integer level) {
        if (level == null) {
            return EnchantText.Token.colored(placeholder, EnchantText.VALUE_COLOR);
        }
        double v = table[Math.min(level, table.length - 1)];
        String text = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return EnchantText.Token.colored(text, EnchantText.VALUE_COLOR);
    }

    /** "+X" for the generic view, or "+" + the resolved number for a specific level - same shape as {@code VanillaEnchantEntry#plusValue}. */
    private static EnchantText.Token plusValue(Integer level, IntUnaryOperator formula) {
        return EnchantText.Token.colored(level == null ? "+X" : "+" + formula.applyAsInt(level), EnchantText.VALUE_COLOR);
    }

    /** "+X" for the generic view, or "+0.5"/"+1"/.../"+2.5" (0.5-per-level, trimmed to a whole number when it lands on one) for a resolved level - see CustomEnchantEffectListener#fishCatch for how this chance is actually rolled. */
    private static String treasureChanceText(Integer level) {
        if (level == null) {
            return "+X";
        }
        double v = 0.5 * level;
        String number = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return "+" + number;
    }
}
