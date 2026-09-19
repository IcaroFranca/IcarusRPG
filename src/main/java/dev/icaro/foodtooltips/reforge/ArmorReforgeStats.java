package dev.icaro.foodtooltips.reforge;

/**
 * The stat bonuses one {@link ArmorReforgePrefix} grants at one {@code ItemTier}, all
 * per equipped piece (see {@link ReforgeService#totalArmorStats} for the sum across all
 * four). Health, Defense, Strength, Agility and Intelligence are flat points (same units
 * the Skills stats screen already uses for each); Crit Chance, Crit Damage and Attack
 * Speed are percentages. Zero means "this reforge doesn't touch that stat at this tier".
 */
public record ArmorReforgeStats(double health, double defense, double strength, double critChance,
                                 double critDamage, double agility, double attackSpeed, double intelligence) {
    static final ArmorReforgeStats NONE = new ArmorReforgeStats(0, 0, 0, 0, 0, 0, 0, 0);

    ArmorReforgeStats plus(ArmorReforgeStats other) {
        return new ArmorReforgeStats(
                this.health + other.health, this.defense + other.defense, this.strength + other.strength,
                this.critChance + other.critChance, this.critDamage + other.critDamage,
                this.agility + other.agility, this.attackSpeed + other.attackSpeed, this.intelligence + other.intelligence);
    }
}
