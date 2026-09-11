package dev.icaro.foodtooltips.enchant;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Permanent, per-player record of every (enchant entry, level) ever applied through
 * the reworked Enchanting Table ({@code EnchantMenuService#applyLevel}) - deliberately
 * never touched by an item obtained any other way (mob drop, trade, loot chest...),
 * since only Table use is meant to count towards the milestone reward. Once true,
 * stays true forever, even if the item carrying that enchant is later lost, destroyed,
 * or has the enchant removed via the Grindstone - a one-time achievement record, not a
 * "currently equipped" check. Backing storage is a byte flag per (entry, level) on the
 * player's own {@link org.bukkit.persistence.PersistentDataContainer}, one {@link
 * NamespacedKey} each - simple and, unlike a single packed string, never needs a
 * read-parse-rewrite round trip for one flag.
 */
public final class EnchantMilestoneService {
    private static final String NAMESPACE = "foodtooltips";

    /** True if {@code p} has already been credited for {@code entry} at {@code level}. */
    public boolean hasAchieved(Player p, EnchantEntry entry, int level) {
        return p.getPersistentDataContainer().has(this.key(entry, level), PersistentDataType.BYTE);
    }

    /**
     * Records {@code entry} at {@code level} as achieved for {@code p} if it wasn't
     * already - returns true only the first time this actually marks it, false on
     * every call after (including a call that finds it already set from some earlier
     * session).
     */
    public boolean achieve(Player p, EnchantEntry entry, int level) {
        NamespacedKey key = this.key(entry, level);
        if (p.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
            return false;
        }
        p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    /** {@link EnchantEntry#id()} sanitized into valid {@link NamespacedKey} key characters (lowercase, no colons/dots from vanilla's own "minecraft:sharpness"-style id). */
    private NamespacedKey key(EnchantEntry entry, int level) {
        String safe = entry.id().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return new NamespacedKey(NAMESPACE, "enchant_milestone_" + safe + "_" + level);
    }
}
