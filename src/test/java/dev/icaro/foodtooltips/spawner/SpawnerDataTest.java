package dev.icaro.foodtooltips.spawner;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SpawnerDataTest {
    @Test
    void roundTrip() {
        SpawnerData d = new SpawnerData();
        d.set(SpawnerModifier.SUGAR, 3);
        d.set(SpawnerModifier.CHORUS_FRUIT, 1);
        d.set(SpawnerModifier.WOOL, 1);
        d.wool = Material.RED_WOOL;
        d.off.add(SpawnerModifier.Toggle.NO_AI);
        d.base = new int[]{200, 800, 4, 6, 16, 4};

        SpawnerData back = SpawnerData.decode(d.encode());
        assertEquals(3, back.count(SpawnerModifier.SUGAR));
        assertEquals(1, back.count(SpawnerModifier.CHORUS_FRUIT));
        assertEquals(Material.RED_WOOL, back.wool);
        assertArrayEquals(new int[]{200, 800, 4, 6, 16, 4}, back.base);
        assertFalse(back.active(SpawnerModifier.Toggle.NO_AI));
        assertTrue(back.active(SpawnerModifier.Toggle.SILENT));
        assertFalse(back.active(SpawnerModifier.Toggle.REDSTONE)); // no Comparator in
    }

    @Test
    void countsAreCappedAndZeroRemoves() {
        SpawnerData d = new SpawnerData();
        d.set(SpawnerModifier.FERMENTED_SPIDER_EYE, 99);
        assertEquals(SpawnerModifier.FERMENTED_SPIDER_EYE.max, d.count(SpawnerModifier.FERMENTED_SPIDER_EYE));
        d.set(SpawnerModifier.FERMENTED_SPIDER_EYE, 0);
        assertFalse(d.hasModifiers());
    }

    @Test
    void emptyAndGarbageDecodeSafely() {
        assertFalse(SpawnerData.decode(null).hasModifiers());
        SpawnerData d = SpawnerData.decode("mods=SUGAR:2,NOPE:3,CLOCK:x;off=WHAT;wool=DIRT;base=1,2");
        assertEquals(2, d.count(SpawnerModifier.SUGAR));
        assertEquals(0, d.count(SpawnerModifier.CLOCK));
        assertEquals(Material.WHITE_WOOL, d.wool);
        assertNull(d.base);
    }

    @Test
    void oldConduitBecomesGlowstone() {
        assertEquals(1, SpawnerData.decode("mods=CONDUIT:1").count(SpawnerModifier.GLOWSTONE));
    }
}
