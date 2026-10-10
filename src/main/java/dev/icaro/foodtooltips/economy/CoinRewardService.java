package dev.icaro.foodtooltips.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Pays coins straight into the killer's balance on every hostile mob kill - no item drops, the
 * server's own economy plugin (ExcellentEconomy on the player's server, behind VaultUnlocked)
 * holds the balance, and its own PlaceholderAPI placeholder puts it on the TAB scoreboard, so the
 * number updates the moment the mob dies (the player's own spec). This plugin never stores
 * coins itself - it only deposits through Vault's Economy API, into whatever currency that
 * economy plugin has configured as its main one.
 *
 * <p>The amount is the mob's own Blood Points value ({@code skills.CombatValorService#mobValor} -
 * the number the Bestiary shows, scaling with how tough the mob is) times {@code
 * economy.coins-per-blood-point}, so tougher mobs pay more without a second per-mob table to
 * maintain.
 *
 * <p>Vault is an optional dependency: every Vault type lives in {@link VaultBridge}, a nested
 * class the JVM only loads once Vault's own classes are confirmed present, so this plugin keeps
 * running (minus the rewards, with a single console warning) on a server without Vault or
 * without any economy plugin registered behind it.
 */
public final class CoinRewardService {
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final Plugin plugin;
    private final boolean enabled;
    private final double coinsPerBloodPoint;
    private boolean warned;

    public CoinRewardService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("economy.kill-rewards", true);
        this.coinsPerBloodPoint = Math.max(0.0, plugin.getConfig().getDouble("economy.coins-per-blood-point", 1.0));
    }

    /** Coins a kill worth {@code bloodPoints} pays at {@code coinsPerBloodPoint} - rounded to whole coins, never negative. */
    public static long coinsFor(long bloodPoints, double coinsPerBloodPoint) {
        return Math.max(0L, Math.round(bloodPoints * coinsPerBloodPoint));
    }

    /** Deposits the coins for a hostile kill worth {@code bloodPoints} into {@code killer}'s balance - a no-op if disabled, worth nothing, or no economy is available. */
    public void rewardKill(Player killer, long bloodPoints) {
        long coins = coinsFor(bloodPoints, this.coinsPerBloodPoint);
        if (!this.enabled || coins <= 0L) {
            return;
        }
        if (!vaultPresent() || !VaultBridge.deposit(killer, coins)) {
            this.warnOnce();
        }
    }

    private void warnOnce() {
        if (this.warned) {
            return;
        }
        this.warned = true;
        this.plugin.getLogger().warning("Kill coin rewards are on (economy.kill-rewards) but no Vault economy is available - "
                + "install Vault/VaultUnlocked plus an economy plugin (e.g. ExcellentEconomy + nightcore). Rewards are skipped until then.");
    }

    private static boolean vaultPresent() {
        try {
            Class.forName(VAULT_ECONOMY_CLASS, false, CoinRewardService.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /** The only place that touches Vault's own types - see the class doc on why it's isolated. */
    private static final class VaultBridge {
        static boolean deposit(Player player, long coins) {
            var registration = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            if (registration == null) {
                return false;
            }
            return registration.getProvider().depositPlayer(player, coins).transactionSuccess();
        }
    }
}
