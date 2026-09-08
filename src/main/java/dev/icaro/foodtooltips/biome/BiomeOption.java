package dev.icaro.foodtooltips.biome;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.Biome;

/**
 * The curated set of Overworld biomes the {@link BiomeWandService} menu offers - only
 * ones with a genuinely distinct grass/foliage tint (the wand's whole point), not every
 * biome that technically has grass. Ordered as they appear in the menu grid.
 */
public enum BiomeOption {
    PLAINS(Biome.PLAINS, Material.GRASS_BLOCK, "Planície", "Plains"),
    FOREST(Biome.FOREST, Material.OAK_LEAVES, "Floresta", "Forest"),
    BIRCH_FOREST(Biome.BIRCH_FOREST, Material.BIRCH_LEAVES, "Floresta de Bétulas", "Birch Forest"),
    DARK_FOREST(Biome.DARK_FOREST, Material.DARK_OAK_LEAVES, "Floresta Sombria", "Dark Forest"),
    SWAMP(Biome.SWAMP, Material.LILY_PAD, "Pântano", "Swamp"),
    MANGROVE_SWAMP(Biome.MANGROVE_SWAMP, Material.MANGROVE_LEAVES, "Pântano de Mangue", "Mangrove Swamp"),
    JUNGLE(Biome.JUNGLE, Material.JUNGLE_LEAVES, "Selva", "Jungle"),
    SAVANNA(Biome.SAVANNA, Material.ACACIA_LEAVES, "Savana", "Savanna"),
    WINDSWEPT_HILLS(Biome.WINDSWEPT_HILLS, Material.STONE, "Montanhas", "Windswept Hills"),
    TAIGA(Biome.TAIGA, Material.SPRUCE_LEAVES, "Taiga", "Taiga"),
    OLD_GROWTH_PINE_TAIGA(Biome.OLD_GROWTH_PINE_TAIGA, Material.PODZOL, "Taiga Antiga", "Old Growth Pine Taiga"),
    BADLANDS(Biome.BADLANDS, Material.RED_SAND, "Terras Áridas", "Badlands"),
    CHERRY_GROVE(Biome.CHERRY_GROVE, Material.CHERRY_LEAVES, "Bosque de Cerejeiras", "Cherry Grove"),
    MEADOW(Biome.MEADOW, Material.PINK_PETALS, "Prado", "Meadow"),
    // Custom biome from the IcarusBiomes datapack (github.com/IcaroFranca/IcarusBiomes) -
    // resolved dynamically from the server's biome registry rather than a compile-time
    // constant, since it doesn't exist unless that datapack is actually installed. biome()
    // is null (and this option quietly excluded from the wand's menu, see
    // BiomeWandService#availableOptions) when the datapack isn't present.
    SHADOWED_GRAVEYARD(resolveCustomBiome("icarusrpg", "shadowed_graveyard"), Material.AMETHYST_CLUSTER, "Cemitério Sombrio", "Shadowed Graveyard");

    private final Biome biome;
    private final Material icon;
    private final String namePt;
    private final String nameEn;

    BiomeOption(Biome biome, Material icon, String namePt, String nameEn) {
        this.biome = biome;
        this.icon = icon;
        this.namePt = namePt;
        this.nameEn = nameEn;
    }

    private static Biome resolveCustomBiome(String namespace, String key) {
        try {
            return Registry.BIOME.get(new NamespacedKey(namespace, key));
        } catch (Exception ignored) {
            return null;
        }
    }

    /** Null if this is a datapack-provided biome and that datapack isn't installed on the server. */
    public Biome biome() {
        return this.biome;
    }

    public Material icon() {
        return this.icon;
    }

    public String displayName(boolean pt) {
        return pt ? this.namePt : this.nameEn;
    }

    /** The default selection - always the first entry, so a freshly-created wand has a sane biome picked. */
    public static BiomeOption defaultOption() {
        return values()[0];
    }
}
