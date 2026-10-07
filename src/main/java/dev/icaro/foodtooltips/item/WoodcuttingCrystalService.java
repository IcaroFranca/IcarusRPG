package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
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
 * room to grow, not a Y-bounded search) and only plants where the ground (leaves skipped,
 * see below) is a bare {@link #PLANTABLE_GROUND} block - a column already topped by another
 * tree's own trunk fails that check on its own, which is what keeps this from ever stacking
 * a new tree directly into an existing one (per the player's own "cuidado para não lotar de
 * árvores"). The species planted matches the target column's own {@link Block#getBiome()}
 * (see {@link #treeTypeFor}), not always Spruce - a crystal placed in a Birch Forest regrows
 * birches.
 *
 * <p>{@link #pulseOne} samples the ground with {@link HeightMap#MOTION_BLOCKING_NO_LEAVES}
 * rather than the plain heightmap - the plain one treats leaves as solid, so it only ever
 * found bare dirt/grass with nothing at all overhead, meaning in practice this only ever
 * regrew a tree exactly where one had just been chopped down (the one spot with a fully
 * open column in an otherwise-canopied forest). Skipping past leaves to the real ground
 * underneath lets it find - and regrow into - the gaps and edges of a forest that was
 * never touched at all, not just literal clearings.
 *
 * <p>{@link #pulseOne} checks for an existing sapling among that pulse's own sampled columns
 * BEFORE ever trying to plant a brand new tree on bare ground - saplings are the crystal's own
 * priority, per the player's own explicit spec ("a prioridade dele são as sapplings"): a
 * player-planted or naturally-dropped sapling sitting in the area gets grown into a full tree
 * (via {@link #SAPLING_TREE_TYPE}, matching the sapling's own species regardless of the
 * column's biome - exactly like a real bonemeal application would) before this ever bothers
 * planting a fresh one from scratch.
 *
 * <p>{@link #pulseOne} also caps out at {@value #MAX_TREES_IN_AREA} trees already standing in
 * its own {@value #AREA_RADIUS}-block area (counted by {@link #countTreesInArea}, per the
 * player's own "limite de 10 árvores crescidas na área dele") - once that many columns are
 * already topped by a log/stem block, a pulse does nothing at all instead of trying to
 * squeeze in an eleventh tree.
 *
 * <p>{@link #treeTypeFor} isn't limited to the handful of vanilla {@link Biome} constants
 * in {@link #TREE_BY_BIOME}: a biome-adding datapack/plugin like Terralith registers its
 * biomes as additional {@link Biome} instances at runtime (there's no compile-time
 * constant for any of them to match against), so beyond that exact map this falls back to
 * matching keywords in the biome's own {@link NamespacedKey#getKey()} (e.g. "old_growth_pine_taiga",
 * or one of Terralith's own descriptively-named biomes like "temperate_rainforest") against
 * {@link #TREE_BY_KEYWORD}, then {@link #BARREN_KEYWORDS} for a biome that's obviously
 * treeless (desert, ocean, peaks...), defaulting to a plain oak for anything left
 * unrecognized rather than silently never planting anything there.
 */
public final class WoodcuttingCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "woodcutting_crystal");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:woodcutting_crystal".getBytes(StandardCharsets.UTF_8));
    private static final int PULSE_TICKS = 200;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    /** Half the side of the square area this crystal regrows trees in - a 20x20 square, per the player's own spec. */
    private static final int AREA_RADIUS = 10;
    /** How many random columns {@link #pulseOne} samples per pulse before giving up for that pass - cheap (one {@link World#getHighestBlockAt} each), so trying several before finding a valid spot is fine. */
    private static final int SAMPLE_ATTEMPTS = 12;
    /** Max trees {@link #countTreesInArea} lets stand in the crystal's own area before {@link #pulseOne} stops regrowing more - per the player's own spec. */
    private static final int MAX_TREES_IN_AREA = 10;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private static final Set<Material> PLANTABLE_GROUND = EnumSet.of(
            Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL,
            Material.ROOTED_DIRT, Material.MYCELIUM, Material.SAND, Material.RED_SAND, Material.MUD);

    /** Every vanilla sapling {@link #pulseOne} can grow into a full tree - see this class's own doc on priority. Deliberately the sapling's OWN species, not {@link #treeTypeFor}'s biome lookup - a Jungle Sapling planted outside a Jungle still grows into a Jungle tree, same as real bonemeal. */
    private static final Map<Material, TreeType> SAPLING_TREE_TYPE = Map.of(
            Material.OAK_SAPLING, TreeType.TREE,
            Material.SPRUCE_SAPLING, TreeType.REDWOOD,
            Material.BIRCH_SAPLING, TreeType.BIRCH,
            Material.JUNGLE_SAPLING, TreeType.JUNGLE,
            Material.ACACIA_SAPLING, TreeType.ACACIA,
            Material.DARK_OAK_SAPLING, TreeType.DARK_OAK,
            Material.CHERRY_SAPLING, TreeType.CHERRY);

    /** Exact matches for the vanilla biomes with one obvious species - checked before {@link #TREE_BY_KEYWORD}'s fallback. */
    private static final Map<Biome, TreeType> TREE_BY_BIOME = Map.ofEntries(
            Map.entry(Biome.PLAINS, TreeType.TREE),
            Map.entry(Biome.SUNFLOWER_PLAINS, TreeType.TREE),
            Map.entry(Biome.FOREST, TreeType.TREE),
            Map.entry(Biome.FLOWER_FOREST, TreeType.TREE),
            Map.entry(Biome.WINDSWEPT_FOREST, TreeType.TREE),
            Map.entry(Biome.BIRCH_FOREST, TreeType.BIRCH),
            Map.entry(Biome.OLD_GROWTH_BIRCH_FOREST, TreeType.BIRCH),
            Map.entry(Biome.DARK_FOREST, TreeType.DARK_OAK),
            Map.entry(Biome.PALE_GARDEN, TreeType.DARK_OAK),
            Map.entry(Biome.TAIGA, TreeType.REDWOOD),
            Map.entry(Biome.SNOWY_TAIGA, TreeType.REDWOOD),
            Map.entry(Biome.OLD_GROWTH_PINE_TAIGA, TreeType.MEGA_REDWOOD),
            Map.entry(Biome.OLD_GROWTH_SPRUCE_TAIGA, TreeType.MEGA_REDWOOD),
            Map.entry(Biome.GROVE, TreeType.REDWOOD),
            Map.entry(Biome.JUNGLE, TreeType.JUNGLE),
            Map.entry(Biome.SPARSE_JUNGLE, TreeType.JUNGLE),
            Map.entry(Biome.BAMBOO_JUNGLE, TreeType.JUNGLE),
            Map.entry(Biome.SAVANNA, TreeType.ACACIA),
            Map.entry(Biome.SAVANNA_PLATEAU, TreeType.ACACIA),
            Map.entry(Biome.WINDSWEPT_SAVANNA, TreeType.ACACIA),
            Map.entry(Biome.SWAMP, TreeType.SWAMP),
            Map.entry(Biome.MANGROVE_SWAMP, TreeType.MANGROVE),
            Map.entry(Biome.CHERRY_GROVE, TreeType.CHERRY));

    private record KeywordTree(String keyword, TreeType type) {}

    /**
     * Fallback for any {@link Biome} not in {@link #TREE_BY_BIOME} (every non-vanilla one,
     * Terralith's included, is one of these) - first keyword found in the biome's own
     * {@link NamespacedKey#getKey()} wins, most specific first (e.g. "sakura" before the
     * generic "grove" a Terralith "Sakura Grove" would otherwise also match).
     */
    private static final List<KeywordTree> TREE_BY_KEYWORD = List.of(
            new KeywordTree("sakura", TreeType.CHERRY),
            new KeywordTree("cherry", TreeType.CHERRY),
            new KeywordTree("blossom", TreeType.CHERRY),
            new KeywordTree("mangrove", TreeType.MANGROVE),
            new KeywordTree("bayou", TreeType.MANGROVE),
            new KeywordTree("bamboo", TreeType.JUNGLE),
            new KeywordTree("jungle", TreeType.JUNGLE),
            new KeywordTree("rainforest", TreeType.JUNGLE),
            new KeywordTree("birch", TreeType.BIRCH),
            new KeywordTree("dark", TreeType.DARK_OAK),
            new KeywordTree("shadow", TreeType.DARK_OAK),
            new KeywordTree("haunted", TreeType.DARK_OAK),
            new KeywordTree("spooky", TreeType.DARK_OAK),
            new KeywordTree("swamp", TreeType.SWAMP),
            new KeywordTree("marsh", TreeType.SWAMP),
            new KeywordTree("bog", TreeType.SWAMP),
            new KeywordTree("orchid", TreeType.SWAMP),
            new KeywordTree("savanna", TreeType.ACACIA),
            new KeywordTree("shrubland", TreeType.ACACIA),
            new KeywordTree("steppe", TreeType.ACACIA),
            new KeywordTree("old_growth", TreeType.MEGA_REDWOOD),
            new KeywordTree("taiga", TreeType.REDWOOD),
            new KeywordTree("pine", TreeType.REDWOOD),
            new KeywordTree("spruce", TreeType.REDWOOD),
            new KeywordTree("conifer", TreeType.REDWOOD),
            new KeywordTree("redwood", TreeType.REDWOOD),
            new KeywordTree("sequoia", TreeType.REDWOOD),
            new KeywordTree("boreal", TreeType.REDWOOD),
            new KeywordTree("wintry", TreeType.REDWOOD),
            new KeywordTree("siberian", TreeType.REDWOOD),
            new KeywordTree("alpine", TreeType.REDWOOD),
            new KeywordTree("grove", TreeType.REDWOOD));

    /** Checked after {@link #TREE_BY_KEYWORD} - a biome whose key contains one of these has no natural tree to regrow, so {@link #treeTypeFor} returns null instead of falling through to the generic oak default. */
    private static final Set<String> BARREN_KEYWORDS = Set.of(
            "desert", "badlands", "mesa", "canyon", "dune", "wasteland", "volcanic", "lava", "crater",
            "ocean", "sea", "beach", "shore", "reef", "river", "lake",
            "peak", "mountain", "cliff", "crag", "stony",
            "frozen", "ice", "snowy", "tundra", "glacial",
            "cave", "cavern", "grotto", "underground", "deep_dark",
            "nether", "end", "void", "mushroom", "basalt", "soul_sand");

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
        meta.lore(List.of(
                Component.text("Place on top of a block in a forest - grows saplings", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("into full trees first, then regrows a tree matching", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("the biome somewhere in a " + (AREA_RADIUS * 2) + "x" + (AREA_RADIUS * 2), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("area every " + (PULSE_TICKS / 20) + "s, up to " + MAX_TREES_IN_AREA + " trees.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak + right-click it to remove.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
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
            p.sendMessage(Component.text("There's already a Woodcutting Crystal too close to here.", NamedTextColor.RED));
            return;
        }
        ArmorStand stand = clicked.getWorld().spawn(spawnAt, ArmorStand.class);
        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setSmall(false);
        stand.setMarker(false);
        // See FarmCrystalService's own doc on this exact fix - a placed, persistent crystal
        // with a real collision box is an invisible wall exactly where it stands.
        stand.setCollidable(false);
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

    /**
     * Tries up to {@link #SAMPLE_ATTEMPTS} random columns within {@value #AREA_RADIUS} blocks
     * of {@code stand} - growing the first SAMPLED sapling found into a full tree always wins
     * over planting a brand new one, even if a bare-ground opportunity was sampled earlier in
     * the same pass (see this class's own doc): the whole batch of samples is drawn once, then
     * checked for a sapling in full before ever falling through to the bare-ground pass.
     */
    private void pulseOne(ArmorStand stand) {
        World world = stand.getWorld();
        Location origin = stand.getLocation();
        if (countTreesInArea(world, origin) >= MAX_TREES_IN_AREA) {
            return;
        }
        List<Block> samples = new ArrayList<>(SAMPLE_ATTEMPTS);
        for (int i = 0; i < SAMPLE_ATTEMPTS; i++) {
            int x = origin.getBlockX() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            int z = origin.getBlockZ() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            samples.add(world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
        }
        for (Block top : samples) {
            Block above = top.getRelative(BlockFace.UP);
            TreeType saplingType = SAPLING_TREE_TYPE.get(above.getType());
            if (saplingType != null && world.generateTree(above.getLocation(), saplingType)) {
                this.beamEffect(origin, above.getLocation());
                return;
            }
        }
        for (Block top : samples) {
            if (!PLANTABLE_GROUND.contains(top.getType())) {
                continue;
            }
            TreeType type = treeTypeFor(top.getBiome());
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

    /**
     * Counts columns within {@value #AREA_RADIUS} blocks of {@code origin} whose ground (leaves
     * skipped, same {@link HeightMap#MOTION_BLOCKING_NO_LEAVES} sampling {@link #pulseOne} itself
     * uses) is a log/stem block - i.e. how many trees already stand in the crystal's own area.
     * Stops early the moment {@value #MAX_TREES_IN_AREA} is reached, since the caller only ever
     * needs to know whether that cap is met, not the exact count above it.
     */
    private static int countTreesInArea(World world, Location origin) {
        int count = 0;
        int baseX = origin.getBlockX();
        int baseZ = origin.getBlockZ();
        for (int x = baseX - AREA_RADIUS; x <= baseX + AREA_RADIUS; x++) {
            for (int z = baseZ - AREA_RADIUS; z <= baseZ + AREA_RADIUS; z++) {
                Block top = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (isLogOrStem(top.getType())) {
                    count++;
                    if (count >= MAX_TREES_IN_AREA) {
                        return count;
                    }
                }
            }
        }
        return count;
    }

    private static boolean isLogOrStem(Material type) {
        return type.name().endsWith("_LOG") || type.name().endsWith("_STEM");
    }

    /** See this class's own doc for how this resolves a biome that isn't one of the vanilla constants in {@link #TREE_BY_BIOME}. */
    private static TreeType treeTypeFor(Biome biome) {
        TreeType exact = TREE_BY_BIOME.get(biome);
        if (exact != null) {
            return exact;
        }
        String key = biome.getKey().getKey();
        for (KeywordTree candidate : TREE_BY_KEYWORD) {
            if (key.contains(candidate.keyword())) {
                return candidate.type();
            }
        }
        for (String barren : BARREN_KEYWORDS) {
            if (key.contains(barren)) {
                return null;
            }
        }
        return TreeType.TREE;
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
