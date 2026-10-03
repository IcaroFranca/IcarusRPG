package dev.icaro.foodtooltips.collections;

/** Which effect a {@link CollectionsMilestone} actually applies when it unlocks - see {@link CollectionsMilestone}'s own fields for which of them each kind reads. */
public enum RewardKind {
    /** Grants {@code CollectionsMilestone#xpAmount} Farming skill XP. */
    FARMING_XP,
    /** Grants {@code CollectionsMilestone#xpAmount} Foraging skill XP. */
    FORAGING_XP,
    /** Unlocks {@code CollectionsMilestone#recipe} (see {@link CollectionsService#hasUnlockedRecipe}). */
    RECIPE_UNLOCK,
    /** Discounts {@code CollectionsMilestone#discountEnchant}'s own Enchanting Table XP cost by {@code CollectionsMilestone#discountPercent} - see {@code EnchantMenuService#discountedCost}. */
    ENCHANT_DISCOUNT,
    /** Grants {@code CollectionsMilestone#xpAmount} Combat XP (see {@code skills.CombatSkillService#addXp}) - Combat has no {@code skills.SkillType} of its own, unlike Farming/Foraging, so this is granted directly rather than through {@code skills.GeneralSkillService#gain}. */
    COMBAT_XP,
    /** Same as {@link #ENCHANT_DISCOUNT}, but for a real vanilla {@link org.bukkit.enchantments.Enchantment} (e.g. Power) instead of one of this plugin's own {@code IcarusEnchant}s - see {@code CollectionsMilestone#vanillaDiscountEnchant}/{@code CollectionsService#vanillaEnchantDiscountPercent}. */
    VANILLA_ENCHANT_DISCOUNT
}
