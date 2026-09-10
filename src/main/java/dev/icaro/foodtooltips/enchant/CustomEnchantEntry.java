package dev.icaro.foodtooltips.enchant;

import java.util.List;
import net.kyori.adventure.text.Component;

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
    public List<Component> genericDescription(boolean pt) {
        return this.enchant.description(pt, null);
    }

    @Override
    public List<Component> resolvedDescription(boolean pt, int level) {
        return this.enchant.description(pt, level);
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
