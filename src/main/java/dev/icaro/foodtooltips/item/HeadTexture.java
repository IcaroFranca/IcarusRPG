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
    /** Every constant above, in one place - {@code GeyserSkullExport} reads this so a texture never has to be added there by hand (and risk being forgotten) each time a new one is added here. */
    public static final Set<String> ALL = Set.of(PLANET, TRASH_CAN, ARROW_RIGHT, ARROW_LEFT, BACK, CLOSE, QUIVER, SUPER_MUSHROOM, ZOMBIE_MINER, SKELETON_MINER,
            SCROLL_UP, SCROLL_DOWN, LAPIS_CORE, TRUE_LAPIS_CORE, BUNDLE, CACTUS_CORE, CARROT_CORE, CHOCOLATE_CORE, FEATHER_CORE, MUSHROOM_CORE,
            MELON_CORE, POTATO_CORE, PUMPKIN_CORE, WHEAT_CORE, POTION_BAG, MUSHROOM_SOUP, MYSTICAL_MUSHROOM_SOUP, FARM_CRYSTAL,
            COW_HAT, MILK_CORE, MILKSHAKE_CORE, WOOL_CORE, RAINBOW_WOOL_CORE, NETHER_WART_CORE, MUTANT_NETHER_WART_CORE, LANTERN_HELMET,
            RABBIT_ARMOR_HELMET, SUGAR_CANE_CORE, OAK_CORE, BIRCH_CORE, SPRUCE_CORE, WOODCUTTING_CRYSTAL);

    private HeadTexture() {
    }
}
