package dev.icaro.foodtooltips.enchant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/** Wraps one of the plugin's own {@link IcarusEnchant} constants as an {@link EnchantEntry}. */
final class CustomEnchantEntry implements EnchantEntry {
    private final IcarusEnchant enchant;

    CustomEnchantEntry(IcarusEnchant enchant) {
        this.enchant = enchant;
    }

    IcarusEnchant enchant() {
        return this.enchant;
    }

    @Override
    public String id() {
        return "custom:" + this.enchant.name();
    }

    @Override
    public String catalogName(boolean pt) {
        return this.enchant.displayName(pt);
    }

    @Override
    public String leveledName(boolean pt, int level) {
        return this.enchant.displayName(pt) + " " + EnchantService.roman(level);
    }

    @Override
    public int maxLevel() {
        return this.enchant.maxLevel();
    }

    @Override
    public int costAtLevel(int level) {
        return this.enchant.costAtLevel(level);
    }

    @Override
    public Component genericDescription(boolean pt) {
        // No numeric-placeholder concept for custom entries yet (IcarusEnchant is
        // currently empty) - plain gray text, same as resolvedDescription below.
        return Component.text(this.enchant.description(pt), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public Component resolvedDescription(boolean pt, int level) {
        return this.genericDescription(pt);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CustomEnchantEntry other && other.enchant == this.enchant;
    }

    @Override
    public int hashCode() {
        return this.enchant.hashCode();
    }
}
