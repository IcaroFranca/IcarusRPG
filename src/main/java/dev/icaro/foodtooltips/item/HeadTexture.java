package dev.icaro.foodtooltips.item;

import java.util.Set;

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
    /** "Quiver" (minecraft-heads.com ID 41389) - the Skills menu's Quiver button and the Quiver screen's own title bar icon, unlocked at Combat level 5. */
    public static final String QUIVER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzhiYmFhMGU0MjBiNjNlNmYyODUwNDZmNGExN2FkNjgyNGY5YzU5ZGM4M2M5ODQ3ZGU0NjU3MGJiZGY5ZmQ0OCJ9fX0=";
    /** "Super Mushroom" (minecraft-heads.com ID 40156) - the Skills menu's Passive Abilities button. */
    public static final String SUPER_MUSHROOM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmY5NmI4ZDAxZjU4MzVlZDM4YWZkNDUzMDIyOGQwYjVhYmI3ZDQ1YTM1NTUxOWVhNjgwYzQwZmZjYTMyZWRmMiJ9fX0=";
    /** "Zombie Miner" (minecraft-heads.com ID 84875) - worn as the Zombie Miner's own helmet in place of a plain Diamond Helmet. */
    public static final String ZOMBIE_MINER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNThiZTA1Y2ZhZTJjNmE3ZDQ3ZGEyY2U4OGIzZTAwYzcyYTE0NWNjMzIxOGYwNDFiM2RkNWJkNWZhNWNhODI3In19fQ==";
    /** "Skeleton Miner" (minecraft-heads.com ID 86234) - worn as the Skeleton Miner's own helmet in place of a plain Diamond Helmet. */
    public static final String SKELETON_MINER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzhmNmMwNDRhMWI0ZGI2ZjgxMDJjYjUxZTJjMzZjNmIzMjc1MTEyOGFiYmQxOGE1ZDljOWM2N2E1NWIxOWFmYiJ9fX0=";
    /**
     * "Miner Helmet" (minecraft-heads.com ID 26723) - shown only on a real Miner's
     * Helmet item (dropped by a Miner or given by /rpgitems), never on the mob's own
     * worn instance, which keeps {@link #ZOMBIE_MINER}/{@link #SKELETON_MINER} - see
     * {@code MinerVariantService#retextureDroppedHelmet}. Deliberately plain Base64,
     * not layered with the IcarusTexture resource pack's own skin patch: combining both
     * on one item broke worn-helmet rendering entirely on Bedrock (via Geyser) - not
     * just an unregistered/default look, no headwear rendered at all.
     */
    public static final String MINER_HELMET_DROP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmJiYTk4NWRjMGI0YTBhMzQ2ZDVkNDRlOWNmNjgwYmNkMzBkZjA1ZDIzOGYzNzRkZjM3MDYxMjUyYWNmOTZlNiJ9fX0=";
    /** "Netherite Arrow Up" (Enchanting Table's own pagination "scroll up" head - see {@code EnchantMenuService#head}). Previously inlined there, so {@code GeyserSkullExport} never saw it and Bedrock players got a blank head. */
    public static final String SCROLL_UP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMzMGM0YWI3ZDAwZmI1NWUzOWIxY2RkM2NiYzkzNDJiMTYyYzc2MTY2ZDIyNDk3MmRlZmJiZjllYzdmZmZhOCJ9fX0=";
    /** "Netherite Arrow Down" (Enchanting Table's own pagination "scroll down" head - see {@code EnchantMenuService#head}). Same previously-inlined-and-unexported issue as {@link #SCROLL_UP}. */
    public static final String SCROLL_DOWN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNmMTBiYzEwNDg3YmVhZDY2NGY2N2I0N2U4YjVhMTcwNTQyZGNjNTc5YTRjZjdjOTFjYjc1NWYwY2FiMWU3MyJ9fX0=";
    /** Every constant above, in one place - {@code GeyserSkullExport} reads this so a texture never has to be added there by hand (and risk being forgotten) each time a new one is added here. */
    public static final Set<String> ALL = Set.of(PLANET, TRASH_CAN, ARROW_RIGHT, ARROW_LEFT, BACK, CLOSE, QUIVER, SUPER_MUSHROOM, ZOMBIE_MINER, SKELETON_MINER,
            MINER_HELMET_DROP, SCROLL_UP, SCROLL_DOWN);

    private HeadTexture() {
    }
}
