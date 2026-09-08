package dev.icaro.foodtooltips.island;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.citizens.CitizensIntegrationService;
import java.util.ArrayList;
import java.util.List;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.SkinTrait;
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
            npc.getOrAddTrait(SkinTrait.class).setSkinName(this.skin, true);
            npc.setProtected(false);
            npc.spawn(point);
            npc.getEntity().getPersistentDataContainer().set(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING, "island_sentinel");
            SentinelTrait sentinel = npc.getOrAddTrait(SentinelTrait.class);
            sentinel.addTarget("player");
            sentinel.setHealth(this.health);
            sentinel.damage = this.damage;
            sentinel.respawnTime = this.respawnTicks;
            sentinel.spawnPoint = point.clone();
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

    /** One point at the zone's center plus the rest spread evenly around it, each snapped to the highest solid block. */
    private List<Location> spawnPoints(World world) {
        int centerX = (this.zone.minX() + this.zone.maxX()) / 2;
        int centerZ = (this.zone.minZ() + this.zone.maxZ()) / 2;
        int spread = Math.max(1, Math.min(this.zone.maxX() - this.zone.minX(), this.zone.maxZ() - this.zone.minZ()) / 4);
        List<Location> points = new ArrayList<>();
        for (int i = 0; i < this.count; i++) {
            double angle = 2.0 * Math.PI / Math.max(1, this.count) * i;
            int x = i == 0 ? centerX : centerX + (int) Math.round(Math.cos(angle) * spread);
            int z = i == 0 ? centerZ : centerZ + (int) Math.round(Math.sin(angle) * spread);
            int y = world.getHighestBlockYAt(x, z) + 1;
            points.add(new Location(world, x + 0.5, y, z + 0.5));
        }
        return points;
    }
}
