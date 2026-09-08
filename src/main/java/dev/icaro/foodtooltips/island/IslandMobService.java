package dev.icaro.foodtooltips.island;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.biome.BiomeOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spawns and maintains the combat island's custom mob population wherever the
 * ground actually has the Shadowed Graveyard biome (painted with the Biome's
 * Wand) - not a fixed area, so the population automatically follows however
 * much of the island is currently painted. Each mob is a plain vanilla Zombie
 * (full vanilla combat AI, already aggressive toward players out of the box -
 * no external plugin needed) dressed in iron armor and a custom player-head
 * texture as its helmet. Sentinela da Ilha respawns itself at its own spawn
 * point some time after dying (see {@link IslandMobListener}), so this
 * service only needs to place the initial population.
 */
public final class IslandMobService {
    /** Biome storage is aligned to 4-block cells (same grid the Biome's Wand paints in) - scanning at this step covers every distinct cell without redundant checks. */
    private static final int CELL = 4;

    private final Plugin plugin;
    private final IslandMobZone zone;
    private final boolean enabled;
    private final boolean suppressNaturalSpawns;
    private final int searchMinX;
    private final int searchMaxX;
    private final int searchMinZ;
    private final int searchMaxZ;
    private final String headTexture;
    private final double health;
    private final double damage;
    private final int respawnTicks;
    private final int count;
    private final List<UUID> spawnedIds = new ArrayList<>();
    private final List<BukkitTask> pendingRespawns = new ArrayList<>();

    public IslandMobService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("island-mobs.enabled", true);
        this.suppressNaturalSpawns = plugin.getConfig().getBoolean("island-mobs.suppress-natural-spawns", true);
        this.zone = new IslandMobZone(
                plugin.getConfig().getString("island-mobs.world", "combat_island"),
                BiomeOption.SHADOWED_GRAVEYARD.biome());
        this.searchMinX = plugin.getConfig().getInt("island-mobs.min-x", 0);
        this.searchMaxX = plugin.getConfig().getInt("island-mobs.max-x", 0);
        this.searchMinZ = plugin.getConfig().getInt("island-mobs.min-z", 0);
        this.searchMaxZ = plugin.getConfig().getInt("island-mobs.max-z", 0);
        this.headTexture = plugin.getConfig().getString("island-mobs.sentinela.head-texture", "");
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

    public boolean ready() {
        return this.enabled && this.zone.biome() != null;
    }

    /** (Re)spawns the whole population - removes any of this service's own mobs still alive first, so it's safe to call again (e.g. an admin command after tweaking config). */
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
        for (Location point : this.spawnPoints(world)) {
            this.spawnOne(point);
        }
        return this.spawnedIds.size();
    }

    /** Spawns a single Sentinela da Ilha at the given point and tracks it. */
    public void spawnOne(Location point) {
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        Zombie zombie = world.spawn(point, Zombie.class);
        zombie.customName(Component.text("Sentinela da Ilha", NamedTextColor.RED));
        zombie.setCustomNameVisible(true);
        zombie.setShouldBurnInDay(false);
        zombie.setBaby(false);
        zombie.getPersistentDataContainer().set(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING, "island_sentinel");
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(this.customHead());
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
            equipment.setHelmetDropChance(0.0f);
            equipment.setChestplateDropChance(0.0f);
            equipment.setLeggingsDropChance(0.0f);
            equipment.setBootsDropChance(0.0f);
        }
        AttributeInstance maxHealth = zombie.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(this.health);
        }
        zombie.setHealth(this.health);
        AttributeInstance attackDamage = zombie.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(this.damage);
        }
        this.spawnedIds.add(zombie.getUniqueId());
    }

    /** Called by {@link IslandMobListener} when one of these mobs dies - schedules a respawn at the same point. */
    public void scheduleRespawn(Location point) {
        this.pendingRespawns.add(Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.spawnOne(point), Math.max(1, this.respawnTicks)));
    }

    public boolean isIslandMob(LivingEntity entity) {
        return "island_sentinel".equals(entity.getPersistentDataContainer().get(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING));
    }

    public void despawnAll() {
        World world = Bukkit.getWorld(this.zone.world());
        if (world != null) {
            for (UUID id : this.spawnedIds) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(id);
                if (entity != null) {
                    entity.remove();
                }
            }
        }
        this.spawnedIds.clear();
        for (BukkitTask task : this.pendingRespawns) {
            task.cancel();
        }
        this.pendingRespawns.clear();
    }

    /** A player head wearing the configured custom texture (base64 "Value"), falling back to a plain head if it's bad or unset. */
    private ItemStack customHead() {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        if (this.headTexture.isBlank()) {
            return item;
        }
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", this.headTexture));
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the spawn.
        }
        return item;
    }

    /**
     * Scans the configured search area (island-mobs.min-x/max-x/min-z/max-z - just a
     * "look here" hint, not a strict boundary) for columns whose ground is actually the
     * Shadowed Graveyard biome, then picks {@link #count} of them spread across the scan
     * order. This is what makes the population follow wherever the biome is actually
     * painted rather than a fixed rectangle.
     */
    private List<Location> spawnPoints(World world) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = this.searchMinX; x <= this.searchMaxX; x += CELL) {
            for (int z = this.searchMinZ; z <= this.searchMaxZ; z += CELL) {
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (world.getBiome(x, y, z) == this.zone.biome()) {
                    candidates.add(new int[]{x, z});
                }
            }
        }
        if (candidates.isEmpty()) {
            this.plugin.getLogger().warning("island-mobs: no '" + this.zone.biome().key() + "' biome found in the search area, skipping spawn.");
            return List.of();
        }
        Collections.shuffle(candidates);
        List<Location> points = new ArrayList<>();
        int step = Math.max(1, candidates.size() / Math.max(1, this.count));
        for (int i = 0; i < this.count && i * step < candidates.size(); i++) {
            int[] c = candidates.get(i * step);
            int y = world.getHighestBlockYAt(c[0], c[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
            points.add(new Location(world, c[0] + 0.5, y, c[1] + 0.5));
        }
        return points;
    }
}
