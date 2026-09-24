package dev.icaro.foodtooltips.item;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCanBuildEvent;

/**
 * Removes vanilla's "must be in dim/dark light (level 12 or below)" requirement for Red
 * and Brown Mushroom - both to place one at all, and to keep an already-placed one from
 * popping off later if the light around it brightens (a torch placed nearby, sunrise
 * clearing a canopy overhead, etc.). Vanilla routes every one of those checks - the
 * player's own placement, the block's ongoing survival re-validation on a neighbor
 * update, and mushroom spread - through this same {@link BlockCanBuildEvent}, so
 * overriding it here in one place covers all three. In particular, right-clicking an
 * existing mushroom with Bone Meal triggers exactly this kind of "can this mushroom
 * still be here" re-check first - without this override, that re-check could fail (and
 * silently eat the mushroom) in light bright enough that placing a new one wouldn't even
 * have been allowed, which is what made Bone Meal look broken in anything but darkness.
 */
public final class MushroomGrowthService implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void canBuild(BlockCanBuildEvent e) {
        Material material = e.getMaterial();
        if (material == Material.RED_MUSHROOM || material == Material.BROWN_MUSHROOM) {
            e.setBuildable(true);
        }
    }
}
