package dev.icaro.foodtooltips.item;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.plugin.Plugin;

/**
 * No Brewing Stand on this server ever needs Blaze Powder - every one this plugin has seen
 * (opened by a player, or placed - {@link #open}/{@link #place}) is tracked by location and
 * kept comfortably topped up ({@value #FUEL_LEVEL}, far more than the handful of charges a
 * real Blaze Powder would ever give) by {@link #refill}'s own periodic pass, so brewing simply
 * never stalls waiting on fuel. A stand nobody has opened or placed since this feature existed
 * (already sitting in the world before an update, say) only starts getting refilled the first
 * time a player opens it - a minor, acceptable gap next to the alternative of scanning every
 * loaded chunk's tile entities on a timer.
 */
public final class BrewingStandFuelService implements Listener {
    private static final int FUEL_LEVEL = 999;
    private static final int REFILL_TICKS = 100;
    private static final int REFILL_THRESHOLD = FUEL_LEVEL / 2;

    private final Plugin plugin;
    private final Set<Location> tracked = new HashSet<>();

    public BrewingStandFuelService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Starts {@link #refill}'s own repeating task - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::refill, REFILL_TICKS, REFILL_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void open(InventoryOpenEvent e) {
        if (e.getInventory() instanceof BrewerInventory inv) {
            BrewingStand stand = inv.getHolder();
            if (stand != null) {
                this.track(stand);
            }
        }
    }

    /**
     * Starts (or refreshes) tracking {@code stand} and immediately tops it up - call
     * whenever a Brewing Stand is about to be used, whether that's the real vanilla UI
     * ({@link #open}) or {@code dev.icaro.foodtooltips.brewing.BrewingMenuService}'s own
     * custom one, which has no fuel slot to show the player at all and so relies entirely
     * on this to keep the stand it's proxying able to brew.
     */
    public void track(BrewingStand stand) {
        this.tracked.add(stand.getLocation());
        stand.setFuelLevel(FUEL_LEVEL);
        stand.update();
    }

    @EventHandler(ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (e.getBlock().getType() == Material.BREWING_STAND) {
            this.tracked.add(e.getBlock().getLocation());
        }
    }

    /** Tops up every tracked stand still below {@link #REFILL_THRESHOLD} - untracks anything that isn't a Brewing Stand anymore (broken, or replaced) instead of leaking forever. */
    private void refill() {
        this.tracked.removeIf(loc -> {
            if (loc.getWorld() == null || !loc.isChunkLoaded()) {
                return false;
            }
            BlockState state = loc.getBlock().getState();
            if (!(state instanceof BrewingStand stand)) {
                return true;
            }
            if (stand.getFuelLevel() < REFILL_THRESHOLD) {
                stand.setFuelLevel(FUEL_LEVEL);
                stand.update();
            }
            return false;
        });
    }
}
