package dev.icaro.foodtooltips.enchant;

/**
 * The plugin's own custom enchantments - entirely separate from (and coexisting
 * with) vanilla enchantments, applied only through the reworked Enchanting Table
 * screen (see {@code EnchantMenuService}), never randomly. Each has a flat max level;
 * the bonus per level is either a flat amount or a percentage point (see {@link
 * #percent()}) - {@code EnchantService} is what actually turns a level into a real
 * stat number, this enum only knows the numbers/text, not how to wire them into
 * combat (that wiring is a deliberate follow-up, not part of this pass).
 *
 * <p>Deliberately empty for now - the previous pass shipped its own invented list of
 * six enchants (Ferocity, Precision, Vampirism, Execution, Vitality, Spirit Ward)
 * without confirming it first, which wasn't wanted. The Enchanting Table screen itself
 * (item slot, table icon, bookshelf, guide button, catalog grid) stays fully wired and
 * working - it just has nothing to show in the catalog until real enchants are added
 * here.
 */
public enum IcarusEnchant {
    ;

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
        };
    }

    /** "+N%" / "+N" for a single level's value, matching {@link #percent()}. */
    public String formattedValue(int level) {
        double v = this.valueAtLevel(level);
        String number = v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
        return "+" + number + (this.percent ? "%" : "");
    }
}
