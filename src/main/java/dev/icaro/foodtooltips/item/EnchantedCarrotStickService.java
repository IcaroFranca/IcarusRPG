package dev.icaro.foodtooltips.item;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enchanted Carrot on a Stick (Carrot Collections M6) - doubles any mount's Speed while held
 * in either hand, per the player's own spec ("dobrar a velocidade de qualquer montaria
 * quando estiver segurando na mão"). {@code Operation#ADD_SCALAR} at 1.0 doubles the mount's
 * base Speed ({@code base * (1 + 1.0)}) - the modifier lives on the MOUNT's own {@link
 * Attribute#MOVEMENT_SPEED}, not the item, and is recomputed every tick from {@code
 * FoodTooltipsPlugin}'s existing periodic loop (same idiom {@code
 * SpeedsterArmorService#applyFullSetSpeed} uses), so it appears/disappears immediately as
 * the item is drawn, sheathed, or the player dismounts.
 *
 * <p>"Qualquer montaria" is read generically as "whatever living, rideable entity {@code
 * p} is currently sitting on and that has a Speed attribute at all" - not just horses, so a
 * saddled Pig/Strider/Camel benefits too, without an exhaustive per-species type check.
 */
public final class EnchantedCarrotStickService {
    private static final NamespacedKey MOUNT_SPEED_KEY = new NamespacedKey("foodtooltips", "enchanted_carrot_stick_mount_speed");

    /** Call from the same periodic per-player pass every other level/gear-scaling bonus in this plugin uses. */
    public void applyMountSpeed(Player p) {
        if (!(p.getVehicle() instanceof LivingEntity mount)) {
            return;
        }
        AttributeInstance speed = mount.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(MOUNT_SPEED_KEY.getNamespace(), MOUNT_SPEED_KEY.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        if (isHolding(p)) {
            speed.addTransientModifier(new AttributeModifier(MOUNT_SPEED_KEY,
                    FarmingCollectionsItemsService.ENCHANTED_CARROT_STICK_MOUNT_SPEED_MULTIPLIER - 1.0, AttributeModifier.Operation.ADD_SCALAR));
        }
    }

    private static boolean isHolding(Player p) {
        return isMarked(p.getInventory().getItemInMainHand()) || isMarked(p.getInventory().getItemInOffHand());
    }

    private static boolean isMarked(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(FarmingCollectionsItemsService.ENCHANTED_CARROT_STICK_KEY, PersistentDataType.BYTE);
    }
}
