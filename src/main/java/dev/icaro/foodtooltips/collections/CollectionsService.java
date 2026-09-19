package dev.icaro.foodtooltips.collections;

import dev.icaro.foodtooltips.enchant.IcarusEnchant;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/**
 * Applies and tracks Collections milestones - the Blacksmith-reforge-style split ({@code
 * reforge.ReforgeService}'s own class doc) of keeping gameplay logic here, separate from
 * {@link CollectionsMenuService} (screen/inventory plumbing only). {@link #record} is the
 * single entry point a harvest/mine/kill hook calls (see {@code
 * skills.GeneralSkillListener#broken} for the Farming ones) - it updates {@link
 * CollectionsProgressService}'s own running total, credits {@code
 * GlobalLevelService#milestoneXp} Global Level XP for every freshly-crossed milestone (same
 * checkpoint mechanism {@code BestiaryProgressService}/mining milestones already use, so a
 * single harvest that crosses more than one milestone at once is still credited exactly
 * once each - see {@link GlobalLevelService#creditMilestones}), and immediately applies any
 * {@link RewardKind#RECIPE_UNLOCK} milestone crossed (real vanilla recipe discovery, so it
 * shows up in the crafting table's own recipe book too - not just this plugin's Recipe Book
 * menu). {@link RewardKind#FARMING_XP} is deliberately NOT granted here - the caller already
 * has its own {@code gain(Player, SkillType, double)} wrapper (level-up message, progress
 * bar, the works), so {@link #record}'s returned {@link Update} hands each freshly-crossed
 * milestone back for the caller to apply that one itself, exactly the same "service tracks &
 * describes, listener grants" split {@code CombatListener}'s own Bestiary-kill handling
 * already uses.
 */
public final class CollectionsService {
    private final CollectionsProgressService progress;
    private final GlobalLevelService global;

    public CollectionsService(CollectionsProgressService progress, GlobalLevelService global) {
        this.progress = progress;
        this.global = global;
    }

    public CollectionsProgressService progress() {
        return this.progress;
    }

    /** {@code unlocked} - every milestone freshly crossed by one {@link #record} call, Farming XP ones included (the caller grants that part itself - see {@link #record}'s own doc) - and {@code globalXp}, the total {@code GlobalLevelService#milestoneXp}-multiplied reward already credited for the whole batch, so a single harvest crossing several milestones at once still gets one accurate combined number to announce instead of the caller re-deriving it. */
    public record Update(List<CollectionsMilestone> unlocked, long globalXp) {
        public boolean any() {
            return !this.unlocked.isEmpty();
        }
    }

    /**
     * Records {@code amount} more of {@code material} collected, applies every
     * non-Farming-XP reward any freshly-crossed milestone grants, and returns those
     * milestones (Farming XP ones included) so the caller can grant that part and
     * announce the unlock. A no-op {@link Update} for a material with no {@link
     * CollectionsEntry}, or if nothing new was crossed.
     */
    public Update record(Player player, Material material, int amount) {
        Optional<CollectionsEntry> found = CollectionsCatalog.find(material);
        if (found.isEmpty() || amount <= 0) {
            return new Update(List.of(), 0L);
        }
        CollectionsEntry entry = found.get();
        int before = this.progress.achieved(player, entry);
        this.progress.addCollected(player, entry, amount);
        int after = this.progress.achieved(player, entry);
        if (after <= before) {
            return new Update(List.of(), 0L);
        }
        List<CollectionsMilestone> unlocked = new ArrayList<>();
        for (int i = before; i < after; i++) {
            CollectionsMilestone milestone = entry.milestones().get(i);
            unlocked.add(milestone);
            if (milestone.kind() == RewardKind.RECIPE_UNLOCK) {
                for (NamespacedKey recipe : milestone.recipes()) {
                    player.discoverRecipe(recipe);
                }
            }
        }
        long globalXp = this.global.creditMilestones(player, "collections", this.progress.totalMilestones(player), GlobalXpSource.COLLECTIONS_MILESTONE);
        return new Update(unlocked, globalXp);
    }

    /** Whether {@code player} has crossed the milestone that unlocks {@code recipe} - what {@code crafting.RecipeBookMenuService}'s own requirement lore and the crafting-table gate ({@code CollectionsRecipeGateListener}) both check. True for any recipe this catalog never gates at all (nothing claims it), so an ungated recipe is never accidentally blocked. */
    public boolean hasUnlockedRecipe(Player player, NamespacedKey recipe) {
        boolean gated = false;
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            for (CollectionsMilestone milestone : entry.milestones()) {
                if (!milestone.recipes().contains(recipe)) {
                    continue;
                }
                gated = true;
                if (this.progress.hasCrossedMilestoneUnlocking(player, entry, recipe)) {
                    return true;
                }
            }
        }
        return !gated;
    }

    /** The milestone that gates {@code recipe} (its own {@link CollectionsMilestone#recipes} contains it), if any - what {@code crafting.RecipeBookMenuService}'s own requirement lore reads for the label text, separate from {@link #hasUnlockedRecipe}'s own per-player met/not-met check. */
    public Optional<CollectionsMilestone> findGatingMilestone(NamespacedKey recipe) {
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            for (CollectionsMilestone milestone : entry.milestones()) {
                if (milestone.recipes().contains(recipe)) {
                    return Optional.of(milestone);
                }
            }
        }
        return Optional.empty();
    }

    /** Total percentage discount {@code player} gets off {@code enchant}'s own Enchanting Table XP cost from every Collections milestone crossed for it (see {@link RewardKind#ENCHANT_DISCOUNT}) - 0 if none. Additive across multiple milestones, though today at most one ever targets a given enchant. */
    public double enchantDiscountPercent(Player player, IcarusEnchant enchant) {
        double percent = 0.0;
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            int achieved = this.progress.achieved(player, entry);
            for (int i = 0; i < achieved; i++) {
                CollectionsMilestone milestone = entry.milestones().get(i);
                if (milestone.kind() == RewardKind.ENCHANT_DISCOUNT && milestone.discountEnchant() == enchant) {
                    percent += milestone.discountPercent();
                }
            }
        }
        return percent;
    }

    /** Syncs every gated recipe's real vanilla discovery state to {@code player}'s current progress - called on join so a recipe unlocked in a past session (or one added to the catalog after they'd already have qualified) still shows up without needing a fresh milestone crossing. Never touches a recipe this catalog doesn't gate. */
    public void syncDiscoveredRecipes(Player player) {
        for (CollectionsEntry entry : CollectionsCatalog.entries()) {
            int achieved = this.progress.achieved(player, entry);
            List<CollectionsMilestone> milestones = entry.milestones();
            for (int i = 0; i < milestones.size(); i++) {
                CollectionsMilestone milestone = milestones.get(i);
                if (milestone.kind() != RewardKind.RECIPE_UNLOCK) {
                    continue;
                }
                boolean unlocked = i < achieved;
                for (NamespacedKey recipe : milestone.recipes()) {
                    if (unlocked) {
                        player.discoverRecipe(recipe);
                    } else {
                        player.undiscoverRecipe(recipe);
                    }
                }
            }
        }
    }
}
