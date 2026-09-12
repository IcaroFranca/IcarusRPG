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
    THORNS("Espinhos", "Thorns", 3, Category.ARMOR),

    /**
     * The plugin's own melee-weapon enchant family (Critical through Venomous below) -
     * entirely new mechanics, not a leveled-up vanilla enchant like most of the entries
     * above. All fourteen share the same item scope (swords and axes/pickaxes/shovels/
     * hoes - see {@link Category#SWORD_OR_TOOL}) and are wired up across {@code
     * CombatListener} (the damage-percentage ones: Critical, Cubism, Ender Slayer,
     * Execute, First Strike, Giant Killer, Impaling, plus Lethality's Defense-reduction
     * debuff) and {@code MeleeEnchantEffectListener} (the on-hit/on-kill ones: Life
     * Steal, Vampirism, Thunderlord, Venomous, Experience, Luck). Cubism/Ender Slayer/
     * Impaling target three new mob categories the plugin didn't have before -
     * {@code CombatListener}'s own {@code CUBIC_TYPES}/{@code ENDER_TYPES}/{@code
     * AQUATIC_TYPES}.
     */
    CRITICAL("Crítico", "Critical", 5, Category.SWORD_OR_TOOL),
    CUBISM("Cubismo", "Cubism", 5, Category.SWORD_OR_TOOL),
    ENDER_SLAYER("Matador do Fim", "Ender Slayer", 5, Category.SWORD_OR_TOOL),
    EXECUTE("Execução", "Execute", 5, Category.SWORD_OR_TOOL),
    EXPERIENCE("Experiência", "Experience", 4, Category.SWORD_OR_TOOL),
    FIRST_STRIKE("Primeiro Golpe", "First Strike", 4, Category.SWORD_OR_TOOL),
    GIANT_KILLER("Matador de Gigantes", "Giant Killer", 5, Category.SWORD_OR_TOOL),
    IMPALING("Perfurante", "Impaling", 5, Category.SWORD_OR_TOOL),
    LETHALITY("Letalidade", "Lethality", 5, Category.SWORD_OR_TOOL),
    LIFE_STEAL("Roubo de Vida", "Life Steal", 3, Category.SWORD_OR_TOOL),
    LUCK("Sorte", "Luck", 5, Category.SWORD_OR_TOOL),
    THUNDERLORD("Senhor do Trovão", "Thunderlord", 5, Category.SWORD_OR_TOOL),
    VAMPIRISM("Vampirismo", "Vampirism", 5, Category.SWORD_OR_TOOL),
    VENOMOUS("Venenoso", "Venomous", 5, Category.SWORD_OR_TOOL);

    /** An entry's item-type restriction - a single material for a held-item entry (a specific bow/rod), or a whole category otherwise, since one {@link Material} can't express "any sword"/"any armor piece". Kept as a nested enum (rather than e.g. a {@code Predicate<Material>} field) so the constant list above - which Java requires to come first in an enum body - never has to forward-reference a same-class static field. */
    private enum Category {
        SINGLE, SWORD, ARMOR, BOOTS, HELMET, SWORD_OR_TOOL
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
    /** Cubism/Ender Slayer/Impaling's own mob-category label colors - see CombatListener's CUBIC_TYPES/ENDER_TYPES/AQUATIC_TYPES for the actual membership these labels refer to. */
    private static final TextColor CUBIC_COLOR = TextColor.color(0x55FF55);
    private static final TextColor ENDER_COLOR = TextColor.color(0x9955FF);
    private static final TextColor AQUATIC_COLOR = TextColor.color(0x3399FF);

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
            case SWORD_OR_TOOL -> n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE");
        };
    }

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. Explicit per-level table, not a formula - see {@code VanillaEnchantEntry#COSTS} for the same idea over vanilla entries. */
    public int costAtLevel(int level) {
        int[] costs = switch (this) {
            case FLAME -> new int[]{25, 50};
            case LURE, INFINITE_QUIVER, LUCK_OF_THE_SEA -> new int[]{10, 20, 30, 40, 50};
            case FIRE_ASPECT, THORNS -> new int[]{15, 30, 45};
            case PROTECTION, FIRE_PROTECTION, BLAST_PROTECTION, PROJECTILE_PROTECTION, FEATHER_FALLING -> new int[]{10, 15, 20, 25, 30};
            case RESPIRATION -> new int[]{10, 20, 30};
            case CRITICAL, CUBISM, ENDER_SLAYER, EXECUTE, GIANT_KILLER, IMPALING, LETHALITY, LUCK, THUNDERLORD, VAMPIRISM, VENOMOUS -> new int[]{10, 20, 30, 40, 50};
            case EXPERIENCE, FIRST_STRIKE -> new int[]{10, 20, 30, 40};
            case LIFE_STEAL -> new int[]{10, 20, 30};
        };
        return costs[Math.max(1, Math.min(level, costs.length)) - 1];
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
                    ? List.of(EnchantText.Token.plain("Diminui o tempo máximo para fisgar algo em"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Shortens the maximum time to catch something by"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case INFINITE_QUIVER -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Economiza flechas"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.perLevel(level, true), EnchantText.Token.plain("das vezes que você atira com o arco."))
                    : List.of(EnchantText.Token.plain("Saves arrows"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.perLevel(level, false), EnchantText.Token.plain("of the time when you fire your bow.")));
            case LUCK_OF_THE_SEA -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), EnchantText.Token.colored(treasureChanceText(level) + " ⛃ Chance de Tesouro", LABEL_COLOR),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain(", o que aumenta a chance de pescar tesouros."))
                    : List.of(EnchantText.Token.plain("Grants"), EnchantText.Token.colored(treasureChanceText(level) + " ⛃ Treasure Chance", LABEL_COLOR),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain(", which increases the chance of fishing treasure.")));
            case FIRE_ASPECT -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Incendeia seus inimigos por"), lookup(FIRE_ASPECT_DURATION, "X", level), EnchantText.Token.plain("s, causando"),
                            lookup(FIRE_ASPECT_PERCENT, "Y", level), EnchantText.Token.plain("% do seu dano por nível por segundo."))
                    : List.of(EnchantText.Token.plain("Ignites your enemies for"), lookup(FIRE_ASPECT_DURATION, "X", level), EnchantText.Token.plain("s, dealing"),
                            lookup(FIRE_ASPECT_PERCENT, "Y", level), EnchantText.Token.plain("% of your damage per level per second.")));
            case PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 4), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 4), EnchantText.Token.colored("❈ Defense", LABEL_COLOR), EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case FIRE_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 2), EnchantText.Token.colored("❂ Defesa Verdadeira", LABEL_COLOR),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain("contra fogo e lava."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 2), EnchantText.Token.colored("❂ True Defense", LABEL_COLOR),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain("against fire and lava.")));
            case BLAST_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain("contra explosões."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defense", LABEL_COLOR),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain("against explosions.")));
            case PROJECTILE_PROTECTION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain("contra projéteis."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 7), EnchantText.Token.colored("❈ Defense", LABEL_COLOR),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain("against projectiles.")));
            case FEATHER_FALLING -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta em"), EnchantText.Token.value(level, "", l -> l), EnchantText.Token.plain("bloco(s)"), EnchantText.perLevel(level, true),
                            EnchantText.Token.plain("a altura de queda segura, e reduz o dano de queda em"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases how high you can fall before taking fall damage by"), EnchantText.Token.value(level, "", l -> l), EnchantText.perLevel(level, false),
                            EnchantText.Token.plain("and reduces fall damage by"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case RESPIRATION -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), plusValue(level, l -> l * 15), EnchantText.Token.colored("⚶ Respiração", RESPIRATION_COLOR), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Grants"), plusValue(level, l -> l * 15), EnchantText.Token.colored("⚶ Respiration", RESPIRATION_COLOR), EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case THORNS -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Concede"), EnchantText.Token.colored("50%", EnchantText.VALUE_COLOR), EnchantText.Token.plain("de chance de refletir"),
                            EnchantText.Token.value(level, "%", l -> l * 3), EnchantText.Token.plain("do dano"), EnchantText.perLevel(level, true), EnchantText.Token.plain("de volta ao atacante."))
                    : List.of(EnchantText.Token.plain("Grants a"), EnchantText.Token.colored("50%", EnchantText.VALUE_COLOR), EnchantText.Token.plain("chance to rebound"),
                            EnchantText.Token.value(level, "%", l -> l * 3), EnchantText.Token.plain("of damage"), EnchantText.perLevel(level, false), EnchantText.Token.plain("dealt back at the attacker.")));
            case CRITICAL -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano crítico da arma em"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases the weapon's critical damage by"), EnchantText.Token.value(level, "%", l -> l * 10), EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case CUBISM -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano contra mobs"), EnchantText.Token.colored("⚂ Cúbicos", CUBIC_COLOR), EnchantText.Token.plain("em"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases damage dealt to"), EnchantText.Token.colored("⚂ Cubic", CUBIC_COLOR), EnchantText.Token.plain("mobs by"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain(".")));
            case ENDER_SLAYER -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano contra mobs"), EnchantText.Token.colored("⊙ do Fim", ENDER_COLOR), EnchantText.Token.plain("em"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases damage dealt to"), EnchantText.Token.colored("⊙ Ender", ENDER_COLOR), EnchantText.Token.plain("mobs by"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain(".")));
            case IMPALING -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano contra mobs"), EnchantText.Token.colored("⚓ Aquáticos", AQUATIC_COLOR), EnchantText.Token.plain("em"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases damage dealt to"), EnchantText.Token.colored("⚓ Aquatic", AQUATIC_COLOR), EnchantText.Token.plain("mobs by"),
                            EnchantText.Token.value(level, "%", l -> lastLevelJump(l, 5, 5, 30)), EnchantText.Token.plain(".")));
            case EXECUTE -> {
                String coefficient = level == null ? "X" : number(0.2 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Aumenta o dano em"), EnchantText.Token.colored(coefficient + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, true),
                                EnchantText.Token.plain("para cada 1% de vida faltando do alvo."))
                        : List.of(EnchantText.Token.plain("Increases damage by"), EnchantText.Token.colored(coefficient + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, false),
                                EnchantText.Token.plain("for each 1% of the target's missing health.")));
            }
            case GIANT_KILLER -> {
                String rate = level == null ? "X" : (level >= 5 ? "0,6" : number(0.1 * level));
                String rateEn = level == null ? "X" : (level >= 5 ? "0.6" : number(0.1 * level));
                String cap = level == null ? "X" : String.valueOf(level >= 5 ? 30 : 5 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Aumenta o dano em"), EnchantText.Token.colored(rate + "%", EnchantText.VALUE_COLOR),
                                EnchantText.Token.plain("para cada 1% de vida extra que o alvo tiver acima da sua, até"),
                                EnchantText.Token.colored(cap + "%", EnchantText.VALUE_COLOR), EnchantText.Token.plain("."))
                        : List.of(EnchantText.Token.plain("Increases damage by"), EnchantText.Token.colored(rateEn + "%", EnchantText.VALUE_COLOR),
                                EnchantText.Token.plain("for each 1% of extra health the target has above your own, up to"),
                                EnchantText.Token.colored(cap + "%", EnchantText.VALUE_COLOR), EnchantText.Token.plain(".")));
            }
            case EXPERIENCE -> {
                String chance = level == null ? "X" : number(12.5 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Adiciona"), EnchantText.Token.colored(chance + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, true),
                                EnchantText.Token.plain("de chance de mobs ou minérios dropar o dobro de orbs de XP."))
                        : List.of(EnchantText.Token.plain("Adds a"), EnchantText.Token.colored(chance + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, false),
                                EnchantText.Token.plain("chance for mobs or ores to drop double XP orbs.")));
            }
            case FIRST_STRIKE -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta o dano do primeiro golpe contra um alvo com vida cheia em"), EnchantText.Token.value(level, "%", l -> l * 25),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain("."))
                    : List.of(EnchantText.Token.plain("Increases the first hit's damage against a full-health target by"), EnchantText.Token.value(level, "%", l -> l * 25),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case LETHALITY -> {
                String amount = level == null ? "X" : number(1.2 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Reduz a"), EnchantText.Token.colored("❈ Defesa", LABEL_COLOR), EnchantText.Token.plain("do alvo em"),
                                EnchantText.Token.colored(amount, EnchantText.VALUE_COLOR), EnchantText.perLevel(level, true),
                                EnchantText.Token.plain("por acerto, por até 4 segundos, acumulando até 4 vezes."))
                        : List.of(EnchantText.Token.plain("Reduces the target's"), EnchantText.Token.colored("❈ Defense", LABEL_COLOR), EnchantText.Token.plain("by"),
                                EnchantText.Token.colored(amount, EnchantText.VALUE_COLOR), EnchantText.perLevel(level, false),
                                EnchantText.Token.plain("per hit, for up to 4 seconds, stacking up to 4 times.")));
            }
            case LIFE_STEAL -> {
                String amount = level == null ? "X" : number(0.5 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Cura"), EnchantText.Token.colored(amount + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, true),
                                EnchantText.Token.plain("da sua vida máxima a cada acerto em um mob."))
                        : List.of(EnchantText.Token.plain("Heals"), EnchantText.Token.colored(amount + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, false),
                                EnchantText.Token.plain("of your max health on every hit against a mob.")));
            }
            case LUCK -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Aumenta em"), EnchantText.Token.value(level, "%", l -> l * 5), EnchantText.perLevel(level, true),
                            EnchantText.Token.plain("a chance de mobs dropar uma peça de armadura."))
                    : List.of(EnchantText.Token.plain("Increases the chance of mobs dropping an armor piece by"), EnchantText.Token.value(level, "%", l -> l * 5),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain(".")));
            case THUNDERLORD -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("A cada 3 acertos, cai um raio no alvo causando"), EnchantText.Token.value(level, "%", l -> l * 8),
                            EnchantText.perLevel(level, true), EnchantText.Token.plain("do dano do acerto."))
                    : List.of(EnchantText.Token.plain("Every 3 hits, a lightning bolt strikes the target for"), EnchantText.Token.value(level, "%", l -> l * 8),
                            EnchantText.perLevel(level, false), EnchantText.Token.plain("of the hit's damage.")));
            case VAMPIRISM -> EnchantText.wrap(pt
                    ? List.of(EnchantText.Token.plain("Cura"), EnchantText.Token.value(level, "%", l -> l), EnchantText.perLevel(level, true),
                            EnchantText.Token.plain("da sua vida faltante sempre que matar um inimigo."))
                    : List.of(EnchantText.Token.plain("Heals"), EnchantText.Token.value(level, "%", l -> l), EnchantText.perLevel(level, false),
                            EnchantText.Token.plain("of your missing health whenever you kill an enemy.")));
            case VENOMOUS -> {
                String amount = level == null ? "X" : number(0.3 * level);
                yield EnchantText.wrap(pt
                        ? List.of(EnchantText.Token.plain("Cada acerto reduz a"), EnchantText.Token.colored("✦ Velocidade", LABEL_COLOR), EnchantText.Token.plain("do alvo e causa"),
                                EnchantText.Token.colored(amount + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, true),
                                EnchantText.Token.plain("do seu dano por segundo, empilhando globalmente até 40 vezes por 5 segundos."))
                        : List.of(EnchantText.Token.plain("Every hit reduces the target's"), EnchantText.Token.colored("✦ Speed", LABEL_COLOR), EnchantText.Token.plain("and deals"),
                                EnchantText.Token.colored(amount + "%", EnchantText.VALUE_COLOR), EnchantText.perLevel(level, false),
                                EnchantText.Token.plain("of your damage per second, stacking globally up to 40 times for 5 seconds.")));
            }
        };
    }

    /** Linear per level, except the last level ({@code maxLevel}) jumps straight to {@code jumpValue} instead of continuing the line - same shape {@code VanillaEnchantEntry#linearCapped}/{@code CombatListener#linearCapped} use, duplicated here for this class's own description text (see those two classes' own docs on why small per-class copies beat one shared method). */
    private static int lastLevelJump(int level, int maxLevel, int perLevel, int jumpValue) {
        return level >= maxLevel ? jumpValue : perLevel * level;
    }

    /** A double trimmed to a whole number when it lands on one (e.g. "1" not "1.0"), otherwise its plain decimal form (e.g. "0.2") - used by the family of descriptions above whose per-level rate doesn't land on a whole number for every level (Execute, Giant Killer, Experience, Lethality, Life Steal, Venomous). */
    private static String number(double v) {
        double rounded = Math.round(v * 100.0) / 100.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
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
