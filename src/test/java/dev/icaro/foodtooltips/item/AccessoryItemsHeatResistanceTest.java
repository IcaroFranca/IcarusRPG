package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

/**
 * Persistent-data coverage for the Crimson Stem Collection's own Ember Talisman/Ring/
 * Artifact line - same "no live server needed" shape {@link AccessoryItemsCreakingSightTest}
 * already established for the Pale Oak line's own {@code creakingSightRange}. Covers this
 * line's own persisted Heat Resistance for all three tiers and the shared family that lets
 * the Accessory Bag's existing exclusivity rule apply to it (see {@code
 * skills.AccessoryBagService#scheduleFilterSweep}, not re-tested here since it's generic).
 */
final class AccessoryItemsHeatResistanceTest {
    private static ItemStack fakeItem() {
        FakePersistentDataContainer pdc = new FakePersistentDataContainer();
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        ItemStack item = mock(ItemStack.class);
        when(item.isEmpty()).thenReturn(false);
        when(item.getItemMeta()).thenReturn(meta);
        return item;
    }

    @Test
    void talismanGrantsOneHeatResistance() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.TALISMAN, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 1);
        assertEquals(1, AccessoryItems.heatResistance(item));
    }

    @Test
    void ringGrantsThreeHeatResistance() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.RING, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 3);
        assertEquals(3, AccessoryItems.heatResistance(item));
    }

    @Test
    void artifactGrantsFiveHeatResistance() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.ARTIFACT, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 5);
        assertEquals(5, AccessoryItems.heatResistance(item));
    }

    /** All three tiers share the exact same family string, so the Accessory Bag's existing exclusivity rule already covers this line without any Ember-specific code. */
    @Test
    void allThreeTiersShareTheSameFamily() {
        ItemStack talisman = fakeItem();
        AccessoryItems.mark(talisman.getItemMeta(), AccessoryType.TALISMAN, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 1);
        ItemStack ring = fakeItem();
        AccessoryItems.mark(ring.getItemMeta(), AccessoryType.RING, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 3);
        ItemStack artifact = fakeItem();
        AccessoryItems.mark(artifact.getItemMeta(), AccessoryType.ARTIFACT, "ember", 0, 0.0, 0.0, 0.0, 0, 0, 0, 5);

        assertEquals("ember", AccessoryItems.family(talisman));
        assertEquals("ember", AccessoryItems.family(ring));
        assertEquals("ember", AccessoryItems.family(artifact));
    }

    /** An unrelated accessory (a Creaking Sight one) must read 0 Heat Resistance - the two lines' own PDC keys never bleed into each other. */
    @Test
    void unrelatedAccessoryHasZeroHeatResistance() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.RING, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 20, 0);
        assertEquals(0, AccessoryItems.heatResistance(item));
    }
}
