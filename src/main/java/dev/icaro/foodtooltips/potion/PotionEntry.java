package dev.icaro.foodtooltips.potion;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * One entry in the Potion Guide/Milestones screens (see {@code PotionGuideMenuService}) -
 * either a real vanilla brewing chain or one of this plugin's own custom potions ({@code
 * item.FarmingCollectionsItemsService}'s Resistance/Adrenaline/Archery/Mana). {@code icon}
 * is the potion's own distinguishing brewing ingredient (Sugar for Swiftness, Blaze Powder
 * for Strength...), not a plain Potion bottle - every entry would otherwise render as the
 * exact same generic bottle icon in the catalog grid, same "icon = the recognizable thing"
 * convention {@code bestiary.BestiaryEntry}/{@code mining.MiningEntry} already use.
 *
 * <p>{@code matches} is how {@code PotionGuideMenuService} recognizes a brewed potion (taken
 * from a Brewing Stand's own output slot) as this entry for milestone crediting - a real
 * vanilla {@link org.bukkit.potion.PotionType} check for a vanilla entry, or this plugin's
 * own PDC marker for a custom one. {@code null} for an entry nothing can actually brew and
 * take credit for (Luck, and the newer mob-head potions this guide only briefly notes -
 * see {@code PotionCatalog}'s own doc on why their exact recipe isn't asserted here).
 */
public record PotionEntry(String id, Material icon, String namePt, String nameEn,
                           List<String> stepsPt, List<String> stepsEn, Predicate<ItemStack> matches) {
}
