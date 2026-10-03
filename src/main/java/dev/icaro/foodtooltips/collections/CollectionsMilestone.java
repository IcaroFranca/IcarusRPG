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
 * switch on {@code kind} to know which behavior applies anyway. {@code vanillaDiscountEnchant}
 * is a second, separate "which enchant" field alongside {@code discountEnchant} - one for this
 * plugin's own {@link IcarusEnchant}s, one for a real vanilla {@code
 * org.bukkit.enchantments.Enchantment} (Power, Sharpness...), identified by its own {@link
 * NamespacedKey} rather than the live {@code Enchantment} constant itself - touching a static
 * field like {@code Enchantment.POWER} from this class's own eager {@code ENTRIES} static
 * initializer (in {@link CollectionsCatalog}) throws {@code IllegalStateException: No
 * RegistryAccess implementation found} outside a running server (every unit test included),
 * since resolving it requires a live Bukkit registry; a plain {@link NamespacedKey} needs no
 * such resolution and is compared against the real enchant's own key only at actual use time
 * (see {@link CollectionsService#vanillaEnchantDiscountPercent}), once a server genuinely is
 * running. {@code recipes} is a list,
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
        String rewardEn,
        NamespacedKey vanillaDiscountEnchant) {

    public CollectionsMilestone(int threshold, RewardKind kind, int xpAmount, List<NamespacedKey> recipes,
            IcarusEnchant discountEnchant, double discountPercent, String rewardPt, String rewardEn) {
        this(threshold, kind, xpAmount, recipes, discountEnchant, discountPercent, rewardPt, rewardEn, null);
    }

    public static CollectionsMilestone farmingXp(int threshold, int xpAmount, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.FARMING_XP, xpAmount, List.of(), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone foragingXp(int threshold, int xpAmount, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.FORAGING_XP, xpAmount, List.of(), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone combatXp(int threshold, int xpAmount, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.COMBAT_XP, xpAmount, List.of(), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone recipeUnlock(int threshold, String rewardPt, String rewardEn, NamespacedKey... recipes) {
        return new CollectionsMilestone(threshold, RewardKind.RECIPE_UNLOCK, 0, List.of(recipes), null, 0.0, rewardPt, rewardEn);
    }

    public static CollectionsMilestone enchantDiscount(int threshold, IcarusEnchant enchant, double percent, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.ENCHANT_DISCOUNT, 0, List.of(), enchant, percent, rewardPt, rewardEn);
    }

    public static CollectionsMilestone vanillaEnchantDiscount(int threshold, NamespacedKey enchantKey, double percent, String rewardPt, String rewardEn) {
        return new CollectionsMilestone(threshold, RewardKind.VANILLA_ENCHANT_DISCOUNT, 0, List.of(), null, percent, rewardPt, rewardEn, enchantKey);
    }

    public String reward(boolean pt) {
        return this.rewardEn;
    }
}
