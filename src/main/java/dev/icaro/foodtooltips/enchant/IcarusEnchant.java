package dev.icaro.foodtooltips.enchant;

/**
 * The plugin's own custom enchantments - entirely separate from (and coexisting
 * with) vanilla enchantments, applied only through the reworked Enchanting Table
 * screen (see {@code EnchantMenuService}), never randomly. Each has a flat max level;
 * the bonus per level is either a flat amount or a percentage point (see {@link
 * #percent()}) - {@code EnchantService} is what actually turns a level into a real
 * stat number, this enum only knows the numbers/text, not how to wire them into
 * combat (that wiring is a deliberate follow-up, not part of this pass).
 */
public enum IcarusEnchant {
    /** Flat Ferocity per level - more extra-hit chance from the existing Ferocity mechanic. */
    FEROCITY("Fúria", "Ferocity", 20.0, false, 5, 3),
    /** Crit Chance percentage points per level. */
    PRECISION("Precisão", "Precision", 4.0, true, 5, 3),
    /** % of damage dealt healed back per level. */
    VAMPIRISM("Vampirismo", "Vampirism", 3.0, true, 5, 4),
    /** Bonus damage percentage per level against a target below half health. */
    EXECUTION("Execução", "Execution", 6.0, true, 5, 4),
    /** Health Regen percentage points per level. */
    VITALITY("Fôlego", "Vitality", 15.0, true, 5, 3),
    /** Flat True Defense per level - the first source config.yml's base-true-defense comment reserves this stat for. */
    SPIRIT_WARD("Couraça Espiritual", "Spirit Ward", 2.0, false, 5, 5);

    private final String namePt;
    private final String nameEn;
    private final double perLevel;
    private final boolean percent;
    private final int maxLevel;
    private final int costPerLevel;

    IcarusEnchant(String namePt, String nameEn, double perLevel, boolean percent, int maxLevel, int costPerLevel) {
        this.namePt = namePt;
        this.nameEn = nameEn;
        this.perLevel = perLevel;
        this.percent = percent;
        this.maxLevel = maxLevel;
        this.costPerLevel = costPerLevel;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }

    public int maxLevel() {
        return this.maxLevel;
    }

    /** Total effect at {@code level} (not incremental) - e.g. level 3 of a 4.0-per-level percent enchant is 12.0%. */
    public double valueAtLevel(int level) {
        return this.perLevel * level;
    }

    public boolean percent() {
        return this.percent;
    }

    /** XP levels (vanilla, like an anvil) needed to apply exactly this one level - not cumulative from level 1. */
    public int costAtLevel(int level) {
        return this.costPerLevel * level;
    }

    /** One line explaining what this enchantment does, independent of any specific level - shown in the guide and as an item's lore (see EnchantService's 4-or-fewer rule). */
    public String description(boolean pt) {
        return switch (this) {
            case FEROCITY -> pt ? "Aumenta a Ferocity, sua chance de acertar golpes extras." : "Increases Ferocity, your chance of landing extra hits.";
            case PRECISION -> pt ? "Aumenta sua Chance Crítica." : "Increases your Crit Chance.";
            case VAMPIRISM -> pt ? "Cura uma % do dano que você causa." : "Heals a % of the damage you deal.";
            case EXECUTION -> pt ? "Dano bônus contra alvos com menos da metade da vida." : "Bonus damage against targets below half health.";
            case VITALITY -> pt ? "Aumenta sua Regeneração de Vida." : "Increases your Health Regen.";
            case SPIRIT_WARD -> pt ? "Aumenta sua Defesa Verdadeira, ignorando reduções percentuais." : "Increases your True Defense, bypassing percentage-based reductions.";
        };
    }

    /** "+N%" / "+N" for a single level's value, matching {@link #percent()}. */
    public String formattedValue(int level) {
        double v = this.valueAtLevel(level);
        String number = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return "+" + number + (this.percent ? "%" : "");
    }
}
