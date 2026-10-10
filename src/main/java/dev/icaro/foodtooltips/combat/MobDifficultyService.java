package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.bestiary.BestiaryCategory;
import dev.icaro.foodtooltips.bestiary.BestiaryEntry;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Boss;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Mob difficulty scaling - how much tougher and more dangerous a hostile mob is than
 * its own vanilla numbers, on top of {@code mob-visuals.health-multiplier}'s existing
 * flat baseline (never below it - a surface/tier-0 mob keeps today's feel, everything
 * here is additive on top). Two independent bonuses stack into that baseline:
 *
 * <ul>
 * <li><b>Tier</b> - {@link BestiaryEntry#combatXp}, the ranking the Bestiary already
 * gives every mob (Zombie 50 up to Ender Dragon 2500), reused as-is rather than a new
 * catalog.
 * <li><b>Depth</b> - how far below each dimension's own reference Y (see {@link
 * #depthReferenceY}) the mob actually spawned; deeper = tougher, everywhere, not just
 * the Nether.
 * </ul>
 *
 * Both a Max Health multiplier and an outgoing-damage multiplier are computed the same
 * way (different, smaller coefficients for damage, so Defense/True Defense investment
 * still meaningfully counters it), each with its own per-dimension floor - the Nether's
 * own floor is deliberately the one non-zero by default (4500 HP / 500 damage), matching
 * how much more lethal that dimension is meant to feel than a deep Overworld cave.
 *
 * <p>Computed once per mob at spawn (see {@link #scale}), idempotent via the same PDC
 * flag {@code CombatListener} already used for its own flat multiplier, so a plugin
 * reload never re-multiplies an already-scaled mob and natural mob turnover is what
 * picks up a changed config, not a retroactive rewrite of everything already alive.
 *
 * <p>Vanilla's own Max Health attribute hard-clamps at 1024 (see {@code RangedAttribute}
 * upstream) regardless of what {@link AttributeInstance#setBaseValue} is given, so a
 * desired total above that can't live in the attribute alone - see {@link #scale}'s own
 * doc for how the excess is tracked separately instead.
 */
public final class MobDifficultyService {
    /** Vanilla's own passive/tameable animals and the handful of defensive-but-not-hostile mobs (Wolf, Bee, Iron Golem, Villager, Snow Golem, Polar Bear) - never scaled, regardless of where they spawn. */
    private static final Set<BestiaryCategory> EXCLUDED_CATEGORIES = EnumSet.of(BestiaryCategory.ANIMALS, BestiaryCategory.NEUTRAL);
    /** {@link BestiaryCategory#AQUATIC} lumps hostile Drowned/Guardian/Elder Guardian together with harmless fish/Turtle/Dolphin/Axolotl - these are the harmless ones, excluded the same way {@link #EXCLUDED_CATEGORIES} excludes Animals/Neutral outright. */
    private static final Set<EntityType> PASSIVE_AQUATIC = EnumSet.of(EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD,
            EntityType.SALMON, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.AXOLOTL);

    private final NamespacedKey scaledKey = new NamespacedKey("foodtooltips", "hp_scaled");
    private final NamespacedKey bonusMaxHpKey = new NamespacedKey("foodtooltips", "mob_bonus_max_hp");
    private final NamespacedKey bonusHpKey = new NamespacedKey("foodtooltips", "mob_bonus_hp");
    private final NamespacedKey dmgMultiplierKey = new NamespacedKey("foodtooltips", "mob_dmg_multiplier");
    private final NamespacedKey overrideMinHealthKey = new NamespacedKey("foodtooltips", "mob_override_min_health");
    private final NamespacedKey overrideMinDamageKey = new NamespacedKey("foodtooltips", "mob_override_min_damage");
    /** Fraction (0-1) of a player's Defense this mob's hits ignore - see {@link #defensePierce}. */
    private final NamespacedKey defensePierceKey = new NamespacedKey("foodtooltips", "mob_defense_pierce");

    private final double flatHealthMultiplier;
    private final double healthPerXp;
    private final double healthPerDepthBlock;
    private final double damagePerXp;
    private final double damagePerDepthBlock;
    private final double realHealthCap;
    private final double depthReferenceYOverworld;
    private final double depthReferenceYNether;
    private final double depthReferenceYEnd;
    private final double minHealthOverworld;
    private final double minHealthNether;
    private final double minHealthEnd;
    /** {@code mob-visuals.end-mobs} - per-type overrides for mobs spawning in The End, see {@link #scale}. */
    private final Map<EntityType, EndMob> endMobs;
    private final double minDamageOverworld;
    private final double minDamageNether;
    private final double minDamageEnd;

    public MobDifficultyService(Plugin p) {
        this.flatHealthMultiplier = Math.max(1.0, p.getConfig().getDouble("mob-visuals.health-multiplier", 5.0));
        this.healthPerXp = p.getConfig().getDouble("mob-visuals.health-multiplier-per-xp", 0.006);
        this.healthPerDepthBlock = p.getConfig().getDouble("mob-visuals.health-multiplier-per-depth-block", 0.03);
        this.damagePerXp = p.getConfig().getDouble("mob-visuals.damage-multiplier-per-xp", 0.0025);
        this.damagePerDepthBlock = p.getConfig().getDouble("mob-visuals.damage-multiplier-per-depth-block", 0.01);
        this.realHealthCap = p.getConfig().getDouble("mob-visuals.real-health-cap", 1000.0);
        this.depthReferenceYOverworld = p.getConfig().getDouble("mob-visuals.depth-reference-y-overworld", 64.0);
        this.depthReferenceYNether = p.getConfig().getDouble("mob-visuals.depth-reference-y-nether", 128.0);
        this.depthReferenceYEnd = p.getConfig().getDouble("mob-visuals.depth-reference-y-end", 64.0);
        this.minHealthOverworld = p.getConfig().getDouble("mob-visuals.min-health-overworld", 0.0);
        this.minHealthNether = p.getConfig().getDouble("mob-visuals.min-health-nether", 4500.0);
        this.minHealthEnd = p.getConfig().getDouble("mob-visuals.min-health-end", 0.0);
        this.endMobs = loadEndMobs(p.getConfig().getConfigurationSection("mob-visuals.end-mobs"));
        this.minDamageOverworld = p.getConfig().getDouble("mob-visuals.min-damage-overworld", 0.0);
        this.minDamageNether = p.getConfig().getDouble("mob-visuals.min-damage-nether", 500.0);
        this.minDamageEnd = p.getConfig().getDouble("mob-visuals.min-damage-end", 0.0);
    }

    /** Whether {@code e} is a real threat this system should touch at all - see {@link #EXCLUDED_CATEGORIES}/{@link #PASSIVE_AQUATIC}. A mob with no Bestiary entry at all (a future/uncatalogued type) defaults to scaling, erring toward "is a threat" rather than silently skipping it. */
    public boolean scales(LivingEntity e) {
        if (e instanceof Player) {
            return false;
        }
        Optional<BestiaryEntry> entry = BestiaryCatalog.find(e.getType());
        if (entry.isEmpty()) {
            return true;
        }
        BestiaryCategory category = entry.get().category();
        if (EXCLUDED_CATEGORIES.contains(category)) {
            return false;
        }
        return category != BestiaryCategory.AQUATIC || !PASSIVE_AQUATIC.contains(e.getType());
    }

    /**
     * Raises {@code e}'s own Max Health/damage floor past whatever {@link #scale} would
     * otherwise give it from tier+depth+the dimension's own floor - for a more specific
     * spawn-time system (e.g. {@code MinerVariantService}) that wants a guaranteed
     * minimum for its own mobs regardless of their Bestiary tier. Must run before {@link
     * #scale} sees this mob (register at a lower event priority - see {@code
     * MinerVariantService}'s own doc on why); a no-op once {@link #scale} already has
     * (same idempotency flag), so calling it late just does nothing rather than
     * retroactively raising an already-scaled mob.
     */
    public void raiseFloor(LivingEntity e, double minHealth, double minDamage) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        if (pdc.has(this.scaledKey, PersistentDataType.BYTE)) {
            return;
        }
        if (minHealth > 0.0) {
            pdc.set(this.overrideMinHealthKey, PersistentDataType.DOUBLE, minHealth);
        }
        if (minDamage > 0.0) {
            pdc.set(this.overrideMinDamageKey, PersistentDataType.DOUBLE, minDamage);
        }
    }

    /**
     * Computes {@code e}'s tier+depth Max Health and outgoing-damage multipliers from
     * where and what it is, and applies the Max Health half immediately. Idempotent via
     * a PDC flag, so safe to call on every spawn unconditionally - already-scaled mobs
     * (or ones {@link #scales} excludes) just no-op.
     *
     * <p>Vanilla's Max Health attribute can't hold the full desired total once it climbs
     * past its own hard-coded 1024 ceiling (a floor like the Nether's 4500 default blows
     * right through that) - the excess above {@code mob-visuals.real-health-cap}
     * (default 1000, a safety margin under 1024) is tracked separately instead, as a
     * {@code mob_bonus_hp} PDC double that a dedicated MONITOR-priority handler on
     * {@code CombatListener} drains before any of it touches the real attribute (see
     * that handler's own doc) - functionally a damage shield, absorbing every damage
     * source the same way, not just melee. {@code MobVisualService}'s own HP label
     * already shows the two added back together, so a viewer never sees the split.
     */
    public void scale(LivingEntity e) {
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        if (pdc.has(this.scaledKey, PersistentDataType.BYTE)) {
            return;
        }
        pdc.set(this.scaledKey, PersistentDataType.BYTE, (byte) 1);
        if (!this.scales(e)) {
            return;
        }
        AttributeInstance attribute = e.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        double vanillaMax = attribute.getValue();
        if (vanillaMax <= 0.0) {
            return;
        }
        int tier = BestiaryCatalog.find(e.getType()).map(BestiaryEntry::combatXp).orElse(0);
        double depth = this.depthBelowReference(e.getLocation());
        double healthMultiplier = this.flatHealthMultiplier + tier * this.healthPerXp + depth * this.healthPerDepthBlock;
        double desiredTotal = Math.max(vanillaMax * healthMultiplier, this.minHealth(e.getWorld()));
        Double overrideMinHealth = pdc.get(this.overrideMinHealthKey, PersistentDataType.DOUBLE);
        if (overrideMinHealth != null) {
            desiredTotal = Math.max(desiredTotal, overrideMinHealth);
        }
        // The End's own per-type overrides (the player's explicit Enderman 750K / Ender Dragon
        // 15M) - far past vanilla's 1024 cap, which is fine: everything above real-health-cap
        // goes to the bonus HP pool below, exactly like the Nether's own 4500 floor already does.
        EndMob endMob = e.getWorld().getEnvironment() == World.Environment.THE_END ? this.endMobs.get(e.getType()) : null;
        if (endMob != null) {
            desiredTotal = Math.max(desiredTotal, endMob.health());
            if (endMob.damage() > 0.0) {
                Double existing = pdc.get(this.overrideMinDamageKey, PersistentDataType.DOUBLE);
                pdc.set(this.overrideMinDamageKey, PersistentDataType.DOUBLE, Math.max(existing == null ? 0.0 : existing, endMob.damage()));
            }
            if (endMob.defensePierce() > 0.0) {
                pdc.set(this.defensePierceKey, PersistentDataType.DOUBLE, endMob.defensePierce());
            }
        }
        double realMax = Math.min(desiredTotal, this.realHealthCap);
        double bonusMax = Math.max(0.0, desiredTotal - realMax);
        attribute.setBaseValue(attribute.getBaseValue() * (realMax / vanillaMax));
        e.setHealth(Math.min(realMax, attribute.getValue()));
        if (bonusMax > 0.0) {
            pdc.set(this.bonusMaxHpKey, PersistentDataType.DOUBLE, bonusMax);
            pdc.set(this.bonusHpKey, PersistentDataType.DOUBLE, bonusMax);
        }
        double damageMultiplier = 1.0 + tier * this.damagePerXp + depth * this.damagePerDepthBlock;
        pdc.set(this.dmgMultiplierKey, PersistentDataType.DOUBLE, damageMultiplier);
    }

    /**
     * {@code mob}'s own outgoing-damage multiplier, computed once at spawn by {@link
     * #scale} - 1.0 (no change) for anything spawned before this system existed, or that
     * {@link #scales} excluded.
     */
    public double damageMultiplier(LivingEntity mob) {
        Double stored = mob.getPersistentDataContainer().get(this.dmgMultiplierKey, PersistentDataType.DOUBLE);
        return stored == null ? 1.0 : stored;
    }

    /** {@code mob}'s own floor - its dimension's own default, or whatever {@link #raiseFloor} set for this specific mob if that's higher. Never allowed to fall below (before the target's own Defense/Protection mitigate it) - 0 means no floor. */
    public double minDamage(LivingEntity mob) {
        double dimensionFloor = this.minDamageForWorld(mob.getWorld());
        Double override = mob.getPersistentDataContainer().get(this.overrideMinDamageKey, PersistentDataType.DOUBLE);
        return override == null ? dimensionFloor : Math.max(dimensionFloor, override);
    }

    private double minDamageForWorld(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> this.minDamageNether;
            case THE_END -> this.minDamageEnd;
            default -> this.minDamageOverworld;
        };
    }

    /** Fraction (0-1) of a player's Defense {@code mob}'s hits ignore - {@code end-mobs.<type>.defense-ignored-percent}, stamped at spawn by {@link #scale}; 0 for everything else. */
    public double defensePierce(LivingEntity mob) {
        Double stored = mob.getPersistentDataContainer().get(this.defensePierceKey, PersistentDataType.DOUBLE);
        return stored == null ? 0.0 : stored;
    }

    /**
     * Drains {@code mob}'s bonus HP pool (see {@link #scale}) by up to {@code damage},
     * returning whatever's left over to actually apply to its real vanilla health -
     * either the full amount (no pool, or already empty) or the remainder once the pool
     * runs dry. Persists the pool's new remaining value back to PDC.
     *
     * <p>A {@link Boss} (Ender Dragon, Wither) splits every hit proportionally instead (see
     * {@link #proportionalShare}): its vanilla boss bar only ever reads the REAL health, so
     * with a pool drained first it would sit frozen at 100% for nearly the whole fight (the
     * Ender Dragon's 15M is 14,999,000 pool over 1000 real) and only start moving at the very
     * end. Splitting keeps real health tracking the true total, so the bar drops smoothly.
     */
    public double absorb(LivingEntity mob, double damage) {
        if (damage <= 0.0) {
            return damage;
        }
        PersistentDataContainer pdc = mob.getPersistentDataContainer();
        Double bonus = pdc.get(this.bonusHpKey, PersistentDataType.DOUBLE);
        if (mob instanceof Boss) {
            Double bonusMax = pdc.get(this.bonusMaxHpKey, PersistentDataType.DOUBLE);
            AttributeInstance attribute = mob.getAttribute(Attribute.MAX_HEALTH);
            if (bonus == null || bonusMax == null || bonusMax <= 0.0 || attribute == null) {
                return damage;
            }
            double[] split = proportionalShare(mob.getHealth(), attribute.getValue(), bonus, bonusMax, damage);
            pdc.set(this.bonusHpKey, PersistentDataType.DOUBLE, split[1]);
            return split[0];
        }
        if (bonus == null || bonus <= 0.0) {
            return damage;
        }
        double absorbed = Math.min(bonus, damage);
        pdc.set(this.bonusHpKey, PersistentDataType.DOUBLE, bonus - absorbed);
        return damage - absorbed;
    }

    /**
     * {@link #absorb}'s Boss split: takes {@code damage} off the true total (real + pool), then
     * re-divides what's left so real health stays the same fraction of its own max as the
     * total is of its own max. Returns {damage to apply to real health, new pool value}.
     */
    static double[] proportionalShare(double realHealth, double realMax, double pool, double poolMax, double damage) {
        double totalMax = realMax + poolMax;
        double newTotal = Math.max(0.0, realHealth + pool - damage);
        double newReal = realMax * newTotal / totalMax;
        double newPool = Math.max(0.0, newTotal - newReal);
        return new double[]{Math.max(0.0, realHealth - newReal), newPool};
    }

    private record EndMob(double health, double damage, double defensePierce) {
    }

    /** {@code mob-visuals.end-mobs} - falls back to the player's own spec (Enderman 750K/3.5K, Ender Dragon 15M/2.2K/70%) when the section is missing, e.g. a server config.yml from before this existed. */
    private static Map<EntityType, EndMob> loadEndMobs(ConfigurationSection section) {
        Map<EntityType, EndMob> mobs = new EnumMap<>(EntityType.class);
        if (section == null) {
            mobs.put(EntityType.ENDERMAN, new EndMob(750000.0, 3500.0, 0.0));
            mobs.put(EntityType.ENDER_DRAGON, new EndMob(15000000.0, 2200.0, 0.70));
            return mobs;
        }
        for (String key : section.getKeys(false)) {
            EntityType type;
            try {
                type = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection mob = section.getConfigurationSection(key);
            if (mob == null) {
                continue;
            }
            double pierce = Math.max(0.0, Math.min(100.0, mob.getDouble("defense-ignored-percent", 0.0))) / 100.0;
            mobs.put(type, new EndMob(Math.max(0.0, mob.getDouble("health", 0.0)), Math.max(0.0, mob.getDouble("damage", 0.0)), pierce));
        }
        return mobs;
    }

    private double minHealth(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> this.minHealthNether;
            case THE_END -> this.minHealthEnd;
            default -> this.minHealthOverworld;
        };
    }

    private double depthBelowReference(Location loc) {
        double referenceY = switch (loc.getWorld().getEnvironment()) {
            case NETHER -> this.depthReferenceYNether;
            case THE_END -> this.depthReferenceYEnd;
            default -> this.depthReferenceYOverworld;
        };
        return Math.max(0.0, referenceY - loc.getY());
    }
}
