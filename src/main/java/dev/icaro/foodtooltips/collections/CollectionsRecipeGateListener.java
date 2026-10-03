package dev.icaro.foodtooltips.collections;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ComplexRecipe;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Recipe;

/**
 * Blocks crafting any {@link CraftingRecipe} (a normal shaped/shapeless crafting-table
 * recipe) or {@link ComplexRecipe} (a vanilla "special" recipe matched by a predicate
 * rather than a fixed grid - Suspicious Stew, Firework Star, Map Cloning... - see
 * Torchflower's own Suspicious Stew gate) {@link CollectionsService} gates behind a
 * milestone the viewer hasn't crossed yet - clears the result slot the instant the recipe
 * would otherwise complete, the same way an ordinary mismatched-ingredients grid shows no
 * result. This is the real crafting gate; {@code crafting.RecipeBookMenuService}'s own book
 * screen uses the same {@link CollectionsService#hasUnlockedRecipe} check to decide whether
 * to list a gated recipe at all (never showing a locked one, per the player's own "SOMENTE
 * RECEITAS DESBLOQUEADAS" spec) - though it only ever renders {@link CraftingRecipe}s (a
 * fixed 3x3 grid to display), so a gated {@link ComplexRecipe} simply never appears there at
 * all, locked or not, same as it already wouldn't for any other complex recipe. This
 * listener is really the belt to that belt-and-suspenders pairing - the actual enforcement
 * layer for a player who arranges the ingredients by hand regardless, or via any other UI
 * (a shulker box's own 3x3 crafting grid, say) this plugin doesn't otherwise gate. Real
 * vanilla recipe discovery ({@link CollectionsService#syncDiscoveredRecipes}) already keeps
 * the crafting table's own recipe book from suggesting it in the first place - that one
 * works off the recipe's {@link NamespacedKey} alone, so it already covered both recipe
 * kinds even before this class did.
 *
 * <p>A {@link org.bukkit.inventory.PotionMix} (the Resistance Potion) has no discovery or
 * gating concept in vanilla at all, so it's deliberately never checked here - see {@code
 * item.FarmingCollectionsItemsService}'s own doc on why that one recipe stays ungated.
 */
public final class CollectionsRecipeGateListener implements Listener {
    private final CollectionsService collections;

    public CollectionsRecipeGateListener(CollectionsService collections) {
        this.collections = collections;
    }

    @EventHandler
    public void prepare(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        NamespacedKey key;
        if (recipe instanceof CraftingRecipe crafting) {
            key = crafting.getKey();
        } else if (recipe instanceof ComplexRecipe complex) {
            key = complex.getKey();
        } else {
            return;
        }
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player && !this.collections.hasUnlockedRecipe(player, key)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
