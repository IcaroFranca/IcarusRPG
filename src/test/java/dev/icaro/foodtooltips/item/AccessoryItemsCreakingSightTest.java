package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

/**
 * Persistent-data coverage for the Pale Oak Log Collection's own Creaking Sight Talisman/Ring/
 * Artifact line - {@link AccessoryItems#mark}/{@link AccessoryItems#creakingSightRange}/{@link
 * AccessoryItems#family} round-tripped through a plain in-memory {@link
 * FakePersistentDataContainer}, same "no live server needed" reasoning {@code
 * reforge.ReforgeServiceTest} already established for this project. Covers the task's own
 * requirements #2 (persistent range data for all three tiers) and #3 (the three tiers sharing
 * one family, which is what makes the Accessory Bag's existing family-exclusivity rule apply
 * to this line the same way it already does to Feather/Vaccine/Mangrove Sweep/Cherry Fortune -
 * see {@code skills.AccessoryBagService#scheduleFilterSweep}, not re-tested here since it's
 * generic and already exercised by every other accessory line).
 */
final class AccessoryItemsCreakingSightTest {
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
    void talismanRangeIsTwelveBlocks() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.TALISMAN, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 12);
        assertEquals(12, AccessoryItems.creakingSightRange(item));
    }

    @Test
    void ringRangeIsTwentyBlocks() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.RING, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 20);
        assertEquals(20, AccessoryItems.creakingSightRange(item));
    }

    @Test
    void artifactRangeIsThirtyTwoBlocks() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.ARTIFACT, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 32);
        assertEquals(32, AccessoryItems.creakingSightRange(item));
    }

    /** Requirement #3: all three tiers share the exact same family string, so the Accessory Bag's existing exclusivity rule (one accessory per family) already covers this line without any Creaking Sight-specific code. */
    @Test
    void allThreeTiersShareTheSameFamily() {
        ItemStack talisman = fakeItem();
        AccessoryItems.mark(talisman.getItemMeta(), AccessoryType.TALISMAN, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 12);
        ItemStack ring = fakeItem();
        AccessoryItems.mark(ring.getItemMeta(), AccessoryType.RING, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 20);
        ItemStack artifact = fakeItem();
        AccessoryItems.mark(artifact.getItemMeta(), AccessoryType.ARTIFACT, "creaking_sight", 0, 0.0, 0.0, 0.0, 0, 0, 32);

        assertEquals("creaking_sight", AccessoryItems.family(talisman));
        assertEquals("creaking_sight", AccessoryItems.family(ring));
        assertEquals("creaking_sight", AccessoryItems.family(artifact));
    }

    /** An unrelated accessory (a Mangrove Sweep one) must read 0 Creaking Sight range - the two lines' own PDC keys never bleed into each other. */
    @Test
    void unrelatedAccessoryHasZeroCreakingSightRange() {
        ItemStack item = fakeItem();
        AccessoryItems.mark(item.getItemMeta(), AccessoryType.RING, "mangrove_sweep", 0, 0.0, 0.0, 0.0, 3, 0, 0);
        assertEquals(0, AccessoryItems.creakingSightRange(item));
    }
}
