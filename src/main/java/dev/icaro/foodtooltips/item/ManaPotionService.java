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
 * Mana Potion (Raw Mutton Collections M2) - +{@value FarmingCollectionsItemsService#MANA_POTION_REGEN_PER_SECOND}
 * Mana regen per second for {@value FarmingCollectionsItemsService#MANA_POTION_DURATION_TICKS}
 * ticks on drink, read by {@code FoodTooltipsPlugin}'s own periodic Mana-regen computation
 * ({@link #regenBonusPerSecond}) alongside {@code stats.mana-regeneration-per-second}. Same
 * "replaces, doesn't stack" and periodic-countdown shape as {@code ArcheryPotionService}.
 */
public final class ManaPotionService implements Listener {
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(FarmingCollectionsItemsService.MANA_POTION_KEY, PersistentDataType.BYTE)) {
            return;
        }
        this.remainingTicks.put(e.getPlayer().getUniqueId(), FarmingCollectionsItemsService.MANA_POTION_DURATION_TICKS);
    }

    /** {@value FarmingCollectionsItemsService#MANA_POTION_REGEN_PER_SECOND} while {@code p}'s own potion is still active, 0 otherwise. */
    public double regenBonusPerSecond(Player p) {
        return this.remainingTicks.containsKey(p.getUniqueId()) ? FarmingCollectionsItemsService.MANA_POTION_REGEN_PER_SECOND : 0.0;
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
