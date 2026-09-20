package dev.icaro.foodtooltips.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Cactus Armor's own full-set bonus: wearing all 4 pieces reflects {@value #REFLECT_PERCENT}
 * (33%) of any hit taken back at the attacker. Each piece's own forced Health/Defense is baked
 * directly by {@code FarmingCollectionsItemsService#cactusPiece} (a real {@code MAX_HEALTH}
 * attribute modifier plus {@code ArmorDefenseService#forceDefense}) - this class only marks
 * pieces so {@link #isFullSet} can recognize them, and handles the reflect itself.
 *
 * <p>{@link #reflect} mirrors {@code ArmorEnchantEffectListener#thorns} exactly: MONITOR
 * priority (after every other damage-modifying listener has already settled on the final
 * damage), and {@code attacker.damage(reflect)} called with NO damage-source argument, so the
 * reflected hit doesn't itself re-enter {@code EntityDamageByEntityEvent} and ping-pong forever
 * between two entities that both happen to reflect damage.
 */
public final class CactusArmorService implements Listener {
    private static final NamespacedKey CACTUS_ARMOR_KEY = new NamespacedKey("foodtooltips", "cactus_armor_piece");
    public static final double REFLECT_PERCENT = 0.33;

    public static void markCactusPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(CACTUS_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isCactusPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CACTUS_ARMOR_KEY, PersistentDataType.BYTE);
    }

    /** Whether {@code entity} is wearing all 4 Cactus Armor pieces at once - anything less doesn't get the reflect. */
    public static boolean isFullSet(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq == null) {
            return false;
        }
        return isCactusPiece(eq.getHelmet()) && isCactusPiece(eq.getChestplate())
                && isCactusPiece(eq.getLeggings()) && isCactusPiece(eq.getBoots());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void reflect(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity target) || !(e.getDamager() instanceof LivingEntity attacker)) {
            return;
        }
        if (!isFullSet(target)) {
            return;
        }
        double reflect = e.getFinalDamage() * REFLECT_PERCENT;
        if (reflect > 0.0 && attacker.isValid() && !attacker.isDead()) {
            attacker.damage(reflect);
        }
    }
}
