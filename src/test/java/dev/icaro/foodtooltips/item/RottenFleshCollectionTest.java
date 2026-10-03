package dev.icaro.foodtooltips.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for the Rotten Flesh Collection's own live stat calculations - {@link
 * ZombiePickaxeService#fortuneForCount} (the "Rotten" ability's own cap), {@link
 * ZombieHatService#defenseForCount} (uncapped), and {@link ZombieSwordService#vitalityCost}
 * (Instant Heal's own resource cost) - none touch Bukkit, same "wiring vs logic" split {@code
 * HurricaneBowServiceTest} already established, so this needs no live server.
 */
final class RottenFleshCollectionTest {
    @Test
    void miningFortuneScalesFivePerUndeadMob() {
        assertEquals(0, ZombiePickaxeService.fortuneForCount(0));
        assertEquals(5, ZombiePickaxeService.fortuneForCount(1));
        assertEquals(20, ZombiePickaxeService.fortuneForCount(4));
    }

    @Test
    void miningFortuneCapsAtTwentyFive() {
        assertEquals(25, ZombiePickaxeService.fortuneForCount(5));
        assertEquals(25, ZombiePickaxeService.fortuneForCount(100));
    }

    @Test
    void zombieHatDefenseScalesTenPerZombieWithNoCap() {
        assertEquals(0, ZombieHatService.defenseForCount(0));
        assertEquals(10, ZombieHatService.defenseForCount(1));
        assertEquals(500, ZombieHatService.defenseForCount(50));
    }

    @Test
    void instantHealCostsAQuarterOfMaxVitality() {
        assertEquals(25.0, ZombieSwordService.vitalityCost(100.0));
        assertEquals(50.0, ZombieSwordService.vitalityCost(200.0));
        assertEquals(0.0, ZombieSwordService.vitalityCost(0.0));
    }
}
