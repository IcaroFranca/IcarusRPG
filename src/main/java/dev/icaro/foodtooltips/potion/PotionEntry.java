package dev.icaro.foodtooltips.potion;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.inventory.ItemStack;

/**
 * One entry in the Potion Guide/Milestones screens (see {@code PotionGuideMenuService}) -
 * either a real vanilla brewing chain or one of this plugin's own custom potions ({@code
 * item.FarmingCollectionsItemsService}'s Resistance/Adrenaline/Archery/Mana). {@code icon}
 * is the actual finished potion itself (a real {@link org.bukkit.inventory.meta.PotionMeta}
 * base type/color, no name or lore set yet - {@code PotionGuideMenuService} clones it and
 * fills those in per language) - not the brewing ingredient, so the guide shows the player
 * what they're actually trying to make, colored bottle and all.
 *
 * <p>{@code matches} is how {@code PotionGuideMenuService} recognizes a brewed potion (taken
 * from a Brewing Stand's own output slot) as this entry for milestone crediting - a real
 * vanilla {@link org.bukkit.potion.PotionType} check for a vanilla entry, or this plugin's
 * own PDC marker/custom effect check for a custom one. {@code null} for an entry nothing can
 * actually brew and take credit for (Luck has no real Brewing Stand recipe at all).
 */
public record PotionEntry(String id, ItemStack icon, String namePt, String nameEn,
                           List<String> stepsPt, List<String> stepsEn, Predicate<ItemStack> matches) {
}
