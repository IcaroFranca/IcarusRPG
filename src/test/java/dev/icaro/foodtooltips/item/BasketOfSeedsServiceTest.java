package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for {@link BasketOfSeedsService#cardinalFacing(float)} - the yaw-to-row-
 * direction snapping {@link BasketOfSeedsService#plantLine} uses, per the player's own "toda a
 * linha de blocos na direção que o jogador estava olhando" spec. No Bukkit static touched (just
 * the plain {@link BlockFace} enum), same "wiring vs logic" split this codebase already uses
 * elsewhere, so this needs no live server.
 */
final class BasketOfSeedsServiceTest {
    @Test
    void facingSouthAroundZero() {
        assertEquals(BlockFace.SOUTH, BasketOfSeedsService.cardinalFacing(0.0f));
        assertEquals(BlockFace.SOUTH, BasketOfSeedsService.cardinalFacing(44.9f));
        assertEquals(BlockFace.SOUTH, BasketOfSeedsService.cardinalFacing(315.0f));
        assertEquals(BlockFace.SOUTH, BasketOfSeedsService.cardinalFacing(359.9f));
    }

    @Test
    void facingWestAroundNinety() {
        assertEquals(BlockFace.WEST, BasketOfSeedsService.cardinalFacing(45.0f));
        assertEquals(BlockFace.WEST, BasketOfSeedsService.cardinalFacing(90.0f));
        assertEquals(BlockFace.WEST, BasketOfSeedsService.cardinalFacing(134.9f));
    }

    @Test
    void facingNorthAroundOneEighty() {
        assertEquals(BlockFace.NORTH, BasketOfSeedsService.cardinalFacing(135.0f));
        assertEquals(BlockFace.NORTH, BasketOfSeedsService.cardinalFacing(180.0f));
        assertEquals(BlockFace.NORTH, BasketOfSeedsService.cardinalFacing(224.9f));
    }

    @Test
    void facingEastAroundTwoSeventy() {
        assertEquals(BlockFace.EAST, BasketOfSeedsService.cardinalFacing(225.0f));
        assertEquals(BlockFace.EAST, BasketOfSeedsService.cardinalFacing(270.0f));
        assertEquals(BlockFace.EAST, BasketOfSeedsService.cardinalFacing(314.9f));
    }

    @Test
    void negativeYawNormalizesCorrectly() {
        // -90 degrees is the same facing as 270.
        assertEquals(BlockFace.EAST, BasketOfSeedsService.cardinalFacing(-90.0f));
    }

    @Test
    void yawBeyondFullCircleNormalizesCorrectly() {
        // 450 degrees is the same facing as 90.
        assertEquals(BlockFace.WEST, BasketOfSeedsService.cardinalFacing(450.0f));
    }
}
