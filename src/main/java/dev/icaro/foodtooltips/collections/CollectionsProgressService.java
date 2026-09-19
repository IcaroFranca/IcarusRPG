package dev.icaro.foodtooltips.collections;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tracks how much of each {@link CollectionsEntry} a player has collected, and how many of
 * its own milestones that amount has crossed - plain player PDC, one {@code Integer} per
 * entry, same "no database" shape {@code bestiary.BestiaryProgressService}/{@code
 * skills.GeneralSkillService}'s own mining milestones already use. Unlike those two, {@link
 * CollectionsMilestone#threshold} is an absolute cumulative amount already (100, 250, 500...
 * exactly as the player's own spec states it), not a per-step increment, so {@link #achieved}
 * is a simple "how many thresholds does this total already clear" count rather than
 * consuming a shared step array.
 */
public final class CollectionsProgressService {

    public int collected(Player player, CollectionsEntry entry) {
        return player.getPersistentDataContainer().getOrDefault(collectedKey(entry), PersistentDataType.INTEGER, 0);
    }

    /** Adds {@code amount} to {@code entry}'s own running total and returns it - see {@link CollectionsService#record} for what happens once this crosses a new milestone. */
    public int addCollected(Player player, CollectionsEntry entry, int amount) {
        int total = this.collected(player, entry) + amount;
        player.getPersistentDataContainer().set(collectedKey(entry), PersistentDataType.INTEGER, total);
        return total;
    }

    public int achieved(Player player, CollectionsEntry entry) {
        return this.achieved(entry, this.collected(player, entry));
    }

    public int achieved(CollectionsEntry entry, int collected) {
        int count = 0;
        for (CollectionsMilestone m : entry.milestones()) {
            if (collected < m.threshold()) {
                break;
            }
            count++;
        }
        return count;
    }

    public int maxMilestones(CollectionsEntry entry) {
        return entry.milestones().size();
    }

    /** Sum of {@link #achieved} across every catalog entry - what {@code GlobalLevelService#creditMilestones}'s own running-total checkpoint reads (see {@link CollectionsService#record}). */
    public int totalMilestones(Player player) {
        return CollectionsCatalog.entries().stream().mapToInt(e -> this.achieved(player, e)).sum();
    }

    /** Whether {@code player} has crossed at least one milestone whose {@link CollectionsMilestone#recipes} includes {@code recipe} - see {@link CollectionsService#hasUnlockedRecipe}, the actual public check callers should use instead of re-deriving this themselves. */
    boolean hasCrossedMilestoneUnlocking(Player player, CollectionsEntry entry, NamespacedKey recipe) {
        int achieved = this.achieved(player, entry);
        for (int i = 0; i < achieved; i++) {
            if (entry.milestones().get(i).recipes().contains(recipe)) {
                return true;
            }
        }
        return false;
    }

    private static NamespacedKey collectedKey(CollectionsEntry entry) {
        return new NamespacedKey("foodtooltips", "collected_" + entry.material().name().toLowerCase(java.util.Locale.ROOT));
    }
}
