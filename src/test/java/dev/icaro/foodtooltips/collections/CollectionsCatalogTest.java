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
        assertTrue(m.get(4).recipes().isEmpty()); // Sprout Armor: no recipe registered yet.
        assertEquals(RewardKind.FARMING_XP, m.get(5).kind());
        assertEquals(5000, m.get(5).xpAmount());
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
}
