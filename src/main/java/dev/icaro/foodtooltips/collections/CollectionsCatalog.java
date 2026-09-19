package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

/**
 * Every collectible material's own milestone ladder, grouped by {@link CollectionsCategory} -
 * same "static final list, never built per-call" shape as {@code bestiary.BestiaryCatalog}/
 * {@code mining.MiningCatalog}. Only Farming (Cactus, Carrot) is populated today - the
 * player's own explicit "o resto vai passando com o tempo" ("the rest will come with time"):
 * Combat/Mining/Foraging/Fishing are real {@link CollectionsCategory} values already (so
 * {@link CollectionsMenuService}'s category screen never has to change shape once they're
 * filled in), they just have no entries yet.
 *
 * <p>The recipe keys below are this class's own - {@code
 * dev.icaro.foodtooltips.item.FarmingCollectionsItemsService} registers its actual {@code
 * Bukkit.addRecipe} calls under these exact same keys, so a milestone's {@link
 * CollectionsMilestone#recipes} and the real registered recipe always agree on identity.
 */
public final class CollectionsCatalog {
    public static final NamespacedKey CACTUS_CORE_RECIPE = new NamespacedKey("foodtooltips", "cactus_core");
    public static final NamespacedKey CARROT_CORE_RECIPE = new NamespacedKey("foodtooltips", "carrot_core");
    public static final NamespacedKey CACTUS_HELMET_RECIPE = new NamespacedKey("foodtooltips", "cactus_helmet");
    public static final NamespacedKey CACTUS_CHESTPLATE_RECIPE = new NamespacedKey("foodtooltips", "cactus_chestplate");
    public static final NamespacedKey CACTUS_LEGGINGS_RECIPE = new NamespacedKey("foodtooltips", "cactus_leggings");
    public static final NamespacedKey CACTUS_BOOTS_RECIPE = new NamespacedKey("foodtooltips", "cactus_boots");

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
                    CollectionsMilestone.farmingXp(100000, 50000, "+50000 XP de Agricultura", "+50000 Farming XP"))));

    private CollectionsCatalog() {
    }

    public static List<CollectionsEntry> entries() {
        return ENTRIES;
    }

    public static List<CollectionsEntry> entries(CollectionsCategory category) {
        return ENTRIES.stream().filter(e -> e.category() == category).toList();
    }

    public static Optional<CollectionsEntry> find(Material material) {
        return ENTRIES.stream().filter(e -> e.material() == material).findFirst();
    }
}
