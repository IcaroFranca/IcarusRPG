package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gunpowder Collection M9 - a plain {@link Material#BOW}, {@link ItemTier#A}. Per the player's
 * own explicit spec: Damage +{@value #DAMAGE}, Strength +{@value #STRENGTH} - summed into one
 * flat {@value #TOTAL_DAMAGE} base arrow damage at shoot time (see {@link #bowShoot}), same
 * "real arrow damage override, everything else in {@code combat.CombatListener}'s own ranged
 * formula still applies on top" trick {@code item.HurricaneBowService} already uses for its own
 * Damage+Strength stat - and Ability: Explosive Shot, which, on impact, deals the exact final
 * damage that hit already dealt (after every multiplier - combat level, crit, enchants...) to
 * every other living entity within {@value #RADIUS} blocks too, per the player's own explicit
 * "every Monster caught in this explosion takes the full damage of the weapon".
 *
 * <p>{@link #explode} reads {@link EntityDamageByEntityEvent#getFinalDamage()} at {@link
 * EventPriority#MONITOR} (after {@code combat.CombatListener}'s own formula has already run) -
 * same "judge the real, fully-resolved number" idea {@code item.ZombieArmorService#projectileHit}
 * already uses - rather than a separate flat ability number, so a stronger wielder's Explosive
 * Shot is also a stronger explosion. The AoE damage itself is dealt via {@link
 * CombatAbilityService#dealAbilityDamage} (a direct {@code target.damage()} call), which doesn't
 * fire another {@link EntityDamageByEntityEvent} with an {@link AbstractArrow} damager - so it
 * can never re-trigger this same handler on its own splash damage.
 */
public final class ExplosiveBowService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "explosive_bow");
    private static final NamespacedKey ARROW_KEY = new NamespacedKey("foodtooltips", "explosive_bow_arrow");
    public static final int DAMAGE = 100;
    public static final int STRENGTH = 20;
    public static final double TOTAL_DAMAGE = DAMAGE + STRENGTH;
    public static final double RADIUS = 4.0;

    private final ItemTierService tiers;
    private final CombatAbilityService abilities;

    public ExplosiveBowService(ItemTierService tiers, CombatAbilityService abilities) {
        this.tiers = tiers;
        this.abilities = abilities;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.A);
        meta.displayName(Component.text("Explosive Bow").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Damage: +" + DAMAGE, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Strength: +" + STRENGTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Explosive Shot", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Creates an explosion on impact!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Every Monster caught in this", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("explosion takes the full damage", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("of the weapon!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isExplosiveBow(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void bowShoot(EntityShootBowEvent e) {
        if (!isExplosiveBow(e.getBow()) || !(e.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        arrow.getPersistentDataContainer().set(ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
        arrow.setDamage(TOTAL_DAMAGE);
        arrow.setCritical(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void explode(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof AbstractArrow arrow) || !arrow.getPersistentDataContainer().has(ARROW_KEY, PersistentDataType.BYTE)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity target) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        double damage = e.getFinalDamage();
        Location center = target.getLocation();
        World world = center.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION, center, 1);
            world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.1f);
        }
        for (Entity nearby : target.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (nearby instanceof LivingEntity other && other != target && other != shooter) {
                this.abilities.dealAbilityDamage(shooter, other, damage);
            }
        }
    }
}
