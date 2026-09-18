package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryEntry;
import dev.icaro.foodtooltips.item.HeadTexture;
import java.util.List;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;

public final class BestiaryCatalog {
    /** Same key {@code MinerVariantService} tags a Zombie/Skeleton Miner with, redeclared here (not imported - {@code combat} depends on this package, not the other way around) so {@link #find(Entity)} can resolve a Miner's own dedicated entry instead of the plain Zombie/Skeleton one every other caller only knowing the real {@link EntityType} would get via {@link #find(EntityType)}. */
    private static final NamespacedKey MINER_VARIANT_KEY = new NamespacedKey("foodtooltips", "miner_variant");
    private static final List<BestiaryEntry> ENTRIES = List.of(BestiaryCatalog.e(EntityType.ZOMBIE, Material.ROTTEN_FLESH, 50, "5", "Rotten Flesh \u2014 100%", "Iron Ingot \u2014 2.5%", "Carrot \u2014 2.5%", "Potato \u2014 2.5%"), BestiaryCatalog.e(EntityType.HUSK, Material.SAND, 52, "5", "Rotten Flesh \u2014 100%", "Iron Ingot \u2014 2.5%", "Carrot \u2014 2.5%", "Potato \u2014 2.5%"), BestiaryCatalog.e(EntityType.DROWNED, Material.TRIDENT, 58, "5", "Rotten Flesh \u2014 100%", "Copper Ingot \u2014 11%", "Trident \u2014 6.25% if holding one", "Nautilus Shell \u2014 8% if holding one"), BestiaryCatalog.e(EntityType.SKELETON, Material.BONE, 50, "5", "Bone \u2014 100%", "Arrow \u2014 100%", "Bow \u2014 8.5%"), BestiaryCatalog.e(EntityType.STRAY, Material.TIPPED_ARROW, 54, "5", "Bone \u2014 100%", "Arrow \u2014 100%", "Arrow of Slowness \u2014 50%", "Bow \u2014 8.5%"), BestiaryCatalog.e(EntityType.CREEPER, Material.GUNPOWDER, 55, "5", "Gunpowder \u2014 0\u20132 (66.7% for at least 1)", "Music Disc \u2014 100% when killed by skeleton"), BestiaryCatalog.e(EntityType.SPIDER, Material.STRING, 48, "5", "String \u2014 0\u20132 (66.7% for at least 1)", "Spider Eye \u2014 33.3%"), BestiaryCatalog.e(EntityType.CAVE_SPIDER, Material.SPIDER_EYE, 52, "5", "String \u2014 0\u20132 (66.7% for at least 1)", "Spider Eye \u2014 33.3%"), BestiaryCatalog.e(EntityType.ENDERMAN, Material.ENDER_PEARL, 80, "5", "Ender Pearl \u2014 50%"), BestiaryCatalog.e(EntityType.WITCH, Material.POTION, 90, "5", "Glowstone Dust \u2014 14.3% per loot roll", "Redstone Dust \u2014 14.3% per loot roll", "Sugar \u2014 14.3% per loot roll", "Stick \u2014 14.3% per loot roll", "Glass Bottle \u2014 14.3% per loot roll", "Spider Eye \u2014 14.3% per loot roll", "Gunpowder \u2014 14.3% per loot roll", "Held Potion \u2014 8.5% while drinking"), BestiaryCatalog.e(EntityType.SLIME, Material.SLIME_BALL, 35, "1\u20133", "Slimeball \u2014 0\u20132 from small slime"), BestiaryCatalog.e(EntityType.MAGMA_CUBE, Material.MAGMA_CREAM, 45, "1\u20134", "Magma Cream \u2014 25% from small/medium"), BestiaryCatalog.e(EntityType.BLAZE, Material.BLAZE_ROD, 75, "10", "Blaze Rod \u2014 50% (player kill)"), BestiaryCatalog.e(EntityType.GHAST, Material.GHAST_TEAR, 85, "5", "Ghast Tear \u2014 0\u20131 (50%)", "Gunpowder \u2014 0\u20132 (66.7% for at least 1)"), BestiaryCatalog.e(EntityType.GUARDIAN, Material.PRISMARINE_SHARD, 70, "10", "Prismarine Shard \u2014 66.7% (0\u20132)", "Cod \u2014 40%", "Prismarine Crystal \u2014 40%"), BestiaryCatalog.e(EntityType.ELDER_GUARDIAN, Material.WET_SPONGE, 300, "10", "Wet Sponge \u2014 100%", "Prismarine Shard \u2014 66.7% (0\u20132)", "Cod \u2014 40%", "Prismarine Crystal \u2014 40%", "Tide Armor Trim \u2014 20%"), BestiaryCatalog.e(EntityType.PHANTOM, Material.PHANTOM_MEMBRANE, 65, "5", "Phantom Membrane \u2014 0\u20131 (50%)"), BestiaryCatalog.e(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SKULL, 95, "5", "Coal \u2014 33.3%", "Bone \u2014 100%", "Stone Sword \u2014 8.5%", "Wither Skeleton Skull \u2014 2.5%"), BestiaryCatalog.e(EntityType.SHULKER, Material.SHULKER_SHELL, 95, "5", "Shulker Shell \u2014 50%"), BestiaryCatalog.e(EntityType.PILLAGER, Material.CROSSBOW, 60, "5", "Crossbow \u2014 8.5%", "Ominous Bottle \u2014 100% if raid captain"), BestiaryCatalog.e(EntityType.VINDICATOR, Material.IRON_AXE, 70, "5", "Emerald \u2014 0\u20131 (player kill)", "Iron Axe \u2014 8.5%"), BestiaryCatalog.e(EntityType.EVOKER, Material.TOTEM_OF_UNDYING, 120, "10", "Totem of Undying \u2014 100%", "Emerald \u2014 50% (0\u20131)", "Ominous Bottle \u2014 100% if raid captain"), BestiaryCatalog.e(EntityType.RAVAGER, Material.SADDLE, 180, "20", "Saddle \u2014 100%"), BestiaryCatalog.e(EntityType.BREEZE, Material.BREEZE_ROD, 90, "10", "Breeze Rod \u2014 100% when eligible"), BestiaryCatalog.e(EntityType.HOGLIN, Material.PORKCHOP, 75, "5", "Raw Porkchop \u2014 100%", "Leather \u2014 100%"), BestiaryCatalog.e(EntityType.ZOGLIN, Material.ROTTEN_FLESH, 80, "5", "Rotten Flesh \u2014 100%"), BestiaryCatalog.e(EntityType.PIGLIN_BRUTE, Material.GOLDEN_AXE, 110, "20", "Golden Axe \u2014 8.5%"), BestiaryCatalog.e(EntityType.WARDEN, Material.SCULK_CATALYST, 750, "5", "Sculk Catalyst \u2014 100%"), BestiaryCatalog.e(EntityType.WITHER, Material.NETHER_STAR, 1500, "50", "Nether Star \u2014 100%"), BestiaryCatalog.e(EntityType.ENDER_DRAGON, Material.DRAGON_EGG, 2500, "12,000 first / 500 repeat", "Dragon Egg \u2014 100% on first kill", "Dragon Breath \u2014 100% when collected with bottles"), BestiaryCatalog.e(EntityType.PIG, Material.PORKCHOP, 0, "1\u20133", "Raw Porkchop \u2014 100%"), BestiaryCatalog.e(EntityType.COW, Material.LEATHER, 0, "1\u20133", "Raw Beef \u2014 100%", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.SHEEP, Material.WHITE_WOOL, 0, "1\u20133", "Raw Mutton \u2014 100%", "Matching Wool \u2014 100% if unsheared"), BestiaryCatalog.e(EntityType.CHICKEN, Material.FEATHER, 0, "1\u20133", "Raw Chicken \u2014 100%", "Feather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.RABBIT, Material.RABBIT_HIDE, 0, "1\u20133", "Raw Rabbit \u2014 100%", "Rabbit Hide \u2014 50%", "Rabbit's Foot \u2014 10%"), BestiaryCatalog.e(EntityType.HORSE, Material.LEATHER, 0, "1\u20133", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.WOLF, Material.BONE, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.BEE, Material.HONEYCOMB, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.IRON_GOLEM, Material.IRON_INGOT, 0, "0", "Iron Ingot \u2014 100% (3\u20135)", "Poppy \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.POLAR_BEAR, Material.COD, 0, "1\u20133", "Raw Cod \u2014 66.7% (0\u20132)", "Raw Salmon \u2014 25%"), BestiaryCatalog.e(EntityType.DOLPHIN, Material.COD, 0, "1\u20133", "Raw Cod \u2014 50%"), BestiaryCatalog.e(EntityType.TURTLE, Material.SEAGRASS, 0, "1\u20133", "Seagrass \u2014 66.7% (0\u20132)", "Bowl \u2014 100% if killed by lightning"), BestiaryCatalog.e(EntityType.COD, Material.COD, 0, "1\u20133", "Raw Cod \u2014 100%", "Bone Meal \u2014 5%"), BestiaryCatalog.e(EntityType.SALMON, Material.SALMON, 0, "1\u20133", "Raw Salmon \u2014 100%", "Bone Meal \u2014 5%"), BestiaryCatalog.e(EntityType.SQUID, Material.INK_SAC, 0, "1\u20133", "Ink Sac \u2014 100% (1\u20133)"), BestiaryCatalog.e(EntityType.GLOW_SQUID, Material.GLOW_INK_SAC, 0, "1\u20133", "Glow Ink Sac \u2014 100% (1\u20133)"), BestiaryCatalog.e(EntityType.GOAT, Material.GOAT_HORN, 0, "1\u20133", "Goat Horn \u2014 0% on death; obtained by ramming"), BestiaryCatalog.e(EntityType.PANDA, Material.BAMBOO, 0, "1\u20133", "Bamboo \u2014 50% (adult)"), BestiaryCatalog.e(EntityType.FOX, Material.SWEET_BERRIES, 0, "1\u20133", "Held item \u2014 100% when carrying one"), BestiaryCatalog.e(EntityType.BAT, Material.COAL, 0, "0", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.PIGLIN, Material.GOLD_INGOT, 0, "5", "Equipped weapon \u2014 8.5%", "Equipped armor \u2014 8.5% per piece"), BestiaryCatalog.e(EntityType.STRIDER, Material.STRING, 0, "1\u20132", "String \u2014 100% (2\u20135)", "Saddle \u2014 100% if equipped"), BestiaryCatalog.e(EntityType.CAT, Material.STRING, 0, "1\u20133", "String \u2014 0% on death; morning gift only"), BestiaryCatalog.e(EntityType.OCELOT, Material.COD, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.DONKEY, Material.LEATHER, 0, "1\u20133", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.MULE, Material.LEATHER, 0, "1\u20133", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.LLAMA, Material.LEATHER, 0, "1\u20133", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.CAMEL, Material.LEATHER, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.MOOSHROOM, Material.RED_MUSHROOM, 0, "1\u20133", "Raw Beef \u2014 100%", "Leather \u2014 66.7% (0\u20132)"), BestiaryCatalog.e(EntityType.SNIFFER, Material.TORCHFLOWER_SEEDS, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.ARMADILLO, Material.ARMADILLO_SCUTE, 0, "1\u20133", "Armadillo Scute \u2014 0% on death; obtained by brushing"), BestiaryCatalog.e(EntityType.FROG, Material.SLIME_BALL, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.AXOLOTL, Material.TROPICAL_FISH, 0, "1\u20133", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.PARROT, Material.FEATHER, 0, "1\u20133", "Feather \u2014 100% (1\u20132)"), BestiaryCatalog.e(EntityType.VILLAGER, Material.EMERALD, 0, "0", "No standard item drop \u2014 0%"), BestiaryCatalog.e(EntityType.SNOW_GOLEM, Material.SNOWBALL, 0, "0", "Snowball \u2014 100% (0\u201315)"), BestiaryCatalog.e("zombie_miner", EntityType.ZOMBIE, Material.DIAMOND_SWORD, HeadTexture.ZOMBIE_MINER, "Zombie Miner", 240, "40", "Rotten Flesh \u2014 100%", "Iron Ingot \u2014 2.5%", "Carrot \u2014 2.5%", "Potato \u2014 2.5%", "Undead's Sword \u2014 2.5%", "Miner's Armor (any piece) \u2014 1% each"), BestiaryCatalog.e("skeleton_miner", EntityType.SKELETON, Material.DIAMOND_HELMET, HeadTexture.SKELETON_MINER, "Skeleton Miner", 240, "40", "Bone \u2014 100%", "Arrow \u2014 100%", "Bow \u2014 8.5%", "Miner's Armor (any piece) \u2014 1% each"));

    private BestiaryCatalog() {
    }

    private static BestiaryEntry e(EntityType type, Material icon, int combatXp, String orbXp, String ... drops) {
        return new BestiaryEntry(type.key().value(), type, icon, null, null, combatXp, orbXp, List.of(drops));
    }

    /**
     * Same as {@link #e(EntityType, Material, int, String, String...)}, with an explicit
     * {@code id} instead of deriving one from {@code type} - for an entry that shares its
     * real vanilla {@link EntityType} with another entry already in this catalog (e.g.
     * "zombie_miner" alongside the plain "zombie" entry, both {@link EntityType#ZOMBIE}) -
     * {@link #find(EntityType)} would otherwise have no way to tell them apart, and two
     * entries can't share one derived id (kill-count PDC keys - see {@code
     * BestiaryProgressService} - are keyed by id, not type).
     */
    private static BestiaryEntry e(String id, EntityType type, Material icon, int combatXp, String orbXp, String ... drops) {
        return new BestiaryEntry(id, type, icon, null, null, combatXp, orbXp, List.of(drops));
    }

    /**
     * Same as {@link #e(String, EntityType, Material, int, String, String...)}, plus its
     * own menu icon ({@code headTexture}, a base64 "Value" - same convention {@code
     * HeadTexture} and {@code BestiaryCategory#headTexture} use) and display name - for a
     * variant entry that would otherwise resolve to the exact same spawn-egg icon and
     * humanized-EntityType name as the base entry it shares an {@link EntityType} with.
     */
    private static BestiaryEntry e(String id, EntityType type, Material icon, String headTexture, String name, int combatXp, String orbXp, String ... drops) {
        return new BestiaryEntry(id, type, icon, headTexture, name, combatXp, orbXp, List.of(drops));
    }

    public static List<BestiaryEntry> entries() {
        return ENTRIES;
    }

    /**
     * The first entry matching {@code type} - for an {@link EntityType} more than one
     * entry shares (see {@link #e(String, EntityType, Material, int, String, String...)}),
     * this is always the "base" one (declared first in {@link #ENTRIES}), never a variant
     * like "zombie_miner". Prefer {@link #find(Entity)} whenever a real mob (not just its
     * type) is available - it already tells a Miner variant apart on its own.
     */
    public static Optional<BestiaryEntry> find(EntityType type) {
        return ENTRIES.stream().filter(e -> e.type() == type).findFirst();
    }

    /**
     * Same as {@link #find(EntityType)}, except a Zombie/Skeleton Miner (see {@link
     * #MINER_VARIANT_KEY}) resolves to its own dedicated entry ("zombie_miner"/
     * "skeleton_miner") instead of the plain Zombie/Skeleton one - every caller that
     * only has an {@link EntityType} (not a real mob to check the tag on) has no way to
     * make that distinction, so this is the one to use whenever an actual {@link Entity}
     * is available (kill-XP, valor, loot bonus, milestones...).
     */
    public static Optional<BestiaryEntry> find(Entity entity) {
        if (entity instanceof LivingEntity le && le.getPersistentDataContainer().has(MINER_VARIANT_KEY, PersistentDataType.BYTE)) {
            Optional<BestiaryEntry> variant = findById(entity.getType() == EntityType.ZOMBIE ? "zombie_miner" : "skeleton_miner");
            if (variant.isPresent()) {
                return variant;
            }
        }
        return find(entity.getType());
    }

    /** Exact id lookup - see {@link #find(EntityType)}'s own doc for why this exists alongside it. */
    public static Optional<BestiaryEntry> findById(String id) {
        return ENTRIES.stream().filter(e -> e.id().equals(id)).findFirst();
    }
}

