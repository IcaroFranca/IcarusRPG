package dev.icaro.foodtooltips.item.legendary;

/**
 * What kind of melee weapon a {@link LegendaryWeapon} is - decides which universal
 * mechanic applies on top of its own unique effect: {@link #DAGGER}s get the backstab
 * damage double at plain vanilla Swing Range (see {@code LegendaryWeaponService}),
 * {@link #LONGSWORD} gets +2 range instead, {@link #SWORD} gets neither - for weapons
 * whose whole identity is a situational damage bonus rather than a reach/positioning
 * gimmick.
 */
public enum WeaponType {
    DAGGER, LONGSWORD, SWORD;

    public String label(boolean pt) {
        return switch (this) {
            case DAGGER -> pt ? "Adaga" : "Dagger";
            case LONGSWORD -> pt ? "Espada Longa" : "Longsword";
            case SWORD -> pt ? "Espada" : "Sword";
        };
    }
}
