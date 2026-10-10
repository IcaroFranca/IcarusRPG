package dev.icaro.foodtooltips.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.CombatProgress;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * Pure-math coverage for {@link MagicalPowerService#statsMultiplier} - the player's own exact
 * "Stats Multiplier = 29.97 x (ln(0.0019 x Magical Power + 1))^1.2" formula, same "logic vs.
 * wiring" split {@code heat.HeatServiceTest} already uses - plus plain-mock coverage (see {@code
 * reforge.ReforgeServiceTest}'s own doc on this pattern) for the Combat-level unlock gate added
 * on top of {@code global.LevelColorService}'s own selection shape (the reference table's own
 * "Combat XV (15)" requirement for the 5 Intermediate Powers).
 */
final class MagicalPowerServiceTest {
    @Test
    void zeroMagicalPowerGivesZeroMultiplier() {
        assertEquals(0.0, MagicalPowerService.statsMultiplier(0.0), 1e-9);
    }

    /** The reference image's own worked example: at 10 Magical Power, the header states the multiplier rounds to 0.25. */
    @Test
    void tenMagicalPowerMatchesTheReferenceImagesWorkedExample() {
        assertEquals(0.25, MagicalPowerService.statsMultiplier(10.0), 0.01);
    }

    @Test
    void multiplierGrowsWithMoreMagicalPower() {
        double low = MagicalPowerService.statsMultiplier(10.0);
        double high = MagicalPowerService.statsMultiplier(100.0);
        assertTrue(high > low);
    }

    @Test
    void multiplierNeverNegative() {
        assertTrue(MagicalPowerService.statsMultiplier(0.0) >= 0.0);
        assertTrue(MagicalPowerService.statsMultiplier(1.0) >= 0.0);
    }

    private static MagicalPowerService serviceAtCombatLevel(int level) {
        AccessoryBagService accessoryBag = mock(AccessoryBagService.class);
        CombatSkillService combat = mock(CombatSkillService.class);
        when(combat.progress(any(Player.class))).thenReturn(new CombatProgress(level, 0.0, 0.0));
        return new MagicalPowerService(accessoryBag, combat);
    }

    private static Player playerWithStoredSelection(String storedId) {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.getOrDefault(any(), eq(PersistentDataType.STRING), any())).thenReturn(storedId);
        Player p = mock(Player.class);
        when(p.getPersistentDataContainer()).thenReturn(pdc);
        return p;
    }

    @Test
    void everyStarterPowerIsAlwaysUnlocked() {
        MagicalPowerService service = serviceAtCombatLevel(0);
        Player p = playerWithStoredSelection("fortuitous");
        for (Power power : PowerCatalog.powers()) {
            if (power.type() == PowerType.STARTER) {
                assertTrue(service.unlocked(p, power), power.id() + " should always be unlocked");
            }
        }
    }

    @Test
    void intermediatePowerLockedBelowRequiredCombatLevel() {
        MagicalPowerService service = serviceAtCombatLevel(MagicalPowerService.REQUIRED_COMBAT_LEVEL - 1);
        Player p = playerWithStoredSelection("fortuitous");
        assertFalse(service.unlocked(p, PowerCatalog.find("commando").orElseThrow()));
    }

    @Test
    void intermediatePowerUnlockedAtRequiredCombatLevel() {
        MagicalPowerService service = serviceAtCombatLevel(MagicalPowerService.REQUIRED_COMBAT_LEVEL);
        Player p = playerWithStoredSelection("fortuitous");
        assertTrue(service.unlocked(p, PowerCatalog.find("commando").orElseThrow()));
    }

    @Test
    void selectRefusesALockedIntermediatePower() {
        MagicalPowerService service = serviceAtCombatLevel(0);
        Player p = playerWithStoredSelection("fortuitous");
        Power commando = PowerCatalog.find("commando").orElseThrow();
        assertFalse(service.select(p, commando));
        verify(p.getPersistentDataContainer(), never()).set(any(), eq(PersistentDataType.STRING), eq("commando"));
    }

    @Test
    void selectPersistsAnUnlockedPower() {
        MagicalPowerService service = serviceAtCombatLevel(MagicalPowerService.REQUIRED_COMBAT_LEVEL);
        Player p = playerWithStoredSelection("fortuitous");
        Power commando = PowerCatalog.find("commando").orElseThrow();
        assertTrue(service.select(p, commando));
        verify(p.getPersistentDataContainer()).set(any(), eq(PersistentDataType.STRING), eq("commando"));
    }

    /** Mirrors {@code global.LevelColorService#effective}'s own fallback: a stored selection that's no longer unlocked (e.g. Combat dropped via {@code /resetstats}) never grants its bonus. */
    @Test
    void effectiveFallsBackToDefaultWhenStoredSelectionIsNoLongerUnlocked() {
        MagicalPowerService service = serviceAtCombatLevel(0);
        Player p = playerWithStoredSelection("commando");
        assertEquals(PowerCatalog.defaultPower(), service.effective(p));
    }

    @Test
    void effectiveKeepsTheStoredSelectionWhenStillUnlocked() {
        MagicalPowerService service = serviceAtCombatLevel(MagicalPowerService.REQUIRED_COMBAT_LEVEL);
        Player p = playerWithStoredSelection("commando");
        assertEquals("commando", service.effective(p).id());
    }

    private static MagicalPowerService serviceWithMagicalPower(int magicalPower) {
        AccessoryBagService accessoryBag = mock(AccessoryBagService.class);
        when(accessoryBag.totalMagicalPower(any(Player.class))).thenReturn(magicalPower);
        CombatSkillService combat = mock(CombatSkillService.class);
        // Every Power unlocked, so each test gets exactly the Power it asks for.
        when(combat.progress(any(Player.class))).thenReturn(new CombatProgress(MagicalPowerService.REQUIRED_COMBAT_LEVEL, 0.0, 0.0));
        return new MagicalPowerService(accessoryBag, combat);
    }

    /** The whole point of the mechanic: every extra accessory (more Magical Power) has to mean bigger real bonuses. */
    @Test
    void moreMagicalPowerGivesBiggerAppliedBonuses() {
        Player p = playerWithStoredSelection("protected");
        MagicalPowerService few = serviceWithMagicalPower(12);
        MagicalPowerService many = serviceWithMagicalPower(78);

        assertTrue(many.healthBonus(p) > few.healthBonus(p));
        assertTrue(many.defensePoints(p) > few.defensePoints(p));
        assertTrue(many.strengthPoints(p) > few.strengthPoints(p));
    }

    /** The whole-point stats are exactly what the Defense/Strength/Mining Speed hooks receive - same rounding the menus show. */
    @Test
    void wholePointBonusesAreTheRoundedLiveBonus() {
        Player p = playerWithStoredSelection("ominous");
        MagicalPowerService service = serviceWithMagicalPower(78);

        assertEquals(Math.round(service.strengthBonus(p)), service.strengthPoints(p));
        assertEquals(Math.round(service.miningSpeedBonus(p)), service.miningSpeedPoints(p));
        assertEquals(0, service.defensePoints(p), "Ominous grants no Defense at all");
    }

    @Test
    void noAccessoriesMeansNoBonus() {
        Player p = playerWithStoredSelection("warrior");
        MagicalPowerService service = serviceWithMagicalPower(0);

        assertEquals(0.0, service.healthBonus(p), 1e-9);
        assertEquals(0L, service.strengthPoints(p));
        assertEquals(0.0, service.critChanceBonus(p), 1e-9);
    }
}
