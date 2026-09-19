package dev.icaro.foodtooltips.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.icaro.foodtooltips.item.ItemTier;
import org.junit.jupiter.api.Test;

/**
 * Transcription check for the whole bow reforge table (9 prefixes x 6 tiers) against the
 * numbers the player specified - a plain data/record equality test, no Bukkit runtime
 * involved at all (unlike {@link ReforgeServiceTest}, nothing here touches {@code ItemStack},
 * {@code Attribute}, or any other Registry-backed type), so every value below is checked with
 * zero risk of a silent typo surviving into the live table. {@link ItemTier#E} is deliberately
 * left unchecked - it's never a real bow tier (see {@link ItemTier#MYTHIC}'s own class doc),
 * {@link BowReforgePrefix#stats} just maps it to {@link ReforgeStats#NONE} the same way every
 * other catalog's own unused tier does.
 */
final class BowReforgePrefixTest {

    private static void assertStats(BowReforgePrefix prefix, ItemTier tier,
                                     double strength, double critChance, double critDamage, double intelligence) {
        assertEquals(new ReforgeStats(strength, critChance, critDamage, intelligence, 0.0), prefix.stats(tier),
                prefix + " @ " + tier);
    }

    @Test
    void deadly() {
        assertStats(BowReforgePrefix.DEADLY, ItemTier.D, 0, 10, 5, 0);
        assertStats(BowReforgePrefix.DEADLY, ItemTier.C, 0, 13, 10, 0);
        assertStats(BowReforgePrefix.DEADLY, ItemTier.B, 0, 16, 18, 0);
        assertStats(BowReforgePrefix.DEADLY, ItemTier.A, 0, 19, 32, 0);
        assertStats(BowReforgePrefix.DEADLY, ItemTier.S, 0, 22, 50, 0);
        assertStats(BowReforgePrefix.DEADLY, ItemTier.MYTHIC, 0, 25, 78, 0);
    }

    @Test
    void fine() {
        assertStats(BowReforgePrefix.FINE, ItemTier.D, 3, 5, 2, 0);
        assertStats(BowReforgePrefix.FINE, ItemTier.C, 7, 7, 4, 0);
        assertStats(BowReforgePrefix.FINE, ItemTier.B, 12, 9, 7, 0);
        assertStats(BowReforgePrefix.FINE, ItemTier.A, 18, 12, 10, 0);
        assertStats(BowReforgePrefix.FINE, ItemTier.S, 25, 15, 15, 0);
        assertStats(BowReforgePrefix.FINE, ItemTier.MYTHIC, 33, 18, 20, 0);
    }

    @Test
    void grand() {
        assertStats(BowReforgePrefix.GRAND, ItemTier.D, 25, 0, 0, 0);
        assertStats(BowReforgePrefix.GRAND, ItemTier.C, 32, 0, 0, 0);
        assertStats(BowReforgePrefix.GRAND, ItemTier.B, 40, 0, 0, 0);
        assertStats(BowReforgePrefix.GRAND, ItemTier.A, 50, 0, 0, 0);
        assertStats(BowReforgePrefix.GRAND, ItemTier.S, 60, 0, 0, 0);
        assertStats(BowReforgePrefix.GRAND, ItemTier.MYTHIC, 75, 0, 0, 0);
    }

    @Test
    void hasty() {
        assertStats(BowReforgePrefix.HASTY, ItemTier.D, 3, 20, 0, 0);
        assertStats(BowReforgePrefix.HASTY, ItemTier.C, 5, 25, 0, 0);
        assertStats(BowReforgePrefix.HASTY, ItemTier.B, 7, 30, 0, 0);
        assertStats(BowReforgePrefix.HASTY, ItemTier.A, 10, 40, 0, 0);
        assertStats(BowReforgePrefix.HASTY, ItemTier.S, 15, 50, 0, 0);
        assertStats(BowReforgePrefix.HASTY, ItemTier.MYTHIC, 20, 60, 0, 0);
    }

    @Test
    void neat() {
        assertStats(BowReforgePrefix.NEAT, ItemTier.D, 0, 10, 4, 3);
        assertStats(BowReforgePrefix.NEAT, ItemTier.C, 0, 12, 8, 6);
        assertStats(BowReforgePrefix.NEAT, ItemTier.B, 0, 14, 14, 10);
        assertStats(BowReforgePrefix.NEAT, ItemTier.A, 0, 17, 20, 15);
        assertStats(BowReforgePrefix.NEAT, ItemTier.S, 0, 20, 30, 20);
        assertStats(BowReforgePrefix.NEAT, ItemTier.MYTHIC, 0, 25, 40, 25);
    }

    @Test
    void rapid() {
        assertStats(BowReforgePrefix.RAPID, ItemTier.D, 2, 0, 35, 0);
        assertStats(BowReforgePrefix.RAPID, ItemTier.C, 3, 0, 45, 0);
        assertStats(BowReforgePrefix.RAPID, ItemTier.B, 4, 0, 55, 0);
        assertStats(BowReforgePrefix.RAPID, ItemTier.A, 7, 0, 65, 0);
        assertStats(BowReforgePrefix.RAPID, ItemTier.S, 10, 0, 75, 0);
        assertStats(BowReforgePrefix.RAPID, ItemTier.MYTHIC, 15, 0, 90, 0);
    }

    @Test
    void unreal() {
        assertStats(BowReforgePrefix.UNREAL, ItemTier.D, 3, 8, 5, 0);
        assertStats(BowReforgePrefix.UNREAL, ItemTier.C, 7, 9, 10, 0);
        assertStats(BowReforgePrefix.UNREAL, ItemTier.B, 12, 10, 18, 0);
        assertStats(BowReforgePrefix.UNREAL, ItemTier.A, 18, 11, 32, 0);
        assertStats(BowReforgePrefix.UNREAL, ItemTier.S, 25, 13, 50, 0);
        assertStats(BowReforgePrefix.UNREAL, ItemTier.MYTHIC, 34, 15, 70, 0);
    }

    @Test
    void awkward() {
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.D, 0, 10, 5, -5);
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.C, 0, 12, 10, -10);
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.B, 0, 15, 18, -18);
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.A, 0, 20, 22, -32);
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.S, 0, 25, 30, -50);
        assertStats(BowReforgePrefix.AWKWARD, ItemTier.MYTHIC, 0, 30, 35, -72);
    }

    @Test
    void rich() {
        assertStats(BowReforgePrefix.RICH, ItemTier.D, 2, 10, 1, 3);
        assertStats(BowReforgePrefix.RICH, ItemTier.C, 3, 12, 2, 6);
        assertStats(BowReforgePrefix.RICH, ItemTier.B, 4, 14, 4, 10);
        assertStats(BowReforgePrefix.RICH, ItemTier.A, 7, 17, 7, 15);
        assertStats(BowReforgePrefix.RICH, ItemTier.S, 10, 20, 15, 20);
        assertStats(BowReforgePrefix.RICH, ItemTier.MYTHIC, 15, 25, 25, 25);
    }

    @Test
    void displayWordsAreTitleCased() {
        assertEquals("Deadly", BowReforgePrefix.DEADLY.displayWord());
        assertEquals("Awkward", BowReforgePrefix.AWKWARD.displayWord());
        assertEquals("Rich", BowReforgePrefix.RICH.displayWord());
    }
}
