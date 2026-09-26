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
 * approach {@code item.ItemTierService} already uses for tier lookups. {@link #type} is
 * purely informational (Talisman/Ring/Artifact tier flavor - the Accessory Bag itself no
 * longer restricts by it, any type fits in any of its 9 slots); {@link #family} is what the
 * bag DOES restrict on - see its own doc. {@link #fallHeightBonus}/{@link
 * #fallDamageReductionPercent} (the Feather line) and {@link #poisonReductionPercent} (the
 * Vaccine line) are summed across every currently stored accessory (see {@code
 * AccessoryBagListener#fall}/{@code AccessoryBagListener#poison}) - an item from one line
 * simply leaves the other line's own stat(s) at zero.
 */
public final class AccessoryItems {
    private static final NamespacedKey TYPE_KEY = new NamespacedKey("foodtooltips", "accessory_type");
    private static final NamespacedKey FAMILY_KEY = new NamespacedKey("foodtooltips", "accessory_family");
    private static final NamespacedKey FALL_HEIGHT_KEY = new NamespacedKey("foodtooltips", "accessory_fall_height_bonus");
    private static final NamespacedKey FALL_REDUCTION_KEY = new NamespacedKey("foodtooltips", "accessory_fall_damage_reduction");
    private static final NamespacedKey POISON_REDUCTION_KEY = new NamespacedKey("foodtooltips", "accessory_poison_damage_reduction");

    private AccessoryItems() {
    }

    /**
     * Tags {@code meta} as an accessory of {@code type}, belonging to {@code family} (a
     * plain identifier for the Collection/upgrade line it comes from, e.g. {@code "feather"}
     * for the Feather Talisman → Ring → Artifact line, {@code "vaccine"} for the Potato
     * Collection's own Vaccine Talisman → Ring → Artifact line - lowercase by convention, but
     * never parsed as an enum so any future Collection can introduce its own without touching
     * this class), granting {@code fallHeightBonus} extra blocks of fall-damage-free height,
     * {@code fallDamageReductionPercent}% off whatever fall damage still applies past that,
     * and {@code poisonReductionPercent}% off any {@code DamageCause.POISON} damage taken -
     * called once while building the item, before {@code ItemStack#setItemMeta}. An item that
     * doesn't grant one of these stats simply passes 0 for it.
     */
    public static void mark(ItemMeta meta, AccessoryType type, String family, int fallHeightBonus, double fallDamageReductionPercent, double poisonReductionPercent) {
        meta.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(FAMILY_KEY, PersistentDataType.STRING, family);
        meta.getPersistentDataContainer().set(FALL_HEIGHT_KEY, PersistentDataType.INTEGER, fallHeightBonus);
        meta.getPersistentDataContainer().set(FALL_REDUCTION_KEY, PersistentDataType.DOUBLE, fallDamageReductionPercent);
        meta.getPersistentDataContainer().set(POISON_REDUCTION_KEY, PersistentDataType.DOUBLE, poisonReductionPercent);
    }

    /** {@code item}'s own {@link AccessoryType} (Talisman/Ring/Artifact), or {@code null} for anything that isn't an accessory at all - what {@code AccessoryBagService} checks before letting an item into the bag at all. Purely descriptive; see {@link #family} for the bag's own actual equip restriction. */
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

    /**
     * {@code item}'s own family identifier (e.g. {@code "feather"}), or {@code null} if it
     * isn't an accessory at all - what {@code AccessoryBagService} actually restricts on:
     * only one accessory per family may sit in the bag at once, regardless of {@link #type}
     * (tier), per the player's own spec ("não posso colocar feather talisman e ring ao mesmo
     * tempo") - carrying more than one Ring, or a Ring from one family alongside a Talisman
     * from a different one, is fine; two accessories from the SAME family (any mix of
     * tiers) is not, since a higher tier is meant to replace the one below it.
     */
    public static String family(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(FAMILY_KEY, PersistentDataType.STRING);
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

    public static double poisonReductionPercent(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return 0.0;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? 0.0 : meta.getPersistentDataContainer().getOrDefault(POISON_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
    }
}
