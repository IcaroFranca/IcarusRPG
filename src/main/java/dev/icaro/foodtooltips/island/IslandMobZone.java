package dev.icaro.foodtooltips.island;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;

/** A specific biome inside one world - a location only counts as "inside" if the ground there actually has this biome (e.g. painted with the Biome's Wand), not a fixed area. {@code biome} is null when its datapack isn't installed, in which case nothing ever counts as inside. */
public record IslandMobZone(String world, Biome biome) {
    public boolean contains(Location loc) {
        World w = loc.getWorld();
        if (this.biome == null || w == null || !w.getName().equals(this.world)) {
            return false;
        }
        return w.getBiome(loc) == this.biome;
    }
}
