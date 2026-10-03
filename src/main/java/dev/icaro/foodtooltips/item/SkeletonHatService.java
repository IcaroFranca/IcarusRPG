package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.skills.AccessoryBagService;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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

/**
 * Bone Collection M4 - a standalone (no Talisman/Ring/Artifact line of its own) Accessory Bag
 * charm: +{@value #INTELLIGENCE_BONUS} Intelligence, +{@value #SPEED_BONUS} Speed (see {@link
 * AccessoryItems#markIntelligenceAndSpeed}, summed via {@link
 * AccessoryBagService#totalIntelligenceBonus}/{@link AccessoryBagService#totalSpeedBonus}),
 * plus a passive {@value #EXPLOSION_CHANCE_PERCENT}% chance for any arrow the wearer shoots
 * (any bow - not tied to Hurricane/Runaan's specifically) to explode on impact for {@value
 * #EXPLOSION_DAMAGE} base damage to everything within {@value #EXPLOSION_RADIUS} blocks -
 * cosmetic particles/sound only, never touching blocks, per the player's own explicit "essa
 * explosão não quebra blocos" spec, hence {@link CombatAbilityService#dealAbilityDamage}
 * (a real {@code target.damage(amount, p)} call) rather than {@link
 * org.bukkit.World#createExplosion} (which would need {@code breakBlocks=false} fought
 * against vanilla's own power-based damage falloff instead of a clean flat number).
 *
 * <p>No real custom head texture has been sent for this one yet (unlike every other accessory
 * in this plugin) - {@link Material#SKELETON_SKULL} stands in until one is.
 */
public final class SkeletonHatService implements Listener {
    private static final String FAMILY = "skeleton_hat";
    private static final int INTELLIGENCE_BONUS = 10;
    private static final int SPEED_BONUS = 2;
    private static final double EXPLOSION_CHANCE = 0.20;
    private static final int EXPLOSION_CHANCE_PERCENT = 20;
    private static final double EXPLOSION_DAMAGE = 50.0;
    private static final double EXPLOSION_RADIUS = 3.0;

    private final AccessoryBagService accessoryBag;
    private final CombatAbilityService abilities;

    public SkeletonHatService(AccessoryBagService accessoryBag, CombatAbilityService abilities) {
        this.accessoryBag = accessoryBag;
        this.abilities = abilities;
    }

    public static ItemStack createItem() {
        ItemStack item = new ItemStack(Material.SKELETON_SKULL);
        ItemMeta meta = item.getItemMeta();
        AccessoryItems.mark(meta, AccessoryType.CHARM, FAMILY, 0, 0.0, 0.0, 0.0, 0, 0, 0, 0);
        AccessoryItems.markIntelligenceAndSpeed(meta, INTELLIGENCE_BONUS, SPEED_BONUS);
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

    @EventHandler(ignoreCancelled = true)
    public void arrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof Player shooter)) {
            return;
        }
        if (!this.accessoryBag.hasFamily(shooter, FAMILY) || ThreadLocalRandom.current().nextDouble() >= EXPLOSION_CHANCE) {
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
