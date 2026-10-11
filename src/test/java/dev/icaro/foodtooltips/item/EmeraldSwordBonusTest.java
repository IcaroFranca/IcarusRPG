package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The Emerald Sword's coin bonus - {@code DMG = 130 + 2.5 x coins^(1/4)}, coins capped at 2B (the player's own formula). */
final class EmeraldSwordBonusTest {

    @Test
    void emptyPurseGivesNoBonus() {
        assertEquals(0.0, EmeraldCollectionService.swordDamageBonus(0), 1e-9);
        assertEquals(0.0, EmeraldCollectionService.swordDamageBonus(-500), 1e-9);
    }

    @Test
    void followsTheFourthRootFormula() {
        assertEquals(25.0, EmeraldCollectionService.swordDamageBonus(10_000), 1e-9);
        assertEquals(250.0, EmeraldCollectionService.swordDamageBonus(100_000_000), 1e-6);
    }

    @Test
    void coinsAreCappedAtTwoBillion() {
        double atCap = EmeraldCollectionService.swordDamageBonus(2_000_000_000.0);
        assertEquals(atCap, EmeraldCollectionService.swordDamageBonus(50_000_000_000.0), 1e-9);
        assertEquals(2.5 * Math.pow(2_000_000_000.0, 0.25), atCap, 1e-9);
    }
}
