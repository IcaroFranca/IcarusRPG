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
 * Rabbit Armor's own full-set bonus (Raw Rabbit Collections M3): permanent Jump Boost II
 * while sneaking. Each piece's own Defense/Speed is baked directly by {@code
 * FarmingCollectionsItemsService#rabbitPiece}/{@code #rabbitHelmet} - this class only marks
 * pieces (so {@link #isFullSet} can recognize them) and refreshes the Jump Boost effect.
 *
 * <p>"Permanente" is implemented as a short top-up refreshed every tick the condition holds
 * (same technique {@code MushroomArmorService} already uses for its own Night Vision), not a
 * literal infinite-duration effect tracked start/stop - correct regardless of how sneaking
 * started or stopped, and self-expires the instant the player stops sneaking or takes off a
 * piece, without needing a separate removal path.
 */
public final class RabbitArmorService {
    private static final NamespacedKey RABBIT_ARMOR_KEY = new NamespacedKey("foodtooltips", "rabbit_armor_piece");
    /** A few ticks more than this class's own caller interval, so consecutive top-ups never let the effect visibly flicker off between passes. */
    private static final int JUMP_BOOST_DURATION_TICKS = 60;

    public static void markRabbitPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(RABBIT_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isRabbitPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(RABBIT_ARMOR_KEY, PersistentDataType.BYTE);
    }

    public static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return false;
        }
        return isRabbitPiece(eq.getHelmet()) && isRabbitPiece(eq.getChestplate())
                && isRabbitPiece(eq.getLeggings()) && isRabbitPiece(eq.getBoots());
    }

    /** Call from the same periodic per-player pass every other armor-set bonus in this plugin uses. */
    public void applyToInventory(Player p) {
        if (isFullSet(p) && p.isSneaking()) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, JUMP_BOOST_DURATION_TICKS, 1, false, false, false));
        }
    }
}
