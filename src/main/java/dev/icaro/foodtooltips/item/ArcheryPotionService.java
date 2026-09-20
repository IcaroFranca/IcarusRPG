package dev.icaro.foodtooltips.item;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Archery Potion (Feather Collections M6) - {@value FarmingCollectionsItemsService#ARCHERY_POTION_PERCENT}%
 * bow/arrow damage for {@value FarmingCollectionsItemsService#ARCHERY_POTION_DURATION_TICKS}
 * ticks on drink, read by {@code combat.CombatListener}'s own projectile-damage formula
 * ({@link #bonusPercent}) alongside its existing enchant/reforge percentage terms. Drinking a
 * fresh one simply replaces the remaining duration (not cumulative - unlike the Mushroom
 * Soups' own flight, nothing in the player's own spec asked for stacking a plain damage
 * buff). {@link #tick} (called from {@code FoodTooltipsPlugin}'s existing periodic loop)
 * counts every player's own remaining duration down.
 */
public final class ArcheryPotionService implements Listener {
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(FarmingCollectionsItemsService.ARCHERY_POTION_KEY, PersistentDataType.BYTE)) {
            return;
        }
        this.remainingTicks.put(e.getPlayer().getUniqueId(), FarmingCollectionsItemsService.ARCHERY_POTION_DURATION_TICKS);
    }

    /** {@value FarmingCollectionsItemsService#ARCHERY_POTION_PERCENT} while {@code p}'s own potion is still active, 0 otherwise. */
    public double bonusPercent(Player p) {
        return this.remainingTicks.containsKey(p.getUniqueId()) ? FarmingCollectionsItemsService.ARCHERY_POTION_PERCENT : 0.0;
    }

    /** Counts {@code p}'s own remaining duration down by {@code elapsedTicks} (the periodic loop's own interval). Call once per pass from {@code FoodTooltipsPlugin}'s existing loop. */
    public void tick(Player p, long elapsedTicks) {
        Integer remaining = this.remainingTicks.get(p.getUniqueId());
        if (remaining == null) {
            return;
        }
        int updated = (int) (remaining - elapsedTicks);
        if (updated > 0) {
            this.remainingTicks.put(p.getUniqueId(), updated);
        } else {
            this.remainingTicks.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.remainingTicks.remove(e.getPlayer().getUniqueId());
    }
}
