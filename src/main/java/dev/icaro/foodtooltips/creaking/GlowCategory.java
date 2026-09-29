package dev.icaro.foodtooltips.creaking;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * The four glow colors the Creaking Sight accessory line (Pale Oak Log Collection) can paint
 * a nearby creature - see {@link EntityClassifier} for how one is picked, and {@link
 * CreakingSightService} for how it's actually rendered (a per-player-only virtual scoreboard
 * team, since real team color is what drives a glowing entity's outline color client-side).
 * Declared in the exact priority order {@link EntityClassifier} checks them in (boss beats
 * hostile beats neutral beats passive), so {@code values()} itself documents that ordering.
 */
public enum GlowCategory {
    /** Boss/miniboss this plugin recognizes (see {@code bestiary.BestiaryProgressService#isBoss}) - highest priority, checked before hostility. */
    PURPLE(NamedTextColor.DARK_PURPLE, "icrs_purple"),
    /** Hostile by nature (implements {@link org.bukkit.entity.Enemy}) OR any {@link org.bukkit.entity.Mob} currently tracking a target, whichever comes first. */
    RED(NamedTextColor.RED, "icrs_red"),
    /** A known "attacks only when provoked" species (Wolf, Bee, Iron Golem, Piglin...) with no current target. */
    YELLOW(NamedTextColor.GOLD, "icrs_yellow"),
    /** Every other safely-recognized creature (an {@link org.bukkit.entity.Animals} or one of the handful of non-breedable passive types this plugin also knows about). */
    GREEN(NamedTextColor.GREEN, "icrs_green");

    private final NamedTextColor color;
    private final String teamName;

    GlowCategory(NamedTextColor color, String teamName) {
        this.color = color;
        this.teamName = teamName;
    }

    public NamedTextColor color() {
        return this.color;
    }

    /** Short, stable, per-color virtual team name - never recreated once sent for a player, see {@link CreakingSightService}. */
    public String teamName() {
        return this.teamName;
    }
}
