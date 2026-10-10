package dev.icaro.foodtooltips.economy;

import java.util.OptionalDouble;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * The plugin's only door to the server economy (ExcellentEconomy on the player's server, behind
 * VaultUnlocked) - through the classic Vault Economy API, which VaultUnlocked still provides.
 * Vault is an optional dependency: every Vault type lives in {@link Bridge}, a nested class the
 * JVM only loads once {@link #vaultPresent} has confirmed Vault's own classes are present, so the
 * rest of the plugin keeps running on a server without Vault (every call here then just reports
 * "no economy").
 */
public final class VaultEconomy {
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private VaultEconomy() {
    }

    /** Adds {@code amount} to {@code player}'s balance - false if there's no economy or it refused. */
    public static boolean deposit(Player player, double amount) {
        return vaultPresent() && Bridge.deposit(player, amount);
    }

    /** Takes {@code amount} from {@code player}'s balance - false if there's no economy or it refused. */
    public static boolean withdraw(Player player, double amount) {
        return vaultPresent() && Bridge.withdraw(player, amount);
    }

    /** {@code player}'s balance, or empty if there's no economy. */
    public static OptionalDouble balance(Player player) {
        return vaultPresent() ? Bridge.balance(player) : OptionalDouble.empty();
    }

    /** {@code amount} in the economy's own display format (e.g. "⛂1,250"), or the plain number without an economy. */
    public static String format(double amount) {
        return vaultPresent() ? Bridge.format(amount) : String.valueOf((long) amount);
    }

    private static boolean vaultPresent() {
        try {
            Class.forName(VAULT_ECONOMY_CLASS, false, VaultEconomy.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }

    /** The only place that touches Vault's own types - see the class doc on why it's isolated. */
    private static final class Bridge {
        static net.milkbowl.vault.economy.Economy provider() {
            var registration = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            return registration == null ? null : registration.getProvider();
        }

        static boolean deposit(Player player, double amount) {
            var economy = provider();
            return economy != null && economy.depositPlayer(player, amount).transactionSuccess();
        }

        static boolean withdraw(Player player, double amount) {
            var economy = provider();
            return economy != null && economy.withdrawPlayer(player, amount).transactionSuccess();
        }

        static OptionalDouble balance(Player player) {
            var economy = provider();
            return economy == null ? OptionalDouble.empty() : OptionalDouble.of(economy.getBalance(player));
        }

        static String format(double amount) {
            var economy = provider();
            return economy == null ? String.valueOf((long) amount) : economy.format(amount);
        }
    }
}
