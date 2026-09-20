package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Grants temporary flight for drinking a Magical/Mystical Mushroom Soup ({@code
 * FarmingCollectionsItemsService#magicalMushroomSoup}/{@code #mysticalMushroomSoup}) -
 * {@value FarmingCollectionsItemsService#MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS}/{@value
 * FarmingCollectionsItemsService#MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS} ticks respectively,
 * "efeito acumulativo se tomar várias" (cumulative across multiple drinks - {@link #consume}
 * always ADDS to whatever's already remaining rather than replacing it, so drinking a second
 * soup before the first wears off genuinely extends the total). {@link #tick} (called from
 * {@code FoodTooltipsPlugin}'s existing periodic loop) counts every player's own remaining
 * balance down and revokes flight once it hits zero.
 *
 * <p>Never touches Creative/Spectator players - they already fly for their own unrelated
 * reason, and forcing {@code setAllowFlight(false)} on them the moment a totally unrelated
 * soup timer expired would be a real, visible bug, not just an edge case.
 */
public final class MushroomSoupFlightService implements Listener {
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent e) {
        ItemStack item = e.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        int grant;
        if (pdc.has(FarmingCollectionsItemsService.MAGICAL_MUSHROOM_SOUP_KEY, PersistentDataType.BYTE)) {
            grant = FarmingCollectionsItemsService.MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS;
        } else if (pdc.has(FarmingCollectionsItemsService.MYSTICAL_MUSHROOM_SOUP_KEY, PersistentDataType.BYTE)) {
            grant = FarmingCollectionsItemsService.MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS;
        } else {
            return;
        }
        Player p = e.getPlayer();
        int total = this.remainingTicks.merge(p.getUniqueId(), grant, Integer::sum);
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setAllowFlight(true);
        }
        Language l = Language.of(p);
        p.sendMessage(Component.text(l.choose("✦ Voo restante: ", "✦ Remaining flight: ") + (total / 20) + "s", NamedTextColor.LIGHT_PURPLE));
    }

    /** Counts every tracked player's own remaining flight balance down by {@code elapsedTicks} (the periodic loop's own interval), revoking flight once it reaches zero. Call once per pass from {@code FoodTooltipsPlugin}'s existing loop. */
    public void tick(Player p, long elapsedTicks) {
        Integer remaining = this.remainingTicks.get(p.getUniqueId());
        if (remaining == null) {
            return;
        }
        int updated = (int) (remaining - elapsedTicks);
        if (updated > 0) {
            this.remainingTicks.put(p.getUniqueId(), updated);
            return;
        }
        this.remainingTicks.remove(p.getUniqueId());
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.remainingTicks.remove(e.getPlayer().getUniqueId());
    }
}
