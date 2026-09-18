package dev.icaro.foodtooltips.mining;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

/**
 * Lazily built {@code Material -> Material} lookup of every furnace-smelted result
 * (Furnace/Blast Furnace/Smoker recipes - not Campfire, which shares {@link
 * CookingRecipe} but isn't a "furnace" in the sense Smelting Touch means) reachable
 * from a single input material, read straight from the server's own registered
 * recipes rather than hardcoded - stays correct across any datapack/recipe changes
 * without needing its own maintained table. Built once on first use and cached
 * forever (recipes don't change at runtime); built lazily rather than eagerly at
 * class-load time since that could otherwise run before the server has finished
 * registering its own recipes.
 */
public final class SmeltingCatalog {
    private static Map<Material, Material> cache;

    private SmeltingCatalog() {
    }

    /** {@code m}'s furnace-smelted result, or null if nothing smelts into it from exactly this material. */
    public static Material smeltedForm(Material m) {
        return table().get(m);
    }

    private static Map<Material, Material> table() {
        if (cache == null) {
            Map<Material, Material> built = new HashMap<>();
            Iterator<Recipe> it = Bukkit.recipeIterator();
            while (it.hasNext()) {
                Recipe recipe = it.next();
                if (!(recipe instanceof CookingRecipe<?> cooking) || recipe instanceof CampfireRecipe) {
                    continue;
                }
                Material result = cooking.getResult().getType();
                for (Material input : inputs(cooking.getInputChoice())) {
                    built.putIfAbsent(input, result);
                }
            }
            cache = built;
        }
        return cache;
    }

    private static List<Material> inputs(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            return mc.getChoices();
        }
        if (choice instanceof RecipeChoice.ExactChoice ec) {
            return ec.getChoices().stream().map(ItemStack::getType).toList();
        }
        return List.of();
    }
}
