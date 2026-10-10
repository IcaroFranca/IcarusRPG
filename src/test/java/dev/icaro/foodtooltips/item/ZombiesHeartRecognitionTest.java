package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * {@link ZombiesHeartService#isZombiesHeart} is the only thing standing between "any player
 * head" and a Zombie Sword/Armor craft (see {@code ZombieSwordService#guardZombiesHeartRecipes})
 * - a heart crafted before its own {@code zombies_heart} marker existed (0.76.219) must still
 * count, or the recipe silently produces nothing ("ta pedindo uma player head generica").
 */
final class ZombiesHeartRecognitionTest {
    @Test
    void heartWithCurrentMarkerIsRecognized() {
        FakePersistentDataContainer pdc = new FakePersistentDataContainer();
        pdc.set(new NamespacedKey("foodtooltips", "zombies_heart"), PersistentDataType.BYTE, (byte) 1);

        assertTrue(ZombiesHeartService.isZombiesHeart(item(pdc)));
    }

    @Test
    void heartCraftedBeforeTheMarkerExistedIsRecognizedByItsAccessoryFamily() {
        FakePersistentDataContainer pdc = new FakePersistentDataContainer();
        ItemStack legacy = item(pdc);
        AccessoryItems.mark(legacy.getItemMeta(), AccessoryType.CHARM, "zombies_heart", 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);

        assertTrue(ZombiesHeartService.isZombiesHeart(legacy));
    }

    @Test
    void otherCustomHeadsAreNotZombiesHearts() {
        FakePersistentDataContainer pdc = new FakePersistentDataContainer();
        ItemStack otherAccessory = item(pdc);
        AccessoryItems.mark(otherAccessory.getItemMeta(), AccessoryType.CHARM, "skeleton_hat", 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);

        assertFalse(ZombiesHeartService.isZombiesHeart(otherAccessory));
        assertFalse(ZombiesHeartService.isZombiesHeart(item(new FakePersistentDataContainer())));
        assertFalse(ZombiesHeartService.isZombiesHeart(null));
    }

    @Test
    void everyRecipeThatConsumesAHeartIsGuarded() {
        assertTrue(ZombiesHeartService.RECIPES.contains(CollectionsCatalog.ZOMBIE_SWORD_RECIPE));
        assertTrue(ZombiesHeartService.RECIPES.contains(CollectionsCatalog.ZOMBIE_CHESTPLATE_RECIPE));
        assertTrue(ZombiesHeartService.RECIPES.contains(CollectionsCatalog.ZOMBIE_LEGGINGS_RECIPE));
        assertTrue(ZombiesHeartService.RECIPES.contains(CollectionsCatalog.ZOMBIE_BOOTS_RECIPE));
        assertFalse(ZombiesHeartService.RECIPES.contains(CollectionsCatalog.LEAPING_SWORD_RECIPE));
    }

    private static ItemStack item(FakePersistentDataContainer pdc) {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }
}
