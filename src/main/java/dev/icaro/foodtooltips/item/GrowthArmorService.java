package dev.icaro.foodtooltips.item;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Armor of Growth's own full-set bonus: every mob a player kills while wearing all 4
 * pieces permanently raises a per-player kill counter (capped at {@value
 * #MAX_BONUS_HEALTH}), stored directly on the player's own {@code
 * PersistentDataContainer} (survives re-logging, unlike an in-memory map) - see {@link
 * #kill}. That counter becomes +1 Max Health per point, but only while the full set
 * stays worn (see {@link #applyToInventory}: a real {@link Attribute#MAX_HEALTH}
 * modifier added/removed directly on the player's own attribute instance, same {@code
 * AttributeInstance#addTransientModifier}/{@code removeModifier} idiom {@code
 * ArmorDefenseService#zero} already uses to neutralize vanilla Armor - not baked into
 * any single piece, unlike every other armor set's own flat per-piece Health in this
 * plugin, since this one has to keep changing without the item itself ever being
 * re-crafted). Each piece's own flat Health/Defense is baked directly by {@code
 * ForagingCollectionsItemsService#growthPiece} instead, same as Chocolate/Cactus Armor's
 * own. Regeneration is also granted while the full set is worn (a plain top-up, same
 * "permanent" idiom {@link ChocolateArmorService} uses for Saturation) - fixed, not
 * scaled by the kill counter at all, per explicit request.
 */
public final class GrowthArmorService implements Listener {
    private static final NamespacedKey GROWTH_ARMOR_KEY = new NamespacedKey("foodtooltips", "growth_armor_piece");
    private static final NamespacedKey GROWTH_KILLS_KEY = new NamespacedKey("foodtooltips", "growth_armor_kills");
    private static final NamespacedKey GROWTH_HEALTH_KEY = new NamespacedKey("foodtooltips", "growth_armor_bonus_health");
    public static final int MAX_BONUS_HEALTH = 100;
    /** A few ticks more than this class's own caller interval, same reasoning {@link ChocolateArmorService#SATURATION_DURATION_TICKS} documents. */
    private static final int REGENERATION_DURATION_TICKS = 60;
    private static final int REGENERATION_AMPLIFIER = 1;

    public static void markGrowthPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(GROWTH_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isGrowthPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(GROWTH_ARMOR_KEY, PersistentDataType.BYTE);
    }

    public static boolean isFullSet(Player p) {
        EntityEquipment eq = p.getEquipment();
        if (eq == null) {
            return false;
        }
        return isGrowthPiece(eq.getHelmet()) && isGrowthPiece(eq.getChestplate())
                && isGrowthPiece(eq.getLeggings()) && isGrowthPiece(eq.getBoots());
    }

    /**
     * +1 permanent Max Health (capped at {@value #MAX_BONUS_HEALTH}) per mob killed
     * while wearing the full set - counted regardless of what killed it, same "any mob"
     * reading {@code EntityDeathEvent#getEntity().getKiller()} already gives every other
     * kill hook in this plugin (see {@code enchant.MeleeEnchantEffectListener}).
     */
    @EventHandler(ignoreCancelled = true)
    public void kill(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null || !isFullSet(p)) {
            return;
        }
        int kills = p.getPersistentDataContainer().getOrDefault(GROWTH_KILLS_KEY, PersistentDataType.INTEGER, 0);
        if (kills < MAX_BONUS_HEALTH) {
            p.getPersistentDataContainer().set(GROWTH_KILLS_KEY, PersistentDataType.INTEGER, kills + 1);
        }
    }

    /** Call from the same periodic per-player pass every other armor-set bonus in this plugin uses. */
    public void applyToInventory(Player p) {
        boolean fullSet = isFullSet(p);
        AttributeInstance attribute = p.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) {
            AttributeModifier old = attribute.getModifier(Key.key(GROWTH_HEALTH_KEY.getNamespace(), GROWTH_HEALTH_KEY.getKey()));
            int kills = p.getPersistentDataContainer().getOrDefault(GROWTH_KILLS_KEY, PersistentDataType.INTEGER, 0);
            int bonus = fullSet ? Math.min(kills, MAX_BONUS_HEALTH) : 0;
            if (old != null && Math.round(old.getAmount()) != bonus) {
                attribute.removeModifier(old);
                old = null;
            }
            if (old == null && bonus > 0) {
                attribute.addTransientModifier(new AttributeModifier(GROWTH_HEALTH_KEY, bonus, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (fullSet) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, REGENERATION_DURATION_TICKS, REGENERATION_AMPLIFIER, false, false, false));
        }
    }
}
