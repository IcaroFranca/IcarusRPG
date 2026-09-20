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
    /** "Netherite Arrow Up" (Enchanting Table's own pagination "scroll up" head - see {@code EnchantMenuService#head}). Previously inlined there, so {@code GeyserSkullExport} never saw it and Bedrock players got a blank head. */
    public static final String SCROLL_UP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMzMGM0YWI3ZDAwZmI1NWUzOWIxY2RkM2NiYzkzNDJiMTYyYzc2MTY2ZDIyNDk3MmRlZmJiZjllYzdmZmZhOCJ9fX0=";
    /** "Netherite Arrow Down" (Enchanting Table's own pagination "scroll down" head - see {@code EnchantMenuService#head}). Same previously-inlined-and-unexported issue as {@link #SCROLL_UP}. */
    public static final String SCROLL_DOWN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNmMTBiYzEwNDg3YmVhZDY2NGY2N2I0N2U4YjVhMTcwNTQyZGNjNTc5YTRjZjdjOTFjYjc1NWYwY2FiMWU3MyJ9fX0=";
    /** "Lapis Lazuli Block" (minecraft-heads.com ID 86417) - the Lapis Core crafting item, see {@code LapisExperienceService}. */
    public static final String LAPIS_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTY1NjA3NDE4ODU0M2Y5YjRhZGNkNjQ1Mjc4MzIwNjhjYmUwOGYxNTZlOWVlOGVkOWMyMDNiOGFkODVhNzZmNyJ9fX0=";
    /** "Ornate Lapis Block" (minecraft-heads.com ID 48300) - the True Lapis Core crafting item, see {@code LapisExperienceService}. */
    public static final String TRUE_LAPIS_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWMwZTkxNDQ3NmUxYjE1ZGEyYTkxZjQ1Njk2ZGQyMTc2NjlkNGRhYzRmYTYyMTY1MDkyOWJhY2UwM2RlMjI1NCJ9fX0=";
    /** "Bundle" (minecraft-heads.com ID 104707) - the Skills menu's Collections button icon. */
    public static final String BUNDLE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODZiZDkyMGI0MDI4MTVhZDg5MDE4ZGY4Mjk3N2JlOWY3ZWExOWU3OTllY2YwMTZmN2YwZGE0YWI0N2NhMjNjNSJ9fX0=";
    /** "Cactus" (minecraft-heads.com ID 67954) - the Cactus Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String CACTUS_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWFiZmRlODM5OGMxMTAyODc1Y2NlOGJmY2MzNDJlNGZlZTM0ZGNjYjQ4MzQxOTc4ZGU1MzQ2YzBiZjU0NWFhNyJ9fX0=";
    /** "Carrot" (minecraft-heads.com ID 28251) - the Carrot Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String CARROT_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjQ0OGMxODNhNzY0MDg2N2U0MjExOGU2OWMzZjRkMTVkYjFmZmIwZDkzNjQ2Yjc3MDc4ZWNlZGNhMmE0MzQ1NCJ9fX0=";
    /** "Chocolate" (minecraft-heads.com ID 119996) - the Chocolate Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String CHOCOLATE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjA5ZDIxYzAxNjU5YTQ3YWU5YTc4ZmZjMjAzMDYyYWIwMWEwNmRiMTc4ZDQxNWQzZDM0NGRmYjcyMzQ3N2RlOCJ9fX0=";
    /** "Feather Reed Grass" (minecraft-heads.com ID 84136) - the Feather Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String FEATHER_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmI3ZWFlZjMyOWRkOTM0ZTY3Y2YzZDQ4MTNjOWI4OWJmZmQ3OTRjZmE1ZWY4YjRlMTU4ZjI1YmQ1ZGQzZTM5MyJ9fX0=";
    /** "Red Mushroom" (minecraft-heads.com ID 72057) - the Mushroom Core crafting item (any mushroom type), see {@code FarmingCollectionsItemsService}. */
    public static final String MUSHROOM_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzMyZGJkNjYxMmU5ZDNmNDI5NDdiNWNhODc4NWJmYjMzNDI1OGYzY2ViODNhZDY5YTVjZGVlYmVhNGNkNjUifX19";
    /** "Watermelon" (minecraft-heads.com ID 102906) - the Melon Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String MELON_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDU1NzI2MWY4YmNhOTgxNWY3YTZjZTBjMTNhZjJhZTQyN2NiNDc1YWJjNzFkOTVmZmRhN2VjYjgwNWU2OGZkIn19fQ==";
    /** "Potato" (minecraft-heads.com ID 124379) - the Potato Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String POTATO_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGY0MDlkZWUxYTdmN2ZiODQ1NzNkOGRmMzRkZGFjYmQyMzU1ODBkY2I4NWRlZmU4ODI3NmFlODU2NmJhN2ZmYiJ9fX0=";
    /** "Pumpkin" (minecraft-heads.com ID 121024) - the Pumpkin Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String PUMPKIN_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWRiNzA3MjE4Y2QyZjcxODc4NDFjNzkwN2IzMjcyMjU5NzI5MTQyNDk5ZDliODZiODY3ZWZiOGU2ZDk3ODUyIn19fQ==";
    /** "Wheat" (minecraft-heads.com ID 53835) - the Wheat Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String WHEAT_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg2YmFkYjBkOTEzYjM5MWZiNDhkNzc3NmMzNzhjYTNmNGIyZGJlNzI0NTM0MDM0ZjM1MGNjZDM4ZjkwNDQ3MyJ9fX0=";
    /** "Potion" (minecraft-heads.com ID 120006) - the Potion Bag's own icon, see {@code skills.PotionBagService}. */
    public static final String POTION_BAG = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWZlZTViN2VjZmUyNDUyNWRkNjMyODdiZTAwOTg5ODkzNWZjODRhYTk5ZjQyZGEzZjBkMDM2ODFiMGQ1ZTE2MCJ9fX0=";
    /** "Mushroom Soup" (minecraft-heads.com ID 59559) - the Magical Mushroom Soup item, see {@code FarmingCollectionsItemsService}. */
    public static final String MUSHROOM_SOUP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTUzMWNkNWE3MjQyZjQ0NWI1ZjZkZDlhMmFmMTk0YTBkYjliMWY0ZWYxODQ3ZTJiNWY4NWE4YTdlNzJjZDY1MyJ9fX0=";
    /** "Mystical Mushroom Soup" (minecraft-heads.com ID 48195) - the Mystical Mushroom Soup item, see {@code FarmingCollectionsItemsService}. */
    public static final String MYSTICAL_MUSHROOM_SOUP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTFlNWQwYzIzZWMxYTFmODEzYzBjNjNmMTEyZTU1YjdiMWM4N2ZlY2QzMjY5YzBmZGJjZTk2ZDAzYjU1OGMwOCJ9fX0=";
    /** "Farm Crystal" (minecraft-heads.com ID 128323) - the Farm Crystal item and its own floating/spinning placed representation, see {@code FarmCrystalService}. */
    public static final String FARM_CRYSTAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjkxODBjMDVjY2I1YTVlNjQzNWFhNWMxOTg3NDIxNjZhYjk4MTkwNGM1NmJlODY5NDNmOGZhYzE1MzQ2OTBmNiJ9fX0=";
    /** Every constant above, in one place - {@code GeyserSkullExport} reads this so a texture never has to be added there by hand (and risk being forgotten) each time a new one is added here. */
    public static final Set<String> ALL = Set.of(PLANET, TRASH_CAN, ARROW_RIGHT, ARROW_LEFT, BACK, CLOSE, QUIVER, SUPER_MUSHROOM, ZOMBIE_MINER, SKELETON_MINER,
            SCROLL_UP, SCROLL_DOWN, LAPIS_CORE, TRUE_LAPIS_CORE, BUNDLE, CACTUS_CORE, CARROT_CORE, CHOCOLATE_CORE, FEATHER_CORE, MUSHROOM_CORE,
            MELON_CORE, POTATO_CORE, PUMPKIN_CORE, WHEAT_CORE, POTION_BAG, MUSHROOM_SOUP, MYSTICAL_MUSHROOM_SOUP, FARM_CRYSTAL);

    private HeadTexture() {
    }
}
