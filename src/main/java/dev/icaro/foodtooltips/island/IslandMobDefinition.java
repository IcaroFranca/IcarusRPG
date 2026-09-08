package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.item.legendary.LegendaryWeapon;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/**
 * One kind of custom island mob, entirely config-driven (island-mobs.mobs.<id> in
 * config.yml) - adding a new one only takes a new config entry, no Java changes.
 * {@code id} doubles as its {@link dev.icaro.foodtooltips.bestiary.BestiaryCatalog}
 * variant id, so it also gets its own distinct Bestiary entry there. {@code
 * displayName}/{@code displayNameEn} are the mob's in-world name shown to a Portuguese
 * or English client respectively (see {@link dev.icaro.foodtooltips.combat.MobVisualService#setLocalizedName}).
 */
public record IslandMobDefinition(
        String id,
        String displayName,
        String displayNameEn,
        EntityType entityType,
        double health,
        double damage,
        double speedMultiplier,
        Material weapon,
        LegendaryWeapon legendaryWeapon,
        double dropChancePercent,
        boolean armored,
        String headTexture,
        int respawnTicks,
        int count) {
}
