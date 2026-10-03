package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for {@link SkeletonsHelmetService#boneCountForRemaining} - the Bone
 * Shield's own charge indicator, per the player's own explicit "os ossos são referentes ao
 * escudo" (all 3 bones vanish the instant a hit consumes the shield, then reappear one by one
 * over the 30-second regen). No Bukkit touched, same "wiring vs logic" split {@code
 * HurricaneBowServiceTest} already established, so this needs no live server.
 */
final class SkeletonsHelmetServiceTest {
    @Test
    void readyOrPastDueShowsAllThreeBones() {
        assertEquals(3, SkeletonsHelmetService.boneCountForRemaining(0L));
        assertEquals(3, SkeletonsHelmetService.boneCountForRemaining(-5000L));
    }

    @Test
    void justConsumedShowsNoBones() {
        assertEquals(0, SkeletonsHelmetService.boneCountForRemaining(30_000L));
    }

    @Test
    void bonesReappearGraduallyDuringRegen() {
        assertEquals(1, SkeletonsHelmetService.boneCountForRemaining(20_000L));
        assertEquals(2, SkeletonsHelmetService.boneCountForRemaining(10_000L));
    }

    @Test
    void almostReadyRoundsUpToFullCount() {
        assertEquals(3, SkeletonsHelmetService.boneCountForRemaining(1L));
    }
}
