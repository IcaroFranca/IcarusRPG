package dev.icaro.foodtooltips.power;

import org.bukkit.Material;

/**
 * One selectable Power preset: a fixed set of BASE stat bonuses (per the player's own reference
 * table/image), each scaled live by {@link MagicalPowerService#statsMultiplier(double)} at the
 * player's current total Magical Power - never a fixed amount, since Magical Power itself
 * changes as the player swaps accessories (see {@code skills.AccessoryBagService#totalMagicalPower}).
 * Mirrors {@code global.LevelColorTheme}'s own "named preset, found by id" shape. A stat this
 * Power doesn't grant is simply {@code 0.0} (most rows in the source table only use 4-7 of the
 * 8 stats below).
 */
public record Power(String id, String name, PowerType type, Material icon,
        double health, double defense, double strength, double speed,
        double critChance, double critDamage, double intelligence, double miningSpeed) {
}
