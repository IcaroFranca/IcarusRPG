package dev.icaro.foodtooltips.item;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Speedster Armor's own full-set bonus (Sugar Cane Collections M3): +{@value #FULL_SET_SPEED_BONUS}
 * Speed while all 4 pieces are worn. Each piece's own Defense/flat per-piece Speed is baked
 * directly by {@code FarmingCollectionsItemsService#speedsterPiece} - a full-set-conditional
 * bonus can't be baked onto any single piece's own meta the same way (removing a DIFFERENT
 * piece would need to revoke it), so this recomputes a real transient {@link AttributeModifier}
 * on the player's own {@link Attribute#MOVEMENT_SPEED} every tick instead - same idiom {@code
 * enchant.ArmorEnchantEffectListener#applyGrowthHealth} already uses for Growth's own Health
 * bonus.
 */
public final class SpeedsterArmorService {
    private static final NamespacedKey SPEEDSTER_ARMOR_KEY = new NamespacedKey("foodtooltips", "speedster_armor_piece");
    private static final NamespacedKey FULL_SET_SPEED_KEY = new NamespacedKey("foodtooltips", "speedster_full_set_speed");
    public static final int FULL_SET_SPEED_BONUS = 20;
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;

    public static void markSpeedsterPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(SPEEDSTER_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isSpeedsterPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(SPEEDSTER_ARMOR_KEY, PersistentDataType.BYTE);
    }

    public static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return false;
        }
        return isSpeedsterPiece(eq.getHelmet()) && isSpeedsterPiece(eq.getChestplate())
                && isSpeedsterPiece(eq.getLeggings()) && isSpeedsterPiece(eq.getBoots());
    }

    /** Call from the same periodic per-player pass every other armor-set bonus in this plugin uses. */
    public void applyFullSetSpeed(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(FULL_SET_SPEED_KEY.getNamespace(), FULL_SET_SPEED_KEY.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        if (isFullSet(p)) {
            speed.addTransientModifier(new AttributeModifier(FULL_SET_SPEED_KEY, FULL_SET_SPEED_BONUS * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
