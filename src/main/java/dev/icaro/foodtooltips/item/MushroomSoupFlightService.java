package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * Grants temporary flight for drinking a Magical/Mystical Mushroom Soup ({@code
 * FarmingCollectionsItemsService#magicalMushroomSoup}/{@code #mysticalMushroomSoup}) -
 * {@value FarmingCollectionsItemsService#MAGICAL_MUSHROOM_SOUP_FLIGHT_TICKS}/{@value
 * FarmingCollectionsItemsService#MYSTICAL_MUSHROOM_SOUP_FLIGHT_TICKS} ticks respectively,
 * "efeito acumulativo se tomar várias" (cumulative across multiple drinks - {@link #consume}
 * always ADDS to whatever's already remaining rather than replacing it, so drinking a second
 * soup before the first wears off genuinely extends the total). {@link #tick} (called from
 * {@code FoodTooltipsPlugin}'s existing periodic loop) counts every player's own remaining
 * balance down, keeps the sidebar scoreboard ({@link #showBoard}) in sync while it does, and
 * revokes flight once it hits zero.
 *
 * <p>Never touches Creative/Spectator players - they already fly for their own unrelated
 * reason, and forcing {@code setAllowFlight(false)} on them the moment a totally unrelated
 * soup timer expired would be a real, visible bug, not just an edge case.
 *
 * <p>Both soups themselves are made drinkable regardless of hunger ({@code
 * FarmingCollectionsItemsService}'s own builders set {@code FoodComponent#setCanAlwaysEat}) -
 * this class only cares about the item actually being consumed, not why vanilla allowed it.
 */
public final class MushroomSoupFlightService implements Listener {
    /** This plugin's only sidebar consumer - deliberately not shared with anything else, so this class can freely register/unregister it without ever touching another feature's own scoreboard state. */
    private static final String OBJECTIVE_ID = "foodtooltips_soup_flight";
    private static final String SCORE_ENTRY = "flight_time";
    private final Map<UUID, Integer> remainingTicks = new HashMap<>();
    /**
     * Late-bound, same "no direct dependency on an unrelated feature" shape every other
     * cross-package callback in this codebase uses (see {@code skills.ArmorDefenseService}'s
     * own {@code protectionBonus}) - lets {@code global.GlobalPresentationService} re-sync its
     * own nametag-color teams onto a player's scoreboard right after {@link #ensurePersonalBoard}
     * swaps it out for a fresh one (a brand new {@link Scoreboard} starts with none of those
     * teams - see this class's own doc on why one has to be created at all). No-op by default,
     * so this class works before it's ever wired.
     */
    private Consumer<Player> onScoreboardReplaced = p -> {};

    /** Wired after construction, same reason as every other late-bound setter in this codebase - see {@link #onScoreboardReplaced}'s own doc. */
    public void onScoreboardReplaced(Consumer<Player> callback) {
        this.onScoreboardReplaced = callback;
    }

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
        this.showBoard(p, total);
    }

    /** Counts every tracked player's own remaining flight balance down by {@code elapsedTicks} (the periodic loop's own interval), refreshing the sidebar and revoking flight once it reaches zero. Call once per pass from {@code FoodTooltipsPlugin}'s existing loop. */
    public void tick(Player p, long elapsedTicks) {
        Integer remaining = this.remainingTicks.get(p.getUniqueId());
        if (remaining == null) {
            return;
        }
        int updated = (int) (remaining - elapsedTicks);
        if (updated > 0) {
            this.remainingTicks.put(p.getUniqueId(), updated);
            this.showBoard(p, updated);
            return;
        }
        this.remainingTicks.remove(p.getUniqueId());
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        this.hideBoard(p);
    }

    /** Renders/updates {@code p}'s own sidebar with the flight time remaining (seconds) - a personal {@link Scoreboard} of their own, so nobody else's sidebar is ever touched by this. */
    private void showBoard(Player p, int ticksRemaining) {
        Scoreboard board = this.ensurePersonalBoard(p);
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY, Component.text(""));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        Language l = Language.of(p);
        objective.displayName(Component.text(l.choose("✦ Voo de Cogumelo ✦", "✦ Mushroom Flight ✦"), NamedTextColor.LIGHT_PURPLE));
        Score score = objective.getScore(SCORE_ENTRY);
        score.customName(Component.text(l.choose("Tempo restante", "Time left"), NamedTextColor.AQUA));
        score.setScore(ticksRemaining / 20);
    }

    /** Removes the flight sidebar from {@code p} once their timer hits zero, reverting them back to the shared main scoreboard if nothing else is using their personal one (see {@link #ensurePersonalBoard}). */
    private void hideBoard(Player p) {
        Scoreboard board = p.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective != null) {
            objective.unregister();
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && board != manager.getMainScoreboard() && board.getObjectives().isEmpty()) {
            p.setScoreboard(manager.getMainScoreboard());
        }
    }

    /**
     * {@code p}'s own current scoreboard if it's already a personal one, else a fresh
     * {@link Scoreboard} of their own (every player shares the same main scoreboard instance
     * by default in this plugin - nothing else ever calls {@code Player#setScoreboard} - so
     * putting a per-player sidebar straight on the shared one would show it to everyone, not
     * just the flying player). {@link #onScoreboardReplaced} fires only on that swap, so
     * {@code global.GlobalPresentationService}'s own nametag-color teams (registered on
     * whatever scoreboard a player currently has) get a chance to re-sync onto the new one.
     */
    private Scoreboard ensurePersonalBoard(Player p) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard current = p.getScoreboard();
        if (manager != null && current == manager.getMainScoreboard()) {
            Scoreboard fresh = manager.getNewScoreboard();
            p.setScoreboard(fresh);
            this.onScoreboardReplaced.accept(p);
            return fresh;
        }
        return current;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.remainingTicks.remove(e.getPlayer().getUniqueId());
    }
}
