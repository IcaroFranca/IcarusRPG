package dev.icaro.foodtooltips.creaking;

import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/**
 * Pure, side-effect-free classification for the Creaking Sight glow (see {@link
 * GlowCategory}) - deliberately takes/returns only plain Bukkit interfaces (no PacketEvents,
 * no scheduler, no Citizens) so it's testable with plain Mockito mocks, the same "no live
 * server needed" reasoning {@code reforge.ReforgeServiceTest} already established for this
 * project. {@link CreakingSightService} is the only caller, and it does the couple of checks
 * that genuinely need environment access first - {@code entity.isValid()}/{@code isDead()}
 * (already covered here too, for a standalone unit test's sake) and whether Citizens is even
 * installed, before asking Citizens whether a given entity is one of ITS OWN NPCs (an entity
 * this class has no way to recognize on its own, since Citizens NPCs are ordinary vanilla
 * entities with a Citizens-only marker).
 *
 * <p>Priority, matching the player's own explicit spec: boss/miniboss beats hostile-or-has-a-
 * target beats known-neutral beats known-passive; anything this class can't place safely in
 * one of those four buckets gets {@code null} (no glow at all) rather than a guess.
 */
public final class EntityClassifier {
    /**
     * Species that only fight back when provoked - Bukkit has no marker interface for
     * "neutral" (unlike {@link Enemy} for hostile or {@link Animals} for most passive
     * creatures), so this is a hand-picked, documented list. Enderman is deliberately NOT
     * here despite vanilla's own more nuanced "aggressive only when looked at/hit" behavior -
     * Bukkit itself classifies it under {@link Enemy}, so it's already caught by the hostile
     * check above this one; treating it as neutral instead would need a bespoke Enderman-only
     * exception this plugin doesn't otherwise need.
     */
    private static final Set<EntityType> NEUTRAL_TYPES = EnumSet.of(
            EntityType.WOLF, EntityType.BEE, EntityType.IRON_GOLEM, EntityType.PANDA,
            EntityType.LLAMA, EntityType.TRADER_LLAMA, EntityType.POLAR_BEAR, EntityType.GOAT,
            EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN);

    /**
     * Passive species {@link Animals} doesn't cover (it isn't breedable, or isn't marked
     * breedable in the Bukkit API despite being harmless) - every ordinary farm/breedable
     * animal (Cow, Pig, Sheep, Chicken, Rabbit, the Horse family, Turtle, Fox, Camel,
     * Sniffer, Armadillo, Axolotl...) is already covered by {@code entity instanceof Animals}
     * and doesn't need to be listed here too.
     */
    private static final Set<EntityType> PASSIVE_TYPES = EnumSet.of(
            EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.SNOW_GOLEM,
            EntityType.BAT, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.DOLPHIN,
            EntityType.ALLAY, EntityType.STRIDER, EntityType.TADPOLE);

    private EntityClassifier() {
    }

    /**
     * {@code null} means "don't glow this entity at all" - either it's on the exclusion list
     * (see this class's own doc: Players, display/marker entities, dropped items, projectiles,
     * anything already dead/invalid) or it's a real {@link Mob} this plugin simply doesn't
     * recognize safely. Citizens NPCs are NOT excluded here (this class can't tell) - {@link
     * CreakingSightService} filters those out itself before ever calling this.
     */
    public static GlowCategory classify(Entity entity) {
        if (entity == null || !entity.isValid() || entity.isDead()) {
            return null;
        }
        if (entity instanceof Player || entity instanceof ArmorStand || entity instanceof Display
                || entity instanceof Item || entity instanceof Projectile) {
            return null;
        }
        if (!(entity instanceof Mob mob)) {
            return null;
        }
        if (BestiaryProgressService.isBoss(mob.getType())) {
            return GlowCategory.PURPLE;
        }
        if (mob instanceof Enemy || mob.getTarget() != null) {
            return GlowCategory.RED;
        }
        if (NEUTRAL_TYPES.contains(mob.getType())) {
            return GlowCategory.YELLOW;
        }
        if (mob instanceof Animals || PASSIVE_TYPES.contains(mob.getType())) {
            return GlowCategory.GREEN;
        }
        return null;
    }
}
