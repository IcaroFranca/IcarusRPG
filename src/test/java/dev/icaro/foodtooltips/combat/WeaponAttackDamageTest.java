package dev.icaro.foodtooltips.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * {@link CombatListener#ownAttackDamage}: the real hit must use the Attack Damage the weapon
 * itself carries (what the stats screen shows), not its Material's generic table value - the
 * Zombie Sword is an IRON_SWORD carrying 100, and used to hit like a plain 30-damage iron sword.
 */
final class WeaponAttackDamageTest {
    @Test
    void zombieSwordUsesItsOwnHundredNotIronSwordsThirty() {
        List<AttributeModifier> zombieSword = List.of(
                modifier("zombie_sword_damage", 100.0, EquipmentSlotGroup.MAINHAND),
                modifier("vanilla_attack_damage_zero", -1.0, EquipmentSlotGroup.MAINHAND));

        assertEquals(100.0, CombatListener.ownAttackDamage(zombieSword), 1e-9);
    }

    @Test
    void offHandOnlyModifiersDoNotCountForAMainHandHit() {
        List<AttributeModifier> weapon = List.of(
                modifier("main", 30.0, EquipmentSlotGroup.MAINHAND),
                modifier("zero", -1.0, EquipmentSlotGroup.MAINHAND),
                modifier("offhand_only", 50.0, EquipmentSlotGroup.OFFHAND));

        assertEquals(30.0, CombatListener.ownAttackDamage(weapon), 1e-9);
    }

    @Test
    void weaponWithoutItsOwnModifiersFallsBackToTheMaterialTable() {
        assertNull(CombatListener.ownAttackDamage(List.of()));
        assertNull(CombatListener.ownAttackDamage((java.util.Collection<AttributeModifier>) null));
        assertNull(CombatListener.ownAttackDamage((ItemStack) null));
    }

    private static AttributeModifier modifier(String key, double amount, EquipmentSlotGroup slot) {
        return new AttributeModifier(new NamespacedKey("test", key), amount, AttributeModifier.Operation.ADD_NUMBER, slot);
    }
}
