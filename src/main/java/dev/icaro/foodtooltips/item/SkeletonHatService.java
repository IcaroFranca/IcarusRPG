package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Bone Collection M4 - a real {@link ItemTier#D} helmet (a plain {@link Material#SKELETON_SKULL},
 * worn like {@code item.ZombieHatService}'s own Zombie Hat, not stored in the Accessory Bag):
 * +{@value #INTELLIGENCE_BONUS} Intelligence, +{@value #SPEED_BONUS} Speed while worn (read by
 * {@code stats.PlayerStatsService} via its own late-bound {@code skeletonHatIntelligenceBonus}/
 * {@code skeletonHatSpeedBonus} fields, same shape {@code item.ZombieSwordService}'s held-weapon
 * bonuses use), plus a passive {@value #EXPLOSION_CHANCE_PERCENT}% chance for any arrow the
 * wearer shoots (any bow - not tied to Hurricane/Runaan's specifically) to explode on impact for
 * {@value #EXPLOSION_DAMAGE} base damage to everything within {@value #EXPLOSION_RADIUS} blocks -
 * cosmetic particles/sound only, never touching blocks, per the player's own explicit "essa
 * explosão não quebra blocos" spec, hence {@link CombatAbilityService#dealAbilityDamage} (a real
 * {@code target.damage(amount, p)} call) rather than {@link org.bukkit.World#createExplosion}
 * (which would need {@code breakBlocks=false} fought against vanilla's own power-based damage
 * falloff instead of a clean flat number).
 *
 * <p>Unlike Intelligence (folded into the custom Max Mana stat purely on demand), Speed has to
 * end up on the real vanilla Movement Speed attribute for the wearer to actually move faster -
 * {@link #applySpeedAttribute} does that with the same idempotent "remove old modifier, reapply
 * if still earned" pattern {@code item.SpeedsterArmorService#applyFullSetSpeed} already uses,
 * called from the same periodic per-player sweep in {@code FoodTooltipsPlugin}.
 *
 * <p>No real custom head texture has been sent for this one yet (unlike every other accessory
 * in this plugin) - {@link Material#SKELETON_SKULL} stands in until one is.
 */
public final class SkeletonHatService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "skeleton_hat");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("foodtooltips", "skeleton_hat_speed");
    public static final int INTELLIGENCE_BONUS = 10;
    public static final int SPEED_BONUS = 2;
    /** Same "Speed point -> real Movement Speed" conversion every other Speed source in this plugin uses - see {@code item.SpeedsterArmorService#SPEED_POINT_TO_ATTRIBUTE}'s own doc. */
    private static final double SPEED_POINT_TO_ATTRIBUTE = 0.001;
    private static final double EXPLOSION_CHANCE = 0.20;
    private static final int EXPLOSION_CHANCE_PERCENT = 20;
    private static final double EXPLOSION_DAMAGE = 50.0;
    private static final double EXPLOSION_RADIUS = 3.0;

    private final ItemTierService tiers;
    private final CombatAbilityService abilities;

    public SkeletonHatService(ItemTierService tiers, CombatAbilityService abilities) {
        this.tiers = tiers;
        this.abilities = abilities;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.D);
        meta.displayName(Component.text("Skeleton Hat", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Intelligence: +" + INTELLIGENCE_BONUS, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                Component.text("Speed: +" + SPEED_BONUS, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text(EXPLOSION_CHANCE_PERCENT + "% chance for your arrows to explode", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("on impact for " + (int) EXPLOSION_DAMAGE + " damage.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSkeletonHat(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** {@value #INTELLIGENCE_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - wired into {@code stats.PlayerStatsService#skeletonHatIntelligenceBonus}. */
    public int intelligenceBonus(Player p) {
        return isSkeletonHat(p.getInventory().getHelmet()) ? INTELLIGENCE_BONUS : 0;
    }

    /** {@value #SPEED_BONUS} while {@code p} wears this exact helmet, or 0 otherwise - the number shown on the Stats screen (wired into {@code stats.PlayerStatsService#skeletonHatSpeedBonus}); {@link #applySpeedAttribute} separately turns this same number into the real Movement Speed attribute. */
    public int speedBonus(Player p) {
        return isSkeletonHat(p.getInventory().getHelmet()) ? SPEED_BONUS : 0;
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
        int bonus = this.speedBonus(p);
        if (bonus > 0) {
            speed.addTransientModifier(new AttributeModifier(SPEED_KEY, bonus * SPEED_POINT_TO_ATTRIBUTE, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void arrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!isSkeletonHat(shooter.getInventory().getHelmet()) || ThreadLocalRandom.current().nextDouble() >= EXPLOSION_CHANCE) {
            return;
        }
        Location loc = arrow.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, loc, 1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        for (Entity nearby : world.getNearbyEntities(loc, EXPLOSION_RADIUS, EXPLOSION_RADIUS, EXPLOSION_RADIUS)) {
            if (nearby instanceof LivingEntity target && target != shooter) {
                this.abilities.dealAbilityDamage(shooter, target, EXPLOSION_DAMAGE);
            }
        }
    }
}
