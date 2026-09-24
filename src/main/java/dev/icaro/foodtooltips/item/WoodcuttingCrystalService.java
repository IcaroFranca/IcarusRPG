package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Woodcutting Crystal (Spruce Log Collections M7) - {@code FarmCrystalService}'s own
 * "invisible {@link ArmorStand} wearing a custom head" floating/spinning technique, copied
 * wholesale (same {@link #place}/{@link #interact}/{@link #damage}/{@link #spin} shape), but
 * {@link #pulse} regrows trees instead of maturing crops.
 *
 * <p>Unlike Farm Crystal, this has no prior "where a tree used to stand" to remember (nothing
 * in this plugin tracks that), so {@link #pulseOne} samples random columns within {@value
 * #AREA_RADIUS} blocks (a {@value #AREA_RADIUS}x2 square, not a cube - a tree needs vertical
 * room to grow, not a Y-bounded search) and only plants where {@link World#getHighestBlockAt}
 * is already a bare {@link #PLANTABLE_GROUND} block - a column already topped by another
 * tree's own log/leaves fails that check on its own, which is what keeps this from ever
 * stacking a new tree into an existing canopy (per the player's own "cuidado para não lotar
 * de árvores" - no separate spacing scan needed, the height-map check already enforces it).
 * The species planted matches the target column's own {@link Block#getBiome()} (see {@link
 * #TREE_BY_BIOME}), not always Spruce - a crystal placed in a Birch Forest regrows birches.
 */
public final class WoodcuttingCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "woodcutting_crystal");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:woodcutting_crystal".getBytes(StandardCharsets.UTF_8));
    private static final int PULSE_TICKS = 400;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    /** Half the side of the square area this crystal regrows trees in - a 20x20 square, per the player's own spec. */
    private static final int AREA_RADIUS = 10;
    /** How many random columns {@link #pulseOne} samples per pulse before giving up for that pass - cheap (one {@link World#getHighestBlockAt} each), so trying several before finding a valid spot is fine. */
    private static final int SAMPLE_ATTEMPTS = 12;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private static final Set<Material> PLANTABLE_GROUND = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MYCELIUM, Material.SAND, Material.RED_SAND, Material.MUD);

    /** Which {@link TreeType} a regrown tree uses for a given {@link Biome} - biomes with no sensible tree (ocean, desert, badlands, etc.) are simply absent, so {@link #pulseOne} just tries another sample instead. */
    private static final Map<Biome, TreeType> TREE_BY_BIOME = Map.ofEntries(
            Map.entry(Biome.PLAINS, TreeType.TREE),
            Map.entry(Biome.FOREST, TreeType.TREE),
            Map.entry(Biome.BIRCH_FOREST, TreeType.BIRCH),
            Map.entry(Biome.DARK_FOREST, TreeType.DARK_OAK),
            Map.entry(Biome.TAIGA, TreeType.REDWOOD),
            Map.entry(Biome.OLD_GROWTH_PINE_TAIGA, TreeType.MEGA_REDWOOD),
            Map.entry(Biome.JUNGLE, TreeType.JUNGLE),
            Map.entry(Biome.SAVANNA, TreeType.ACACIA),
            Map.entry(Biome.SWAMP, TreeType.SWAMP),
            Map.entry(Biome.MANGROVE_SWAMP, TreeType.MANGROVE),
            Map.entry(Biome.CHERRY_GROVE, TreeType.CHERRY));

    private final Plugin plugin;

    public WoodcuttingCrystalService(Plugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isWoodcuttingCrystalItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    public ItemStack createItem() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        var meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(ITEM_PROFILE);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.WOODCUTTING_CRYSTAL));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Woodcutting Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isWoodcuttingCrystalEntity(Entity e) {
        return e.getType() == EntityType.ARMOR_STAND && e.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    /** Starts {@link #pulse}'s and {@link #spin}'s own repeating tasks - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::pulse, PULSE_TICKS, PULSE_TICKS);
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::spin, SPIN_TICKS, SPIN_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void place(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = e.getItem();
        if (!isWoodcuttingCrystalItem(item)) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location spawnAt = clicked.getLocation().add(0.5, 2.5, 0.5);
        if (this.withinRangeOfAnother(spawnAt)) {
            Language l = Language.of(p);
            p.sendMessage(Component.text(l.choose(
                    "Já existe um Woodcutting Crystal perto demais daqui.",
                    "There's already a Woodcutting Crystal too close to here."), NamedTextColor.RED));
            return;
        }
        ArmorStand stand = clicked.getWorld().spawn(spawnAt, ArmorStand.class);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(false);
        stand.setMarker(false);
        stand.setSilent(true);
        stand.setPersistent(true);
        stand.setCustomNameVisible(false);
        stand.setCanMove(false);
        stand.getEquipment().setHelmet(this.createItem());
        stand.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Sneak-right-click on a Woodcutting Crystal to remove it, item back in hand. */
    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().isSneaking() || !isWoodcuttingCrystalEntity(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        e.getRightClicked().remove();
        for (ItemStack overflow : e.getPlayer().getInventory().addItem(this.createItem()).values()) {
            e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), overflow);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (isWoodcuttingCrystalEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    private void spin() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isWoodcuttingCrystalEntity(entity)) {
                    Location loc = entity.getLocation();
                    entity.setRotation((loc.getYaw() + SPIN_DEGREES_PER_STEP) % 360.0f, loc.getPitch());
                }
            }
        }
    }

    private void pulse() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isWoodcuttingCrystalEntity(entity)) {
                    this.pulseOne((ArmorStand) entity);
                }
            }
        }
    }

    /** Tries up to {@link #SAMPLE_ATTEMPTS} random columns within {@value #AREA_RADIUS} blocks of {@code stand}, regrowing the first one that's bare ground with a known tree for its biome - see this class's own doc. */
    private void pulseOne(ArmorStand stand) {
        World world = stand.getWorld();
        Location origin = stand.getLocation();
        for (int i = 0; i < SAMPLE_ATTEMPTS; i++) {
            int x = origin.getBlockX() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            int z = origin.getBlockZ() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            Block top = world.getHighestBlockAt(x, z);
            if (!PLANTABLE_GROUND.contains(top.getType())) {
                continue;
            }
            TreeType type = TREE_BY_BIOME.get(top.getBiome());
            if (type == null) {
                continue;
            }
            Location plantAt = top.getLocation().add(0.5, 1.0, 0.5);
            if (world.generateTree(plantAt, type)) {
                this.beamEffect(origin, plantAt);
                return;
            }
        }
    }

    /** A brief one-shot particle trail from {@code from} to {@code to} - same {@code FarmCrystalService#beamEffect} technique. */
    private void beamEffect(Location from, Location to) {
        World world = from.getWorld();
        double distance = from.distance(to);
        int steps = Math.max(1, (int) (distance / BEAM_PARTICLE_SPACING));
        double dx = (to.getX() - from.getX()) / steps;
        double dy = (to.getY() - from.getY()) / steps;
        double dz = (to.getZ() - from.getZ()) / steps;
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.END_ROD, from.getX() + dx * i, from.getY() + dy * i, from.getZ() + dz * i, 1, 0, 0, 0, 0);
        }
    }

    /** Whether another Woodcutting Crystal already sits within {@value #AREA_RADIUS} blocks of {@code spawnAt} - keeps two crystals' own 20x20 areas from overlapping. */
    private boolean withinRangeOfAnother(Location spawnAt) {
        for (Entity entity : spawnAt.getWorld().getNearbyEntities(spawnAt, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (isWoodcuttingCrystalEntity(entity)) {
                return true;
            }
        }
        return false;
    }
}
