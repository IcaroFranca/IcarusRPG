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
    public static final NamespacedKey SPROUT_HELMET_RECIPE = new NamespacedKey("foodtooltips", "sprout_helmet");
    public static final NamespacedKey SPROUT_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "sprout_chestplate");
    public static final NamespacedKey SPROUT_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "sprout_leggings");
    public static final NamespacedKey SPROUT_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "sprout_boots");
    public static final NamespacedKey ENCHANTED_CARROT_STICK_RECIPE = new NamespacedKey("foodtooltips", "enchanted_carrot_stick");
    public static final NamespacedKey COW_HAT_RECIPE = new NamespacedKey("foodtooltips", "cow_hat");
    public static final NamespacedKey MILK_CORE_RECIPE = new NamespacedKey("foodtooltips", "milk_core");
    public static final NamespacedKey MILKSHAKE_CORE_RECIPE = new NamespacedKey("foodtooltips", "milkshake_core");
    public static final NamespacedKey WOOL_CORE_RECIPE = new NamespacedKey("foodtooltips", "wool_core");
    public static final NamespacedKey RAINBOW_WOOL_CORE_RECIPE = new NamespacedKey("foodtooltips", "rainbow_wool_core");
    public static final NamespacedKey NETHER_WART_CORE_RECIPE = new NamespacedKey("foodtooltips", "nether_wart_core");
    public static final NamespacedKey MUTANT_NETHER_WART_CORE_RECIPE = new NamespacedKey("foodtooltips", "mutant_nether_wart_core");
    public static final NamespacedKey LANTERN_HELMET_RECIPE = new NamespacedKey("foodtooltips", "lantern_helmet");
    public static final NamespacedKey RABBIT_HELMET_RECIPE = new NamespacedKey("foodtooltips", "rabbit_helmet");
    public static final NamespacedKey RABBIT_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "rabbit_chestplate");
    public static final NamespacedKey RABBIT_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "rabbit_leggings");
    public static final NamespacedKey RABBIT_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "rabbit_boots");
    public static final NamespacedKey SUGAR_CANE_CORE_RECIPE = new NamespacedKey("foodtooltips", "sugar_cane_core");
    public static final NamespacedKey SPEEDSTER_HELMET_RECIPE = new NamespacedKey("foodtooltips", "speedster_helmet");
    public static final NamespacedKey SPEEDSTER_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "speedster_chestplate");
    public static final NamespacedKey SPEEDSTER_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "speedster_leggings");
    public static final NamespacedKey SPEEDSTER_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "speedster_boots");
    public static final NamespacedKey OAK_CORE_RECIPE = new NamespacedKey("foodtooltips", "oak_core");
    public static final NamespacedKey LEAFLET_HELMET_RECIPE = new NamespacedKey("foodtooltips", "leaflet_helmet");
    public static final NamespacedKey LEAFLET_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "leaflet_chestplate");
    public static final NamespacedKey LEAFLET_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "leaflet_leggings");
    public static final NamespacedKey LEAFLET_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "leaflet_boots");
    public static final NamespacedKey BIOME_WAND_FOREST_PLAINS_RECIPE = new NamespacedKey("foodtooltips", "biome_wand_forest_plains");
    public static final NamespacedKey SCULPTORS_AXE_RECIPE = new NamespacedKey("foodtooltips", "sculptors_axe");
    public static final NamespacedKey BIRCH_CORE_RECIPE = new NamespacedKey("foodtooltips", "birch_core");
    public static final NamespacedKey SPRUCE_AXE_RECIPE = new NamespacedKey("foodtooltips", "spruce_axe");
    public static final NamespacedKey SPRUCE_CORE_RECIPE = new NamespacedKey("foodtooltips", "spruce_core");
    public static final NamespacedKey WOODCUTTING_CRYSTAL_RECIPE = new NamespacedKey("foodtooltips", "woodcutting_crystal");
    public static final NamespacedKey DARK_OAK_CORE_RECIPE = new NamespacedKey("foodtooltips", "dark_oak_core");
    public static final NamespacedKey GROWTH_HELMET_RECIPE = new NamespacedKey("foodtooltips", "growth_helmet");
    public static final NamespacedKey GROWTH_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "growth_chestplate");
    public static final NamespacedKey GROWTH_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "growth_leggings");
    public static final NamespacedKey GROWTH_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "growth_boots");
    public static final NamespacedKey ACACIA_CORE_RECIPE = new NamespacedKey("foodtooltips", "acacia_core");
    public static final NamespacedKey SAVANNA_BOW_RECIPE = new NamespacedKey("foodtooltips", "savanna_bow");
    public static final NamespacedKey JUNGLE_CORE_RECIPE = new NamespacedKey("foodtooltips", "jungle_core");
    public static final NamespacedKey TREECAPITATOR_RECIPE = new NamespacedKey("foodtooltips", "treecapitator");

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
                    CollectionsMilestone.recipeUnlock(5000,
                            "Desbloqueia a receita da Sprout Armor", "Unlocks the Sprout Armor recipe",
                            SPROUT_HELMET_RECIPE, SPROUT_CHESTPLATE_RECIPE, SPROUT_LEGGINGS_RECIPE, SPROUT_BOOTS_RECIPE),
                    CollectionsMilestone.recipeUnlock(10000,
                            "Desbloqueia a receita da Enchanted Carrot on a Stick", "Unlocks the Enchanted Carrot on a Stick recipe",
                            ENCHANTED_CARROT_STICK_RECIPE),
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
                    // A PotionMix, not a real CraftingRecipe - no recipes to gate here, same
                    // "unlocked in name only" limitation Resistance/Adrenaline Potion already
                    // accept (see CollectionsRecipeGateListener's own doc).
                    new CollectionsMilestone(10000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia a receita da Poção de Arquearia", "Unlocks the Archery Potion recipe"),
                    new CollectionsMilestone(25000, RewardKind.FARMING_XP, 10000, List.of(), null, 0.0,
                            "+10000 XP de Agricultura", "+10000 Farming XP"),
                    new CollectionsMilestone(50000, RewardKind.FARMING_XP, 25000, List.of(), null, 0.0,
                            "+25000 XP de Agricultura", "+25000 Farming XP"),
                    new CollectionsMilestone(100000, RewardKind.FARMING_XP, 50000, List.of(), null, 0.0,
                            "+50000 XP de Agricultura", "+50000 Farming XP"))),
            // Wardrobe unlock/expansion milestones carry no recipes (a feature unlock, not a
            // craftable item) - see skills.WardrobeService for the actual mechanic. M2/M4/M6
            // carry real crafted rewards (Cow Hat/Milk Core/Milkshake Core) instead of the
            // generic ladder's own plain Farming XP.
            new CollectionsEntry(Material.LEATHER, Material.LEATHER, CollectionsCategory.FARMING, "Couro", "Leather", genericXpWithOverrides(
                    at(1, new CollectionsMilestone(25, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o Wardrobe (3 colunas)", "Unlocks the Wardrobe (3 columns)")),
                    at(2, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[1],
                            "Desbloqueia a receita do Cow Hat", "Unlocks the Cow Hat recipe",
                            COW_HAT_RECIPE)),
                    at(3, new CollectionsMilestone(DEFAULT_THRESHOLDS[2], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Wardrobe: +2 colunas (5 no total)", "Wardrobe: +2 columns (5 total)")),
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Milk Core", "Unlocks the Milk Core recipe",
                            MILK_CORE_RECIPE)),
                    at(5, new CollectionsMilestone(DEFAULT_THRESHOLDS[4], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Wardrobe: +2 colunas (7 no total)", "Wardrobe: +2 columns (7 total)")),
                    at(6, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[5],
                            "Desbloqueia a receita do Milkshake Core", "Unlocks the Milkshake Core recipe",
                            MILKSHAKE_CORE_RECIPE)),
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
            // M2/M4/M6 carry real crafted rewards (Mana Potion/Wool Core/Rainbow Wool Core)
            // instead of the generic ladder's own plain Farming XP.
            new CollectionsEntry(Material.MUTTON, Material.MUTTON, CollectionsCategory.FARMING, "Carneiro Cru", "Raw Mutton", genericXpWithOverrides(
                    // A PotionMix, not a real CraftingRecipe - same "unlocked in name only"
                    // limitation as the Archery Potion (Feather M6) above.
                    at(2, new CollectionsMilestone(DEFAULT_THRESHOLDS[1], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia a receita da Poção de Mana", "Unlocks the Mana Potion recipe")),
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Wool Core", "Unlocks the Wool Core recipe",
                            WOOL_CORE_RECIPE)),
                    at(6, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[5],
                            "Desbloqueia a receita do Rainbow Wool Core", "Unlocks the Rainbow Wool Core recipe",
                            RAINBOW_WOOL_CORE_RECIPE)))),
            // Potion Bag unlock/expansion milestones carry no recipes, same reasoning as
            // the Wardrobe (Leather) entry above - see skills.PotionBagService. M4/M6 carry
            // real crafted rewards (Nether Wart Core/Mutant Nether Wart Core) instead of the
            // generic ladder's own plain Farming XP.
            new CollectionsEntry(Material.NETHER_WART, Material.NETHER_WART, CollectionsCategory.FARMING, "Verruga do Nether", "Nether Wart", genericXpWithOverrides(
                    at(1, new CollectionsMilestone(DEFAULT_THRESHOLDS[0], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia a Potion Bag (9 slots)", "Unlocks the Potion Bag (9 slots)")),
                    at(3, new CollectionsMilestone(DEFAULT_THRESHOLDS[2], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (18 no total)", "Potion Bag: +9 slots (18 total)")),
                    at(4, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[3],
                            "Desbloqueia a receita do Nether Wart Core", "Unlocks the Nether Wart Core recipe",
                            NETHER_WART_CORE_RECIPE)),
                    at(5, new CollectionsMilestone(DEFAULT_THRESHOLDS[4], RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Potion Bag: +9 slots (27 no total)", "Potion Bag: +9 slots (27 total)")),
                    at(6, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[5],
                            "Desbloqueia a receita do Mutant Nether Wart Core", "Unlocks the Mutant Nether Wart Core recipe",
                            MUTANT_NETHER_WART_CORE_RECIPE)),
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
                            FARM_CRYSTAL_RECIPE)),
                    at(7, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[6],
                            "Desbloqueia a receita do Lantern Helmet", "Unlocks the Lantern Helmet recipe",
                            LANTERN_HELMET_RECIPE)))),
            new CollectionsEntry(Material.CHICKEN, Material.CHICKEN, CollectionsCategory.FARMING, "Frango Cru", "Raw Chicken", genericXp()),
            new CollectionsEntry(Material.PORKCHOP, Material.PORKCHOP, CollectionsCategory.FARMING, "Porco Cru", "Raw Porkchop", genericXp()),
            new CollectionsEntry(Material.RABBIT, Material.RABBIT, CollectionsCategory.FARMING, "Coelho Cru", "Raw Rabbit", genericXpWithOverrides(
                    at(1, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[0], IcarusEnchant.LUCK, 25.0,
                            "-25% de custo em XP para Sorte", "-25% XP cost for Luck")),
                    at(2, CollectionsMilestone.enchantDiscount(DEFAULT_THRESHOLDS[1], IcarusEnchant.LUCK_OF_THE_SEA, 25.0,
                            "-25% de custo em XP para Sorte do Mar", "-25% XP cost for Luck of the Sea")),
                    at(3, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[2],
                            "Desbloqueia a receita da Rabbit Armor", "Unlocks the Rabbit Armor recipe",
                            RABBIT_HELMET_RECIPE, RABBIT_CHESTPLATE_RECIPE, RABBIT_LEGGINGS_RECIPE, RABBIT_BOOTS_RECIPE)))),
            new CollectionsEntry(Material.WHEAT_SEEDS, Material.WHEAT_SEEDS, CollectionsCategory.FARMING, "Sementes", "Seeds", genericXp()),
            new CollectionsEntry(Material.SUGAR_CANE, Material.SUGAR_CANE, CollectionsCategory.FARMING, "Cana-de-açúcar", "Sugar Cane", genericXpWithOverrides(
                    at(2, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[1],
                            "Desbloqueia a receita do Sugar Cane Core", "Unlocks the Sugar Cane Core recipe",
                            SUGAR_CANE_CORE_RECIPE)),
                    at(3, CollectionsMilestone.recipeUnlock(DEFAULT_THRESHOLDS[2],
                            "Desbloqueia a receita da Speedster Armor", "Unlocks the Speedster Armor recipe",
                            SPEEDSTER_HELMET_RECIPE, SPEEDSTER_CHESTPLATE_RECIPE, SPEEDSTER_LEGGINGS_RECIPE, SPEEDSTER_BOOTS_RECIPE)))),
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
                            HAYMAKER_HELMET_RECIPE, HAYMAKER_CHESTPLATE_RECIPE, HAYMAKER_LEGGINGS_RECIPE, HAYMAKER_BOOTS_RECIPE)))),
            // Personal Storage unlock/expansion milestones carry no recipes, same reasoning
            // as the Wardrobe (Leather)/Potion Bag (Nether Wart) entries above - see
            // skills.PersonalStorageService. Custom thresholds (not DEFAULT_THRESHOLDS), per
            // the player's own explicit spec.
            new CollectionsEntry(Material.OAK_LOG, Material.OAK_LOG, CollectionsCategory.FORAGING, "Tora de Carvalho", "Oak Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(100,
                            "Desbloqueia a receita da Leaflet Armor", "Unlocks the Leaflet Armor recipe",
                            LEAFLET_HELMET_RECIPE, LEAFLET_CHESTPLATE_RECIPE, LEAFLET_LEGGINGS_RECIPE, LEAFLET_BOOTS_RECIPE),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita do Oak Core", "Unlocks the Oak Core recipe",
                            OAK_CORE_RECIPE),
                    new CollectionsMilestone(500, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o Armazenamento Pessoal (9 slots)", "Unlocks Personal Storage (9 slots)"),
                    CollectionsMilestone.recipeUnlock(1000,
                            "Desbloqueia a receita da Varinha de Biomas (Floresta/Planície)", "Unlocks the Biome's Wand recipe (Forest/Plains)",
                            BIOME_WAND_FOREST_PLAINS_RECIPE),
                    new CollectionsMilestone(2000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Armazenamento Pessoal: +18 slots (27 no total)", "Personal Storage: +18 slots (27 total)"),
                    CollectionsMilestone.foragingXp(5000, 5000, "+5000 XP de Coleta", "+5000 Foraging XP"),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    new CollectionsMilestone(25000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Armazenamento Pessoal: +18 slots (45 no total)", "Personal Storage: +18 slots (45 total)"))),
            // Templates for the rest of the wood-log family - milestones to be filled in
            // later ("os moldes deles por hora, que jajá te passo o que cada um vai ter").
            new CollectionsEntry(Material.SPRUCE_LOG, Material.SPRUCE_LOG, CollectionsCategory.FORAGING, "Tora de Spruce", "Spruce Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(100,
                            "Desbloqueia a receita do Spruce Axe", "Unlocks the Spruce Axe recipe",
                            SPRUCE_AXE_RECIPE),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita do Spruce Core", "Unlocks the Spruce Core recipe",
                            SPRUCE_CORE_RECIPE),
                    new CollectionsMilestone(500, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o teleporte para o Taiga mais próximo", "Unlocks fast travel to the nearest Taiga"),
                    new CollectionsMilestone(1000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Libera Taiga e Taiga Antiga na Varinha de Biomas", "Unlocks Taiga and Old Growth Pine Taiga on the Biome's Wand"),
                    CollectionsMilestone.foragingXp(2000, 3000, "+3000 XP de Coleta", "+3000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(5000,
                            "Desbloqueia a receita do Woodcutting Crystal", "Unlocks the Woodcutting Crystal recipe",
                            WOODCUTTING_CRYSTAL_RECIPE),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    CollectionsMilestone.foragingXp(25000, 25000, "+25000 XP de Coleta", "+25000 Foraging XP"))),
            new CollectionsEntry(Material.BIRCH_LOG, Material.BIRCH_LOG, CollectionsCategory.FORAGING, "Tora de Bétula", "Birch Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.foragingXp(100, 2000, "+2000 XP de Coleta", "+2000 Foraging XP"),
                    new CollectionsMilestone(250, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o teleporte para a Floresta de Bétulas mais próxima", "Unlocks fast travel to the nearest Birch Forest"),
                    CollectionsMilestone.recipeUnlock(500,
                            "Desbloqueia a receita do Sculptor's Axe", "Unlocks the Sculptor's Axe recipe",
                            SCULPTORS_AXE_RECIPE),
                    CollectionsMilestone.recipeUnlock(1000,
                            "Desbloqueia a receita do Birch Core", "Unlocks the Birch Core recipe",
                            BIRCH_CORE_RECIPE),
                    new CollectionsMilestone(2000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Libera Floresta de Bétulas na Varinha de Biomas", "Unlocks Birch Forest on the Biome's Wand"),
                    CollectionsMilestone.foragingXp(5000, 5000, "+5000 XP de Coleta", "+5000 Foraging XP"),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    CollectionsMilestone.foragingXp(25000, 25000, "+25000 XP de Coleta", "+25000 Foraging XP"))),
            new CollectionsEntry(Material.JUNGLE_LOG, Material.JUNGLE_LOG, CollectionsCategory.FORAGING, "Tora de Jungle", "Jungle Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.foragingXp(100, 2000, "+2000 XP de Coleta", "+2000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita do Jungle Core", "Unlocks the Jungle Core recipe",
                            JUNGLE_CORE_RECIPE),
                    new CollectionsMilestone(500, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o teleporte para o Jungle mais próximo", "Unlocks fast travel to the nearest Jungle"),
                    new CollectionsMilestone(1000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Libera Selva na Varinha de Biomas", "Unlocks Jungle on the Biome's Wand"),
                    CollectionsMilestone.foragingXp(2000, 5000, "+5000 XP de Coleta", "+5000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(5000,
                            "Desbloqueia a receita do Treecapitator", "Unlocks the Treecapitator recipe",
                            TREECAPITATOR_RECIPE),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    CollectionsMilestone.foragingXp(25000, 25000, "+25000 XP de Coleta", "+25000 Foraging XP"))),
            new CollectionsEntry(Material.ACACIA_LOG, Material.ACACIA_LOG, CollectionsCategory.FORAGING, "Tora de Acácia", "Acacia Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.foragingXp(100, 2000, "+2000 XP de Coleta", "+2000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita do Acacia Core", "Unlocks the Acacia Core recipe",
                            ACACIA_CORE_RECIPE),
                    new CollectionsMilestone(500, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o teleporte para o Savana mais próximo", "Unlocks fast travel to the nearest Savanna"),
                    CollectionsMilestone.recipeUnlock(1000,
                            "Desbloqueia a receita do Savanna Bow", "Unlocks the Savanna Bow recipe",
                            SAVANNA_BOW_RECIPE),
                    new CollectionsMilestone(2000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Libera Savana na Varinha de Biomas", "Unlocks Savanna on the Biome's Wand"),
                    CollectionsMilestone.foragingXp(5000, 5000, "+5000 XP de Coleta", "+5000 Foraging XP"),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    CollectionsMilestone.foragingXp(25000, 25000, "+25000 XP de Coleta", "+25000 Foraging XP"))),
            new CollectionsEntry(Material.DARK_OAK_LOG, Material.DARK_OAK_LOG, CollectionsCategory.FORAGING, "Tora de Carvalho Escuro", "Dark Oak Log", List.of(
                    CollectionsMilestone.foragingXp(50, 1000, "+1000 XP de Coleta", "+1000 Foraging XP"),
                    CollectionsMilestone.foragingXp(100, 2000, "+2000 XP de Coleta", "+2000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(250,
                            "Desbloqueia a receita do Dark Oak Core", "Unlocks the Dark Oak Core recipe",
                            DARK_OAK_CORE_RECIPE),
                    new CollectionsMilestone(500, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Desbloqueia o teleporte para a Floresta Sombria mais próxima", "Unlocks fast travel to the nearest Dark Forest"),
                    new CollectionsMilestone(1000, RewardKind.RECIPE_UNLOCK, 0, List.of(), null, 0.0,
                            "Libera Floresta Sombria na Varinha de Biomas", "Unlocks Dark Forest on the Biome's Wand"),
                    CollectionsMilestone.foragingXp(2000, 5000, "+5000 XP de Coleta", "+5000 Foraging XP"),
                    CollectionsMilestone.enchantDiscount(5000, IcarusEnchant.GROWTH, 25.0,
                            "-25% de custo em XP para Crescimento", "-25% XP cost for Growth"),
                    CollectionsMilestone.foragingXp(10000, 10000, "+10000 XP de Coleta", "+10000 Foraging XP"),
                    CollectionsMilestone.recipeUnlock(25000,
                            "Desbloqueia a receita da Armor of Growth", "Unlocks the Armor of Growth recipe",
                            GROWTH_HELMET_RECIPE, GROWTH_CHESTPLATE_RECIPE, GROWTH_LEGGINGS_RECIPE, GROWTH_BOOTS_RECIPE))),
            new CollectionsEntry(Material.MANGROVE_LOG, Material.MANGROVE_LOG, CollectionsCategory.FORAGING, "Tora de Mangue", "Mangrove Log", List.of()),
            new CollectionsEntry(Material.CHERRY_LOG, Material.CHERRY_LOG, CollectionsCategory.FORAGING, "Tora de Cerejeira", "Cherry Log", List.of()),
            new CollectionsEntry(Material.PALE_OAK_LOG, Material.PALE_OAK_LOG, CollectionsCategory.FORAGING, "Tora de Carvalho Pálido", "Pale Oak Log", List.of()),
            new CollectionsEntry(Material.CRIMSON_STEM, Material.CRIMSON_STEM, CollectionsCategory.FORAGING, "Talo Carmesim", "Crimson Stem", List.of()),
            new CollectionsEntry(Material.WARPED_STEM, Material.WARPED_STEM, CollectionsCategory.FORAGING, "Talo Distorcido", "Warped Stem", List.of()));

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
