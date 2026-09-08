package dev.icaro.foodtooltips.island;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Spawns and maintains the combat island's custom mob population inside a
 * configured zone. Each mob is a plain vanilla Zombie (full vanilla combat AI,
 * already aggressive toward players out of the box - no external plugin
 * needed) dressed in iron armor and a custom player-head texture as its
 * helmet. Sentinela da Ilha respawns itself at its own spawn point some time
 * after dying (see {@link IslandMobListener}), so this service only needs to
 * place the initial population.
 */
public final class IslandMobService {
    private final Plugin plugin;
    private final IslandMobZone zone;
    private final boolean enabled;
    private final boolean suppressNaturalSpawns;
    private final String headTexture;
    private final double health;
    private final double damage;
    private final int respawnTicks;
    private final int count;
    private final int spreadRadius;
    private final List<UUID> spawnedIds = new ArrayList<>();
    private final List<BukkitTask> pendingRespawns = new ArrayList<>();

    public IslandMobService(Plugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("island-mobs.enabled", true);
        this.suppressNaturalSpawns = plugin.getConfig().getBoolean("island-mobs.suppress-natural-spawns", true);
        this.zone = new IslandMobZone(
                plugin.getConfig().getString("island-mobs.world", "combat_island"),
                plugin.getConfig().getInt("island-mobs.min-x", 0),
                plugin.getConfig().getInt("island-mobs.max-x", 0),
                plugin.getConfig().getInt("island-mobs.min-z", 0),
                plugin.getConfig().getInt("island-mobs.max-z", 0));
        this.headTexture = plugin.getConfig().getString("island-mobs.sentinela.head-texture", "");
        this.health = plugin.getConfig().getDouble("island-mobs.sentinela.health", 200.0);
        this.damage = plugin.getConfig().getDouble("island-mobs.sentinela.damage", 12.0);
        this.respawnTicks = plugin.getConfig().getInt("island-mobs.sentinela.respawn-ticks", 200);
        this.count = Math.max(0, plugin.getConfig().getInt("island-mobs.sentinela.count", 3));
        // 3/4 of the way from the zone's center to its nearest edge - spreads spawn points
        // across most of the zone instead of clustering them near the middle.
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

    public boolean ready() {
        return this.enabled;
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
