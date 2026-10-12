package dev.icaro.foodtooltips.crafting;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * Recipes that take more than one item from a grid slot (160 logs = 5 slots of 32 for an
 * Enchanted Log, 2 or 4 Enchanted Cobblestone around a Personal Compactor 4000/5000) - a vanilla
 * recipe always takes exactly one per slot, so this keeps the per-slot amounts in one place for
 * every crafting surface: the real crafting table ({@link #prepare}/{@link #craft} here), the
 * Skills menu's own table ({@code CraftingMenuService#takeOutput}) and the recipe book, which
 * shows the amount on each ingredient.
 */
public final class SlotAmounts implements Listener {
    /** Recipe -> amount needed in a filled slot, by its row-major grid index (0-8). */
    private static final Map<NamespacedKey, IntUnaryOperator> AMOUNTS = new ConcurrentHashMap<>();

    /** Every filled slot of {@code key}'s grid needs {@code perSlot.applyAsInt(index)} items. */
    public static void register(NamespacedKey key, IntUnaryOperator perSlot) {
        AMOUNTS.put(key, perSlot);
    }

    /** How many items {@code key} takes from grid slot {@code index} - 1 for any ordinary recipe. */
    public static int amount(NamespacedKey key, int index) {
        IntUnaryOperator perSlot = key == null ? null : AMOUNTS.get(key);
        return perSlot == null ? 1 : Math.max(1, perSlot.applyAsInt(index));
    }

    public static NamespacedKey keyOf(Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey() : null;
    }

    public static boolean registered(NamespacedKey key) {
        return key != null && AMOUNTS.containsKey(key);
    }

    /** Whether every filled slot of the 3x3 {@code matrix} holds at least what {@code key} takes from it. */
    public static boolean satisfied(NamespacedKey key, ItemStack[] matrix) {
        for (int i = 0; i < matrix.length; i++) {
            ItemStack stack = matrix[i];
            if (stack != null && !stack.isEmpty() && stack.getAmount() < amount(key, i)) {
                return false;
            }
        }
        return true;
    }

    /** {@code matrix} after one craft of {@code key}: each filled slot loses its own amount. */
    public static ItemStack[] consume(NamespacedKey key, ItemStack[] matrix) {
        ItemStack[] out = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack stack = matrix[i];
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int left = stack.getAmount() - amount(key, i);
            out[i] = left > 0 ? stack.asQuantity(left) : null;
        }
        return out;
    }

    // ---- Real crafting table ----

    /** Hides the result until every slot has its full amount. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        NamespacedKey key = keyOf(e.getRecipe());
        if (registered(key) && !satisfied(key, e.getInventory().getMatrix())) {
            e.getInventory().setResult(null);
        }
    }

    /** Does the craft by hand, taking each slot's own amount instead of vanilla's one. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void craft(CraftItemEvent e) {
        NamespacedKey key = keyOf(e.getRecipe());
        if (!registered(key) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        e.setCancelled(true);
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (!satisfied(key, matrix) || inv.getResult() == null) {
            return;
        }
        ItemStack result = inv.getResult().clone();
        inv.setMatrix(consume(key, matrix));
        ItemStack cursor = e.getView().getCursor();
        if (!e.isShiftClick() && (cursor == null || cursor.isEmpty())) {
            e.getView().setCursor(result);
        } else if (!e.isShiftClick() && cursor.isSimilar(result) && cursor.getAmount() + result.getAmount() <= cursor.getMaxStackSize()) {
            e.getView().setCursor(cursor.add(result.getAmount()));
        } else {
            for (ItemStack overflow : p.getInventory().addItem(result).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
    }
}
