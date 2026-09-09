package dev.icaro.foodtooltips.island;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.biome.BiomeOption;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeapon;
import dev.icaro.foodtooltips.item.legendary.LegendaryWeaponService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
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
 * Spawns and maintains the combat island's custom mob population wherever the
 * ground actually has the Shadowed Graveyard biome (painted with the Biome's
 * Wand) - not a fixed area, so the population automatically follows however
 * much of the island is currently painted. Every mob kind (see
 * {@link IslandMobDefinition}) is a plain vanilla entity - full vanilla combat
 * AI, already aggressive toward players out of the box, no external plugin
 * needed - dressed in gear per its config. Each one respawns itself at its own
 * spawn point some time after dying (see {@link IslandMobListener}), so this
 * service only needs to place the initial population.
 */
public final class IslandMobService {
    /** Biome storage is aligned to 4-block cells (same grid the Biome's Wand paints in) - scanning at this step covers every distinct cell without redundant checks. */
    private static final int CELL = 4;

    private final Plugin plugin;
    private final LegendaryWeaponService legendary;
    private final MobVisualService visuals;
    private final IslandMobZone zone;
    private final boolean enabled;
    private final boolean suppressNaturalSpawns;
    private final int searchMinX;
    private final int searchMaxX;
    private final int searchMinZ;
    private final int searchMaxZ;
    private final List<IslandMobDefinition> definitions;
    private final Map<String, IslandMobDefinition> definitionsById;
    private final List<UUID> spawnedIds = new ArrayList<>();
    private final List<BukkitTask> pendingRespawns = new ArrayList<>();

    public IslandMobService(Plugin plugin, LegendaryWeaponService legendary, MobVisualService visuals) {
        this.plugin = plugin;
        this.legendary = legendary;
        this.visuals = visuals;
        this.enabled = plugin.getConfig().getBoolean("island-mobs.enabled", true);
        this.suppressNaturalSpawns = plugin.getConfig().getBoolean("island-mobs.suppress-natural-spawns", true);
        this.zone = new IslandMobZone(
                plugin.getConfig().getString("island-mobs.world", "combat_island"),
                BiomeOption.SHADOWED_GRAVEYARD.biome());
        this.searchMinX = plugin.getConfig().getInt("island-mobs.min-x", 0);
        this.searchMaxX = plugin.getConfig().getInt("island-mobs.max-x", 0);
        this.searchMinZ = plugin.getConfig().getInt("island-mobs.min-z", 0);
        this.searchMaxZ = plugin.getConfig().getInt("island-mobs.max-z", 0);
        this.definitions = this.loadDefinitions(plugin);
        Map<String, IslandMobDefinition> byId = new HashMap<>();
        for (IslandMobDefinition def : this.definitions) {
            byId.put(def.id(), def);
        }
        this.definitionsById = byId;
    }

    private List<IslandMobDefinition> loadDefinitions(Plugin plugin) {
        List<IslandMobDefinition> result = new ArrayList<>();
        ConfigurationSection mobs = plugin.getConfig().getConfigurationSection("island-mobs.mobs");
        if (mobs == null) {
            return result;
        }
        for (String id : mobs.getKeys(false)) {
            ConfigurationSection m = mobs.getConfigurationSection(id);
            if (m == null) {
                continue;
            }
            EntityType type;
            try {
                type = EntityType.valueOf(m.getString("entity-type", "ZOMBIE").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("island-mobs.mobs." + id + ": invalid entity-type '" + m.getString("entity-type") + "', skipping.");
                continue;
            }
            Material weapon = Material.matchMaterial(m.getString("weapon", "IRON_SWORD").toUpperCase(Locale.ROOT));
            String legendaryWeaponName = m.getString("legendary-weapon", "");
            LegendaryWeapon legendaryWeapon = null;
            if (!legendaryWeaponName.isBlank()) {
                try {
                    legendaryWeapon = LegendaryWeapon.valueOf(legendaryWeaponName.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("island-mobs.mobs." + id + ": invalid legendary-weapon '" + legendaryWeaponName + "', ignoring.");
                }
            }
            String displayNamePt = m.getString("display-name", id);
            result.add(new IslandMobDefinition(
                    id,
                    displayNamePt,
                    m.getString("display-name-en", displayNamePt),
                    type,
                    m.getDouble("health", 20.0),
                    m.getDouble("damage", 3.0),
                    m.getDouble("speed-multiplier", 1.0),
                    weapon == null ? Material.IRON_SWORD : weapon,
                    legendaryWeapon,
                    m.getDouble("drop-chance-percent", 0.0),
                    m.getBoolean("armored", false),
                    m.getString("head-texture", ""),
                    m.getInt("respawn-ticks", 200),
                    Math.max(0, m.getInt("count", 0))));
        }
        return result;
    }

    public IslandMobZone zone() {
        return this.zone;
    }

    /** Every non-blank head-texture Base64 configured across {@code island-mobs.mobs.*} - see {@code GeyserSkullExport}. */
    public Set<String> headTextures() {
        Set<String> textures = new HashSet<>();
        for (IslandMobDefinition def : this.definitions) {
            if (!def.headTexture().isBlank()) {
                textures.add(def.headTexture());
            }
        }
        return textures;
    }

    public boolean suppressNaturalSpawns() {
        return this.suppressNaturalSpawns;
    }

    public boolean ready() {
        return this.enabled && this.zone.biome() != null && !this.definitions.isEmpty();
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
        List<int[]> candidates = this.biomeCandidates(world);
        if (candidates.isEmpty()) {
            this.plugin.getLogger().warning("island-mobs: no '" + this.zone.biome().key() + "' biome found in the search area, skipping spawn.");
            return 0;
        }
        Collections.shuffle(candidates);
        int index = 0;
        for (IslandMobDefinition def : this.definitions) {
            for (int i = 0; i < def.count(); i++) {
                int[] c = candidates.get(index % candidates.size());
                index++;
                this.spawnOne(def, this.toLocation(world, c));
            }
        }
        return this.spawnedIds.size();
    }

    /** Spawns a single mob of the given kind at the given point and tracks it. */
    public void spawnOne(IslandMobDefinition def, Location point) {
        World world = point.getWorld();
        if (world == null) {
            return;
        }
        LivingEntity entity = (LivingEntity) world.spawnEntity(point, def.entityType());
        // No vanilla customName here (that's one fixed string for every viewer) - the
        // in-world name is rendered per-viewer language instead, see setLocalizedName.
        this.visuals.setLocalizedName(entity, def.displayName(), def.displayNameEn());
        if (entity instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
            zombie.setBaby(false);
        }
        entity.getPersistentDataContainer().set(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING, def.id());
        EntityEquipment equipment = entity.getEquipment();
        if (equipment != null) {
            equipment.setItemInMainHand(def.legendaryWeapon() != null ? this.legendary.create(def.legendaryWeapon(), Language.PT) : new ItemStack(def.weapon()));
            // Drops are handled by IslandMobListener's own chance roll (rollDrop), not by
            // this held copy - it must never drop on its own regardless of that roll.
            equipment.setItemInMainHandDropChance(0.0f);
            // Every undead mob here wears something on its head - a real vanilla headgear
            // (any helmet, not just a pumpkin) is what stops it from catching fire in
            // daylight, so even a mob with no custom head texture still gets a plain iron
            // helmet purely for that reason, not for looks.
            equipment.setHelmet(def.headTexture().isBlank() ? new ItemStack(Material.IRON_HELMET) : this.customHead(def.headTexture()));
            equipment.setHelmetDropChance(0.0f);
            if (def.armored()) {
                equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
                equipment.setChestplateDropChance(0.0f);
                equipment.setLeggingsDropChance(0.0f);
                equipment.setBootsDropChance(0.0f);
            }
        }
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(def.health());
        }
        entity.setHealth(def.health());
        AttributeInstance attackDamage = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackDamage != null) {
            attackDamage.setBaseValue(def.damage());
        }
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && def.speedMultiplier() != 1.0) {
            speed.setBaseValue(speed.getBaseValue() * def.speedMultiplier());
        }
        this.spawnedIds.add(entity.getUniqueId());
    }

    /** Called by {@link IslandMobListener} when one of these mobs dies - schedules a respawn of the same kind at the same point. */
    public void scheduleRespawn(String defId, Location point) {
        IslandMobDefinition def = this.definitionsById.get(defId);
        if (def == null) {
            return;
        }
        this.pendingRespawns.add(Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.spawnOne(def, point), Math.max(1, def.respawnTicks())));
    }

    /**
     * Called by {@link IslandMobListener} on death - rolls this mob kind's
     * drop-chance-percent and returns a fresh copy of its legendary-weapon drop in
     * {@code l} if it hits, else null. {@code l} must be the actual killer's language
     * (not a hardcoded default) - an item's name/lore is baked in once at creation,
     * so a drop built in the wrong language stays wrong forever once it's in someone's
     * inventory, unlike a menu-created copy that's always built fresh per viewer.
     */
    public ItemStack rollDrop(String defId, Language l) {
        IslandMobDefinition def = this.definitionsById.get(defId);
        if (def == null || def.legendaryWeapon() == null || def.dropChancePercent() <= 0.0) {
            return null;
        }
        if (ThreadLocalRandom.current().nextDouble(100.0) >= def.dropChancePercent()) {
            return null;
        }
        return this.legendary.create(def.legendaryWeapon(), l);
    }

    /** This entity's island-mob definition id, or null if it isn't one of ours. */
    public String islandMobId(LivingEntity entity) {
        String id = entity.getPersistentDataContainer().get(BestiaryCatalog.VARIANT_KEY, PersistentDataType.STRING);
        return id != null && this.definitionsById.containsKey(id) ? id : null;
    }

    /**
     * Removes every island mob currently alive - not just the ones this exact service
     * instance spawned (tracked in {@link #spawnedIds}, which starts empty again on
     * every server restart and so misses anything that survived from a previous
     * session), but every entity in the zone's world tagged with the variant PDC key
     * (which does survive a restart, since it's saved with the entity). Without this,
     * a restart would leave the old population alive - with whatever stale gear/state
     * it had before - and stack a brand new one on top of it instead of replacing it.
     */
    public void despawnAll() {
        World world = Bukkit.getWorld(this.zone.world());
        if (world != null) {
            for (LivingEntity entity : world.getEntitiesByClass(LivingEntity.class)) {
                if (this.islandMobId(entity) != null) {
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

    /** A player head wearing the given custom texture (base64 "Value"), falling back to a plain head if it's bad. */
    private ItemStack customHead(String texture) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
            item.setItemMeta(meta);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the spawn.
        }
        return item;
    }

    private Location toLocation(World world, int[] xz) {
        int y = world.getHighestBlockYAt(xz[0], xz[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return new Location(world, xz[0] + 0.5, y, xz[1] + 0.5);
    }

    /**
     * Scans the configured search area (island-mobs.min-x/max-x/min-z/max-z - just a
     * "look here" hint, not a strict boundary) for columns whose ground is actually the
     * Shadowed Graveyard biome. This is what makes the population follow wherever the
     * biome is actually painted rather than a fixed rectangle.
     */
    private List<int[]> biomeCandidates(World world) {
        List<int[]> candidates = new ArrayList<>();
        for (int x = this.searchMinX; x <= this.searchMaxX; x += CELL) {
            for (int z = this.searchMinZ; z <= this.searchMaxZ; z += CELL) {
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (world.getBiome(x, y, z) == this.zone.biome()) {
                    candidates.add(new int[]{x, z});
                }
            }
        }
        return candidates;
    }
}
