package dev.icaro.foodtooltips.economy;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;
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
 * <p>Vault is an optional dependency (see {@link VaultEconomy}): without it, or without an
 * economy plugin registered behind it, rewards are skipped with a single console warning.
 */
public final class CoinRewardService {
    private final Plugin plugin;
    private final ToDoubleFunction<LivingEntity> totalMaxHealth;
    private final boolean enabled;
    private final boolean spawnerMobsPay;
    private final NavigableMap<Double, Long> coinsByHealth;
    private final Map<EntityType, Long> bossCoins;
    /** The killer's Scavenger level (main-hand weapon) and the mob's level (its HP / 5, the number on its name tag) - late-bound so this class doesn't depend on the enchant/combat packages; both default to 0 (no bonus) until wired. */
    private java.util.function.ToIntFunction<Player> scavengerLevel = p -> 0;
    private java.util.function.ToIntFunction<LivingEntity> mobLevel = mob -> 0;
    /** Extra coins per mob level from the Scavenger Talisman ({@code item.CombatTalismanService#scavengerCoinsPerMobLevel}) - stacks with the enchant's own rate. Defaults to none. */
    private java.util.function.ToDoubleFunction<Player> talismanCoinsPerMobLevel = p -> 0.0;
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

    /** Wires Scavenger in - see {@link #scavengerLevel}. */
    public void scavenger(java.util.function.ToIntFunction<Player> scavengerLevel, java.util.function.ToIntFunction<LivingEntity> mobLevel) {
        this.scavengerLevel = scavengerLevel;
        this.mobLevel = mobLevel;
    }

    /** Wires the Scavenger Talisman in - see {@link #talismanCoinsPerMobLevel}. */
    public void scavengerTalisman(java.util.function.ToDoubleFunction<Player> talismanCoinsPerMobLevel) {
        this.talismanCoinsPerMobLevel = talismanCoinsPerMobLevel;
    }

    /** Scavenger's bonus for one kill: {@code coinsPerMobLevel} (see {@code enchant.IcarusEnchant#scavengerCoinsPerMobLevel}) times the mob's level, rounded to whole coins. */
    public static long scavengerBonus(double coinsPerMobLevel, int mobLevel) {
        return Math.max(0L, Math.round(coinsPerMobLevel * Math.max(0, mobLevel)));
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
        // Scavenger enchant (0.3/level of the enchant) and Scavenger Talisman (+0.5) add up into
        // one coins-per-mob-level rate, the player's own "acumula com scavenger enchantment".
        double perMobLevel = dev.icaro.foodtooltips.enchant.IcarusEnchant.scavengerCoinsPerMobLevel(this.scavengerLevel.applyAsInt(killer))
                + this.talismanCoinsPerMobLevel.applyAsDouble(killer);
        if (perMobLevel > 0.0) {
            coins += scavengerBonus(perMobLevel, this.mobLevel.applyAsInt(mob));
        }
        if (coins <= 0L) {
            return;
        }
        if (!VaultEconomy.deposit(killer, coins)) {
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

}
