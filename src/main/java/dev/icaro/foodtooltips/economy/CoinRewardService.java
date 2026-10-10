package dev.icaro.foodtooltips.economy;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * Pays coins straight into the killer's balance on every hostile mob kill - no item drops, the
 * server's own economy plugin (ExcellentEconomy on the player's server, behind VaultUnlocked)
 * holds the balance, and its own PlaceholderAPI placeholder puts it on the TAB scoreboard, so the
 * number updates the moment the mob dies (the player's own spec). This plugin never stores
 * coins itself - it only deposits through Vault's Economy API, into whatever currency that
 * economy plugin has configured as its main one.
 *
 * <p>The amount follows how strong the mob is - its total Max Health at spawn (bonus HP pool
 * included, see {@code combat.MobDifficultyService}), looked up in {@code
 * economy.coins-by-health}'s brackets - per the player's own anchors: a common ~100-200 HP mob
 * pays 1, a 300 HP Miner 4, a 4500 HP Nether mob 20. {@code economy.boss-coins} pins a fixed
 * amount for specific bosses instead (the Ender Dragon's 5000), and {@code
 * economy.spawner-mobs-pay: false} stops spawner-spawned mobs (mob farms) from paying anything.
 *
 * <p>Vault is an optional dependency: every Vault type lives in {@link VaultBridge}, a nested
 * class the JVM only loads once Vault's own classes are confirmed present, so this plugin keeps
 * running (minus the rewards, with a single console warning) on a server without Vault or
 * without any economy plugin registered behind it.
 */
public final class CoinRewardService {
    private static final String VAULT_ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final Plugin plugin;
    private final ToDoubleFunction<LivingEntity> totalMaxHealth;
    private final boolean enabled;
    private final boolean spawnerMobsPay;
    private final NavigableMap<Double, Long> coinsByHealth;
    private final Map<EntityType, Long> bossCoins;
    private boolean warned;

    /** {@code totalMaxHealth} - a mob's full Max Health including its bonus HP pool ({@code combat.MobVisualService#effectiveMaxHealth}). */
    public CoinRewardService(Plugin plugin, ToDoubleFunction<LivingEntity> totalMaxHealth) {
        this.plugin = plugin;
        this.totalMaxHealth = totalMaxHealth;
        this.enabled = plugin.getConfig().getBoolean("economy.kill-rewards", true);
        this.spawnerMobsPay = plugin.getConfig().getBoolean("economy.spawner-mobs-pay", true);
        this.coinsByHealth = loadBrackets(plugin.getConfig().getConfigurationSection("economy.coins-by-health"));
        this.bossCoins = loadBossCoins(plugin.getConfig().getConfigurationSection("economy.boss-coins"));
    }

    /** The bracket a mob with {@code maxHealth} total HP falls into - the highest threshold at or below it; 0 below the lowest one. */
    public static long coinsFor(double maxHealth, NavigableMap<Double, Long> brackets) {
        Map.Entry<Double, Long> bracket = brackets.floorEntry(maxHealth);
        return bracket == null ? 0L : Math.max(0L, bracket.getValue());
    }

    /** The player's own table - used whenever {@code economy.coins-by-health} is missing (e.g. a server config.yml from before it existed). */
    public static NavigableMap<Double, Long> defaultBrackets() {
        NavigableMap<Double, Long> brackets = new TreeMap<>();
        brackets.put(0.0, 1L);
        brackets.put(250.0, 4L);
        brackets.put(500.0, 6L);
        brackets.put(1000.0, 10L);
        brackets.put(2500.0, 20L);
        brackets.put(5000.0, 30L);
        brackets.put(10000.0, 50L);
        brackets.put(50000.0, 100L);
        brackets.put(250000.0, 200L);
        brackets.put(750000.0, 400L);
        return brackets;
    }

    /** Deposits the coins {@code mob} is worth into {@code killer}'s balance - a no-op if disabled, worth nothing, a spawner mob while those don't pay, or no economy is available. */
    public void rewardKill(Player killer, LivingEntity mob) {
        if (!this.enabled) {
            return;
        }
        if (!this.spawnerMobsPay && mob.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            return;
        }
        Long fixed = this.bossCoins.get(mob.getType());
        long coins = fixed != null ? fixed : coinsFor(this.totalMaxHealth.applyAsDouble(mob), this.coinsByHealth);
        if (coins <= 0L) {
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

    private static NavigableMap<Double, Long> loadBrackets(ConfigurationSection section) {
        if (section == null) {
            return defaultBrackets();
        }
        NavigableMap<Double, Long> brackets = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            try {
                brackets.put(Double.parseDouble(key), section.getLong(key));
            } catch (NumberFormatException ex) {
                // Not a health threshold - ignored.
            }
        }
        return brackets.isEmpty() ? defaultBrackets() : brackets;
    }

    private static Map<EntityType, Long> loadBossCoins(ConfigurationSection section) {
        Map<EntityType, Long> coins = new EnumMap<>(EntityType.class);
        if (section == null) {
            coins.put(EntityType.ENDER_DRAGON, 5000L);
            return coins;
        }
        for (String key : section.getKeys(false)) {
            try {
                coins.put(EntityType.valueOf(key.toUpperCase(Locale.ROOT)), section.getLong(key));
            } catch (IllegalArgumentException ex) {
                // Not an entity type - ignored.
            }
        }
        return coins;
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
