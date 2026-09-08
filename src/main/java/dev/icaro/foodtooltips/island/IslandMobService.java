package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.citizens.CitizensIntegrationService;
import java.util.ArrayList;
import java.util.List;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
import net.citizensnpcs.trait.waypoint.WanderWaypointProvider;
import net.citizensnpcs.trait.waypoint.Waypoints;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.mcmonkey.sentinel.SentinelTrait;

/**
 * Spawns and maintains the combat island's custom mob population (Citizens NPCs
 * with Sentinel-driven combat AI) inside a configured zone - only active when
 * both Citizens and Sentinel are installed. Uses Citizens' temporary NPC
 * registry: these mobs aren't meant to persist across restarts, and Sentinel
 * re-spawns each one on its own {@code respawnTime} ticks after it dies (see
 * {@code sentinel.spawnPoint} below), so this service only needs to place the
 * initial population - no periodic upkeep task required.
 */
public final class IslandMobService {
    private final Plugin plugin;
    private final CitizensIntegrationService citizens;
    private final IslandMobZone zone;
    private final boolean enabled;
    private final boolean suppressNaturalSpawns;
    private final String skin;
    private final double health;
    private final double damage;
    private final int respawnTicks;
    private final int count;
    private final int spreadRadius;
    private final List<Integer> spawnedIds = new ArrayList<>();

    public IslandMobService(Plugin plugin, CitizensIntegrationService citizens) {
        this.plugin = plugin;
        this.citizens = citizens;
        this.enabled = plugin.getConfig().getBoolean("island-mobs.enabled", true);
        this.suppressNaturalSpawns = plugin.getConfig().getBoolean("island-mobs.suppress-natural-spawns", true);
        this.zone = new IslandMobZone(
                plugin.getConfig().getString("island-mobs.world", "combat_island"),
                plugin.getConfig().getInt("island-mobs.min-x", 0),
                plugin.getConfig().getInt("island-mobs.max-x", 0),
                plugin.getConfig().getInt("island-mobs.min-z", 0),
                plugin.getConfig().getInt("island-mobs.max-z", 0));
        this.skin = plugin.getConfig().getString("island-mobs.sentinela.skin", "Notch");
        this.health = plugin.getConfig().getDouble("island-mobs.sentinela.health", 200.0);
        this.damage = plugin.getConfig().getDouble("island-mobs.sentinela.damage", 12.0);
        this.respawnTicks = plugin.getConfig().getInt("island-mobs.sentinela.respawn-ticks", 200);
        this.count = Math.max(0, plugin.getConfig().getInt("island-mobs.sentinela.count", 3));
        // 3/4 of the way from the zone's center to its nearest edge - spreads spawn points
        // (and each mob's own wander/chase leash, see spawnPopulation) across most of the
        // zone instead of clustering them near the middle.
        int halfWidth = (this.zone.maxX() - this.zone.minX()) / 2;
        int halfDepth = (this.zone.maxZ() - this.zone.minZ()) / 2;
        this.spreadRadius = Math.max(1, (int) (Math.min(halfWidth, halfDepth) * 0.75));
    }

    public IslandMobZone zone() {
        return this.zone;
    }

    public boolean suppressNaturalSpawns() {
        return this.suppressNaturalSpawns;
    }

    /** Whether this service can actually spawn anything right now (config + both soft-deps present). */
    public boolean ready() {
        return this.enabled && this.citizens.sentinelAvailable();
    }

    /** (Re)spawns the whole population - despawns any of this service's own NPCs still alive first, so it's safe to call again (e.g. an admin command after tweaking config). */
    public int spawnPopulation() {
        if (!this.ready()) {
            return 0;
        }
        this.despawnAll();
        World world = Bukkit.getWorld(this.zone.world());
        if (world == null) {
            this.plugin.getLogger().warning("island-mobs: world '" + this.zone.world() + "' isn't loaded, skipping spawn.");
            return 0;
        }
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        for (Location point : this.spawnPoints(world)) {
            NPC npc = registry.createNPC(EntityType.PLAYER, "Sentinela da Ilha");
            npc.setProtected(false);
            npc.spawn(point);
            // SkinTrait only has something to attach the fetched texture to once the NPC's
            // cosmetic entity actually exists - setting it before spawn() is a silent no-op
            // (onSkinChange short-circuits on a null cosmetic entity), so it falls back to
            // the default Steve/Alex skin instead of fetching the real one.
            npc.getOrAddTrait(SkinTrait.class).setSkinName(this.skin, true);
            npc.getEntity().getPersistentDataContainer().set(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING, "island_sentinel");
            // Wander waypoints, anchored to this NPC's own spawn point (its current location
            // right after spawn()), give Sentinel's chaseRange leash something real to return
            // to once combat ends - without a Waypoints trait, nearestPathPoint() always
            // returns null and the NPC just keeps whatever target it last had, letting every
            // mob converge onto one player instead of staying spread across the island.
            Waypoints waypoints = npc.getOrAddTrait(Waypoints.class);
            waypoints.setWaypointProvider("wander");
            if (waypoints.getCurrentProvider() instanceof WanderWaypointProvider wander) {
                wander.setXYRange(this.spreadRadius / 2, 3);
            }
            SentinelTrait sentinel = npc.getOrAddTrait(SentinelTrait.class);
            sentinel.addTarget("player");
            sentinel.setHealth(this.health);
            sentinel.damage = this.damage;
            sentinel.respawnTime = this.respawnTicks;
            sentinel.spawnPoint = point.clone();
            sentinel.chaseRange = this.spreadRadius;
            this.spawnedIds.add(npc.getId());
        }
        return this.spawnedIds.size();
    }

    public void despawnAll() {
        NPCRegistry registry = CitizensAPI.getNPCRegistry();
        for (int id : this.spawnedIds) {
            NPC npc = registry.getById(id);
            if (npc != null) {
                npc.destroy();
            }
        }
        this.spawnedIds.clear();
    }

    /** One point at the zone's center plus the rest spread evenly around it at {@link #spreadRadius}, each snapped to the highest solid block. */
    private List<Location> spawnPoints(World world) {
        int centerX = (this.zone.minX() + this.zone.maxX()) / 2;
        int centerZ = (this.zone.minZ() + this.zone.maxZ()) / 2;
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < this.count; i++) {
            double angle = 2.0 * Math.PI / Math.max(1, this.count) * i;
            int x = i == 0 ? centerX : centerX + (int) Math.round(Math.cos(angle) * this.spreadRadius);
            int z = i == 0 ? centerZ : centerZ + (int) Math.round(Math.sin(angle) * this.spreadRadius);
            int y = world.getHighestBlockYAt(x, z) + 1;
            points.add(new Location(world, x + 0.5, y, z + 0.5));
        }
        return points;
    }
}
