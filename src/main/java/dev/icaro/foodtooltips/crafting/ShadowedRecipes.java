package dev.icaro.foodtooltips.crafting;

import dev.icaro.foodtooltips.item.ZombiesHeartService;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

/**
 * The Zombie Sword/Armor recipes accept any {@link Material#PLAYER_HEAD} (see {@code
 * item.ZombieSwordService#guardZombiesHeartRecipes} on why), so they also match the grid of
 * every other head-based recipe with the same shape - Weather Wand and Emerald Sword ("core,
 * core, stick"), Emerald Chestplate/Leggings/Boots. On Paper 26.2 the server picks the Zombie
 * recipe for those grids no matter which was registered first (the old "register Zombie last"
 * fix stopped working - confirmed with {@code Bukkit#getCraftingRecipe}), and the guard then
 * cleared the result, so none of them could be crafted at all.
 *
 * <p>{@link #effective} answers "which recipe does this grid really belong to": the matched
 * recipe itself, unless it's a Zombie one whose head slots aren't Zombie's Hearts - then the
 * plugin's own shaped recipe that genuinely matches the grid (or null if none does). Both the
 * Zombie guard (to show the right result) and {@code collections.CollectionsRecipeGateListener}
 * (to check the right milestone) go through it.
 */
public final class ShadowedRecipes {
    private ShadowedRecipes() {
    }

    /** The recipe {@code matrix} really crafts, given the server matched {@code matched} - see the class doc. */
    public static Recipe effective(Recipe matched, ItemStack[] matrix) {
        if (!(matched instanceof CraftingRecipe crafting) || !ZombiesHeartService.RECIPES.contains(crafting.getKey()) || allHeadsAreZombiesHearts(matrix)) {
            return matched;
        }
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe recipe = it.next();
            if (recipe instanceof ShapedRecipe shaped && !ZombiesHeartService.RECIPES.contains(shaped.getKey())
                    && isPluginRecipe(shaped.getKey()) && matches(shaped, matrix)) {
                return shaped;
            }
        }
        return null;
    }

    private static boolean isPluginRecipe(NamespacedKey key) {
        return !NamespacedKey.MINECRAFT.equals(key.getNamespace());
    }

    private static boolean allHeadsAreZombiesHearts(ItemStack[] matrix) {
        for (ItemStack slot : matrix) {
            if (slot != null && slot.getType() == Material.PLAYER_HEAD && !ZombiesHeartService.isZombiesHeart(slot)) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code recipe}'s shape (at any offset, plain or mirrored, as vanilla allows) matches {@code matrix} - a square 2x2 or 3x3 crafting grid. */
    static boolean matches(ShapedRecipe recipe, ItemStack[] matrix) {
        int size = (int) Math.round(Math.sqrt(matrix.length));
        String[] shape = recipe.getShape();
        int height = shape.length;
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row.length());
        }
        if (width > size || height > size) {
            return false;
        }
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        for (int dy = 0; dy + height <= size; dy++) {
            for (int dx = 0; dx + width <= size; dx++) {
                if (fits(shape, width, choices, matrix, size, dx, dy, false) || fits(shape, width, choices, matrix, size, dx, dy, true)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fits(String[] shape, int width, Map<Character, RecipeChoice> choices, ItemStack[] matrix, int size, int dx, int dy, boolean mirrored) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                ItemStack item = matrix[y * size + x];
                boolean empty = item == null || item.isEmpty();
                int sx = x - dx;
                int sy = y - dy;
                RecipeChoice choice = null;
                if (sy >= 0 && sy < shape.length && sx >= 0 && sx < width) {
                    String row = shape[sy];
                    int column = mirrored ? width - 1 - sx : sx;
                    char c = column < row.length() ? row.charAt(column) : ' ';
                    choice = c == ' ' ? null : choices.get(c);
                }
                if (choice == null ? !empty : empty || !choice.test(item)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@code recipe}'s key, or null for one without (should never happen for a crafting recipe). */
    public static NamespacedKey keyOf(Recipe recipe) {
        return recipe instanceof Keyed keyed ? keyed.getKey() : null;
    }
}
