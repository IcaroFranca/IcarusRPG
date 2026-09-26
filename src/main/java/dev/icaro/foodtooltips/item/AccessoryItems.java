package dev.icaro.foodtooltips.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The shared PDC "contract" every accessory item (Feather Talisman/Ring/Artifact today,
 * any future Collection's own accessory line later) writes at creation time and {@code
 * AccessoryBagService} reads back generically, without needing to know which specific item
 * it's looking at - same "read a plain data tag, not a hardcoded Material/name check"
 * approach {@code item.ItemTierService} already uses for tier lookups. {@link #type} gates
 * which of the Accessory Bag's three equip slots an item can go in; {@link #fallHeightBonus}/
 * {@link #fallDamageReductionPercent} are summed across every currently equipped accessory
 * (see {@code AccessoryBagListener#fall}).
 */
public final class AccessoryItems {
    private static final NamespacedKey TYPE_KEY = new NamespacedKey("foodtooltips", "accessory_type");
    private static final NamespacedKey FALL_HEIGHT_KEY = new NamespacedKey("foodtooltips", "accessory_fall_height_bonus");
    private static final NamespacedKey FALL_REDUCTION_KEY = new NamespacedKey("foodtooltips", "accessory_fall_damage_reduction");

    private AccessoryItems() {
    }

    /** Tags {@code meta} as an accessory of {@code type}, granting {@code fallHeightBonus} extra blocks of fall-damage-free height and {@code fallDamageReductionPercent}% off whatever fall damage still applies past that - called once while building the item, before {@code ItemStack#setItemMeta}. */
    public static void mark(ItemMeta meta, AccessoryType type, int fallHeightBonus, double fallDamageReductionPercent) {
        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(FALL_HEIGHT_KEY, PersistentDataType.INTEGER, fallHeightBonus);
        meta.getPersistentDataContainer().set(FALL_REDUCTION_KEY, PersistentDataType.DOUBLE, fallDamageReductionPercent);
    }

    /** {@code item}'s own {@link AccessoryType}, or {@code null} for anything that isn't an accessory at all - what {@code AccessoryBagService} checks before letting an item into one of its three type-specific equip slots. */
    public static AccessoryType type(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        String raw = meta.getPersistentDataContainer().get(TYPE_KEY, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return AccessoryType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static int fallHeightBonus(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0 : meta.getPersistentDataContainer().getOrDefault(FALL_HEIGHT_KEY, PersistentDataType.INTEGER, 0);
    }

    public static double fallDamageReductionPercent(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0.0;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0.0 : meta.getPersistentDataContainer().getOrDefault(FALL_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
    }
}
