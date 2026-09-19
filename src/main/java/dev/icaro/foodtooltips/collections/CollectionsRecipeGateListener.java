package dev.icaro.foodtooltips.collections;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingRecipe;
import org.bukkit.inventory.Recipe;

/**
 * Blocks crafting any {@link CraftingRecipe} {@link CollectionsService} gates behind a
 * milestone the viewer hasn't crossed yet - clears the result slot the instant the recipe
 * would otherwise complete, the same way an ordinary mismatched-ingredients grid shows no
 * result. This is the real crafting gate; {@code crafting.RecipeBookMenuService}'s own
 * requirement lore is only ever a preview, matching the player's own explicit spec that a
 * gated recipe is still visible in the book with a requirement warning, not actually
 * craftable until met. Real vanilla recipe discovery ({@link
 * CollectionsService#syncDiscoveredRecipes}) already keeps the crafting table's own recipe
 * book from suggesting it in the first place - this listener is the actual enforcement
 * layer for a player who arranges the ingredients by hand regardless.
 *
 * <p>Only ever looks at {@link CraftingRecipe}s (a crafting-table grid) - a {@link
 * org.bukkit.inventory.PotionMix} (the Resistance Potion) has no discovery or gating
 * concept in vanilla at all, so it's deliberately never checked here - see {@code
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
        if (!(recipe instanceof CraftingRecipe crafting)) {
            return;
        }
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player && !this.collections.hasUnlockedRecipe(player, crafting.getKey())) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
