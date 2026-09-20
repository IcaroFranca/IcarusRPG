package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Every collectible material's own milestone ladder, grouped by {@link CollectionsCategory} -
 * same "static final list, never built per-call" shape as {@code bestiary.BestiaryCatalog}/
 * {@code mining.MiningCatalog}. Only Farming is populated today - the player's own explicit
 * "o resto vai passando com o tempo" ("the rest will come with time"): Combat/Mining/
 * Foraging/Fishing are real {@link CollectionsCategory} values already (so {@link
 * CollectionsMenuService}'s category screen never has to change shape once they're filled
 * in), they just have no entries yet.
 *
 * <p>The recipe keys below are this class's own - {@code
 * dev.icaro.foodtooltips.item.FarmingCollectionsItemsService} registers its actual {@code
 * Bukkit.addRecipe} calls under these exact same keys, so a milestone's {@link
 * CollectionsMilestone#recipes} and the real registered recipe always agree on identity. The
 * two {@code MUSHROOM_BLOCK} keys are the exception - real *vanilla* recipe keys (Bukkit
 * ships one already for each), gated here without this plugin ever registering a recipe of
 * its own for them; see {@code collections.CollectionsRecipeGateListener}, which checks any
 * {@code CraftingRecipe} regardless of who registered it, not just this plugin's own.
 */
public final class CollectionsCatalog {
    public static final NamespacedKey CACTUS_CORE_RECIPE = new NamespacedKey("foodtooltips", "cactus_core");
    public static final NamespacedKey CARROT_CORE_RECIPE = new NamespacedKey("foodtooltips", "carrot_core");
    public static final NamespacedKey CACTUS_HELMET_RECIPE = new NamespacedKey("foodtooltips", "cactus_helmet");
    public static final NamespacedKey CACTUS_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "cactus_chestplate");
    public static final NamespacedKey CACTUS_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "cactus_leggings");
    public static final NamespacedKey CACTUS_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "cactus_boots");
    public static final NamespacedKey CHOCOLATE_CORE_RECIPE = new NamespacedKey("foodtooltips", "chocolate_core");
    public static final NamespacedKey CHOCOLATE_HELMET_RECIPE = new NamespacedKey("foodtooltips", "chocolate_helmet");
    public static final NamespacedKey CHOCOLATE_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "chocolate_chestplate");
    public static final NamespacedKey CHOCOLATE_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "chocolate_leggings");
    public static final NamespacedKey CHOCOLATE_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "chocolate_boots");
    public static final NamespacedKey FEATHER_CORE_RECIPE = new NamespacedKey("foodtooltips", "feather_core");
    public static final NamespacedKey MUSHROOM_CORE_RECIPE = new NamespacedKey("foodtooltips", "mushroom_core");
    public static final NamespacedKey MUSHROOM_HELMET_RECIPE = new NamespacedKey("foodtooltips", "mushroom_helmet");
    public static final NamespacedKey MUSHROOM_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "mushroom_chestplate");
    public static final NamespacedKey MUSHROOM_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "mushroom_leggings");
    public static final NamespacedKey MUSHROOM_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "mushroom_boots");
    /** Real vanilla recipe keys - see this class's own doc. */
    public static final NamespacedKey RED_MUSHROOM_BLOCK_RECIPE = NamespacedKey.minecraft("red_mushroom_block");
    public static final NamespacedKey BROWN_MUSHROOM_BLOCK_RECIPE = NamespacedKey.minecraft("brown_mushroom_block");
    public static final NamespacedKey MAGICAL_MUSHROOM_SOUP_RECIPE = new NamespacedKey("foodtooltips", "magical_mushroom_soup");
    public static final NamespacedKey MYSTICAL_MUSHROOM_SOUP_RECIPE = new NamespacedKey("foodtooltips", "mystical_mushroom_soup");
    public static final NamespacedKey MELON_CORE_RECIPE = new NamespacedKey("foodtooltips", "melon_core");
    public static final NamespacedKey POTATO_CORE_RECIPE = new NamespacedKey("foodtooltips", "potato_core");
    public static final NamespacedKey PUMPKIN_CORE_RECIPE = new NamespacedKey("foodtooltips", "pumpkin_core");
    public static final NamespacedKey WHEAT_CORE_RECIPE = new NamespacedKey("foodtooltips", "wheat_core");
    public static final NamespacedKey FARMER_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "farmer_boots");
    public static final NamespacedKey FARM_CRYSTAL_RECIPE = new NamespacedKey("foodtooltips", "farm_crystal");
    public static final NamespacedKey FARMHAND_HELMET_RECIPE = new NamespacedKey("foodtooltips", "farmhand_helmet");
    public static final NamespacedKey FARMHAND_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "farmhand_chestplate");
    public static final NamespacedKey FARMHAND_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "farmhand_leggings");
    public static final NamespacedKey FARMHAND_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "farmhand_boots");
    public static final NamespacedKey HAYMAKER_HELMET_RECIPE = new NamespacedKey("foodtooltips", "haymaker_helmet");
    public static final NamespacedKey HAYMAKER_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "haymaker_chestplate");
    public static final NamespacedKey HAYMAKER_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "haymaker_leggings");
    public static final NamespacedKey HAYMAKER_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "haymaker_boots");

    /**
     * The threshold ladder every "no special reward decided yet" entry uses (the player's
     * own words: "coloca uma progressão de Farming XP como recompensa até eu pensar em algo
     * melhor") - same shape as Cactus's own table, since nothing else was specified. {@link
     * #genericXp()}/{@link #genericXpWithOverrides} build a fresh 9-milestone list from this
     * plus {@link #DEFAULT_XP}, so swapping any single tier for a real reward later (as
     * already happened for Pumpkin/Wheat/Raw Rabbit's own enchant discounts below) never
     * needs the threshold repeated by hand.
     */
    private static final int[] DEFAULT_THRESHOLDS = {100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000};
    /** Placeholder Farming XP reward per tier, paired with {@link #DEFAULT_THRESHOLDS} - a plain increasing progression, explicitly provisional (see this class's own field doc). */
    private static final int[] DEFAULT_XP = {1000, 2000, 3000, 4000, 5000, 7500, 10000, 25000, 50000};

    private static List<CollectionsMilestone> genericXp() {
        List<CollectionsMilestone> list = new ArrayList<>();
        for (int i = 0; i < DEFAULT_THRESHOLDS.length; i++) {
            int xp = DEFAULT_XP[i];
            list.add(CollectionsMilestone.farmingXp(DEFAULT_THRESHOLDS[i], xp,
                    "+" + xp + " XP de Agricultura", "+" + xp + " Farming XP"));
        }
        return list;
    }

    /** {@link #genericXp()} with one or more tiers (0-indexed, i.e. index 0 is Milestone 1) replaced by a real reward - every "generic progression, but Milestone N unlocks X" entry below uses this instead of repeating the whole ladder by hand. */
    @SafeVarargs
    private static List<CollectionsMilestone> genericXpWithOverrides(java.util.Map.Entry<Integer, CollectionsMilestone>... overrides) {
        List<CollectionsMilestone> list = genericXp();
        for (var override : overrides) {
            list.set(override.getKey(), override.getValue());
        }
        return list;
    }

    private static java.util.Map.Entry<Integer, CollectionsMilestone> at(int milestoneNumber, CollectionsMilestone milestone) {
        return java.util.Map.entry(milestoneNumber - 1, milestone);
    }

    private static final List<CollectionsEntry> ENTRIES = List.of(
            new CollectionsEntry(Material.CACTUS, Material.CACTUS, CollectionsCategory.FARMING, "Cacto", "Cactus", List.of(
                    CollectionsMilestone.farmingXp(100, 1000, "+1000 XP de Agricultura", "+1000 Farming XP"),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita da Armadura de Cacto", "Unlocks the Cactus Armor recipe",
                            CACTUS_HELMET_RECIPE, CACTUS_CHESTPLATE_RECIPE, CACTUS_LEGGINGS_RECIPE, CACTUS_BOOTS_RECIPE),
                    CollectionsMilestone.recipeUnlock(500,
                            "Desbloqueia a receita da Poção de Resistência", "Unlocks the Potion of Resistance recipe"),
                    CollectionsMilestone.recipeUnlock(1000,
                            "Desbloqueia a receita do Cactus Core", "Unlocks the Cactus Core recipe",
                            CACTUS_CORE_RECIPE),
                    CollectionsMilestone.farmingXp(2500, 10000, "+10000 XP de Agricultura", "+10000 Farming XP"),
                    CollectionsMilestone.enchantDiscount(5000, IcarusEnchant.PIERCING, 25.0,
                            "-25% de custo em XP para Penetrante", "-25% XP cost for Piercing"),
                    CollectionsMilestone.enchantDiscount(10000, IcarusEnchant.THORNS, 25.0,
                            "-25% de custo em XP para Espinhos", "-25% XP cost for Thorns"),
                    CollectionsMilestone.farmingXp(25000, 25000, "+25000 XP de Agricultura", "+25000 Farming XP"),
                    CollectionsMilestone.farmingXp(50000, 50000, "+50000 XP de Agricultura", "+50000 Farming XP"))),
            new CollectionsEntry(Material.CARROTS, Material.CARROT, CollectionsCategory.FARMING, "Cenoura", "Carrot", List.of(
                    CollectionsMilestone.farmingXp(100, 1000, "+1000 XP de Agricultura", "+1000 Farming XP"),
                    CollectionsMilestone.farmingXp(250, 2000, "+2000 XP de Agricultura", "+2000 Farming XP"),
                    CollectionsMilestone.farmingXp(500, 3000, "+3000 XP de Agricultura", "+3000 Farming XP"),
                    CollectionsMilestone.recipeUnlock(1750,
                            "Desbloqueia a receita do Carrot Core", "Unlocks the Carrot Core recipe",
                            CARROT_CORE_RECIPE),
                    // Sprout Armor's own recipe doesn't exist yet (see FarmingCollectionsItemsService's
                    // own doc) - an empty recipe list still marks this milestone real and completable,
                    // ready to gate a recipe the moment one is added, without needing to touch this
                    // catalog entry again.
                    CollectionsMilestone.recipeUnlock(5000,
                            "Desbloqueia a Sprout Armor (receita em breve)", "Unlocks Sprout Armor (recipe coming soon)"),
                    CollectionsMilestone.farmingXp(10000, 5000, "+5000 XP de Agricultura", "+5000 Farming XP"),
                    CollectionsMilestone.farmingXp(25000, 10000, "+10000 XP de Agricultura", "+10000 Farming XP"),
                    CollectionsMilestone.farmingXp(50000, 25000, "+25000 XP de Agricultura", "+25000 Farming XP"),
                    CollectionsMilestone.farmingXp(100000, 50000, "+50000 XP de Agricultura", "+50000 Farming XP"))),
            new CollectionsEntry(Material.COCOA, Material.COCOA_BEANS, CollectionsCategory.FARMING, "Cocoa Beans", "Cocoa Beans", List.of(
                    new CollectionsMilestone(75, RewardKind.FARMING_XP, 1000, List.of(), null, 0.0,
                            "+1000 XP de Agricultura", "+1000 Farming XP"),
                    // A PotionMix, not a real CraftingRecipe - no recipes to gate here, same
                    // "unlocked in name only" limitation the Resistance Potion (Cactus M3)
                    // already accepts, since CollectionsRecipeGateListener only ever
                    // intercepts the crafting table, never the Brewing Stand.
                    new CollectionsMilestone(200, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia a receita da Poção de Adrenalina", "Unlocks the Adrenaline Potion recipe"),
                    new CollectionsMilestone(500, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), IcarusEnchant.REPLENISH, 25.0,
                            "-25% de custo em XP para Reabastecer", "-25% XP cost for Replenish"),
                    new CollectionsMilestone(2000, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(CHOCOLATE_CORE_RECIPE), null, 0.0,
                            "Desbloqueia a receita do Chocolate Core", "Unlocks the Chocolate Core recipe"),
                    new CollectionsMilestone(5000, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(CHOCOLATE_HELMET_RECIPE, CHOCOLATE_CHESTPLATE_RECIPE, CHOCOLATE_LEGGINGS_RECIPE, CHOCOLATE_BOOTS_RECIPE), null, 0.0,
                            "Desbloqueia a receita da Chocolate Armor", "Unlocks the Chocolate Armor recipe"),
                    new CollectionsMilestone(10000, RewardKind.FARMING_XP, 5000, List.of(), null, 0.0,
                            "+5000 XP de Agricultura", "+5000 Farming XP"),
                    new CollectionsMilestone(25000, RewardKind.FARMING_XP, 10000, List.of(), null, 0.0,
                            "+10000 XP de Agricultura", "+10000 Farming XP"),
                    new CollectionsMilestone(50000, RewardKind.FARMING_XP, 25000, List.of(), null, 0.0,
                            "+25000 XP de Agricultura", "+25000 Farming XP"),
                    new CollectionsMilestone(100000, RewardKind.FARMING_XP, 50000, List.of(), null, 0.0,
                            "+50000 XP de Agricultura", "+50000 Farming XP"))),
            new CollectionsEntry(Material.FEATHER, Material.FEATHER, CollectionsCategory.FARMING, "Pena", "Feather", List.of(
                    new CollectionsMilestone(50, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), IcarusEnchant.PROJECTILE_PROTECTION, 25.0,
                            "-25% de custo em XP para Proteção contra Projétil", "-25% XP cost for Projectile Protection"),
                    new CollectionsMilestone(100, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), IcarusEnchant.FEATHER_FALLING, 25.0,
                            "-25% de custo em XP para Queda de Pena", "-25% XP cost for Feather Falling"),
                    new CollectionsMilestone(250, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), IcarusEnchant.AIMING, 25.0,
                            "-25% de custo em XP para Mira", "-25% XP cost for Aiming"),
                    new CollectionsMilestone(1000, RewardKind.RECIPE_UNLOCK, 0, List.of(FEATHER_CORE_RECIPE), null, 0.0,
                            "Desbloqueia a receita do Feather Core", "Unlocks the Feather Core recipe"),
                    new CollectionsMilestone(2500, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), IcarusEnchant.SNIPE, 25.0,
                            "-25% de custo em XP para Tiro Longo", "-25% XP cost for Snipe"),
                    new CollectionsMilestone(10000, RewardKind.FARMING_XP, 5000, List.of(), null, 0.0,
                            "+5000 XP de Agricultura", "+5000 Farming XP"),
                    new CollectionsMilestone(25000, RewardKind.FARMING_XP, 10000, List.of(), null, 0.0,
                            "+10000 XP de Agricultura", "+10000 Farming XP"),
                    new CollectionsMilestone(50000, RewardKind.FARMING_XP, 25000, List.of(), null, 0.0,
                            "+25000 XP de Agricultura", "+25000 Farming XP"),
                    new CollectionsMilestone(100000, RewardKind.FARMING_XP, 50000, List.of(), null, 0.0,
                            "+50000 XP de Agricultura", "+50000 Farming XP"))),
            // Wardrobe unlock/expansion milestones carry no recipes (a feature unlock, not a
            // craftable item) - see skills.WardrobeService for the actual mechanic.
            new CollectionsEntry(Material.LEATHER, Material.LEATHER, CollectionsCategory.FARMING, "Couro", "Leather", genericXpWithOverrides(
                    at(1, new CollectionsMilestone(25, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o Wardrobe (3 colunas)", "Unlocks the Wardrobe (3 columns)")),
                    at(3, new CollectionsMilestone(DEFAULT_THRESHOLDS[2], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Wardrobe: +2 colunas (5 no total)", "Wardrobe: +2 columns (5 total)")),
                    at(5, new CollectionsMilestone(DEFAULT_THRESHOLDS[4], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Wardrobe: +2 colunas (7 no total)", "Wardrobe: +2 columns (7 total)")),
                    at(7, new CollectionsMilestone(DEFAULT_THRESHOLDS[6], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Wardrobe: +2 colunas (9 no total)", "Wardrobe: +2 columns (9 total)")))),
            new CollectionsEntry(Material.MELON_SLICE, Material.MELON_SLICE, CollectionsCategory.FARMING, "Fatia de Melancia", "Melon Slice", genericXpWithOverrides(
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Melon Core", "Unlocks the Melon Core recipe",
                            MELON_CORE_RECIPE)))),
            new CollectionsEntry(Material.RED_MUSHROOM, Material.RED_MUSHROOM, CollectionsCategory.FARMING, "Cogumelo", "Mushroom", List.of(
                    new CollectionsMilestone(50, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(MAGICAL_MUSHROOM_SOUP_RECIPE), null, 0.0,
                            "Desbloqueia a receita da Magical Mushroom Soup", "Unlocks the Magical Mushroom Soup recipe"),
                    new CollectionsMilestone(100, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(MUSHROOM_HELMET_RECIPE, MUSHROOM_CHESTPLATE_RECIPE, MUSHROOM_LEGGINGS_RECIPE, MUSHROOM_BOOTS_RECIPE), null, 0.0,
                            "Desbloqueia a receita da Mushroom Armor", "Unlocks the Mushroom Armor recipe"),
                    new CollectionsMilestone(250, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(RED_MUSHROOM_BLOCK_RECIPE, BROWN_MUSHROOM_BLOCK_RECIPE), null, 0.0,
                            "Desbloqueia as receitas dos Blocos de Cogumelo", "Unlocks the Mushroom Block recipes"),
                    new CollectionsMilestone(1000, RewardKind.RECIPE_UNLOCK, 0, List.of(MUSHROOM_CORE_RECIPE), null, 0.0,
                            "Desbloqueia a receita do Mushroom Core", "Unlocks the Mushroom Core recipe"),
                    new CollectionsMilestone(2500, RewardKind.RECIPE_UNLOCK, 0,
                            List.of(MYSTICAL_MUSHROOM_SOUP_RECIPE), null, 0.0,
                            "Desbloqueia a receita da Mystical Mushroom Soup", "Unlocks the Mystical Mushroom Soup recipe"),
                    new CollectionsMilestone(10000, RewardKind.FARMING_XP, 5000, List.of(), null, 0.0,
                            "+5000 XP de Agricultura", "+5000 Farming XP"),
                    new CollectionsMilestone(25000, RewardKind.FARMING_XP, 10000, List.of(), null, 0.0,
                            "+10000 XP de Agricultura", "+10000 Farming XP"),
                    new CollectionsMilestone(50000, RewardKind.FARMING_XP, 25000, List.of(), null, 0.0,
                            "+25000 XP de Agricultura", "+25000 Farming XP"),
                    new CollectionsMilestone(100000, RewardKind.FARMING_XP, 50000, List.of(), null, 0.0,
                            "+50000 XP de Agricultura", "+50000 Farming XP"))),
            new CollectionsEntry(Material.MUTTON, Material.MUTTON, CollectionsCategory.FARMING, "Carneiro Cru", "Raw Mutton", genericXp()),
            // Potion Bag unlock/expansion milestones carry no recipes, same reasoning as
            // the Wardrobe (Leather) entry above - see skills.PotionBagService.
            new CollectionsEntry(Material.NETHER_WART, Material.NETHER_WART, CollectionsCategory.FARMING, "Verruga do Nether", "Nether Wart", genericXpWithOverrides(
                    at(1, new CollectionsMilestone(DEFAULT_THRESHOLDS[0], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia a Potion Bag (9 slots)", "Unlocks the Potion Bag (9 slots)")),
                    at(3, new CollectionsMilestone(DEFAULT_THRESHOLDS[2], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (18 no total)", "Potion Bag: +9 slots (18 total)")),
                    at(5, new CollectionsMilestone(DEFAULT_THRESHOLDS[4], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (27 no total)", "Potion Bag: +9 slots (27 total)")),
                    at(7, new CollectionsMilestone(DEFAULT_THRESHOLDS[6], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (36 no total)", "Potion Bag: +9 slots (36 total)")),
                    at(9, new CollectionsMilestone(DEFAULT_THRESHOLDS[8], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (45 no total)", "Potion Bag: +9 slots (45 total)")))),
            new CollectionsEntry(Material.POTATOES, Material.POTATO, CollectionsCategory.FARMING, "Batata", "Potato", genericXpWithOverrides(
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Potato Core", "Unlocks the Potato Core recipe",
                            POTATO_CORE_RECIPE)))),
            new CollectionsEntry(Material.PUMPKIN, Material.PUMPKIN, CollectionsCategory.FARMING, "Abóbora", "Pumpkin", genericXpWithOverrides(
                    at(3, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[2], IcarusEnchant.CUBISM, 25.0,
                            "-25% de custo em XP para Cubismo", "-25% XP cost for Cubism")),
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Pumpkin Core", "Unlocks the Pumpkin Core recipe",
                            PUMPKIN_CORE_RECIPE)),
                    at(5, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[4],
                            "Desbloqueia a receita das Farmer Boots", "Unlocks the Farmer Boots recipe",
                            FARMER_BOOTS_RECIPE)),
                    at(6, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[5],
                            "Desbloqueia a receita do Farm Crystal", "Unlocks the Farm Crystal recipe",
                            FARM_CRYSTAL_RECIPE)))),
            new CollectionsEntry(Material.CHICKEN, Material.CHICKEN, CollectionsCategory.FARMING, "Frango Cru", "Raw Chicken", genericXp()),
            new CollectionsEntry(Material.PORKCHOP, Material.PORKCHOP, CollectionsCategory.FARMING, "Porco Cru", "Raw Porkchop", genericXp()),
            new CollectionsEntry(Material.RABBIT, Material.RABBIT, CollectionsCategory.FARMING, "Coelho Cru", "Raw Rabbit", genericXpWithOverrides(
                    at(1, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[0], IcarusEnchant.LUCK, 25.0,
                            "-25% de custo em XP para Sorte", "-25% XP cost for Luck")),
                    at(2, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[1], IcarusEnchant.LUCK_OF_THE_SEA, 25.0,
                            "-25% de custo em XP para Sorte do Mar", "-25% XP cost for Luck of the Sea")))),
            new CollectionsEntry(Material.WHEAT_SEEDS, Material.WHEAT_SEEDS, CollectionsCategory.FARMING, "Sementes", "Seeds", genericXp()),
            new CollectionsEntry(Material.SUGAR_CANE, Material.SUGAR_CANE, CollectionsCategory.FARMING, "Cana-de-açúcar", "Sugar Cane", genericXp()),
            new CollectionsEntry(Material.WHEAT, Material.WHEAT, CollectionsCategory.FARMING, "Trigo", "Wheat", genericXpWithOverrides(
                    at(1, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[0], IcarusEnchant.HARVESTING, 25.0,
                            "-25% de custo em XP para Colheita", "-25% XP cost for Harvesting")),
                    at(2, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[1],
                            "Desbloqueia a receita da Farmhand Armor", "Unlocks the Farmhand Armor recipe",
                            FARMHAND_HELMET_RECIPE, FARMHAND_CHESTPLATE_RECIPE, FARMHAND_LEGGINGS_RECIPE, FARMHAND_BOOTS_RECIPE)),
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Wheat Core", "Unlocks the Wheat Core recipe",
                            WHEAT_CORE_RECIPE)),
                    at(5, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[4],
                            "Desbloqueia a receita da Haymaker Armor", "Unlocks the Haymaker Armor recipe",
                            HAYMAKER_HELMET_RECIPE, HAYMAKER_CHESTPLATE_RECIPE, HAYMAKER_LEGGINGS_RECIPE, HAYMAKER_BOOTS_RECIPE)))));

    private CollectionsCatalog() {
    }

    public static List<CollectionsEntry> entries() {
        return ENTRIES;
    }

    public static List<CollectionsEntry> entries(CollectionsCategory category) {
        return ENTRIES.stream().filter(e -> e.category() == category).toList();
    }

    /**
     * Matches either {@code material} (the block identity progress is stored under) or
     * {@code drop} (the item a harvest hook actually reports) - {@link CollectionsService
     * #record} is always called with whatever material the triggering event naturally
     * hands it, which is the harvested item for a crop with a distinct block/drop pair
     * (Carrot, Potato, Cocoa Beans - see {@code skills.GeneralSkillListener#cropDrop}), not
     * the block. Only checking {@code material()} would silently drop every one of those
     * entries' progress forever, since {@code find(CARROT)} would never match an entry
     * keyed by {@code CARROTS}.
     */
    public static Optional<CollectionsEntry> find(Material material) {
        return ENTRIES.stream().filter(e -> e.material() == material || e.drop() == material).findFirst();
    }
}
