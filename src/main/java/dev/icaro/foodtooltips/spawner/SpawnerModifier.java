package dev.icaro.foodtooltips.spawner;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Every item the spawner menu ({@link SpawnerUpgradeService}) accepts, inspired by
 * Apotheosis' spawner modifiers. The items are stored in the spawner (not consumed) - how
 * many sit in a slot is the modifier's level, up to {@link #max}. The last four are on/off
 * unlocks: one item enables the effect, and {@link #toggle} names the menu switch for the
 * ones that can then be turned off without taking the item out.
 */
public enum SpawnerModifier {
    SUGAR(Material.SUGAR, 32, "Sugar", "Min spawn delay: -5 ticks per item (down to 40)", null),
    CLOCK(Material.CLOCK, 38, "Clock", "Max spawn delay: -20 ticks per item (down to 40)", null),
    FERMENTED_SPIDER_EYE(Material.FERMENTED_SPIDER_EYE, 4, "Fermented Spider Eye", "Mobs per spawn: +1 per item", null),
    GHAST_TEAR(Material.GHAST_TEAR, 5, "Ghast Tear", "Max nearby mobs: +2 per item", null),
    PRISMARINE_CRYSTALS(Material.PRISMARINE_CRYSTALS, 5, "Prismarine Crystals", "Player range: +4 blocks per item", null),
    BLAZE_ROD(Material.BLAZE_ROD, 4, "Blaze Rod", "Spawn area: +1 block per item", null),
    POINTED_DRIPSTONE(Material.POINTED_DRIPSTONE, 10, "Pointed Dripstone", "Mob health: -5% per item", null),
    NETHER_STAR(Material.NETHER_STAR, 1, "Nether Star", "Works with no players nearby", null),
    CONDUIT(Material.CONDUIT, 1, "Conduit", "Ignores light and spawn conditions", null),
    COMPARATOR(Material.COMPARATOR, 1, "Comparator", "Unlocks redstone control", Toggle.REDSTONE),
    CHORUS_FRUIT(Material.CHORUS_FRUIT, 1, "Chorus Fruit", "Unlocks mobs with No AI", Toggle.NO_AI),
    WOOL(Material.WHITE_WOOL, 1, "Wool", "Unlocks silent mobs", Toggle.SILENT);

    /** The menu switches - each only works while its unlocking item ({@link #unlockedBy}) is in the spawner. */
    public enum Toggle {
        REDSTONE("Redstone control", "Only spawns while powered by redstone"),
        NO_AI("Mobs with No AI", "Spawned mobs don't move or attack"),
        SILENT("Silent mobs", "Spawned mobs make no sound");

        public final String label;
        public final String description;

        Toggle(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public SpawnerModifier unlockedBy() {
            for (SpawnerModifier m : SpawnerModifier.values()) {
                if (m.toggle == this) {
                    return m;
                }
            }
            throw new IllegalStateException(this.name());
        }
    }

    public final Material material;
    public final int max;
    public final String label;
    public final String effect;
    public final Toggle toggle;

    SpawnerModifier(Material material, int max, String label, String effect, Toggle toggle) {
        this.material = material;
        this.max = max;
        this.label = label;
        this.effect = effect;
        this.toggle = toggle;
    }

    /**
     * Whether {@code item} is this modifier's item - Wool takes any color. A custom-named item
     * (every custom item built on one of these materials has its own name) never counts; lore
     * alone doesn't matter, since the plugin's own tooltips add lore to plain items too (Chorus
     * Fruit's food attributes).
     */
    public boolean accepts(ItemStack item) {
        if (item == null || item.isEmpty() || item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return false;
        }
        return this == WOOL ? item.getType().name().endsWith("_WOOL") : item.getType() == this.material;
    }

    /** The modifier {@code item} belongs to, or null. */
    public static SpawnerModifier of(ItemStack item) {
        for (SpawnerModifier m : values()) {
            if (m.accepts(item)) {
                return m;
            }
        }
        return null;
    }
}
