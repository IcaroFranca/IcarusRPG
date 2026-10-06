package dev.icaro.foodtooltips.power;

import dev.icaro.foodtooltips.skills.AccessoryBagService;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Magical Power (the sum of every equipped accessory's {@link ItemTierService}-tier points - see
 * {@link AccessoryBagService#totalMagicalPower}) and Powers (named presets of BASE stat bonuses,
 * {@link PowerCatalog}) together: {@link #statsMultiplier(double)} is the player's own exact
 * "Stats Multiplier" formula, and each {@code xBonus(Player)} method below is the selected {@link
 * Power}'s base value for that stat times the player's own live multiplier - never a fixed
 * amount, recomputed every time since Magical Power itself moves as accessories are swapped.
 * One Power selected at a time, persisted via PDC, same shape as {@code
 * global.LevelColorService} (no unlock gate for now - the player's own spec only asked for a
 * pick-one-of-10 menu, not a progression gate between Starter and Intermediate).
 *
 * <p>Health and Speed are real vanilla attributes (own dedicated {@link NamespacedKey}s so they
 * stack independently of every other Health/Speed source) applied via the same idempotent
 * "remove old modifier, reapply if still earned" pattern {@code
 * AccessoryBagService#applyAccessoryHealth}/{@code item.SkeletonHatService#applySpeedAttribute}
 * already use, called from the same periodic per-player sweep in {@code FoodTooltipsPlugin}.
 * Defense/Strength/Intelligence/Crit Chance/Crit Damage/Mining Speed instead plug into their own
 * existing late-bound hook fields in {@code skills.ArmorDefenseService}/{@code
 * stats.PlayerStatsService}/{@code combat.CombatListener}/{@code skills.GeneralSkillService} -
 * see {@code FoodTooltipsPlugin}'s own wiring for exactly where each one lands.
 */
public final class MagicalPowerService {
    private static final NamespacedKey SELECTED_KEY = new NamespacedKey("foodtooltips", "selected_power");
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("foodtooltips", "power_health_bonus_attribute");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "power_speed_bonus_attribute");
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses - see {@code item.SkeletonHatService#SPEED_POINT_TO_ATTRIBUTE}'s own doc. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;

    private final AccessoryBagService accessoryBag;

    public MagicalPowerService(AccessoryBagService accessoryBag) {
        this.accessoryBag = accessoryBag;
    }

    /** {@code Stats Multiplier = 29.97 x (ln(0.0019 x Magical Power + 1))^1.2} - the exact formula from the player's own reference image, applied to every Power's base stat values below. */
    public static double statsMultiplier(double magicalPower) {
        return 29.97 * Math.pow(Math.log(0.0019 * magicalPower + 1.0), 1.2);
    }

    public double multiplier(Player p) {
        return statsMultiplier(this.accessoryBag.totalMagicalPower(p));
    }

    public Power selected(Player p) {
        String id = p.getPersistentDataContainer().getOrDefault(SELECTED_KEY, PersistentDataType.STRING, PowerCatalog.defaultPower().id());
        return PowerCatalog.find(id).orElse(PowerCatalog.defaultPower());
    }

    public void select(Player p, Power power) {
        p.getPersistentDataContainer().set(SELECTED_KEY, PersistentDataType.STRING, power.id());
    }

    public double healthBonus(Player p) {
        return this.selected(p).health() * this.multiplier(p);
    }

    public double defenseBonus(Player p) {
        return this.selected(p).defense() * this.multiplier(p);
    }

    public double strengthBonus(Player p) {
        return this.selected(p).strength() * this.multiplier(p);
    }

    public double speedBonus(Player p) {
        return this.selected(p).speed() * this.multiplier(p);
    }

    public double critChanceBonus(Player p) {
        return this.selected(p).critChance() * this.multiplier(p);
    }

    public double critDamageBonus(Player p) {
        return this.selected(p).critDamage() * this.multiplier(p);
    }

    public double intelligenceBonus(Player p) {
        return this.selected(p).intelligence() * this.multiplier(p);
    }

    public double miningSpeedBonus(Player p) {
        return this.selected(p).miningSpeed() * this.multiplier(p);
    }

    /** Converts {@link #healthBonus} into the real vanilla Max Health attribute - see this class's own doc. */
    public void applyHealthAttribute(Player p) {
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) {
            return;
        }
        AttributeModifier old = health.getModifier(Key.key(HEALTH_KEY.getNamespace(), HEALTH_KEY.getKey()));
        if (old != null) {
            health.removeModifier(old);
        }
        double bonus = this.healthBonus(p);
        if (bonus > 0.0) {
            health.addTransientModifier(new AttributeModifier(HEALTH_KEY, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    /** Converts {@link #speedBonus} into the real vanilla Movement Speed attribute - see this class's own doc. */
    public void applySpeedAttribute(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier old = speed.getModifier(Key.key(SPEED_KEY.getNamespace(), SPEED_KEY.getKey()));
        if (old != null) {
            speed.removeModifier(old);
        }
        double bonus = this.speedBonus(p);
        if (bonus > 0.0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_KEY, bonus * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
