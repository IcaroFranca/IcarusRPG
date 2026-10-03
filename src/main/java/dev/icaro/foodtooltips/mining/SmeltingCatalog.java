package dev.icaro.foodtooltips.mining;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
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

    /**
     * {@link RecipeChoice.ItemTypeChoice} is the one shape {@link RecipeChoice.MaterialChoice}/
     * {@link RecipeChoice.ExactChoice} alone didn't cover - real vanilla furnace recipes this
     * Paper version registers (Ancient Debris -&gt; Netherite Scrap among them, per the
     * player's own explicit "Mining Fortune também tem que valer para o ancient debris quando
     * a picareta tiver smelting touch") report their input this way, not as a
     * {@code MaterialChoice} - without this case {@link #smeltedForm} silently returned null
     * for every one of them, so Smelting Touch never converted their drop at all despite the
     * recipe genuinely existing. {@link RegistryAccess#registryAccess()} is safe here (unlike
     * a static field initializer elsewhere in this project that crashed under a server-less
     * test environment) since {@link #table} only ever runs lazily, well after a real server
     * has finished booting.
     */
    private static List<Material> inputs(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice mc) {
            return mc.getChoices();
        }
        if (choice instanceof RecipeChoice.ExactChoice ec) {
            return ec.getChoices().stream().map(ItemStack::getType).toList();
        }
        if (choice instanceof RecipeChoice.ItemTypeChoice itc) {
            Registry<ItemType> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM);
            return itc.itemTypes().resolve(registry).stream().map(ItemType::asMaterial).toList();
        }
        return List.of();
    }
}
