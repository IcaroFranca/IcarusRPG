package dev.icaro.foodtooltips.enchant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pure math coverage for {@link AnvilMenuService#repairAmount}/{@link
 * AnvilMenuService#boostedMaxDurability} - the Anvil's own ore-repair mechanic (Coal 5%,
 * Copper 15%, Iron 25%, Gold 30%, Diamond 50%, Netherite 100% + a permanent +10% Max
 * Durability bump, always a fraction of the item's OWN current Max Durability, never its
 * Material - per the player's own explicit spec). No Bukkit static touched, same "wiring vs
 * logic" split {@code creaking.CreakingSightService}/{@code heat.HeatService} already
 * established, so this needs no live server.
 */
final class AnvilMenuServiceTest {
    @Test
    void coalRestoresFivePercentOfMaxDurability() {
        assertEquals(50, AnvilMenuService.repairAmount(1000, 1000, 0.05));
    }

    @Test
    void netheriteFullyRepairsRegardlessOfMaxDurability() {
        assertEquals(1000, AnvilMenuService.repairAmount(1000, 1561, 1.0));
    }

    @Test
    void repairNeverOvershootsTheCurrentDamage() {
        // Only 10 damage left, but a 50% (Diamond) repair on a high max durability would
        // otherwise restore far more than there is damage to undo.
        assertEquals(10, AnvilMenuService.repairAmount(10, 1561, 0.50));
    }

    @Test
    void anAlreadyFullyRepairedItemRestoresNothing() {
        assertEquals(0, AnvilMenuService.repairAmount(0, 1000, 0.50));
    }

    /** The whole point of the player's own spec ("independe do material que o item é feito"): the SAME fraction of the SAME max durability restores the same amount, whether that max durability happens to belong to a cheap or an expensive item - repairAmount itself never even sees a Material. */
    @Test
    void sameMaxDurabilityAndFractionRestoreTheSameAmountRegardlessOfItem() {
        int fromWoodenHoe = AnvilMenuService.repairAmount(500, 500, 0.25);
        int fromNetheritePickaxe = AnvilMenuService.repairAmount(500, 500, 0.25);
        assertEquals(fromWoodenHoe, fromNetheritePickaxe);
        assertEquals(125, fromWoodenHoe);
    }

    @Test
    void roundsToTheNearestWholeDurabilityPoint() {
        // 15% of 1561 (a real vanilla Diamond tool's own Max Durability) is 234.15.
        assertEquals(234, AnvilMenuService.repairAmount(1561, 1561, 0.15));
    }

    /** Netherite is the one repair material that's still useful at 0 damage - {@code computeRepair} itself allows this case through (not covered here, see that method's own doc), but repairAmount's own math already naturally returns 0 rather than something nonsensical when there's nothing left to repair. */
    @Test
    void netheriteOnAnAlreadyFullItemRepairsNothingButStillValid() {
        assertEquals(0, AnvilMenuService.repairAmount(0, 1561, 1.0));
    }

    @Test
    void boostedMaxDurabilityAddsTenPercent() {
        assertEquals(1100, AnvilMenuService.boostedMaxDurability(1000));
    }

    /** The whole point of the player's own chosen design: repeated Netherite use compounds (×1.10 each time) rather than resetting to a flat +10% of some original baseline. */
    @Test
    void boostedMaxDurabilityCompoundsAcrossRepeatedApplications() {
        int afterOne = AnvilMenuService.boostedMaxDurability(1000);
        int afterTwo = AnvilMenuService.boostedMaxDurability(afterOne);
        int afterThree = AnvilMenuService.boostedMaxDurability(afterTwo);
        assertEquals(1100, afterOne);
        assertEquals(1210, afterTwo);
        assertEquals(1331, afterThree);
    }

    @Test
    void boostedMaxDurabilityRoundsToTheNearestWholePoint() {
        // 10% of 1561 is 156.1.
        assertEquals(1717, AnvilMenuService.boostedMaxDurability(1561));
    }
}
