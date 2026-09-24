package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import java.util.List;
import org.bukkit.NamespacedKey;

/**
 * One tier of a {@link CollectionsEntry}'s own milestone ladder - {@code threshold} is the
 * absolute cumulative amount collected (not an increment over the previous tier, e.g. 250
 * means "250 collected in total", matching the player's own spec verbatim), and {@code kind}
 * says which of the other fields actually matter (see {@link RewardKind}'s own doc). Every
 * milestone also grants {@code GlobalLevelService#milestoneXp} Global Level XP regardless of
 * kind - that part is handled once in {@link CollectionsService#record}, not per-milestone
 * here.
 *
 * <p>Only one of {@code xpAmount}/{@code recipes}/{@code discountEnchant}+{@code
 * discountPercent} is ever populated, matching {@code kind} - a plain record with nullable/
 * zero unused fields rather than a sealed hierarchy per kind, since there are only three
 * kinds today and every consumer (the menu, {@link CollectionsService}) already has to
 * switch on {@code kind} to know which behavior applies anyway. {@code recipes} is a list,
 * not a single key, because one milestone can unlock several recipes at once (a full 4-piece
 * armor set counts as "one recipe" in the player's own spec) - {@link
 * #recipeUnlock(int, String, String, NamespacedKey...)} takes it as varargs for a single-key
 * unlock to still read naturally at the call site. An empty list is valid too (Sprout Armor's
 * own milestone: unlocked in spirit, no recipe registered yet - see {@code
 * FarmingCollectionsItemsService}'s own doc).
 */
public record CollectionsMilestone(
        int threshold,
        RewardKind kind,
        int xpAmount,
        List<NamespacedKey> recipes,
        IcarusEnchant discountEnchant,
        double discountPercent,
        String rewardPt,
        String rewardEn) {

    public static CollectionsMilestone farmingXp(int threshold, int xpAmount, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.FARMING_XP, xpAmount, List.of(), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone foragingXp(int threshold, int xpAmount, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.FORAGING_XP, xpAmount, List.of(), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone recipeUnlock(int threshold, String rewardPt, String rewardEn, NamespacedKey... recipes) {
        return new CollectionsMilestone(threshold, RewardKind.RECIPE_UNLOCK, 0, List.of(recipes), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone enchantDiscount(int threshold, IcarusEnchant enchant, double percent, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), enchant, percent, rewardPt, rewardEn);
    }

    public String reward(boolean pt) {
        return pt ? this.rewardPt : this.rewardEn;
    }
}
