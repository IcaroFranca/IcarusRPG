package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Gunpowder Collection M8 - a {@link ItemTier#B} helmet (a green-dyed Leather leggings, same
 * "dyed leather stands in" trick {@code item.CreeperHatService} already uses), crafted with the
 * standard vanilla Leggings shape but {@code CombatCollectionsItemsService}'s own Gunpowder Core
 * in place of the usual material. Per the player's own explicit spec: Health +{@value #HEALTH}
 * (a real {@link Attribute#MAX_HEALTH} modifier baked into the item, scoped to {@link
 * EquipmentSlotGroup#LEGS} - same shape {@code item.ZombieArmorService} already uses) and
 * Defense +{@value #DEFENSE} (see {@link ArmorDefenseService#forceDefense}).
 *
 * <p>Ability: Detonate - the moment a hit would drop the wearer below {@value
 * #TRIGGER_HEALTH_PERCENT}% of their own Max Health, {@value #DAMAGE} damage and a strong
 * knockback hits every real hostile mob (the same {@link Enemy}-plus-Zombified-Piglin-exception
 * classification {@code enchant.BowEnchantEffectListener#nearestEnemy} already uses) within
 * {@value #RADIUS} blocks, on a {@value #COOLDOWN_SECONDS}s cooldown - same {@code MONITOR},
 * read-the-final-damage-only shape {@code item.ZombieArmorService#projectileHit} already uses
 * (a pure side effect, unlike {@code combat.CombatListener#secondWind}'s own full nullification -
 * the wearer still takes the hit that dropped them below the threshold).
 */
public final class CreeperPantsService implements Listener {
    private static final NamespacedKey KEY = new NamespacedKey("foodtooltips", "creeper_pants");
    private static final NamespacedKey HEALTH_KEY = new NamespacedKey("foodtooltips", "creeper_pants_health");
    public static final int HEALTH = 200;
    public static final int DEFENSE = 65;
    public static final double TRIGGER_HEALTH_PERCENT = 20.0;
    public static final double DAMAGE = 150.0;
    public static final double RADIUS = 6.0;
    public static final double KNOCKBACK_STRENGTH = 1.4;
    public static final int COOLDOWN_SECONDS = 60;
    private static final long COOLDOWN_MILLIS = COOLDOWN_SECONDS * 1000L;

    private final ItemTierService tiers;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public CreeperPantsService(ItemTierService tiers, CombatAbilityService abilities) {
        this.tiers = tiers;
        this.abilities = abilities;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(Color.GREEN);
        }
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        this.tiers.forceTier(meta, ItemTier.B);
        ArmorDefenseService.forceDefense(meta, DEFENSE);
        ArmorDefenseService.markOwnDefenseLore(meta);
        meta.addAttributeModifier(Attribute.MAX_HEALTH,
                new AttributeModifier(HEALTH_KEY, HEALTH, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS));
        meta.displayName(Component.text("Creeper Pants", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Health: +" + HEALTH, NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                Component.text("Defense: +" + DEFENSE, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                Component.empty().decoration(TextDecoration.ITALIC, false),
                Component.text("Ability: Detonate", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                Component.text("Causes an explosion when dropping", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("below " + (int) TRIGGER_HEALTH_PERCENT + "% HP, damaging and", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("knocking back all monsters around you.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Cooldown: " + COOLDOWN_SECONDS + "s", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isCreeperPants(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void detonate(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isCreeperPants(p.getInventory().getLeggings())) {
            return;
        }
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr == null ? 20.0 : maxHealthAttr.getValue();
        double remaining = p.getHealth() - e.getFinalDamage();
        if (remaining / maxHealth >= TRIGGER_HEALTH_PERCENT / 100.0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.cooldowns.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        this.cooldowns.put(p.getUniqueId(), now + COOLDOWN_MILLIS);
        Location center = p.getLocation();
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.9f);
        for (Entity nearby : p.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
            if (!(nearby instanceof Enemy enemy) || !enemy.isValid() || enemy.isDead() || enemy.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                continue;
            }
            this.abilities.dealAbilityDamage(p, enemy, DAMAGE);
            Vector direction = enemy.getLocation().toVector().subtract(center.toVector());
            direction.setY(0.0);
            direction = direction.lengthSquared() < 1.0E-4 ? new Vector(1.0, 0.0, 0.0) : direction.normalize();
            Vector push = direction.multiply(KNOCKBACK_STRENGTH).setY(0.4);
            enemy.setVelocity(enemy.getVelocity().add(push));
        }
    }
}
