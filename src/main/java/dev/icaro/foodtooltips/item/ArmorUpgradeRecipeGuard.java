package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

/**
 * The armor upgrade recipes (Farmhand -> Haymaker, Haymaker -> Sprout) take their center piece as
 * "any piece of that armor type" ({@code FarmingCollectionsItemsService#newHaymakerRecipe}/{@code
 * #newSproutRecipe}) - this is what actually requires it to be the right armor, by the piece's own
 * PDC marker. They used to use an exact match, which a worn piece can never satisfy: the inventory
 * sweeps add Durability/Defense lore to it, and the durability itself changes with use - so these
 * upgrades refused every real piece. Same "match broadly, verify by marker" pattern as {@code
 * ZombieSwordService#guardZombiesHeartRecipes}.
 */
public final class ArmorUpgradeRecipeGuard implements Listener {
    private static final Set<NamespacedKey> HAYMAKER_RECIPES = Set.of(CollectionsCatalog.HAYMAKER_HELMET_RECIPE,
            CollectionsCatalog.HAYMAKER_CHESTPLATE_RECIPE, CollectionsCatalog.HAYMAKER_LEGGINGS_RECIPE, CollectionsCatalog.HAYMAKER_BOOTS_RECIPE);
    private static final Set<NamespacedKey> SPROUT_RECIPES = Set.of(CollectionsCatalog.SPROUT_HELMET_RECIPE,
            CollectionsCatalog.SPROUT_CHESTPLATE_RECIPE, CollectionsCatalog.SPROUT_LEGGINGS_RECIPE, CollectionsCatalog.SPROUT_BOOTS_RECIPE);

    @EventHandler
    public void prepare(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return;
        }
        Predicate<ItemStack> required;
        if (HAYMAKER_RECIPES.contains(crafting.getKey())) {
            required = FarmingCollectionsItemsService::isFarmhandPiece;
        } else if (SPROUT_RECIPES.contains(crafting.getKey())) {
            required = FarmingCollectionsItemsService::isHaymakerPiece;
        } else {
            return;
        }
        for (ItemStack slot : e.getInventory().getMatrix()) {
            if (slot != null && isArmor(slot) && !required.test(slot)) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    private static boolean isArmor(ItemStack item) {
        String name = item.getType().name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }
}
