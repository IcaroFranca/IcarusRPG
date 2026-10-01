package dev.icaro.foodtooltips.i18n;

import org.bukkit.entity.Player;

/**
 * Portuguese support has been removed from the whole plugin - every piece of in-game text
 * is English-only now, regardless of a player's own client locale. {@link #PT} and {@link
 * #of} are kept (rather than deleting this type outright) only because dozens of call sites
 * across the codebase still thread a {@code Language}/{@code boolean pt} value through for
 * now - all of them resolve to English either way, since {@link #of} never returns {@link
 * #PT} anymore and every consumer that used to branch on it (see e.g. {@code
 * item.AccessoryType#displayName}) was updated to ignore that value and return its own
 * English text unconditionally.
 */
public enum Language {
    PT,
    EN;

    /** Always {@link #EN} now - see this class's own doc. */
    public static Language of(Player p) {
        return EN;
    }
}

