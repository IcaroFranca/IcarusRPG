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
 * Overrides vanilla sword attack damage with the plugin's own progression - vanilla's
 * 4-8 range is meaningless next to hundreds of HP on the health pools this RPG already
 * uses, so every sword material gets a flat total instead:
 *
 * <pre>
 * Wood / Gold   -&gt; 20
 * Stone / Copper -&gt; 25
 * Iron          -&gt; 30
 * Diamond       -&gt; 35
 * Netherite     -&gt; 40
 * </pre>
 *
 * <p>Same idempotent-PDC-marker pattern as {@link ItemTierService#tooltip}: each sword
 * is only rewritten once, checked via {@link #appliedKey} on its own {@link ItemMeta}.
 *
 * <p>Setting an item's {@code attribute_modifiers} component explicitly (which is what
 * {@link ItemMeta#addAttributeModifier} does) replaces the material's implicit default
 * set entirely rather than adding on top of it - so this also has to re-declare the
 * vanilla attack-speed penalty ({@link #ATTACK_SPEED_DELTA}) that every sword material
 * normally carries, or the sword would silently swing at the player's bare-fist speed
 * once its damage modifier is set. {@code CombatSkillService#applyAttackSpeed} layers
 * its own player-level Attack Speed skill bonus independently on top of this - different
 * NamespacedKey, same Attribute, both just add into the same total like vanilla intends.
 */
public final class SwordDamageService {
    /** Vanilla's own attack-speed penalty for any sword (base 4.0 -&gt; 1.6), identical across materials. */
    private static final double ATTACK_SPEED_DELTA = -2.4;
    /** Players' base {@link Attribute#ATTACK_DAMAGE} with an empty hand - the modifier amount is the desired total minus this. */
    private static final double BASE_ATTACK_DAMAGE = 1.0;

    private final NamespacedKey appliedKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey speedKey;

    public SwordDamageService(Plugin plugin) {
        this.appliedKey = new NamespacedKey(plugin, "sword_damage_applied");
        this.damageKey = new NamespacedKey(plugin, "sword_attack_damage");
        this.speedKey = new NamespacedKey(plugin, "sword_attack_speed");
    }

    /** Total attack damage (base included) a sword of this Material should hit for, or null if not a sword. */
    public static Double totalDamage(Material m) {
        String name = m.name();
        if (!name.endsWith("_SWORD")) {
            return null;
        }
        String family = name.substring(0, name.length() - "_SWORD".length());
        return switch (family) {
            case "WOODEN", "WOOD", "GOLDEN", "GOLD" -> 20.0;
            case "STONE", "COPPER" -> 25.0;
            case "IRON" -> 30.0;
            case "DIAMOND" -> 35.0;
            case "NETHERITE" -> 40.0;
            default -> null;
        };
    }

    /** Applies the custom attack-damage/speed pair to every sword in the player's inventory (storage and offhand). */
    public void applySwordDamage(Player p) {
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

    /** Returns the mutated item if it needed rewriting, or null if it's not a sword or was already done. */
    private ItemStack rewrite(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        Double total = totalDamage(item.getType());
        if (total == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE)) {
            return null;
        }
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(this.damageKey, total - BASE_ATTACK_DAMAGE,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(this.speedKey, ATTACK_SPEED_DELTA,
                        AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}
