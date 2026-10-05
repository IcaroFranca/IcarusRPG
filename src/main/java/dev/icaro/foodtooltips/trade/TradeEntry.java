package dev.icaro.foodtooltips.trade;

import org.bukkit.Material;

/**
 * One row of {@link TradeMenuService}'s own screen - trade {@code costAmount} of {@code
 * costMaterial} for {@code rewardAmount} of {@code rewardMaterial}, gated behind crossing
 * {@code milestoneNumber} (1-indexed, same convention {@code
 * biome.BiomeWandService}'s own {@code BiomeUnlock#milestoneNumber} uses) of the Wheat Seeds
 * Collection - checked live via {@code collections.CollectionsProgressService#achieved}, the
 * same "no recipe behind it, read the Collection directly" shape {@code
 * item.TillingHoeService}'s own area growth already uses, since a trade isn't a real {@code
 * org.bukkit.inventory.CraftingRecipe} {@code collections.CollectionsRecipeGateListener}
 * could gate. {@code costLabel}/{@code rewardLabel} are plain display strings rather than
 * derived from the {@link Material} name, so the lore can say exactly what the player's own
 * spec called each trade (e.g. "Long Grass" for {@link Material#SHORT_GRASS} - the old
 * Minecraft Beta-era name for that exact plant, which the player's own spec uses verbatim).
 */
public record TradeEntry(int milestoneNumber, String name, Material costMaterial, int costAmount, String costLabel,
        Material rewardMaterial, int rewardAmount, String rewardLabel) {
}
