package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryCategory;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * {@code id} is the real identity used for progress-tracking (PDC kill keys)
 * and lookup - for a vanilla entry it's always {@code type.key().value()}, so
 * {@link BestiaryCatalog#find(EntityType)} can recognize a "canonical" entry
 * by that equality. A variant (a custom mob sharing a vanilla EntityType,
 * e.g. a Citizens Player-type NPC) gets its own distinct {@code id} instead,
 * so its kills/milestones never mix with real kills of that raw EntityType -
 * looked up instead via {@link BestiaryCatalog#find(org.bukkit.entity.Entity)},
 * which checks the variant PDC tag first. {@code categoryOverride} lets a variant pick
 * its own tab instead of falling through to whatever {@link #category()} would compute
 * from its raw {@code type} (e.g. the combat island's mobs share EntityTypes with real
 * Overworld mobs but don't belong in that tab) - null for every canonical entry.
 */
public record BestiaryEntry(String id, EntityType type, Material icon, int combatXp, String orbXp, List<String> drops, String customName, BestiaryCategory categoryOverride) {
    public int awardedCombatXp() {
        return this.combatXp <= 0 ? 0 : Math.max(1, (int)Math.round((double)this.combatXp / 10.0));
    }

    /** Display name - a variant's own name if set, otherwise humanized from its EntityType key. */
    public String displayName() {
        if (this.customName != null) {
            return this.customName;
        }
        String v = this.type.key().value().replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    public BestiaryCategory category() {
        if (this.categoryOverride != null) {
            return this.categoryOverride;
        }
        return switch (this.type) {
            case EntityType.DROWNED, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD, EntityType.SALMON, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.AXOLOTL -> BestiaryCategory.AQUATIC;
            case EntityType.CAVE_SPIDER, EntityType.SLIME, EntityType.WARDEN, EntityType.BREEZE, EntityType.BAT -> BestiaryCategory.CAVES;
            case EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.PIGLIN_BRUTE, EntityType.PIGLIN, EntityType.STRIDER, EntityType.WITHER -> BestiaryCategory.NETHER;
            case EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDER_DRAGON -> BestiaryCategory.THE_END;
            case EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT, EntityType.HORSE, EntityType.FOX, EntityType.PANDA, EntityType.GOAT, EntityType.CAT, EntityType.OCELOT, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.CAMEL, EntityType.MOOSHROOM, EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.FROG, EntityType.PARROT -> BestiaryCategory.ANIMALS;
            case EntityType.WOLF, EntityType.BEE, EntityType.IRON_GOLEM, EntityType.POLAR_BEAR, EntityType.VILLAGER, EntityType.SNOW_GOLEM -> BestiaryCategory.NEUTRAL;
            default -> BestiaryCategory.TERRESTRIAL;
        };
    }
}
