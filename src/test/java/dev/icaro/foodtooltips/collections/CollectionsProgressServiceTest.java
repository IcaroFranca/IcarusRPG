package dev.icaro.foodtooltips.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

/**
 * {@link CollectionsProgressService#achieved(CollectionsEntry, int)} is pure arithmetic over
 * a plain {@code int} - no {@code Player}/PDC involved - so it's fully testable without any
 * Bukkit runtime, unlike the reroll-shaped tests in {@code reforge.ReforgeServiceTest}.
 * Regression coverage for exactly the boundary behavior a milestone system like this lives
 * or dies by: a total sitting exactly ON a threshold counts as reached (matching the
 * player's own "Milestone 1 (100)" phrasing - 100 collected means milestone 1 is done, not
 * still one short), and a total between two thresholds only counts the lower one.
 */
final class CollectionsProgressServiceTest {
    private final CollectionsProgressService service = new CollectionsProgressService();
    private final CollectionsEntry cactus = CollectionsCatalog.find(Material.CACTUS).orElseThrow();

    @Test
    void zeroCollectedIsZeroMilestones() {
        assertEquals(0, this.service.achieved(this.cactus, 0));
    }

    @Test
    void oneBelowFirstThresholdIsStillZero() {
        assertEquals(0, this.service.achieved(this.cactus, 99));
    }

    @Test
    void exactlyOnAThresholdCountsAsReached() {
        assertEquals(1, this.service.achieved(this.cactus, 100));
        assertEquals(4, this.service.achieved(this.cactus, 1000));
    }

    @Test
    void betweenTwoThresholdsCountsOnlyTheLowerOne() {
        assertEquals(1, this.service.achieved(this.cactus, 249));
        assertEquals(4, this.service.achieved(this.cactus, 2499));
    }

    @Test
    void pastTheLastThresholdCapsAtMaxMilestones() {
        assertEquals(9, this.service.achieved(this.cactus, 1_000_000));
        assertEquals(9, this.service.maxMilestones(this.cactus));
    }

    @Test
    void totalMilestonesAcrossCatalogIsPureDataSum() {
        // Same shape check as bestiary/mining's own totalMilestones - just verifying the
        // static structure this sums over (every catalog entry counted, not just Farming's)
        // without needing a Player, since achieved(entry, count) is already covered above.
        int farmingEntries = CollectionsCatalog.entries(CollectionsCategory.FARMING).size();
        assertEquals(17, farmingEntries);
        for (var category : List.of(CollectionsCategory.COMBAT, CollectionsCategory.MINING,
                CollectionsCategory.FORAGING, CollectionsCategory.FISHING)) {
            assertEquals(0, CollectionsCatalog.entries(category).size(), category + " should have no entries yet");
        }
    }
}
