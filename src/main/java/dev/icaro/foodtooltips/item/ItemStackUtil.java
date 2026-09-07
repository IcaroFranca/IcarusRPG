package dev.icaro.foodtooltips.item;

import org.bukkit.inventory.ItemStack;

/**
 * Merges any same-item stacks in an inventory's storage array into as few slots as
 * possible, respecting max stack size. Needed because several plugin systems
 * (currently {@link ItemTierService} and {@code FoodTooltipListener}) rewrite an
 * item's lore/name once per item, one tick after it lands in the inventory - for
 * that one tick its lore doesn't match an already-tagged stack of the same item, so
 * the game can't merge them and they end up as two separate slots. Worse, if two
 * such systems tag the same item at different times relative to each other (one
 * stack gets both its lore rewrites in one order, another stack in the other order),
 * the two stacks freeze with different lore *order* forever and can never merge on
 * their own again - re-coalescing every tick, right after tagging, heals both cases
 * instead of leaving the split stuck.
 */
public final class ItemStackUtil {
    private ItemStackUtil() {
    }

    /** Returns true if anything moved (caller is responsible for writing the array back to the inventory). */
    public static boolean coalesce(ItemStack[] storage) {
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack into = storage[i];
            if (into == null || into.isEmpty() || into.getAmount() >= into.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < storage.length; j++) {
                ItemStack from = storage[j];
                if (from == null || from.isEmpty() || !into.isSimilar(from)) {
                    continue;
                }
                int move = Math.min(into.getMaxStackSize() - into.getAmount(), from.getAmount());
                if (move <= 0) {
                    continue;
                }
                into.setAmount(into.getAmount() + move);
                from.setAmount(from.getAmount() - move);
                if (from.getAmount() <= 0) {
                    storage[j] = null;
                }
                changed = true;
                if (into.getAmount() >= into.getMaxStackSize()) {
                    break;
                }
            }
        }
        return changed;
    }
}
