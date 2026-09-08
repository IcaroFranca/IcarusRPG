package dev.icaro.foodtooltips.citizens;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Soft-dependency bridge to Citizens2 (player-skinned custom mobs) - mirrors
 * ProtectionService's pattern: an outer presence flag guards every call into
 * Citizens' own classes, so the plugin works fine with Citizens absent.
 */
public final class CitizensIntegrationService {
    private final boolean citizensPresent = Bukkit.getPluginManager().getPlugin("Citizens") != null;
    private final boolean sentinelPresent = Bukkit.getPluginManager().getPlugin("Sentinel") != null;

    public boolean available() {
        return this.citizensPresent;
    }

    /** Sentinel gives an NPC its combat AI (targeting, chasing, attacking) - needed for any NPC meant to fight, not just exist. */
    public boolean sentinelAvailable() {
        return this.citizensPresent && this.sentinelPresent;
    }

    /**
     * Citizens tags every entity it spawns with this Bukkit metadata key - the
     * standard, documented way other plugins tell "this is an NPC, not a real
     * connected player" apart, even without Citizens on the classpath. Player-type
     * Citizens NPCs (used for real player skins) are still {@code instanceof Player}
     * to Bukkit, so combat/ability code that branches on that must check this first.
     */
    public static boolean isNpc(Entity entity) {
        return entity.hasMetadata("NPC");
    }
}
