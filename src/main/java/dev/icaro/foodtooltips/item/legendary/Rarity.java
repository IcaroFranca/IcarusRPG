package dev.icaro.foodtooltips.item.legendary;

import net.kyori.adventure.text.format.NamedTextColor;

/** Display color and label for a {@link LegendaryWeapon}'s rarity tier - purely cosmetic, unrelated to {@code ItemTier} (legendary weapons opt out of that generic system entirely, see {@code ItemTierService#tooltip}). */
public enum Rarity {
    C(NamedTextColor.GRAY, "Classe C", "C-Rank"),
    B(NamedTextColor.GREEN, "Classe B", "B-Rank"),
    A(NamedTextColor.AQUA, "Classe A", "A-Rank"),
    S(NamedTextColor.LIGHT_PURPLE, "Classe S", "S-Rank"),
    MYTHIC(NamedTextColor.GOLD, "??", "??");

    private final NamedTextColor color;
    private final String labelPt;
    private final String labelEn;

    Rarity(NamedTextColor color, String labelPt, String labelEn) {
        this.color = color;
        this.labelPt = labelPt;
        this.labelEn = labelEn;
    }

    public NamedTextColor color() {
        return this.color;
    }

    public String label(boolean pt) {
        return pt ? this.labelPt : this.labelEn;
    }
}
