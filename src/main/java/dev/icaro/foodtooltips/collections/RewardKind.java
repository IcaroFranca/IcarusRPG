package dev.icaro.foodtooltips.collections;

/** Which effect a {@link CollectionsMilestone} actually applies when it unlocks - see {@link CollectionsMilestone}'s own fields for which of them each kind reads. */
public enum RewardKind {
    /** Grants {@code CollectionsMilestone#xpAmount} Farming skill XP. */
    FARMING_XP,
    /** Unlocks {@code CollectionsMilestone#recipe} (see {@link CollectionsService#hasUnlockedRecipe}). */
    RECIPE_UNLOCK,
    /** Discounts {@code CollectionsMilestone#discountEnchant}'s own Enchanting Table XP cost by {@code CollectionsMilestone#discountPercent} - see {@code EnchantMenuService#discountedCost}. */
    ENCHANT_DISCOUNT
}
