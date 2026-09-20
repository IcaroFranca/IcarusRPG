package dev.icaro.foodtooltips.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * Transcription check for the Farming Collections tables (Cactus, Carrot) against the
 * numbers the player specified - a plain data/record equality test, no Bukkit runtime
 * involved at all (same reasoning as {@code reforge.BowReforgePrefixTest}: {@link Material}
 * and {@link org.bukkit.NamespacedKey} are used here only as plain values, never triggering
 * the Registry-backed statics that block a live server object from being constructed in a
 * unit test - see {@code reforge.ReforgeServiceTest}'s own class doc for that limitation).
 */
final class CollectionsCatalogTest {

    private static CollectionsEntry cactus() {
        return CollectionsCatalog.find(Material.CACTUS).orElseThrow();
    }

    private static CollectionsEntry carrot() {
        return CollectionsCatalog.find(Material.CARROTS).orElseThrow();
    }

    @Test
    void cactusThresholdsMatchSpec() {
        List<CollectionsMilestone> m = cactus().milestones();
        assertEquals(9, m.size());
        int[] expected = {100, 250, 500, 1000, 2500, 5000, 10000, 25000, 50000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.get(i).threshold(), "Cactus milestone " + (i + 1));
        }
    }

    @Test
    void cactusRewardKinds() {
        List<CollectionsMilestone> m = cactus().milestones();
        assertEquals(RewardKind.FARMING_XP, m.get(0).kind());
        assertEquals(1000, m.get(0).xpAmount());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(1).kind());
        assertEquals(4, m.get(1).recipes().size());
        assertTrue(m.get(1).recipes().contains(CollectionsCatalog.CACTUS_HELMET_RECIPE));
        assertTrue(m.get(1).recipes().contains(CollectionsCatalog.CACTUS_CHESTPLATE_RECIPE));
        assertTrue(m.get(1).recipes().contains(CollectionsCatalog.CACTUS_LEGGINGS_RECIPE));
        assertTrue(m.get(1).recipes().contains(CollectionsCatalog.CACTUS_BOOTS_RECIPE));
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(2).kind());
        assertTrue(m.get(2).recipes().isEmpty()); // Resistance Potion: a PotionMix, no CraftingRecipe key to gate.
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(3).kind());
        assertEquals(List.of(CollectionsCatalog.CACTUS_CORE_RECIPE), m.get(3).recipes());
        assertEquals(RewardKind.FARMING_XP, m.get(4).kind());
        assertEquals(10000, m.get(4).xpAmount());
        assertEquals(RewardKind.ENCHANT_DISCOUNT, m.get(5).kind());
        assertEquals(IcarusEnchant.PIERCING, m.get(5).discountEnchant());
        assertEquals(25.0, m.get(5).discountPercent());
        assertEquals(RewardKind.ENCHANT_DISCOUNT, m.get(6).kind());
        assertEquals(IcarusEnchant.THORNS, m.get(6).discountEnchant());
        assertEquals(25.0, m.get(6).discountPercent());
        assertEquals(RewardKind.FARMING_XP, m.get(7).kind());
        assertEquals(25000, m.get(7).xpAmount());
        assertEquals(RewardKind.FARMING_XP, m.get(8).kind());
        assertEquals(50000, m.get(8).xpAmount());
    }

    @Test
    void carrotThresholdsMatchSpec() {
        List<CollectionsMilestone> m = carrot().milestones();
        assertEquals(9, m.size());
        int[] expected = {100, 250, 500, 1750, 5000, 10000, 25000, 50000, 100000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.get(i).threshold(), "Carrot milestone " + (i + 1));
        }
    }

    @Test
    void carrotRewardKinds() {
        List<CollectionsMilestone> m = carrot().milestones();
        assertEquals(RewardKind.FARMING_XP, m.get(0).kind());
        assertEquals(1000, m.get(0).xpAmount());
        assertEquals(RewardKind.FARMING_XP, m.get(1).kind());
        assertEquals(2000, m.get(1).xpAmount());
        assertEquals(RewardKind.FARMING_XP, m.get(2).kind());
        assertEquals(3000, m.get(2).xpAmount());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(3).kind());
        assertEquals(List.of(CollectionsCatalog.CARROT_CORE_RECIPE), m.get(3).recipes());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(4).kind());
        assertEquals(4, m.get(4).recipes().size()); // Sprout Armor
        assertTrue(m.get(4).recipes().contains(CollectionsCatalog.SPROUT_HELMET_RECIPE));
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(5).kind());
        assertEquals(List.of(CollectionsCatalog.ENCHANTED_CARROT_STICK_RECIPE), m.get(5).recipes());
        assertEquals(RewardKind.FARMING_XP, m.get(6).kind());
        assertEquals(10000, m.get(6).xpAmount());
        assertEquals(RewardKind.FARMING_XP, m.get(7).kind());
        assertEquals(25000, m.get(7).xpAmount());
        assertEquals(RewardKind.FARMING_XP, m.get(8).kind());
        assertEquals(50000, m.get(8).xpAmount());
    }

    @Test
    void cactusAndCarrotAreBothFarmingCategory() {
        assertEquals(CollectionsCategory.FARMING, cactus().category());
        assertEquals(CollectionsCategory.FARMING, carrot().category());
    }

    /**
     * Regression test for the real bug {@code CollectionsService#record} used to hit for any
     * entry whose block and drop are different materials: {@code find} used to check only
     * {@code material()}, so a harvest hook reporting the drop item (Carrot, Potato, Cocoa
     * Beans - see {@code skills.GeneralSkillListener#cropDrop}) never found its own entry and
     * silently recorded nothing at all. Both identities must resolve to the same entry now.
     */
    @Test
    void findMatchesBothBlockAndDropIdentity() {
        assertEquals(carrot(), CollectionsCatalog.find(Material.CARROT).orElseThrow());
        CollectionsEntry potato = CollectionsCatalog.find(Material.POTATOES).orElseThrow();
        assertEquals(potato, CollectionsCatalog.find(Material.POTATO).orElseThrow());
        CollectionsEntry cocoa = CollectionsCatalog.find(Material.COCOA).orElseThrow();
        assertEquals(cocoa, CollectionsCatalog.find(Material.COCOA_BEANS).orElseThrow());
    }

    @Test
    void everyFarmingEntryIsPresent() {
        List<CollectionsEntry> farming = CollectionsCatalog.entries(CollectionsCategory.FARMING);
        assertEquals(17, farming.size());
        for (CollectionsEntry entry : farming) {
            assertEquals(9, entry.milestones().size(), entry.nameEn() + " should have exactly 9 milestones");
        }
    }

    @Test
    void cocoaBeansMatchesSpec() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.COCOA).orElseThrow().milestones();
        int[] expected = {75, 200, 500, 2000, 5000, 10000, 25000, 50000, 100000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.get(i).threshold(), "Cocoa Beans milestone " + (i + 1));
        }
        assertEquals(RewardKind.FARMING_XP, m.get(0).kind());
        assertEquals(1000, m.get(0).xpAmount());
        // Adrenaline Potion - a PotionMix, no recipe key to gate (see the catalog's own doc).
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(1).kind());
        assertTrue(m.get(1).recipes().isEmpty());
        assertEquals(RewardKind.ENCHANT_DISCOUNT, m.get(2).kind());
        assertEquals(IcarusEnchant.REPLENISH, m.get(2).discountEnchant());
        assertEquals(25.0, m.get(2).discountPercent());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(3).kind());
        assertEquals(List.of(CollectionsCatalog.CHOCOLATE_CORE_RECIPE), m.get(3).recipes());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(4).kind());
        assertEquals(4, m.get(4).recipes().size());
        assertTrue(m.get(4).recipes().contains(CollectionsCatalog.CHOCOLATE_HELMET_RECIPE));
    }

    @Test
    void featherMatchesSpec() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.FEATHER).orElseThrow().milestones();
        int[] expected = {50, 100, 250, 1000, 2500, 10000, 25000, 50000, 100000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.get(i).threshold(), "Feather milestone " + (i + 1));
        }
        assertEquals(IcarusEnchant.PROJECTILE_PROTECTION, m.get(0).discountEnchant());
        assertEquals(IcarusEnchant.FEATHER_FALLING, m.get(1).discountEnchant());
        assertEquals(IcarusEnchant.AIMING, m.get(2).discountEnchant());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(3).kind());
        assertEquals(List.of(CollectionsCatalog.FEATHER_CORE_RECIPE), m.get(3).recipes());
        assertEquals(IcarusEnchant.SNIPE, m.get(4).discountEnchant());
    }

    @Test
    void mushroomMatchesSpec() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.RED_MUSHROOM).orElseThrow().milestones();
        int[] expected = {50, 100, 250, 1000, 2500, 10000, 25000, 50000, 100000};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], m.get(i).threshold(), "Mushroom milestone " + (i + 1));
        }
        // Magical Mushroom Soup - a real Shapeless recipe now, unlike the PotionMix-based
        // Adrenaline Potion/Resistance Potion, so it does get a real recipe key here.
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(0).kind());
        assertEquals(List.of(CollectionsCatalog.MAGICAL_MUSHROOM_SOUP_RECIPE), m.get(0).recipes());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(1).kind());
        assertEquals(4, m.get(1).recipes().size());
        assertTrue(m.get(1).recipes().contains(CollectionsCatalog.MUSHROOM_HELMET_RECIPE));
        assertEquals(List.of(CollectionsCatalog.RED_MUSHROOM_BLOCK_RECIPE, CollectionsCatalog.BROWN_MUSHROOM_BLOCK_RECIPE), m.get(2).recipes());
        assertEquals(List.of(CollectionsCatalog.MUSHROOM_CORE_RECIPE), m.get(3).recipes());
        assertEquals(RewardKind.RECIPE_UNLOCK, m.get(4).kind());
        assertEquals(List.of(CollectionsCatalog.MYSTICAL_MUSHROOM_SOUP_RECIPE), m.get(4).recipes());
    }

    @Test
    void pumpkinUnlocksCubismAtMilestoneThree() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.PUMPKIN).orElseThrow().milestones();
        assertEquals(RewardKind.ENCHANT_DISCOUNT, m.get(2).kind());
        assertEquals(IcarusEnchant.CUBISM, m.get(2).discountEnchant());
        assertEquals(500, m.get(2).threshold()); // unchanged from the generic ladder's own M3 threshold
    }

    @Test
    void rawRabbitUnlocksLuckDiscountsAtMilestonesOneAndTwo() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.RABBIT).orElseThrow().milestones();
        assertEquals(IcarusEnchant.LUCK, m.get(0).discountEnchant());
        assertEquals(IcarusEnchant.LUCK_OF_THE_SEA, m.get(1).discountEnchant());
    }

    @Test
    void wheatUnlocksHarvestingAtMilestoneOne() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.WHEAT).orElseThrow().milestones();
        assertEquals(IcarusEnchant.HARVESTING, m.get(0).discountEnchant());
    }

    @Test
    void wheatUnlocksFarmhandCoreAndHaymakerAtTwoFourFive() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.WHEAT).orElseThrow().milestones();
        assertEquals(List.of(CollectionsCatalog.FARMHAND_HELMET_RECIPE, CollectionsCatalog.FARMHAND_CHESTPLATE_RECIPE,
                CollectionsCatalog.FARMHAND_LEGGINGS_RECIPE, CollectionsCatalog.FARMHAND_BOOTS_RECIPE), m.get(1).recipes());
        assertEquals(List.of(CollectionsCatalog.WHEAT_CORE_RECIPE), m.get(3).recipes());
        assertEquals(List.of(CollectionsCatalog.HAYMAKER_HELMET_RECIPE, CollectionsCatalog.HAYMAKER_CHESTPLATE_RECIPE,
                CollectionsCatalog.HAYMAKER_LEGGINGS_RECIPE, CollectionsCatalog.HAYMAKER_BOOTS_RECIPE), m.get(4).recipes());
    }

    @Test
    void pumpkinUnlocksCoreBootsAndCrystal() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.PUMPKIN).orElseThrow().milestones();
        assertEquals(List.of(CollectionsCatalog.PUMPKIN_CORE_RECIPE), m.get(3).recipes());
        assertEquals(List.of(CollectionsCatalog.FARMER_BOOTS_RECIPE), m.get(4).recipes());
        assertEquals(List.of(CollectionsCatalog.FARM_CRYSTAL_RECIPE), m.get(5).recipes());
    }

    @Test
    void potatoUnlocksCoreAtMilestoneFour() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.POTATOES).orElseThrow().milestones();
        assertEquals(List.of(CollectionsCatalog.POTATO_CORE_RECIPE), m.get(3).recipes());
    }

    @Test
    void melonSliceUnlocksCoreAtMilestoneFour() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.MELON_SLICE).orElseThrow().milestones();
        assertEquals(List.of(CollectionsCatalog.MELON_CORE_RECIPE), m.get(3).recipes());
    }

    @Test
    void leatherUnlocksWardrobeAtOneThreeFiveSeven() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.LEATHER).orElseThrow().milestones();
        for (int i : new int[]{0, 2, 4, 6}) {
            assertEquals(RewardKind.RECIPE_UNLOCK, m.get(i).kind(), "milestone index " + i);
            assertTrue(m.get(i).recipes().isEmpty(), "milestone index " + i);
        }
        // M2/M4/M6 carry real crafted rewards (Cow Hat/Milk Core/Milkshake Core) instead of
        // the generic ladder's own plain Farming XP.
        assertEquals(List.of(CollectionsCatalog.COW_HAT_RECIPE), m.get(1).recipes());
        assertEquals(List.of(CollectionsCatalog.MILK_CORE_RECIPE), m.get(3).recipes());
        assertEquals(List.of(CollectionsCatalog.MILKSHAKE_CORE_RECIPE), m.get(5).recipes());
    }

    @Test
    void netherWartUnlocksPotionBagAtOneThreeFiveSevenNine() {
        List<CollectionsMilestone> m = CollectionsCatalog.find(Material.NETHER_WART).orElseThrow().milestones();
        for (int i : new int[]{0, 2, 4, 6, 8}) {
            assertEquals(RewardKind.RECIPE_UNLOCK, m.get(i).kind(), "milestone index " + i);
            assertTrue(m.get(i).recipes().isEmpty(), "milestone index " + i);
        }
        assertEquals(RewardKind.FARMING_XP, m.get(1).kind());
    }
}
