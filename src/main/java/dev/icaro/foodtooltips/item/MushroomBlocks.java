package dev.icaro.foodtooltips.item;

import org.bukkit.Material;

/**
 * The three blocks a huge mushroom is made of - its stem and its red or brown cap. The
 * throwable axes ({@link SpruceAxeService}, {@link TreecapitatorService}) treat them as one
 * "tree", so a throw or a chop sweeps through the whole mushroom instead of stopping where the
 * stem meets the cap.
 */
public final class MushroomBlocks {
    private MushroomBlocks() {
    }

    public static boolean is(Material m) {
        return m == Material.MUSHROOM_STEM || m == Material.RED_MUSHROOM_BLOCK || m == Material.BROWN_MUSHROOM_BLOCK;
    }

    /** Whether a sweep that started on {@code origin} may continue into {@code next} - the same Material, or both parts of a huge mushroom. */
    public static boolean sameTree(Material origin, Material next) {
        return origin == next || (is(origin) && is(next));
    }
}
