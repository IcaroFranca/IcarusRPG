package dev.icaro.foodtooltips.creaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Requirements #5 (entities entering/staying/leaving range), #6 (removal when the accessory
 * itself is removed) and #8 (real flags/glow never clobbered by the artificial one) for {@link
 * CreakingSightService}'s own packet-free internals ({@link CreakingSightService#diff}/{@link
 * CreakingSightService#trueSharedFlags}/{@link CreakingSightService#isCitizensNpc}) - none of
 * these touch a Bukkit static or PacketEvents, so, like {@link EntityClassifierTest}, this
 * needs no live server. The packet-sending half ({@code scanOne}/{@code revertOne}/team
 * management) is deliberately NOT unit-tested here - it's wiring around these already-tested
 * pieces, the same "wiring vs. logic" split {@code reforge.ReforgeServiceTest} draws around
 * the one path it can't exercise without a live {@code RegistryAccess} either (see that class's
 * own doc).
 */
final class CreakingSightServiceTest {
    private static UUID id() {
        return UUID.randomUUID();
    }

    @Test
    void anEntityNewToRangeIsReportedAsChanged() {
        UUID entering = id();
        Map<UUID, GlowCategory> current = new HashMap<>();
        Map<UUID, GlowCategory> nearbyNow = Map.of(entering, GlowCategory.GREEN);

        CreakingSightService.DiffResult diff = CreakingSightService.diff(current, nearbyNow);

        assertEquals(Map.of(entering, GlowCategory.GREEN), diff.changed());
        assertTrue(diff.left().isEmpty());
    }

    @Test
    void anEntityStillNearbyWithTheSameCategoryIsNotReReported() {
        UUID staying = id();
        Map<UUID, GlowCategory> current = new HashMap<>(Map.of(staying, GlowCategory.RED));
        Map<UUID, GlowCategory> nearbyNow = Map.of(staying, GlowCategory.RED);

        CreakingSightService.DiffResult diff = CreakingSightService.diff(current, nearbyNow);

        assertTrue(diff.changed().isEmpty(), "no packet should be sent for something that hasn't changed");
        assertTrue(diff.left().isEmpty());
    }

    @Test
    void anEntityThatChangedCategoryIsReportedAsChanged() {
        UUID target = id();
        Map<UUID, GlowCategory> current = new HashMap<>(Map.of(target, GlowCategory.YELLOW));
        Map<UUID, GlowCategory> nearbyNow = Map.of(target, GlowCategory.RED);

        CreakingSightService.DiffResult diff = CreakingSightService.diff(current, nearbyNow);

        assertEquals(Map.of(target, GlowCategory.RED), diff.changed());
    }

    @Test
    void anEntityNoLongerNearbyIsReportedAsLeft() {
        UUID leaving = id();
        Map<UUID, GlowCategory> current = new HashMap<>(Map.of(leaving, GlowCategory.GREEN));
        Map<UUID, GlowCategory> nearbyNow = Map.of();

        CreakingSightService.DiffResult diff = CreakingSightService.diff(current, nearbyNow);

        assertTrue(diff.changed().isEmpty());
        assertEquals(Set.of(leaving), diff.left());
    }

    /** Requirement #6: removing the accessory means the next pass has an empty {@code nearbyNow} (range resolves to 0, so nothing is ever sampled) - every previously-tracked entity must come back as "left" so {@code clearAll} reverts every one of them. */
    @Test
    void everyTrackedEntityIsLeftWhenNothingIsNearbyAnymore() {
        Map<UUID, GlowCategory> current = new HashMap<>(Map.of(id(), GlowCategory.RED, id(), GlowCategory.GREEN, id(), GlowCategory.YELLOW));
        int before = current.size();

        CreakingSightService.DiffResult diff = CreakingSightService.diff(current, Map.of());

        assertTrue(diff.changed().isEmpty());
        assertEquals(before, diff.left().size());
    }

    @Test
    void citizensIsNeverQueriedWhenItIsNotInstalled() {
        // citizensInstalled=false must short-circuit before ever touching CitizensAPI - if it
        // didn't, this would throw (no live Citizens registry exists in this test) instead of
        // returning false.
        assertFalse(CreakingSightService.isCitizensNpc(false, mock(org.bukkit.entity.Entity.class)));
    }

    /**
     * Uses a plain {@link Entity} mock, not {@link LivingEntity}, deliberately - the real
     * safety-critical case here is exactly this one (see this class's own doc: "o efeito
     * artificial não pode remover um glowing que já esteja sendo aplicado"), and {@link
     * Entity#isGlowing()} is already on the base interface, so this covers it without ever
     * entering {@link CreakingSightService#trueSharedFlags}'s own {@code instanceof
     * LivingEntity} branch - see {@link #livingEntitySpecificFlagsArePreserved}'s own doc for
     * why that branch can't be exercised in this test environment at all.
     */
    @Test
    void trueSharedFlagsPreservesARealGlowAlreadyOnTheEntity() {
        Entity entity = mock(Entity.class);
        when(entity.isGlowing()).thenReturn(true);
        byte flags = CreakingSightService.trueSharedFlags(entity);
        assertTrue((flags & 0x40) != 0, "a real glow already on the entity must survive the reconstruction");
    }

    @Test
    void trueSharedFlagsPreservesFireAndSneaking() {
        Entity entity = mock(Entity.class);
        when(entity.getFireTicks()).thenReturn(20);
        when(entity.isSneaking()).thenReturn(true);

        byte flags = CreakingSightService.trueSharedFlags(entity);

        assertTrue((flags & 0x01) != 0, "on fire");
        assertTrue((flags & 0x02) != 0, "sneaking");
        assertTrue((flags & 0x40) == 0, "not glowing for real, so the glow bit must stay off here");
    }

    @Test
    void trueSharedFlagsIsAllZeroForAPlainEntityWithNothingSet() {
        Entity entity = mock(Entity.class);
        assertEquals((byte) 0, CreakingSightService.trueSharedFlags(entity));
    }

    /**
     * {@code LivingEntity#hasPotionEffect(PotionEffectType.INVISIBILITY)} - part of {@link
     * CreakingSightService#trueSharedFlags}'s own {@code instanceof LivingEntity} branch -
     * needs {@code PotionEffectType}'s static initializer to run, which (same root cause
     * {@code reforge.ReforgeServiceTest}'s own class doc already documents for {@code
     * Material#asItemType()}) needs a live {@code RegistryAccess} bound by a real running
     * server: {@code org.bukkit.Registry}'s own static initializer throws ("No RegistryAccess
     * implementation found") outside one, and once it does, every other Registry-backed
     * static (this one included) stays broken for the rest of this JVM. Left {@link Disabled}
     * rather than silently skipped or worked around, same as that class's own precedent -
     * real coverage needs a working fake {@code RegistryAccess} wired in via {@code
     * META-INF/services}, tracked as the same follow-up work.
     */
    @Test
    @Disabled("PotionEffectType.INVISIBILITY needs a live RegistryAccess - see this method's own doc")
    void livingEntitySpecificFlagsArePreserved() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.isSwimming()).thenReturn(true);
        when(entity.hasPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(true);
        when(entity.isGliding()).thenReturn(true);

        byte flags = CreakingSightService.trueSharedFlags(entity);

        assertTrue((flags & 0x10) != 0, "swimming");
        assertTrue((flags & 0x20) != 0, "invisible");
        assertTrue((flags & (byte) 0x80) != 0, "gliding");
    }
}
