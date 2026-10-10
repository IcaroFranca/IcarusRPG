package dev.icaro.foodtooltips.economy;

import java.util.OptionalDouble;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.Plugin;

/**
 * Dying costs money instead of items (the player's own spec - replacing the graves plugin): the
 * player keeps their whole inventory (and, by default, their XP levels - which this plugin's own
 * enchanting spends, so losing them too would be a second penalty), and loses {@code
 * death.money-loss-percent} (50% by default) of their balance instead, rounded down to whole
 * coins. Runs at {@link EventPriority#HIGHEST} so it has the last say on keep-inventory over
 * any earlier handler, while MONITOR listeners still only observe.
 */
public final class DeathPenaltyService implements Listener {
    private final Plugin plugin;
    private final boolean keepInventory;
    private final boolean keepExperience;
    private final double moneyLossPercent;

    public DeathPenaltyService(Plugin plugin) {
        this.plugin = plugin;
        this.keepInventory = plugin.getConfig().getBoolean("death.keep-inventory", true);
        this.keepExperience = plugin.getConfig().getBoolean("death.keep-experience", true);
        this.moneyLossPercent = Math.max(0.0, Math.min(100.0, plugin.getConfig().getDouble("death.money-loss-percent", 50.0)));
    }

    /** Whole coins a death takes from {@code balance} at {@code percent} - rounded down, never negative. */
    public static long moneyLost(double balance, double percent) {
        if (balance <= 0.0 || percent <= 0.0) {
            return 0L;
        }
        return (long) Math.floor(balance * percent / 100.0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent e) {
        if (this.keepInventory) {
            e.setKeepInventory(true);
            e.getDrops().clear();
        }
        if (this.keepExperience) {
            e.setKeepLevel(true);
            e.setDroppedExp(0);
        }
        this.takeMoney(e.getEntity());
    }

    private void takeMoney(Player p) {
        OptionalDouble balance = VaultEconomy.balance(p);
        if (balance.isEmpty()) {
            return;
        }
        long lost = moneyLost(balance.getAsDouble(), this.moneyLossPercent);
        if (lost <= 0L) {
            return;
        }
        if (!VaultEconomy.withdraw(p, lost)) {
            this.plugin.getLogger().warning("Could not take the death penalty (" + lost + ") from " + p.getName() + "'s balance.");
            return;
        }
        p.sendMessage(Component.text("You died and lost " + VaultEconomy.format(lost) + " (" + trimmed(this.moneyLossPercent)
                + "% of your money). You kept your items.", NamedTextColor.RED));
    }

    private static String trimmed(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
