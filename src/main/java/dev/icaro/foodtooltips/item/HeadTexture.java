package dev.icaro.foodtooltips.item;

/**
 * Shared base64 "Value" custom player-head textures (from minecraft-heads.com) used as
 * menu icons in more than one place - kept here once instead of the same long string
 * duplicated (and prone to a copy-paste typo) across files.
 */
public final class HeadTexture {
    /** "Planet Minecraft Globe" (minecraft-heads.com ID 221) - the Locais/Locations menu button icon in /skills' main menu. */
    public static final String PLANET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjFkZDRmZTRhNDI5YWJkNjY1ZGZkYjNlMjEzMjFkNmVmYTZhNmI1ZTdiOTU2ZGI5YzVkNTljOWVmYWIyNSJ9fX0=";
    /** "Trash Can" (minecraft-heads.com ID 119077) - the Skills menu's trash button and the Trash Can screen's own drop slot. */
    public static final String TRASH_CAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmZmYjkxMjYxMmEwNmU3ZWJmODY1YmU1MGNlOWZmNjA5MTk1ZWZkMTliYmU0OTdjNjFlMjI4YzczZWY3NzU3In19fQ==";
    /** "White Arrow Right" (minecraft-heads.com ID 9223) - the "next page" icon on paginated menus (Skills, Bestiary). */
    public static final String ARROW_RIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTU2YTM2MTg0NTllNDNiMjg3YjIyYjdlMjM1ZWM2OTk1OTQ1NDZjNmZjZDZkYzg0YmZjYTRjZjMwYWI5MzExIn19fQ==";
    /** "White Arrow Left" (minecraft-heads.com ID 9226) - the "previous page" icon on paginated menus (Skills, Bestiary). */
    public static final String ARROW_LEFT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvY2RjOWU0ZGNmYTQyMjFhMWZhZGMxYjViMmIxMWQ4YmVlYjU3ODc5YWYxYzQyMzYyMTQyYmFlMWVkZDUifX19";
    /** "Black Backward II" (minecraft-heads.com ID 8785) - the "back to previous menu" icon used across every menu. */
    public static final String BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTE4YTJkZDViZWYwYjA3M2IxMzI3MWE3ZWViOWNmZWE3YWZlODU5M2M1N2E5MzgyMWU0MzE3NTU3MjQ2MTgxMiJ9fX0=";
    /** "Red X" (minecraft-heads.com ID 9382) - the "close this menu" icon, replacing a plain Barrier everywhere it's used as one. */
    public static final String CLOSE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzU1MmRjYjA1MGVmYzljYWI0MjdmNDYwNDhmMThmYzgwMzQ3NWY3In19fQ==";

    private HeadTexture() {
    }
}
