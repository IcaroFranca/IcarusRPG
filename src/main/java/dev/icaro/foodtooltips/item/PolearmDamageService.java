package dev.icaro.foodtooltips.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Multiplies (not replaces, unlike {@code SwordDamageService}/{@code ToolDamageService}'s
 * own flat-total approach) attack damage by {@value #DAMAGE_MULTIPLIER} for every Spear
 * tier (Wood/Stone/Copper/Iron/Gold/Diamond/Netherite - added in the Mounts of Mayhem
 * update), the Trident, and the Mace. A {@link AttributeModifier.Operation#MULTIPLY_SCALAR_1}
 * modifier scales whatever vanilla's own base already is (including any dynamic bonus,
 * like the Spear's own charged-attack multiplier or the Mace's fall-distance bonus,
 * which aren't part of the flat {@code Attribute#ATTACK_DAMAGE} value this reads) rather
 * than overwriting it outright - the vanilla Attack Damage tooltip line already shows
 * the live total including this modifier, so unlike the flat-total services there's no
 * need to hide it and write a custom lore line instead.
 */
public final class PolearmDamageService {
    /** Every polearm's final damage is this many times vanilla's own base. */
    private static final double DAMAGE_MULTIPLIER = 5.0;

    private final NamespacedKey damageKey;
    private final NamespacedKey appliedKey;

    public PolearmDamageService(Plugin plugin) {
        this.damageKey = new NamespacedKey(plugin, "polearm_damage_multiplier");
        this.appliedKey = new NamespacedKey(plugin, "polearm_damage_applied");
    }

    /** Any Spear tier, the Trident, or the Mace. */
    public static boolean isPolearm(Material m) {
        return m.name().endsWith("_SPEAR") || m == Material.TRIDENT || m == Material.MACE;
    }

    /** Applies the multiplier to every polearm in the player's inventory (storage and offhand) - a one-shot attribute, same idempotent PDC-marker pattern as {@code SwordDamageService}. */
    public void applyPolearmDamage(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.rewrite(storage[i]);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack offhand = this.rewrite(inv.getItemInOffHand());
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    private ItemStack rewrite(ItemStack item) {
        if (item == null || item.isEmpty() || !isPolearm(item.getType())) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE)) {
            return null;
        }
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(this.damageKey, DAMAGE_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
