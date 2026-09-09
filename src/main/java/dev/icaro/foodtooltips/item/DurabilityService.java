package dev.icaro.foodtooltips.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Multiplies every damageable item's Max Durability by {@code items.durability-multiplier}
 * (default 5) - every tool, weapon, armor piece, bow/crossbow, elytra, shield, fishing
 * rod, trident etc., since {@link org.bukkit.Material#getMaxDurability()} already
 * covers exactly that set with no per-Material list to maintain (returns 0 for
 * anything that isn't damageable, which {@link #multiply} skips).
 *
 * <p>Same one-shot pattern as {@code ItemTierService#applyItemTiers}: idempotent via a
 * PDC marker on the item's own {@link ItemMeta}, applied on join and re-applied every
 * HUD tick in {@code FoodTooltipsPlugin} to reach anything crafted/looted/bought/given
 * afterwards, without needing a dedicated pickup/inventory-click hook. An item's
 * current damage is scaled up by the same multiplier as its new max, so an
 * already-half-worn tool stays half-worn (proportionally) instead of the multiplier
 * handing it free uses out of nowhere.
 */
public final class DurabilityService {
    private final NamespacedKey appliedKey;
    private final int multiplier;

    public DurabilityService(Plugin plugin) {
        this.appliedKey = new NamespacedKey(plugin, "durability_multiplied");
        this.multiplier = Math.max(1, plugin.getConfig().getInt("items.durability-multiplier", 5));
    }

    /** Applies the multiplier to every item in the player's inventory (storage, armor and offhand). */
    public void applyDurability(Player p) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            if (this.multiply(storage[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        changed = false;
        for (int i = 0; i < armor.length; i++) {
            if (this.multiply(armor[i])) {
                changed = true;
            }
        }
        if (changed) {
            inv.setArmorContents(armor);
        }
        ItemStack offhand = inv.getItemInOffHand();
        if (this.multiply(offhand)) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Mutates {@code item} in place and returns true if it needed multiplying, or false if it's not damageable or was already done. */
    private boolean multiply(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        int vanillaMax = item.getType().getMaxDurability();
        if (vanillaMax <= 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || meta.getPersistentDataContainer().has(this.appliedKey, PersistentDataType.BYTE)) {
            return false;
        }
        int currentDamage = damageable.hasDamage() ? damageable.getDamage() : 0;
        damageable.setMaxDamage(vanillaMax * this.multiplier);
        damageable.setDamage(currentDamage * this.multiplier);
        meta.getPersistentDataContainer().set(this.appliedKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return true;
    }
}
