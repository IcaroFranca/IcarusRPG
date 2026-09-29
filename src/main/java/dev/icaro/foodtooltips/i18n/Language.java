package dev.icaro.foodtooltips.i18n;

import org.bukkit.entity.Player;

public enum Language {
    PT,
    EN;

    /** The server is English-only; the player's client locale is intentionally ignored. */
    public static Language of(Player p) {
        return EN;
    }

    public String choose(String pt, String en) {
        return this == PT ? pt : en;
    }
}

