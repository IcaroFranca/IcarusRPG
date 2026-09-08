package dev.icaro.foodtooltips.island;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * One kind of custom island mob, entirely config-driven (island-mobs.mobs.<id> in
 * config.yml) - adding a new one only takes a new config entry, no Java changes.
 * {@code id} doubles as its {@link dev.icaro.foodtooltips.bestiary.BestiaryCatalog}
 * variant id, so it also gets its own distinct Bestiary entry there.
 */
public record IslandMobDefinition(
        String id,
        String displayName,
        EntityType entityType,
        double health,
        double damage,
        double speedMultiplier,
        Material weapon,
        boolean armored,
        String headTexture,
        int respawnTicks,
        int count) {
}
