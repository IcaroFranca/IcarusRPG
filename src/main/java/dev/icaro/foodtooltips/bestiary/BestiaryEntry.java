package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryCategory;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * {@code id} is the real identity used for progress-tracking (PDC kill keys) and
 * lookup - always {@code type.key().value()}, the raw EntityType's own key, except
 * for an id-explicit variant entry like "zombie_miner" (see {@code
 * BestiaryCatalog#e(String, EntityType, Material, int, String, String...)}).
 *
 * <p>{@code headTexture} and {@code name} are both usually null - only a variant
 * entry that shares its real {@link EntityType} with another entry (a Miner
 * alongside the plain Zombie/Skeleton) needs its own menu icon (a custom head, same
 * textures the mob itself wears - see {@code HeadTexture}) and display name to tell
 * it apart, since {@link #displayName} and the menu's own spawn-egg icon lookup
 * would otherwise resolve identically for both.
 */
public record BestiaryEntry(String id, EntityType type, Material icon, String headTexture, String name, int combatXp, String orbXp, List<String> drops) {
    public int awardedCombatXp() {
        return this.combatXp <= 0 ? 0 : Math.max(1, (int)Math.round((double)this.combatXp / 10.0));
    }

    /** {@code name} if this entry pins its own (a variant like "zombie_miner"), otherwise humanized from the EntityType's own key - language-independent, since Minecraft's internal mob names don't otherwise differ between PT/EN. */
    public String displayName(Language l) {
        if (this.name != null) {
            return this.name;
        }
        String v = this.type.key().value().replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    public BestiaryCategory category() {
        if (this.id.equals("zombie_miner") || this.id.equals("skeleton_miner")) {
            // Both mine below Y0 in the Overworld, same as every other CAVES mob - unlike
            // their base EntityType (ZOMBIE/SKELETON), which falls to TERRESTRIAL below.
            return BestiaryCategory.CAVES;
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
