package dev.icaro.foodtooltips.creaking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

/**
 * Requirement #4 (hostile/neutral/passive/boss classification) and part of #7 (Players never
 * glow) for {@link EntityClassifier#classify} - plain Mockito mocks of the real Bukkit entity
 * interfaces, no live server needed (same reasoning {@code reforge.ReforgeServiceTest}
 * established): Mockito's own mock of e.g. {@link Zombie} genuinely satisfies {@code
 * instanceof Monster}/{@code instanceof Enemy} checks, since the generated proxy implements
 * that interface's full hierarchy, exactly like a real Bukkit implementation would.
 */
final class EntityClassifierTest {
    private static <T extends Mob> T aliveMob(Class<T> type) {
        T mob = mock(type);
        when(mob.isValid()).thenReturn(true);
        when(mob.isDead()).thenReturn(false);
        return mob;
    }

    @Test
    void wardenIsAlwaysPurpleRegardlessOfTargetOrHostility() {
        Mob warden = aliveMob(Mob.class);
        when(warden.getType()).thenReturn(EntityType.WARDEN);
        assertEquals(GlowCategory.PURPLE, EntityClassifier.classify(warden));
    }

    @Test
    void zombieIsRedViaTheHostileEnemyMarker() {
        Zombie zombie = aliveMob(Zombie.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(zombie.getTarget()).thenReturn(null);
        assertEquals(GlowCategory.RED, EntityClassifier.classify(zombie));
    }

    @Test
    void aNeutralWolfWithATargetIsRedNotYellow() {
        Wolf wolf = aliveMob(Wolf.class);
        when(wolf.getType()).thenReturn(EntityType.WOLF);
        when(wolf.getTarget()).thenReturn(mock(LivingEntity.class));
        assertEquals(GlowCategory.RED, EntityClassifier.classify(wolf));
    }

    @Test
    void aNeutralWolfWithNoTargetIsYellow() {
        Wolf wolf = aliveMob(Wolf.class);
        when(wolf.getType()).thenReturn(EntityType.WOLF);
        when(wolf.getTarget()).thenReturn(null);
        assertEquals(GlowCategory.YELLOW, EntityClassifier.classify(wolf));
    }

    @Test
    void aCowIsGreenViaTheAnimalsMarker() {
        Cow cow = aliveMob(Cow.class);
        when(cow.getType()).thenReturn(EntityType.COW);
        when(cow.getTarget()).thenReturn(null);
        assertEquals(GlowCategory.GREEN, EntityClassifier.classify(cow));
    }

    @Test
    void aVillagerIsGreenViaThePassiveFallbackList() {
        Villager villager = aliveMob(Villager.class);
        when(villager.getType()).thenReturn(EntityType.VILLAGER);
        when(villager.getTarget()).thenReturn(null);
        assertEquals(GlowCategory.GREEN, EntityClassifier.classify(villager));
    }

    /**
     * A bare {@link Mob} mock - deliberately NOT also mocked as {@link org.bukkit.entity.Enemy}
     * or {@link org.bukkit.entity.Animals} (unlike a real Bukkit Cat, which the {@code getType}
     * value below borrows from, but this mock's own interface set is intentionally minimal) -
     * proves the classifier returns {@code null} (no glow) rather than guessing when a {@link
     * Mob} doesn't match any of the four recognized buckets, per the player's own explicit
     * "caso alguma criatura não possa ser classificada com segurança, não aplique glowing nela".
     */
    @Test
    void anUnrecognizedMobShapeGlowsNothingRatherThanGuessing() {
        Mob mystery = aliveMob(Mob.class);
        when(mystery.getType()).thenReturn(EntityType.CAT);
        when(mystery.getTarget()).thenReturn(null);
        assertNull(EntityClassifier.classify(mystery));
    }

    @Test
    void playersAreNeverClassified() {
        Player player = mock(Player.class);
        when(player.isValid()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        assertNull(EntityClassifier.classify(player));
    }

    @Test
    void armorStandsAreNeverClassified() {
        ArmorStand standEntity = mock(ArmorStand.class);
        when(standEntity.isValid()).thenReturn(true);
        when(standEntity.isDead()).thenReturn(false);
        assertNull(EntityClassifier.classify(standEntity));
    }

    @Test
    void droppedItemsAreNeverClassified() {
        Item item = mock(Item.class);
        when(item.isValid()).thenReturn(true);
        when(item.isDead()).thenReturn(false);
        assertNull(EntityClassifier.classify(item));
    }

    @Test
    void invalidOrDeadEntitiesAreNeverClassified() {
        Zombie invalid = mock(Zombie.class);
        when(invalid.isValid()).thenReturn(false);
        assertNull(EntityClassifier.classify(invalid));

        Zombie dead = mock(Zombie.class);
        when(dead.isValid()).thenReturn(true);
        when(dead.isDead()).thenReturn(true);
        assertNull(EntityClassifier.classify(dead));
    }
}
