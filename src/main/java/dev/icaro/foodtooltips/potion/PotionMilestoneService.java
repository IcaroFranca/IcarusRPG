package dev.icaro.foodtooltips.potion;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Permanent, per-player record of every potion ever brewed and collected (taken out of a
 * Brewing Stand's own output slot - see {@code PotionGuideMenuService}), one flag per
 * {@link PotionEntry}. Same "one-time achievement record, byte flag per id in the player's
 * own PDC" shape {@code enchant.EnchantMilestoneService} already uses for enchant milestones -
 * no per-level tracking here though, since a potion's own Redstone/Glowstone variants all
 * fold into the same entry (see {@link PotionCatalog}'s own doc).
 */
public final class PotionMilestoneService {
    private static final String NAMESPACE = "foodtooltips";

    public boolean hasAchieved(Player p, PotionEntry entry) {
        return p.getPersistentDataContainer().has(this.key(entry), PersistentDataType.BYTE);
    }

    /** Records {@code entry} as achieved for {@code p} if it wasn't already - true only the first time this actually marks it. */
    public boolean achieve(Player p, PotionEntry entry) {
        NamespacedKey key = this.key(entry);
        if (p.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return false;
        }
        p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    private NamespacedKey key(PotionEntry entry) {
        return new NamespacedKey(NAMESPACE, "potion_milestone_" + entry.id());
    }
}
