package dev.icaro.foodtooltips.reforge;

/**
 * The stat bonuses one {@link ReforgePrefix} grants at one {@code ItemTier}. Strength and
 * Intelligence are flat points (same units as the Skills stats screen); Crit Chance, Crit
 * Damage and Attack Speed are percentages. Zero means "this reforge doesn't touch that stat
 * at this tier" - {@link ReforgeMenuService}/{@code ReforgeService} skip a zero stat's lore
 * line entirely rather than printing a "+0" line.
 */
public record ReforgeStats(double strength, double critChance, double critDamage, double intelligence, double attackSpeed) {
    static final ReforgeStats NONE = new ReforgeStats(0, 0, 0, 0, 0);
}
