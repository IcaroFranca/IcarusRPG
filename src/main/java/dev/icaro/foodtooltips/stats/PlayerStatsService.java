package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import dev.icaro.foodtooltips.reforge.ReforgeService;
import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Tracks every non-vanilla combat stat. Base values come from config; the
 * combat ability tree layers additional bonuses on top for every stat here
 * except {@link PlayerStats#trueDefense()} (deliberately tree-independent —
 * see {@link CombatAbilityService}'s class doc for which ability grants
 * which bonus). Intelligence also gets {@link GeneralSkillService#bonusIntelligence}
 * (Alchemy/Enchanting, 1 per level) layered on top, which in turn feeds
 * Max Mana one-for-one — see {@link #effectiveMaxMana(Player)}. Agility is the same
 * kind of pairing for movement Speed: {@link #effectiveAgility(Player)} (base plus
 * {@link LegendaryWeaponService#heldAgilityBonus}, e.g. Baruka's Dagger) is the number
 * shown on the stats screen, and separately feeds the real vanilla Movement Speed
 * attribute one-for-one — as a percentage point per point of Agility, see {@code
 * LegendaryWeaponService#create}'s own attribute modifier on the item, since unlike
 * Mana (a fully custom resource) Speed has to end up on the real attribute for the
 * player to actually move faster. {@link #abilities(CombatAbilityService)},
 * {@link #general(GeneralSkillService)} and {@link #legendary(LegendaryWeaponService)}
 * are wired in after construction (these services depend on each other) exactly like
 * {@link #global(GlobalLevelService)} already is.
 */
public final class PlayerStatsService {
    private final NamespacedKey mana;
    private final NamespacedKey maxMana;
    private final NamespacedKey vitality;
    private final NamespacedKey maxVitality;
    private final NamespacedKey swingRangeKey;
    private final double base;
    private final double baseHealth;
    private final double baseVitality;
    private final double ferocity;
    private final double ferocityCap;
    private final double swingRange;
    private final double swingRangeCap;
    private final double intelligence;
    private final double agility;
    private final double abilityDamage;
    private final double healthRegen;
    private final double mending;
    private final double trueDefense;
    private GlobalLevelService global;
    private CombatAbilityService abilities;
    private GeneralSkillService general;
    private LegendaryWeaponService legendary;
    private ReforgeService reforge;
    private AccessoryBagService accessoryBag;
    /** The Rotten Flesh Collection's own Zombie Sword - +50 Strength while it's the held main-hand weapon (see {@code item.ZombieSwordService#heldStrengthBonus}), same late-bound idea as {@link #legendary}'s own {@code heldAgilityBonus} for Baruka's Dagger. */
    private java.util.function.ToIntFunction<Player> heldWeaponStrengthBonus = p -> 0;
    /** The Rotten Flesh Collection's own Zombie Sword - +50 Intelligence while held (see {@code item.ZombieSwordService#heldIntelligenceBonus}), folded into {@link #effectiveIntelligence} alongside {@link #skeletonHatIntelligenceBonus}. */
    private java.util.function.ToIntFunction<Player> heldWeaponIntelligenceBonus = p -> 0;
    /** The Bone Collection's own Skeleton Hat - +10 Intelligence while worn as a helmet (see {@code item.SkeletonHatService#intelligenceBonus}), same late-bound shape as {@link #heldWeaponIntelligenceBonus}. */
    private java.util.function.ToIntFunction<Player> skeletonHatIntelligenceBonus = p -> 0;
    /** The Bone Collection's own Skeleton Hat - +2 Speed while worn as a helmet (see {@code item.SkeletonHatService#speedBonus}), folded into {@link #effectiveAgility}. */
    private java.util.function.ToIntFunction<Player> skeletonHatSpeedBonus = p -> 0;
    /** The new Magical Power/Powers system's own Strength bonus (see {@code power.MagicalPowerService#strengthBonus}), folded into {@link #stats}'s own {@code globalStrength} line alongside {@link #heldWeaponStrengthBonus}. A {@code ToDoubleFunction}, not {@code ToIntFunction} like every sibling hook here, since a Power's bonus is a live fraction (base stat x the formula's Stats Multiplier) rather than a flat per-item amount - rounded only where it's actually added to a whole-number stat. */
    private java.util.function.ToDoubleFunction<Player> accessoryStrengthBonus = p -> 0.0;
    /** The new Magical Power/Powers system's own Intelligence bonus (see {@code power.MagicalPowerService#intelligenceBonus}), folded into {@link #effectiveIntelligence} alongside {@link #heldWeaponIntelligenceBonus}/{@link #skeletonHatIntelligenceBonus} - same fractional reasoning as {@link #accessoryStrengthBonus}. */
    private java.util.function.ToDoubleFunction<Player> accessoryIntelligenceBonus = p -> 0.0;

    private static boolean attributeResolved;
    private static Attribute entityInteractionRangeAttribute;

    public PlayerStatsService(Plugin p) {
        this.base = p.getConfig().getDouble("stats.base-mana", 100.0);
        this.baseHealth = Math.max(1.0, p.getConfig().getDouble("stats.base-health", 100.0));
        this.baseVitality = p.getConfig().getDouble("stats.base-vitality", 100.0);
        this.ferocityCap = p.getConfig().getDouble("stats.ferocity-cap", 500.0);
        this.ferocity = clamp(p.getConfig().getDouble("stats.base-ferocity", 0.0), 0.0, this.ferocityCap);
        this.swingRangeCap = p.getConfig().getDouble("stats.swing-range-cap", 15.0);
        this.swingRange = clamp(p.getConfig().getDouble("stats.base-swing-range", 3.0), 0.0, this.swingRangeCap);
        this.intelligence = Math.max(0.0, p.getConfig().getDouble("stats.base-intelligence", 0.0));
        this.agility = Math.max(0.0, p.getConfig().getDouble("stats.base-agility", 0.0));
        this.abilityDamage = Math.max(0.0, p.getConfig().getDouble("stats.base-ability-damage", 0.0));
        this.healthRegen = Math.max(0.0, p.getConfig().getDouble("stats.base-health-regen", 100.0));
        this.mending = Math.max(0.0, p.getConfig().getDouble("stats.base-mending", 100.0));
        this.trueDefense = Math.max(0.0, p.getConfig().getDouble("stats.base-true-defense", 0.0));
        this.mana = new NamespacedKey("foodtooltips", "stat_mana");
        this.maxMana = new NamespacedKey("foodtooltips", "stat_max_mana");
        this.vitality = new NamespacedKey("foodtooltips", "stat_vitality");
        this.maxVitality = new NamespacedKey("foodtooltips", "stat_max_vitality");
        this.swingRangeKey = new NamespacedKey("foodtooltips", "stat_swing_range");
    }

    public void global(GlobalLevelService global) {
        this.global = global;
    }

    public void abilities(CombatAbilityService abilities) {
        this.abilities = abilities;
    }

    public void general(GeneralSkillService general) {
        this.general = general;
    }

    public void legendary(LegendaryWeaponService legendary) {
        this.legendary = legendary;
    }

    public void reforge(ReforgeService reforge) {
        this.reforge = reforge;
    }

    public void accessoryBag(AccessoryBagService accessoryBag) {
        this.accessoryBag = accessoryBag;
    }

    /** Wired in after construction - see {@link #heldWeaponStrengthBonus}. */
    public void heldWeaponStrengthBonus(java.util.function.ToIntFunction<Player> heldWeaponStrengthBonus) {
        this.heldWeaponStrengthBonus = heldWeaponStrengthBonus;
    }

    /** Wired in after construction - see {@link #heldWeaponIntelligenceBonus}. */
    public void heldWeaponIntelligenceBonus(java.util.function.ToIntFunction<Player> heldWeaponIntelligenceBonus) {
        this.heldWeaponIntelligenceBonus = heldWeaponIntelligenceBonus;
    }

    /** Wired in after construction - see {@link #skeletonHatIntelligenceBonus}. */
    public void skeletonHatIntelligenceBonus(java.util.function.ToIntFunction<Player> skeletonHatIntelligenceBonus) {
        this.skeletonHatIntelligenceBonus = skeletonHatIntelligenceBonus;
    }

    /** Wired in after construction - see {@link #accessoryStrengthBonus}. */
    public void accessoryStrengthBonus(java.util.function.ToDoubleFunction<Player> accessoryStrengthBonus) {
        this.accessoryStrengthBonus = accessoryStrengthBonus;
    }

    /** Wired in after construction - see {@link #accessoryIntelligenceBonus}. */
    public void accessoryIntelligenceBonus(java.util.function.ToDoubleFunction<Player> accessoryIntelligenceBonus) {
        this.accessoryIntelligenceBonus = accessoryIntelligenceBonus;
    }

    /** Wired in after construction - see {@link #skeletonHatSpeedBonus}. */
    public void skeletonHatSpeedBonus(java.util.function.ToIntFunction<Player> skeletonHatSpeedBonus) {
        this.skeletonHatSpeedBonus = skeletonHatSpeedBonus;
    }

    // ---- Base config values (for the Combat Stats breakdown - see SkillsMenuService#combatStatsItem) ----

    public double baseHealth() {
        return this.baseHealth;
    }

    public double baseVitality() {
        return this.baseVitality;
    }

    public double baseFerocity() {
        return this.ferocity;
    }

    public double baseSwingRange() {
        return this.swingRange;
    }

    public double baseIntelligence() {
        return this.intelligence;
    }

    public double baseAgility() {
        return this.agility;
    }

    public double baseAbilityDamage() {
        return this.abilityDamage;
    }

    public double baseHealthRegen() {
        return this.healthRegen;
    }

    public double baseMending() {
        return this.mending;
    }

    public double baseTrueDefense() {
        return this.trueDefense;
    }

    /** Base Max Mana plus effective Intelligence (base plus {@link GeneralSkillService#bonusIntelligence}, Alchemy/Enchanting, 1 per level). */
    private double effectiveMaxMana(Player p) {
        return this.get(p, this.maxMana, this.base) + this.effectiveIntelligence(p);
    }

    /** Base Intelligence plus Alchemy/Enchanting's per-level bonus, plus whatever the player's currently-held weapon and equipped armor reforges grant (see {@link ReforgeService}), plus the Bone Collection's own Skeleton Hat bonus while worn ({@link #skeletonHatIntelligenceBonus}) - same "held/worn item bonus" pairing {@link #effectiveAgility} uses for Agility. */
    private double effectiveIntelligence(Player p) {
        double reforgeBonus = this.reforge == null ? 0.0
                : this.reforge.statsOf(p.getInventory().getItemInMainHand()).intelligence()
                        + this.reforge.bowStatsOf(p.getInventory().getItemInMainHand()).intelligence()
                        + this.reforge.totalArmorStats(p).intelligence();
        double skeletonHatBonus = this.skeletonHatIntelligenceBonus.applyAsInt(p);
        double weaponBonus = this.heldWeaponIntelligenceBonus.applyAsInt(p);
        double accessoryBonus = this.accessoryIntelligenceBonus.applyAsDouble(p);
        return this.intelligence + (this.general == null ? 0 : this.general.bonusIntelligence(p)) + reforgeBonus + skeletonHatBonus + weaponBonus + accessoryBonus;
    }

    /** Base Agility plus whatever the player's currently-held weapon (e.g. Baruka's Dagger, +10 while wielded) and equipped armor reforges (see {@link ReforgeService}) grant, plus the Bone Collection's own Skeleton Hat bonus while worn ({@link #skeletonHatSpeedBonus} - {@code item.SkeletonHatService#applySpeedAttribute} separately turns this same number into the real Movement Speed attribute) - the number shown on the stats screen, paired with movement Speed the same way Intelligence is paired with Max Mana. */
    public double effectiveAgility(Player p) {
        double reforgeArmorAgility = this.reforge == null ? 0.0 : this.reforge.totalArmorStats(p).agility();
        double skeletonHatBonus = this.skeletonHatSpeedBonus.applyAsInt(p);
        return this.agility + (this.legendary == null ? 0 : this.legendary.heldAgilityBonus(p)) + reforgeArmorAgility + skeletonHatBonus;
    }

    public void init(Player p) {
        PersistentDataContainer d = p.getPersistentDataContainer();
        if (!d.has(this.maxMana, PersistentDataType.DOUBLE)) {
            d.set(this.maxMana, PersistentDataType.DOUBLE, this.base);
        }
        if (!d.has(this.mana, PersistentDataType.DOUBLE)) {
            d.set(this.mana, PersistentDataType.DOUBLE, this.base);
        }
        if (!d.has(this.maxVitality, PersistentDataType.DOUBLE)) {
            d.set(this.maxVitality, PersistentDataType.DOUBLE, this.baseVitality);
        }
        if (!d.has(this.vitality, PersistentDataType.DOUBLE)) {
            d.set(this.vitality, PersistentDataType.DOUBLE, this.baseVitality);
        }
    }

    private double get(Player p, NamespacedKey k, double fallback) {
        this.init(p);
        return p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.DOUBLE, fallback);
    }

    public PlayerStats stats(Player p) {
        double effectiveMaxMana = this.effectiveMaxMana(p);
        double storedMana = this.get(p, this.mana, effectiveMaxMana);
        double accessoryVitalityBonus = this.accessoryBag == null ? 0.0 : this.accessoryBag.totalVitalityBonus(p);
        double effectiveMaxVitality = this.get(p, this.maxVitality, this.baseVitality) + accessoryVitalityBonus;
        double storedVitality = this.get(p, this.vitality, effectiveMaxVitality);
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        long globalStrength = (this.global == null ? 0L : this.global.snapshot(p).strength()) + this.heldWeaponStrengthBonus.applyAsInt(p)
                + Math.round(this.accessoryStrengthBonus.applyAsDouble(p));

        double swingRangeBonus = this.abilities == null ? 0.0 : this.abilities.swingRangeBonus(p);
        double healthRegenBonus = this.abilities == null ? 0.0 : this.abilities.healthRegenBonus(p);
        double mendingBonus = this.abilities == null ? 0.0 : this.abilities.mendingBonus(p);
        double accessoryMendingBonus = this.accessoryBag == null ? 0.0 : this.accessoryBag.totalMendingBonus(p);

        return new PlayerStats(
                p.getHealth(),
                a == null ? 20.0 : a.getValue(),
                Math.min(effectiveMaxMana, storedMana),
                effectiveMaxMana,
                globalStrength,
                clamp(this.ferocity, 0.0, this.ferocityCap),
                clamp(this.swingRange + swingRangeBonus, 0.0, this.swingRangeCap),
                this.effectiveIntelligence(p),
                this.abilityDamage,
                this.healthRegen + healthRegenBonus,
                Math.min(effectiveMaxVitality, storedVitality),
                effectiveMaxVitality,
                this.mending + mendingBonus + accessoryMendingBonus,
                this.trueDefense);
    }

    public void regen(Player p, double n) {
        this.setMana(p, this.stats(p).mana() + n);
    }

    public void regenVitality(Player p, double n) {
        this.setVitality(p, this.stats(p).vitality() + n);
    }

    public void regenHealth(Player p, double amount) {
        if (amount <= 0.0 || p.isDead()) {
            return;
        }
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? 20.0 : a.getValue();
        if (p.getHealth() < max) {
            p.setHealth(Math.min(max, p.getHealth() + amount));
        }
    }

    public void setMana(Player p, double n) {
        p.getPersistentDataContainer().set(this.mana, PersistentDataType.DOUBLE, clamp(n, 0.0, this.effectiveMaxMana(p)));
    }

    public void setMaxMana(Player p, double n) {
        p.getPersistentDataContainer().set(this.maxMana, PersistentDataType.DOUBLE, Math.max(1.0, n));
        this.setMana(p, this.get(p, this.mana, n));
    }

    public boolean withdrawMana(Player p, double amount) {
        if (amount < 0.0) {
            return false;
        }
        double current = this.stats(p).mana();
        if (current < amount) {
            return false;
        }
        this.setMana(p, current - amount);
        return true;
    }

    public void setVitality(Player p, double n) {
        double m = this.get(p, this.maxVitality, this.baseVitality);
        p.getPersistentDataContainer().set(this.vitality, PersistentDataType.DOUBLE, clamp(n, 0.0, m));
    }

    public boolean withdrawVitality(Player p, double amount) {
        if (amount < 0.0) {
            return false;
        }
        double current = this.stats(p).vitality();
        if (current < amount) {
            return false;
        }
        this.setVitality(p, current - amount);
        return true;
    }

    /**
     * Sets the player's base Max Health (vanilla default is 20; this plugin's default
     * is {@code stats.base-health}, 100). Bonuses on top (Bestiary milestones, Global
     * Level's HP-per-level) are transient modifiers added separately, so they compose
     * correctly on top of whichever base is set here regardless of call order.
     */
    public void applyBaseHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) {
            a.setBaseValue(this.baseHealth);
        }
    }

    /**
     * Applies the Swing Range stat to the vanilla melee/interaction-range
     * attribute, if this server version exposes it under the registry key
     * this method knows about. No-ops silently otherwise so an older/newer
     * server never fails to start over a cosmetic stat.
     */
    public void applySwingRange(Player p) {
        Attribute attribute = resolveEntityInteractionRangeAttribute();
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = p.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier old = instance.getModifier(Key.key(this.swingRangeKey.getNamespace(), this.swingRangeKey.getKey()));
        if (old != null) {
            instance.removeModifier(old);
        }
        double delta = this.stats(p).swingRange() - instance.getBaseValue();
        if (Math.abs(delta) > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(this.swingRangeKey, delta, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /**
     * The vanilla melee/interaction-range attribute this server version exposes under
     * the registry key {@link #applySwingRange} knows about, or null if none resolve -
     * exposed (not private) so other per-item Swing Range sources (see {@code
     * LegendaryWeaponService}'s -1 dagger penalty) can attach their own {@link
     * EquipmentSlotGroup#MAINHAND}-scoped modifier to the very same attribute without
     * duplicating this resolution logic.
     */
    public static Attribute resolveEntityInteractionRangeAttribute() {
        if (attributeResolved) {
            return entityInteractionRangeAttribute;
        }
        attributeResolved = true;
        for (String key : new String[]{"player.entity_interaction_range", "entity_interaction_range"}) {
            try {
                Attribute found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (found != null) {
                    entityInteractionRangeAttribute = found;
                    break;
                }
            } catch (RuntimeException ignored) {
                // Try the next candidate key; give up gracefully if none resolve.
            }
        }
        return entityInteractionRangeAttribute;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
