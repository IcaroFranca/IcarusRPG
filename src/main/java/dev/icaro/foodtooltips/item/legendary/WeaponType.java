package dev.icaro.foodtooltips.item.legendary;

/**
 * What kind of melee weapon a {@link LegendaryWeapon} is - decides which universal
 * mechanic applies on top of its own unique effect: {@link #DAGGER}s get the -1 Swing
 * Range penalty and the backstab damage double (see {@code LegendaryWeaponService}),
 * {@link #LONGSWORD} gets neither.
 */
public enum WeaponType {
    DAGGER, LONGSWORD;

    public String label(boolean pt) {
        return switch (this) {
            case DAGGER -> pt ? "Adaga" : "Dagger";
            case LONGSWORD -> pt ? "Espada Longa" : "Longsword";
        };
    }
}
