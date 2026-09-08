package dev.icaro.foodtooltips.island;

import org.bukkit.Location;

/** A rectangular X/Z region (full world height) inside one world where custom island mobs spawn instead of vanilla ones. */
public record IslandMobZone(String world, int minX, int maxX, int minZ, int maxZ) {
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(this.world)) {
            return false;
        }
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }
}
