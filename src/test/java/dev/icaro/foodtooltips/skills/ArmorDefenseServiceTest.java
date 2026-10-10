package dev.icaro.foodtooltips.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

/**
 * {@link ArmorDefenseService#ignoresArmorDefense}: a Zombie/Skeleton Miner wears Miner's Armor for
 * looks only - none of its armor's Defense (base or Protection) may apply to it - while any other
 * wearer keeps counting its armor exactly as before.
 */
final class ArmorDefenseServiceTest {
    private static final int PROTECTION_BONUS = 20;

    @Test
    void ordinaryWearerCountsArmorAndProtection() {
        ArmorDefenseService service = service();
        LivingEntity mob = mobWearingDiamondChestplate();

        assertEquals(40 + PROTECTION_BONUS, service.defense(mob));
    }

    @Test
    void entityThatIgnoresArmorDefenseGetsNoneOfIt() {
        ArmorDefenseService service = service();
        LivingEntity miner = mobWearingDiamondChestplate();
        service.ignoresArmorDefense(e -> e == miner);

        assertEquals(0, service.defense(miner));
        assertEquals(0.0, service.damageReduction(miner), 1e-9);
    }

    @Test
    void ignoringOneEntityDoesNotAffectOthers() {
        ArmorDefenseService service = service();
        LivingEntity miner = mobWearingDiamondChestplate();
        LivingEntity other = mobWearingDiamondChestplate();
        service.ignoresArmorDefense(e -> e == miner);

        assertEquals(40 + PROTECTION_BONUS, service.defense(other));
    }

    private static ArmorDefenseService service() {
        ArmorDefenseService service = new ArmorDefenseService();
        service.protectionBonus(e -> PROTECTION_BONUS);
        return service;
    }

    private static LivingEntity mobWearingDiamondChestplate() {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        ItemStack chestplate = mock(ItemStack.class);
        when(chestplate.getType()).thenReturn(Material.DIAMOND_CHESTPLATE);
        when(chestplate.getItemMeta()).thenReturn(meta);
        EntityEquipment equipment = mock(EntityEquipment.class);
        when(equipment.getChestplate()).thenReturn(chestplate);
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getEquipment()).thenReturn(equipment);
        when(pdc.has(any(), any())).thenReturn(false);
        return mob;
    }
}
