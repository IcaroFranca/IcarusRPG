package dev.icaro.foodtooltips.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Structural coverage for {@link PowerCatalog} - the player's own confirmed scope is exactly the 10 powers from the reference table (5 Starter, 5 Intermediate), nothing from the real Hypixel Skyblock power list beyond it. */
final class PowerCatalogTest {
    @Test
    void hasExactlyTenPowers() {
        assertEquals(10, PowerCatalog.powers().size());
    }

    @Test
    void everyIdIsUnique() {
        Set<String> ids = new HashSet<>();
        for (Power power : PowerCatalog.powers()) {
            assertTrue(ids.add(power.id()), "duplicate id: " + power.id());
        }
    }

    @Test
    void fiveStarterAndFiveIntermediate() {
        long starter = PowerCatalog.powers().stream().filter(power -> power.type() == PowerType.STARTER).count();
        long intermediate = PowerCatalog.powers().stream().filter(power -> power.type() == PowerType.INTERMEDIATE).count();
        assertEquals(5, starter);
        assertEquals(5, intermediate);
    }

    @Test
    void findResolvesEveryCatalogId() {
        for (Power power : PowerCatalog.powers()) {
            assertEquals(power, PowerCatalog.find(power.id()).orElse(null));
        }
    }

    @Test
    void findIsCaseInsensitiveAndNullSafe() {
        assertEquals(PowerCatalog.defaultPower(), PowerCatalog.find("FORTUITOUS").orElse(null));
        assertTrue(PowerCatalog.find(null).isEmpty());
        assertTrue(PowerCatalog.find("not_a_power").isEmpty());
    }

    @Test
    void defaultPowerIsFortuitous() {
        assertEquals("fortuitous", PowerCatalog.defaultPower().id());
    }

    @Test
    void everyPowerGrantsAtLeastOneStat() {
        for (Power power : PowerCatalog.powers()) {
            double sum = power.health() + power.defense() + power.strength() + power.speed()
                    + power.critChance() + power.critDamage() + power.intelligence() + power.miningSpeed();
            assertTrue(sum > 0.0, power.id() + " grants no stats at all");
        }
    }

    @Test
    void everyPowerHasAnIconAndAName() {
        for (Power power : PowerCatalog.powers()) {
            assertTrue(power.icon() != null);
            assertTrue(power.name() != null && !power.name().isBlank());
        }
    }

    @Test
    void powersListIsImmutable() {
        List<Power> powers = PowerCatalog.powers();
        assertTrue(powers.getClass().getName().contains("Immutable") || powers instanceof java.util.List);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> powers.add(PowerCatalog.defaultPower()));
    }
}
