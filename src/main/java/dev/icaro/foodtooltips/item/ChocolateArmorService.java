package dev.icaro.foodtooltips.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Chocolate Armor's own full-set bonus: permanent Saturation while all 4 pieces are worn.
 * Each piece's own +{@value dev.icaro.foodtooltips.item.FarmingCollectionsItemsService#CHOCOLATE_HEALTH_PER_PIECE}
 * Health is baked directly by {@code FarmingCollectionsItemsService#chocolatePiece} - this
 * class only marks pieces (so {@link #isFullSet} can recognize them) and refreshes the
 * Saturation effect.
 *
 * <p>"Permanente" is implemented the exact same way {@link RabbitArmorService} does for its
 * own Jump Boost: a short top-up refreshed every tick the full set is worn, not a literal
 * infinite-duration effect tracked start/stop - correct regardless of how the set was put on
 * or taken off, and self-expires the instant a piece comes off, without needing a separate
 * removal path.
 */
public final class ChocolateArmorService {
    private static final NamespacedKey CHOCOLATE_ARMOR_KEY = new NamespacedKey("foodtooltips", "chocolate_armor_piece");
    /** A few ticks more than this class's own caller interval, so consecutive top-ups never let the effect visibly flicker off between passes. */
    private static final int SATURATION_DURATION_TICKS = 60;

    public static void markChocolatePiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(CHOCOLATE_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isChocolatePiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CHOCOLATE_ARMOR_KEY, PersistentDataType.BYTE);
    }

    public static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return false;
        }
        return isChocolatePiece(eq.getHelmet()) && isChocolatePiece(eq.getChestplate())
                && isChocolatePiece(eq.getLeggings()) && isChocolatePiece(eq.getBoots());
    }

    /** Call from the same periodic per-player pass every other armor-set bonus in this plugin uses. */
    public void applyToInventory(Player p) {
        if (isFullSet(p)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, SATURATION_DURATION_TICKS, 0, false, false, false));
        }
    }
}
