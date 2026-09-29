package dev.icaro.foodtooltips.heat;

import dev.icaro.foodtooltips.skills.AccessoryBagService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * The Nether's own Heat mechanic: every player accumulates Heat (0-{@value #MAX_HEAT}) the
 * whole time they're in a Nether world, per the player's own spec (image + follow-up
 * answers) -
 *
 * <ul>
 *   <li>Heat only exists in the Nether - there's no decay, no persistence, nothing to load
 *   or save: a player simply has 0 Heat the instant they aren't in one (checked fresh every
 *   {@link #tickAll} pass rather than on a world-change event, so leaving the Nether by ANY
 *   means - portal, {@code /tp}, death and respawn, plugin teleport - self-corrects within
 *   one second without needing its own listener for each).
 *   <li>Heat rises by {@value #HEAT_PER_TICK} every "Heat Tick", which happens every
 *   {@code y = 1 * (1 + Heat Resistance)} seconds (the player's own formula/image) - see
 *   {@link #tickIntervalSeconds}. {@link AccessoryBagService#totalHeatResistance} (the
 *   Crimson Stem Collection's own Ember Talisman/Ring/Artifact line) is what slows this down.
 *   <li>Each current Heat point grants +{@value #MINING_SPEED_PER_HEAT} Mining Speed and
 *   +{@value #MINING_FORTUNE_PER_HEAT} Mining Fortune, live (see {@link #miningSpeedBonus}/
 *   {@link #miningFortuneBonus}, wired into {@code skills.GeneralSkillService}) - a real,
 *   growing reward for staying deep in the Nether, not just a hazard.
 *   <li>Heat &ge; {@value #FIRE_THRESHOLD}: the player catches fire, via a perfectly ordinary
 *   {@link Player#setFireTicks} - real vanilla combustion, so Fire Resistance/armor/enchants
 *   reduce or cancel it exactly like any other fire, same as the player's own "começa a pegar
 *   fogo" wording (a consequence, not yet the "ignores everything" tier).
 *   <li>Heat = {@value #MAX_HEAT} (the hard cap, never exceeded): "o dano do fogo começa a
 *   ignorar qualquer defesa ou resistência a ele" - applied as a direct {@link
 *   Player#setHealth} reduction ({@link #trueDamagePerTick}/second) rather than through the
 *   normal damage event pipeline, since Fire Resistance suppresses vanilla fire damage before
 *   any event fires at all (there's no "ignore this one status effect" hook on that path) -
 *   bypassing the whole pipeline is the only way to genuinely guarantee nothing (armor,
 *   enchant, potion effect) softens it, matching the spec's own word "qualquer".
 * </ul>
 *
 * <p>Shows each tracked player their own current Heat on a personal sidebar {@link
 * Scoreboard}, same "ensure a personal board, don't clobber {@code global
 * .GlobalPresentationService}'s own nametag-color teams" pattern {@code
 * item.MushroomSoupFlightService} already established (duplicated here rather than shared,
 * same as every other small per-class idiom in this plugin - e.g. {@code isFiller} across
 * five different bag/storage services).
 */
public final class HeatService implements Listener {
    public static final int MAX_HEAT = 100;
    public static final int FIRE_THRESHOLD = 90;
    /** Heat gained per "Heat Tick" at the base rate (before {@link #tickIntervalSeconds} slows it down) - the player's own chosen default. */
    private static final int HEAT_PER_TICK = 1;
    public static final int MINING_SPEED_PER_HEAT = 4;
    public static final int MINING_FORTUNE_PER_HEAT = 1;
    /** How much real health {@link #MAX_HEAT} Heat's own "ignores everything" damage takes off every second it's held at the cap - 2 hearts/second, real enough to force the player out rather than ignore it, without being instant death. */
    private static final double TRUE_DAMAGE_PER_SECOND = 4.0;
    /** Refreshed every {@link #SCAN_INTERVAL_TICKS} pass while Heat &ge; {@link #FIRE_THRESHOLD} - longer than that interval so the fire never has a chance to expire between passes. */
    private static final int FIRE_REFRESH_TICKS = 60;
    private static final long SCAN_INTERVAL_TICKS = 20L;
    private static final String OBJECTIVE_ID = "icarus_heat";
    private static final String SCORE_ENTRY = "heat";

    private final Plugin plugin;
    private final AccessoryBagService accessoryBag;
    /** {@code p}'s own current Heat (0-{@value #MAX_HEAT}) - absent means untracked, read back as 0 by {@link #currentHeat}. Only ever holds an entry for a player currently confirmed to be in the Nether (see this class's own doc on why leaving self-corrects rather than needing its own event). */
    private final Map<UUID, Integer> heat = new HashMap<>();
    /** Seconds accumulated since {@code p}'s own last Heat Tick, carried across {@link #tickAll} passes so a Heat Resistance-lengthened interval (e.g. 4s) still lands correctly rather than resetting every 1s pass. */
    private final Map<UUID, Double> progress = new HashMap<>();
    /** Every player {@link #showBoard} has ever put the Heat objective on - so {@link #hideBoard} only ever needs to run for someone who actually has it, and {@link #clear} can tell "was tracked, needs a visible reset" apart from "never was". */
    private final Set<UUID> boardShown = new HashSet<>();
    /** Same late-bound idea as {@code item.MushroomSoupFlightService#onScoreboardReplaced} - lets {@code global.GlobalPresentationService} re-sync its own nametag-color teams onto a freshly-created personal board. */
    private Consumer<Player> onScoreboardReplaced = p -> {
    };
    private BukkitTask task;

    public HeatService(Plugin plugin, AccessoryBagService accessoryBag) {
        this.plugin = plugin;
        this.accessoryBag = accessoryBag;
    }

    /** Wired in after construction - see {@link #onScoreboardReplaced}. */
    public void onScoreboardReplaced(Consumer<Player> callback) {
        this.onScoreboardReplaced = callback;
    }

    public void start() {
        this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickAll, SCAN_INTERVAL_TICKS, SCAN_INTERVAL_TICKS);
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
        for (UUID id : new java.util.ArrayList<>(this.heat.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.clear(p);
            }
        }
        this.heat.clear();
        this.progress.clear();
        this.boardShown.clear();
    }

    /** {@code p}'s own current Heat (0-{@value #MAX_HEAT}), or 0 if they aren't currently tracked (not in the Nether). */
    public int currentHeat(Player p) {
        return this.heat.getOrDefault(p.getUniqueId(), 0);
    }

    /** {@value #MINING_SPEED_PER_HEAT} per current Heat point - wired into {@code skills.GeneralSkillService#heatMiningSpeedBonus}. */
    public int miningSpeedBonus(Player p) {
        return this.currentHeat(p) * MINING_SPEED_PER_HEAT;
    }

    /** {@value #MINING_FORTUNE_PER_HEAT} per current Heat point - wired into {@code skills.GeneralSkillService#heatMiningFortuneBonus}. */
    public int miningFortuneBonus(Player p) {
        return this.currentHeat(p) * MINING_FORTUNE_PER_HEAT;
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        this.heat.remove(id);
        this.progress.remove(id);
        this.boardShown.remove(id);
    }

    private void tickAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != World.Environment.NETHER) {
                if (this.heat.containsKey(p.getUniqueId())) {
                    this.clear(p);
                }
                continue;
            }
            this.tickOne(p);
        }
    }

    private void tickOne(Player p) {
        UUID id = p.getUniqueId();
        int resistance = this.accessoryBag.totalHeatResistance(p);
        double intervalSeconds = tickIntervalSeconds(resistance);
        double elapsedSeconds = SCAN_INTERVAL_TICKS / 20.0;
        TickResult result = accumulate(this.progress.getOrDefault(id, 0.0), elapsedSeconds, intervalSeconds);
        this.progress.put(id, result.leftoverSeconds());
        int updated = Math.min(MAX_HEAT, this.heat.getOrDefault(id, 0) + result.gained() * HEAT_PER_TICK);
        this.heat.put(id, updated);
        this.showBoard(p, updated);
        this.applyEffects(p, updated);
    }

    private void applyEffects(Player p, int currentHeat) {
        if (currentHeat >= FIRE_THRESHOLD) {
            p.setFireTicks(Math.max(p.getFireTicks(), FIRE_REFRESH_TICKS));
        }
        if (currentHeat >= MAX_HEAT) {
            double damage = TRUE_DAMAGE_PER_SECOND * (SCAN_INTERVAL_TICKS / 20.0);
            p.setHealth(Math.max(0.0, p.getHealth() - damage));
        }
    }

    /** Drops {@code p} out of tracking entirely (they left the Nether) and hides their sidebar - {@link #tickAll} is what actually notices and calls this, not a world-change listener (see this class's own doc). */
    private void clear(Player p) {
        UUID id = p.getUniqueId();
        this.heat.remove(id);
        this.progress.remove(id);
        this.hideBoard(p);
    }

    // ---- Pure tick math (no Bukkit statics touched - see heat.HeatServiceTest) ----

    /** {@code y = 1 * (1 + Heat Resistance)}, in seconds - the player's own formula, exactly. */
    static double tickIntervalSeconds(int heatResistance) {
        return 1.0 * (1 + Math.max(0, heatResistance));
    }

    /** How many whole Heat Ticks {@code elapsedSeconds} (added to whatever fraction of a tick was already {@code previousLeftoverSeconds} left over from before) completes at {@code intervalSeconds} per tick, plus however much is left over afterward - carried into the next call so a slow (Heat Resistance-lengthened) interval still lands exactly on schedule across many 1-second passes instead of losing the remainder each time. */
    static TickResult accumulate(double previousLeftoverSeconds, double elapsedSeconds, double intervalSeconds) {
        double total = previousLeftoverSeconds + elapsedSeconds;
        int gained = (int) (total / intervalSeconds);
        double leftover = total - gained * intervalSeconds;
        return new TickResult(gained, leftover);
    }

    record TickResult(int gained, double leftoverSeconds) {
    }

    // ---- Sidebar (same pattern as item.MushroomSoupFlightService#showBoard/#hideBoard/#ensurePersonalBoard) ----

    private void showBoard(Player p, int currentHeat) {
        Scoreboard board = this.ensurePersonalBoard(p);
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY, Component.text(""));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(Component.text("🔥 Heat 🔥", NamedTextColor.RED));
        Score score = objective.getScore(SCORE_ENTRY);
        score.customName(Component.text(currentHeat + "/" + MAX_HEAT, NamedTextColor.GOLD));
        score.setScore(currentHeat);
        this.boardShown.add(p.getUniqueId());
    }

    private void hideBoard(Player p) {
        if (!this.boardShown.remove(p.getUniqueId())) {
            return;
        }
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
}
