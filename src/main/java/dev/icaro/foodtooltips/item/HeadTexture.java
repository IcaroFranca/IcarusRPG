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
    /** "Cow (temperate)" (minecraft-heads.com ID 115920) - the Cow Hat item, see {@code CowHatService}. */
    public static final String COW_HAT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGY4Y2UzOTE1YTIxMTY4ZDgzOTQyMTQ1NTJjZGI1NjUyZTg1NWU5ZTM4MjAwMWQ1OTY5NzJmZTNjMDA5ZWE3ZCJ9fX0=";
    /** "Milk Bucket" (minecraft-heads.com ID 126180) - the Milk Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String MILK_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjRlZmNiOTJmYzFlMTRkMjRmMmMyY2JiYWQ0ODQyN2RjYWNmYWZkODQzMmJiNGMyNjIxZjVkNDA3YWUzMWNkMiJ9fX0=";
    /** "Banana Milkshake" (minecraft-heads.com ID 55176) - the Milkshake Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String MILKSHAKE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjk2MGI3N2VjMDViNjAyYjU1ZjBmMzYzYzlmODY0YTFhYjcyNjRhMWZiZTRjM2I0OTIyOWJhNzQzMTJhZTVmOCJ9fX0=";
    /** "Wool (white)" (minecraft-heads.com ID 18239) - the Wool Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String WOOL_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDA4ZGY2MGM1MTA3NGVlZjI1NDRmZjM4Y2VhZDllMTY2NzVhZTQyNTE5MTYxMDUxODBlMWY4Y2UxOTdhYjNiYyJ9fX0=";
    /** "Wool (rainbow)" (minecraft-heads.com ID 18667) - the Rainbow Wool Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String RAINBOW_WOOL_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDRiMDM3OTRiOWIzZTNiNWQwN2UzYmU2OGI5NmFmODdkZjIxNWMzNzUyZTU0NzM2YzgwZjdkNTBiZDM0MzdhNCJ9fX0=";
    /** "Nether Wart Block" (minecraft-heads.com ID 52965) - the Nether Wart Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String NETHER_WART_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzEzMGY3ZjJjMjhhODFlZWI0ZTI5YjM0OTk5NDA5MDhlNmRmNjM3NzNiYmQ0MmMzMThjMWVkNTI0YjE4ODQwMyJ9fX0=";
    /** "Nether Wart" (minecraft-heads.com ID 23329) - the Mutant Nether Wart Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String MUTANT_NETHER_WART_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTExYTNjZWM3YWFmOTA0MjEyY2NmOTNiYjY3YTNjYWYzZDY0OTc4M2JhOTBiOGI2MGJiNjNjNzY4N2ViMzlmIn19fQ==";
    /** "Jack O'Lantern" (minecraft-heads.com ID 115662) - the Lantern Helmet item, see {@code FarmingCollectionsItemsService}. */
    public static final String LANTERN_HELMET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWNjYmM4NGQ2MGNmNDA5OWE1NGRiNjY1NGJiNWFkODNiZWM4ZjRlMGIwYjNkMDAzYjBlODcxOGZkOGYwYWMyYSJ9fX0=";
    /** "Rabbit" (minecraft-heads.com ID 129537) - the Rabbit Armor's own helmet, see {@code RabbitArmorService}. */
    public static final String RABBIT_ARMOR_HELMET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGEwY2EyYjkwZDgzMWYxN2Y3YzhiNzcxZmIzOTczY2NhMmQ3NmE0NTZiYjdiNDdiODBkNjg3MmIwNWIyNTFmNSJ9fX0=";
    /** "Sugar Cane" (minecraft-heads.com ID 20) - the Sugar Cane Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String SUGAR_CANE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODYyNGJhY2I1ZjE5ODZlNjQ3N2FiY2U0YWU3ZGNhMTgyMGE1MjYwYjYyMzNiNTViYTFkOWJhOTM2Yzg0YiJ9fX0=";
    /** "Oak Log" (minecraft-heads.com Custom Head ID 89446) - the Oak Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String OAK_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzI3ODU5YjdjZWEzODgzNjJjMTkyOThiZjIyMWFlZDAzOWVlNzQ5MDMzNWIyYTJlMmJhNmJlOGQxM2M0NTNhNyJ9fX0=";
    /** "Birch Log" (minecraft-heads.com Custom Head ID 89448) - the Birch Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String BIRCH_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYWY5ZGI4ZDY3NTE2NjA2ZmNiM2I2NGYzODlhZGY2MTYxM2FhZDY5Yzk1MThhNmNlMjc3ZjlmYTZmYmJlZWU3MyJ9fX0=";
    /** "Spruce Log" (minecraft-heads.com Custom Head ID 89447) - the Spruce Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String SPRUCE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGFjYzU4ODhmMDc2MDQyY2QyMDM0MmM4MzVmOWQ2Y2I4YjgzN2UzNTAyYjRiZWUzYzhkMDI2MDQ4OTFjNzE1NSJ9fX0=";
    /** "Woodcutting Crystal" (minecraft-heads.com Custom Head ID 128316) - the Woodcutting Crystal item and its own floating/spinning placed representation, see {@code WoodcuttingCrystalService}. */
    public static final String WOODCUTTING_CRYSTAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWRjM2I5YTRjNTkxY2Q3YmJlZDNlMTZiMzRhZDhkNTA3NjA4ZmJlNDRkYzcyOGE3ZWQxNDFmMmUxNzI5ZDgyIn19fQ==";
    /** "Wheat Crystal" on minecraft-heads.com (Custom Head ID 128322) - that's the head's own name on that site, not the item's; it's used in-game as the Animal Crystal item and its own floating/spinning placed representation, see {@code AnimalCrystalService}. */
    public static final String ANIMAL_CRYSTAL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTgzMDJmZjIyYTIzMmUxMGQ2Y2Y2MGJkZDRkMjgwNjFjYWFhOTc0Y2FmOTA3YTBkYzNkZjk3ZTA3MWU0MmQzMSJ9fX0=";
    /** "Dark Oak Log" (minecraft-heads.com Custom Head ID 89451) - the Dark Oak Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String DARK_OAK_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzM2ZGZiNGM4ZDY5NzFkZDk2ZjVhMWZlNGI0NzIwYTM5NTE2MTcwMmQ2YzI5YWI4NzE0MjJlNjYwNjNkYmIwYSJ9fX0=";
    /** "Acacia Log" (minecraft-heads.com Custom Head ID 89450) - the Acacia Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String ACACIA_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmY4MDJhMjc4NGYxOWQ5MTFjMzczZDM3Y2Q5ZWYzOTlkYzIyM2UxNmQxODg2NGE2YTdiZmUwNzZhY2YwM2NjYiJ9fX0=";
    /** "Jungle Log" (minecraft-heads.com Custom Head ID 89449) - the Jungle Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String JUNGLE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTljNmM0MmU2OTFhZWIwNzhiMGQ4NDg5NGIxMGI2NDNiYTM2YThiY2I4YjZiZWQ5MTE5YWUyYmRmYTg3YTlhNiJ9fX0=";
    /** "Mangrove Log" (minecraft-heads.com Custom Head ID 89452) - the Mangrove Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String MANGROVE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTEyOGYzMjlhYzhkYWM2YTcxMGZhNzUyNDg2MGJjOWNhZmRmMjljNTNiODMwNjg1ZTFhMzZhYjNiMTBiYjBkYSJ9fX0=";
    /** "Mangrove Log (spring, rounded, sideways)" (minecraft-heads.com Custom Head ID 51564) - the Flowered Mangrove Core crafting item (the Mangrove Sweep Artifact's own power ingredient), see {@code ForagingCollectionsItemsService}. */
    public static final String FLOWERED_MANGROVE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWEyY2FiODlmNTlmOWQ5YTVlM2EwMDhjNDY4NTBkMzlkMzE5ZjQyOTY5MDk1MTljZTEyYjZmNzBiOTFlNjQ0MCJ9fX0=";
    /** "Cherry Log" (minecraft-heads.com Custom Head ID 89453) - the Cherry Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String CHERRY_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTAzZDEzNTMzM2ZkOWViOWZmMDM5YzEwMzY0Yjk3YjE2ZDllNzc5NzQ4Y2FhMWJlYWYxYjc1ZWRmMDBjOWE0OCJ9fX0=";
    /** "Stripped Cherry Log" (minecraft-heads.com Custom Head ID 89465) - the Pink Cherry Core crafting item (the Cherry Fortune Artifact's own power ingredient), see {@code ForagingCollectionsItemsService}. */
    public static final String PINK_CHERRY_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTJjZTQ4Mzc3N2ZmYWUyNDZlNjAxMDU2NGNkZjlhYTJiZGYwODFjZDI0MzYyZmFlNzVmZTRlMWEwMjgzODdkYiJ9fX0=";
    /** "Crimson Stem" (minecraft-heads.com Custom Head ID 89455) - the Crimson Core crafting item, see {@code ForagingCollectionsItemsService}. */
    public static final String CRIMSON_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTJlNmJiY2QyMjgyMjU5NGUxNDIyZTM0NWQ4MWVhODEzMjA0ODBmOTBlODU3MzcyY2ZjNDU4NjU3ZDFkNGI0ZiJ9fX0=";
    /** "Crimson Tree" (minecraft-heads.com Custom Head ID 51044) - the True Crimson Core crafting item (the Ember Artifact's own power ingredient), see {@code ForagingCollectionsItemsService}. */
    public static final String TRUE_CRIMSON_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2IwMzFjZTVhMjk2YWRiOTZhMzUzNjM0MTJkYmEyMTVkZTYxYzI1Zjc3NjhiMmQ4NmYyYzcyZDI3OTZkNjQ2In19fQ==";
    /** "Smoldering Embers" (minecraft-heads.com Custom Head ID 127561) - the Crimson Stem Collection's Ember Talisman accessory, see {@code ForagingCollectionsItemsService}. */
    public static final String EMBER_TALISMAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmEwYjQ1YjM5YTdiYjY2Zjg0YzE3MjM0ZjgzMDUwY2NhNDU2Zjk4NjE2NjIzMGFmMWQyOTI3MDliOGE3N2U1MSJ9fX0=";
    /** "Embers" (minecraft-heads.com Custom Head ID 89573) - the Crimson Stem Collection's Ember Ring accessory, see {@code ForagingCollectionsItemsService}. */
    public static final String EMBER_RING = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWYwZmE0MTgwMGNjOTkyMzEwN2Y2MjZjYjllOTY5ZWFjMGNiMGUxOTA0ZGZhODAwMzFhZTU4MWZmNzQxYmYyZCJ9fX0=";
    /** "Rekindled Ember Fragment" (minecraft-heads.com Custom Head ID 52821) - the Crimson Stem Collection's Ember Artifact accessory, see {@code ForagingCollectionsItemsService}. */
    public static final String EMBER_ARTIFACT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOGRjZmYxYWNjMTJmMTZkNDRhZWUxZjdlODQ0MTRjYjJlOGVhN2ViNDY0MmU3ZDI5MmQ3NmYxYzE3YjRiNDM0YSJ9fX0=";
    /** "Cactus Flower" (minecraft-heads.com Custom Head ID 127271) - the Flower Cactus Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String FLOWER_CACTUS_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmFhMWQ0OTU3Y2E0NTg5OWRmMTQ3OGUyM2M5NTc4MmUzNDQxYmQ1MjJhZDA4NzcxYjYwYzU0ZjhkYzA2M2MzOSJ9fX0=";
    /** "Golden Carrots" (minecraft-heads.com Custom Head ID 14339) - the Golden Carrot Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String GOLDEN_CARROT_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTU1YjQ4OTg2NDJiZDQ4M2I2NzM2MTM2NjFhZmQ2N2U0ZDE0ZTQ0NGUzMjZjNGYzNjMxNTY0NWE0ZDMxMyJ9fX0=";
    /** "Chocolate Truffle With Sprinkles" (minecraft-heads.com Custom Head ID 92101) - the True Chocolate Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String TRUE_CHOCOLATE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGY5OTEyMTVlMjU4ZDY1Zjc3ZWVkZWI2OTM1NmY1NTIxYmIyNGNiOWRkNDc3NjVlMzhmNTJhODkyZDc5ODlhZCJ9fX0=";
    /** "Vaccine Ring" (minecraft-heads.com Custom Head ID 59341) - the Potato Collection's Vaccine Ring accessory, see {@code FarmingCollectionsItemsService}. */
    public static final String VACCINE_RING = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQ4ZWU5MmI5ZWYxZGQ5ZjYwODNkYWIxMTNmZTE3MDhmZGU0ZjQwMzlhZDJmODVmMDZmMzE3Y2VmZTQ5ZjNhZCJ9fX0=";
    /** "Baked Potato" (minecraft-heads.com Custom Head ID 124381) - the Baked Potato Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String BAKED_POTATO_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDczZGUwYjBlZTViOWM2MWFhZWM3NjNhZTU0OGVjOTkyZDIwZDM0NWE5NzRlZWJjMjI2OWQ1MGZhZmRjM2QxYSJ9fX0=";
    /** "Vaccine Artifact" (minecraft-heads.com Custom Head ID 59340) - the Potato Collection's Vaccine Artifact accessory, see {@code FarmingCollectionsItemsService}. */
    public static final String VACCINE_ARTIFACT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzQzMzIwNjdmOWZhMTY5NWMzZDU3N2MzZWYyNjQzNTViZWUxNjFiNWUzYTMwMTkwNmE0ZjQ0M2IwMDJiOTJmYSJ9fX0=";
    /** "Pumpkin" (minecraft-heads.com Custom Head ID 4598) - the Pumpkin Collection's Farmer Orb accessory, see {@code FarmingCollectionsItemsService}. */
    public static final String FARMER_ORB = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzNiMTcyYTk3OWVmZjliZjViZmI4MGViODUxNWQ5M2U0ODg4Y2M0MWNkODA2NDczZjJhOWEwZDhiZDI5ZTMifX19";
    /** "Brown Mushroom Cap" (minecraft-heads.com Custom Head ID 94344) - the Mushroom Collection's Night Vision Charm accessory, see {@code FarmingCollectionsItemsService}. */
    public static final String NIGHT_VISION_CHARM = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmZkMmJkMmI1ZjhhYWViZjlmM2UwMjI1NzNkMjVmMDAxOGI5YmY1NmU1MjdiMzdiZjM3NmE5NGU0ZjAxZmZjOSJ9fX0=";
    /** "Potion of Invisibility" (minecraft-heads.com Custom Head ID 129621) - the Nether Wart Collection's Potion Affinity Talisman accessory, see {@code FarmingCollectionsItemsService}. */
    public static final String POTION_AFFINITY_TALISMAN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWE0NTc4ZjNlN2Q2ZjA0MWIzNTVmNWYyZDAwYzk1MzQ2YmNlZThiYTNhMzg4MzNhZDQ0ODdmN2Q1YmNiNTY5ZiJ9fX0=";
    /**
     * "Potion of Swiftness" (minecraft-heads.com Custom Head ID 129618) - the Nether Wart
     * Collection's Potion Affinity Ring AND Artifact accessories both, see {@code
     * FarmingCollectionsItemsService}: the player gave the exact same {@code /give} texture for
     * both, so both share this one constant rather than two identical copies of it.
     */
    public static final String POTION_AFFINITY_RING_AND_ARTIFACT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRkMWE0MTA4ZTc5MGUxMjQxYTNkMjNlM2QxNjM3ZTkyMDI2OWYyMDRlM2U0Zjg5MjcwZWQ3MTIzNGZiODVlYiJ9fX0=";
    /** "Beetroot" (minecraft-heads.com Custom Head ID 47449) - the Beetroot Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String BEETROOT_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZWM5ZTE3MTg3N2EzMTA3MzhmYTM3NzVjODAzOWE3N2FiMTQxNDE3ZjU2ZDI3NmE3MzE4OWM5NzQ5MjBlMjRkMiJ9fX0=";
    /** "Picnic Basket" (minecraft-heads.com Custom Head ID 11930) - the Basket of Seeds item, see {@code BasketOfSeedsService}. */
    public static final String PICNIC_BASKET = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2E2YmY5MTZlMjhjY2I4MGI0ZWJmYWNmOTg2ODZhZDZhZjdjNGZiMjU3ZTU3YThjYjc4YzcxZDE5ZGNjYjIifX19";
    /** "Fire Flower" (minecraft-heads.com Custom Head ID 124104) - the Torchflower Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String TORCHFLOWER_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE2NmNhZGY0YjY2MWNiZTQ1YTA1MGJiMWE1NzFlY2MyYzAxNTQwNDg5MjVkOWYwYmMyYWMyMWI3MjRiZWFlNiJ9fX0=";
    /** "Fire Snake (head)" (minecraft-heads.com Custom Head ID 124144) - the Radiant Torchflower Core crafting item, {@link #TORCHFLOWER_CORE}'s own upgrade - see {@code FarmingCollectionsItemsService}. */
    public static final String RADIANT_TORCHFLOWER_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTQ2M2YxMjdiMmI5OTkxMDdmNWIzNmEzOGU0NGI3N2RjZGYyZjhhNzQ4ODg1MGYzM2QyM2M0NTc2YjA5OTAwMCJ9fX0=";
    /** "Seeds" (minecraft-heads.com Custom Head ID 13596) - the Wheat Seeds Core crafting item, see {@code FarmingCollectionsItemsService}. */
    public static final String WHEAT_SEEDS_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTY3NjEzNzhmN2Y5M2JjMmI4YmU4OTM1YWNlMmM5MmQ0ZmMyYzFjNzVjNDMyOGNhYjVjYzA5NzU4N2NiOWVlIn19fQ==";
    /** "Bone Block" (minecraft-heads.com Custom Head ID 104632) - the Bone Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String BONE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzMwMTM5MmJmMDUyZjkxZDNhYTU2YjVhNWMxN2Q1ZWEyNjFlMTJjNWRiZmViOGYwNGZlMmEwMjhkMzBmYTU5NCJ9fX0=";
    /** "Pile of Bones" (minecraft-heads.com Custom Head ID 71780) - the Pile of Bone Core crafting item, {@link #BONE_CORE}'s own upgrade - see {@code CombatCollectionsItemsService}. */
    public static final String PILE_OF_BONE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjMxY2IwMTYzYjg2YjRjMjJlZjMxYjlkZDk3YzI1YTU3OWY5OWY3YTNhOWFhMmRmM2ZiZTE2ZTMyMzVkOTY2ZiJ9fX0=";
    /** "Rotten Apple" (minecraft-heads.com Custom Head ID 65522) - the Rotten Flesh Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String ROTTEN_FLESH_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2RhNmJhOTU1N2ZhMjNiNjBlOWFmODYyY2U4ZTkwNmIxMzlkNDljYzkwOTUzZDQ1MTRjZDA2OGQwNThjMTgxNyJ9fX0=";
    /** "Heart (moldy)" (minecraft-heads.com Custom Head ID 60520) - the Zombie's Heart item, see {@code item.ZombiesHeartService}. */
    public static final String ZOMBIES_HEART = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjUwYWFjYzE3YTA5ZDkwMzNkNTQ1NzliNjNiMTY0OGI3MzZkMjc0MDUwZmU4N2JkNGMxMDExNjYyMjhkZjEzMCJ9fX0=";
    /** "Spider" (minecraft-heads.com Custom Head ID 119773) - the Spider Hat item, see {@code item.SpiderHatService}. */
    public static final String SPIDER_HAT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkODYyZWFhNWFiYTIxNTk4ODdlY2NjYzkzNTk0NDA5OTY4ZmMzZjY0MTg5NjZmMzFhZjk4ZGNmZWFhNmNlOCJ9fX0=";
    /** "Spider Eye" (minecraft-heads.com Custom Head ID 96941) - the Spider Eye Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String SPIDER_EYE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjA5NWVjMDMyNDA5ZGE1YjRhNzMxMTg3NTBjMWExMTFhNDU0MzIxYmQ4NWEyODJjNDE3MGM4NDQ3NTQ0MzhlYiJ9fX0=";
    /** "Fermented Spider Eye" (minecraft-heads.com Custom Head ID 96942) - the Fermented Spider Eye Core crafting item, {@link #SPIDER_EYE_CORE}'s own upgrade - see {@code CombatCollectionsItemsService}. */
    public static final String FERMENTED_SPIDER_EYE_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTAxMjhlOWRiNGI1NDRlMTQ3ZWM2OGY5NGQ4NWY5ZGI4MTA5OTRhZWI5NDNiMDM4ZGQ0OTFlYTJlYTlhNDY5NiJ9fX0=";
    /** "Spider Web Cocoon" (minecraft-heads.com Custom Head ID 61720) - the String Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String STRING_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZmI1ODY0MWU3NmRhODI2MjE3MDhhM2Q2YzEwYmI0NTBjMjNkNDc1ZTUyMTAzMTBkMGI4N2U0NjBhNWZjMjM1NCJ9fX0=";
    /** "Gunpowder" (minecraft-heads.com Custom Head ID 125399) - the Gunpowder Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String GUNPOWDER_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzBhOWU3NDM5NGMyNzBhZTY1MmQwODM2NGZkZTJlZGNkMGFmMjllZjMzZTNkYzMxOGFhYTM1M2ZmNjdlYmU1MSJ9fX0=";
    /** "Firework Rocket" (minecraft-heads.com Custom Head ID 4446) - the Firework Core crafting item, {@link #GUNPOWDER_CORE}'s own upgrade - see {@code CombatCollectionsItemsService}. */
    public static final String FIREWORK_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzAyZjQ4ZjM0ZDIyZGVkNzQwNGY3NmU4YTEzMmFmNWQ3OTE5YzhkY2Q1MWRmNmU3YTg1ZGRmYWM4NWFiIn19fQ==";
    /** "Ender Pearl" (minecraft-heads.com Custom Head ID 116) - the Ender Pearl Core crafting item, see {@code CombatCollectionsItemsService}. */
    public static final String ENDER_PEARL_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWNiN2MyMWNjNDNkYzE3Njc4ZWU2ZjE2NTkxZmZhYWIxZjYzN2MzN2Y0ZjZiYmQ4Y2VhNDk3NDUxZDc2ZGI2ZCJ9fX0=";
    /** "Eye of Ender" (minecraft-heads.com Custom Head ID 126728) - the Eye of Ender Core crafting item, {@link #ENDER_PEARL_CORE}'s own upgrade - see {@code CombatCollectionsItemsService}. */
    public static final String EYE_OF_ENDER_CORE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzRiZTY3YWVkZWRlMzE2ZTVmMDBhN2FkM2ZiZTMwYTgxY2VmNjdkNGRmN2NlZDEzMGZiZTAyYmUwMTU0OGJjZSJ9fX0=";
    /** Every constant above, in one place - {@code GeyserSkullExport} reads this so a texture never has to be added there by hand (and risk being forgotten) each time a new one is added here. */
    public static final Set<String> ALL = Set.of(PLANET, TRASH_CAN, ARROW_RIGHT, ARROW_LEFT, BACK, CLOSE, QUIVER, SUPER_MUSHROOM, ZOMBIE_MINER, SKELETON_MINER,
            SCROLL_UP, SCROLL_DOWN, LAPIS_CORE, TRUE_LAPIS_CORE, BUNDLE, CACTUS_CORE, CARROT_CORE, CHOCOLATE_CORE, FEATHER_CORE, MUSHROOM_CORE,
            MELON_CORE, POTATO_CORE, PUMPKIN_CORE, WHEAT_CORE, POTION_BAG, MUSHROOM_SOUP, MYSTICAL_MUSHROOM_SOUP, FARM_CRYSTAL,
            COW_HAT, MILK_CORE, MILKSHAKE_CORE, WOOL_CORE, RAINBOW_WOOL_CORE, NETHER_WART_CORE, MUTANT_NETHER_WART_CORE, LANTERN_HELMET,
            RABBIT_ARMOR_HELMET, SUGAR_CANE_CORE, OAK_CORE, BIRCH_CORE, SPRUCE_CORE, WOODCUTTING_CRYSTAL, ANIMAL_CRYSTAL, DARK_OAK_CORE, ACACIA_CORE, JUNGLE_CORE, MANGROVE_CORE, FLOWERED_MANGROVE_CORE,
            CHERRY_CORE, PINK_CHERRY_CORE, CRIMSON_CORE, TRUE_CRIMSON_CORE, EMBER_TALISMAN, EMBER_RING, EMBER_ARTIFACT,
            FLOWER_CACTUS_CORE, GOLDEN_CARROT_CORE, TRUE_CHOCOLATE_CORE, VACCINE_RING, BAKED_POTATO_CORE, VACCINE_ARTIFACT,
            FARMER_ORB, NIGHT_VISION_CHARM, POTION_AFFINITY_TALISMAN, POTION_AFFINITY_RING_AND_ARTIFACT,
            BEETROOT_CORE, PICNIC_BASKET, TORCHFLOWER_CORE, RADIANT_TORCHFLOWER_CORE, WHEAT_SEEDS_CORE, BONE_CORE, PILE_OF_BONE_CORE,
            ROTTEN_FLESH_CORE, ZOMBIES_HEART, SPIDER_HAT, SPIDER_EYE_CORE, FERMENTED_SPIDER_EYE_CORE, STRING_CORE, GUNPOWDER_CORE, FIREWORK_CORE,
            ENDER_PEARL_CORE, EYE_OF_ENDER_CORE);

    private HeadTexture() {
    }
}
