package dev.icaro.foodtooltips.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * Custom crops drawn with player heads, Hypixel-Greenhouse style - see {@link Crop} for each one.
 *
 * <p>A crop's seed (a vanilla seed item with this class's mark, from the Farm Merchant or
 * {@code /tomatoseed}/{@code /cornseed}) planted on farmland becomes a real vanilla crop this class
 * remembers (in the chunk's own PDC) as that custom crop. Vanilla does the growing - light, water,
 * Bone Meal - but the plant never reaches its last age (the one showing the vanilla produce):
 * {@link Crop#maxAge} is as far as it goes. Its fruit is drawn by small {@link ItemDisplay} heads on
 * the plant: none while it's young, green from {@link Crop#fruitAge}, ripe once vanilla would have
 * grown it past {@link Crop#maxAge}. Right click a ripe plant to pick it; it drops back to {@link
 * Crop#harvestAge} and grows them again. Breaking it gives the seed back (plus the crop if ripe),
 * never the vanilla produce.
 *
 * <p>Java only for now: Geyser doesn't render display entities, so Bedrock players see just the
 * plant.
 */
public final class CustomCropService implements Listener, CommandExecutor {
    /**
     * One custom crop. {@code plantedAs} is the block its seed item places (vanilla's own), which
     * becomes {@code base} - the vanilla crop actually grown - right away.
     */
    public enum Crop {
        /** Tomato: Potatoes, leafy at age 4-6; 3 round fruits among the leaves. */
        TOMATO("tomato", "Tomato", NamedTextColor.RED, Material.WHEAT_SEEDS, Key.key("icarus", "tomato_seeds"), Material.WHEAT, Material.POTATOES,
                4, 6, 4, 2, 4, 3, 3.6f,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjc3YWQ3YjBlYmZiM2MyZWVhM2Y5Y2Q0NTI5ZTExNTMxOWFiYTNlNzEyZTMzMzU1MGM5OWQ1Y2YyNGIwODkifX19",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE4Njg2M2ZkZjNmM2UyOTc5ZWM2ZDk3OGEwODhkOGE0Zjg2NjRjNTBiNWI2ZDI5YWQ0YTdhYzI2NGEwMTcifX19",
                new Vector3f(0.45f, 0.45f, 0.45f),
                new double[][]{{0.28, 0.30, 0.32}, {0.70, 0.40, 0.55}, {0.42, 0.50, 0.74}}),
        /** Corn: the Pitcher crop, 2 blocks tall at age 3 (before it flowers); 4 long ears up its sides. */
        CORN("corn", "Corn", NamedTextColor.YELLOW, Material.PITCHER_POD, null, Material.PITCHER_CROP, Material.PITCHER_CROP,
                3, 3, 3, 2, 4, 4, 4.8f,
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2VjZDZkZWIzODAyNWUzMjJjZWFlYzc2OWYxMzU1MWVhYTgwMzhjN2ZlYzMxYTg1Y2U2ZDk0MTdiMWM3Njk5NCJ9fX0=",
                "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDM5MWRmZmJlYTJmYzNmMmFkNzhhNjIzZjQ5YmY3ZTExMjE2OTQxMTJjMzc1OWZlZWQ0MTU2ZmMyYmE0NmMwIn19fQ==",
                new Vector3f(0.32f, 0.6f, 0.32f),
                new double[][]{{0.22, 0.55, 0.30}, {0.78, 0.80, 0.62}, {0.30, 1.15, 0.72}, {0.72, 1.40, 0.36}});

        final String id;
        final String name;
        final NamedTextColor color;
        final Material seedMaterial;
        /** The seed's IcarusTexture icon, or null to keep the vanilla seed's. */
        final Key seedModel;
        final Material plantedAs;
        final Material base;
        /** From this age the plant shows green fruit. */
        final int fruitAge;
        /** The plant never grows past this age - growing past it ripens the fruit instead. */
        final int maxAge;
        /** The age a picked plant goes back to. */
        final int harvestAge;
        final int minHarvest;
        final int maxHarvest;
        final int nutrition;
        final float saturation;
        final String greenTexture;
        final String ripeTexture;
        /** A head is half a block wide: this scales it per axis (corn ears are stretched up). */
        final Vector3f fruitScale;
        /** Where each fruit hangs, from the plant's lower block's own lower corner (y can go past 1 on a 2-tall plant). */
        final double[][] fruitSpots;
        final NamespacedKey seedKey;
        final NamespacedKey fruitKey;
        /** A chunk's plants of this crop: "x,y,z,ripe" entries (world coordinates) joined by ";". */
        final NamespacedKey chunkKey;

        Crop(String id, String name, NamedTextColor color, Material seedMaterial, Key seedModel, Material plantedAs, Material base,
                int fruitAge, int maxAge, int harvestAge, int minHarvest, int maxHarvest, int nutrition, float saturation,
                String greenTexture, String ripeTexture, Vector3f fruitScale, double[][] fruitSpots) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.seedMaterial = seedMaterial;
            this.seedModel = seedModel;
            this.plantedAs = plantedAs;
            this.base = base;
            this.fruitAge = fruitAge;
            this.maxAge = maxAge;
            this.harvestAge = harvestAge;
            this.minHarvest = minHarvest;
            this.maxHarvest = maxHarvest;
            this.nutrition = nutrition;
            this.saturation = saturation;
            this.greenTexture = greenTexture;
            this.ripeTexture = ripeTexture;
            this.fruitScale = fruitScale;
            this.fruitSpots = fruitSpots;
            this.seedKey = new NamespacedKey("foodtooltips", id + "_seed");
            this.fruitKey = new NamespacedKey("foodtooltips", id);
            this.chunkKey = new NamespacedKey("foodtooltips", id + "_plants");
        }
    }

    private final Plugin plugin;
    /** Every known plant, keyed by its lower block. */
    private final Map<Pos, Plant> plants = new HashMap<>();

    private record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }

        Block block() {
            World w = Bukkit.getWorld(this.world);
            return w == null ? null : w.getBlockAt(this.x, this.y, this.z);
        }
    }

    private static final class Plant {
        final Crop crop;
        boolean ripe;
        /** What the fruits show now: 0 nothing, 1 green, 2 ripe. */
        int shown;
        final List<ItemDisplay> fruits = new ArrayList<>();

        Plant(Crop crop, boolean ripe) {
            this.crop = crop;
            this.ripe = ripe;
        }
    }

    public CustomCropService(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                this.load(c);
            }
        }
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 10L, 10L);
    }

    /** Removes every fruit display - from {@code FoodTooltipsPlugin#onDisable}. The plants themselves stay saved in their chunks. */
    public void clearAll() {
        this.plants.values().forEach(CustomCropService::removeFruits);
        this.plants.clear();
    }

    // ---- Items ----

    public ItemStack seed(Crop crop, int amount) {
        ItemStack item = new ItemStack(crop.seedMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(crop.seedKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(crop.name + " Seed", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Plant on farmland.", NamedTextColor.GRAY),
                line("Right click when ripe to pick the " + (crop == Crop.TOMATO ? "Tomatoes" : crop.name) + ".", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        if (crop.seedModel != null) {
            item.setData(DataComponentTypes.ITEM_MODEL, crop.seedModel);
        }
        return item;
    }

    public ItemStack fruit(Crop crop, int amount) {
        ItemStack item = head(crop.ripeTexture, crop.id);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(crop.fruitKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text(crop.name, crop.color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(crop.nutrition).saturation(crop.saturation).build());
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable().build());
        return item;
    }

    private static ItemStack head(String texture, String id) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("icarusrpg:" + id).getBytes(StandardCharsets.UTF_8)));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        item.setItemMeta(meta);
        return item;
    }

    private static boolean has(ItemStack item, NamespacedKey key) {
        return item != null && !item.isEmpty() && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** {@code /<crop>seed [player] [amount]} ({@code /tomatoseed}, {@code /cornseed}) - admin. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Crop crop = command.getName().equalsIgnoreCase("cornseed") ? Crop.CORN : Crop.TOMATO;
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Component.text("Usage: /" + command.getName() + " [player] [amount]", NamedTextColor.RED));
            return true;
        }
        int amount = 16;
        if (args.length >= 2) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                // keep 16
            }
        }
        target.getInventory().addItem(this.seed(crop, amount));
        sender.sendMessage(Component.text(amount + " " + crop.name + " Seed given to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    // ---- Planting / harvesting ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void plant(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        for (Crop crop : Crop.values()) {
            if (has(hand, crop.fruitKey)) {
                e.setCancelled(true); // the crop is food, not a decoration block
                return;
            }
            if (has(hand, crop.seedKey) && e.getBlockPlaced().getType() == crop.plantedAs) {
                Block b = e.getBlockPlaced();
                if (crop.plantedAs != crop.base) {
                    b.setType(crop.base);
                }
                this.plants.put(Pos.of(b), new Plant(crop, false));
                this.save(b.getChunk());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void pick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Block b = lowerHalf(e.getClickedBlock());
        Plant plant = this.plants.get(Pos.of(b));
        if (plant == null || !plant.ripe) {
            return;
        }
        e.setCancelled(true);
        plant.ripe = false;
        setAge(b, plant.crop.harvestAge);
        Location at = b.getLocation().add(0.5, 0.4, 0.5);
        b.getWorld().dropItemNaturally(at, this.fruit(plant.crop, harvest(plant.crop)));
        b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);
        this.refresh(Pos.of(b), plant, b);
        this.save(b.getChunk());
    }

    /** Before anything else reads the drops: a custom crop never drops the vanilla produce - the seed back, plus the crop if ripe. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void drops(BlockDropItemEvent e) {
        Block b = e.getBlock();
        // The top half of a 2-tall crop was broken: the plant is keyed by the block under it.
        if (e.getBlockState().getBlockData() instanceof Bisected half && half.getHalf() == Bisected.Half.TOP) {
            b = b.getRelative(0, -1, 0);
        }
        Plant plant = this.plants.remove(Pos.of(b));
        if (plant == null) {
            return;
        }
        e.getItems().clear();
        removeFruits(plant);
        Location at = b.getLocation().add(0.5, 0.3, 0.5);
        b.getWorld().dropItemNaturally(at, this.seed(plant.crop, 1));
        if (plant.ripe) {
            b.getWorld().dropItemNaturally(at, this.fruit(plant.crop, harvest(plant.crop)));
        }
        this.save(b.getChunk());
    }

    private static int harvest(Crop crop) {
        return ThreadLocalRandom.current().nextInt(crop.minHarvest, crop.maxHarvest + 1);
    }

    // ---- Growth: never past the crop's max age ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void grow(BlockGrowEvent e) {
        Block b = lowerHalf(e.getBlock());
        Plant plant = this.plants.get(Pos.of(b));
        if (plant != null && e.getNewState().getBlockData() instanceof Ageable age && age.getAge() > plant.crop.maxAge) {
            e.setCancelled(true);
            setAge(b, plant.crop.maxAge);
            this.ripen(b, plant);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void boneMeal(BlockFertilizeEvent e) {
        for (BlockState state : e.getBlocks()) {
            Block b = state.getBlockData() instanceof Bisected half && half.getHalf() == Bisected.Half.TOP ? state.getBlock().getRelative(0, -1, 0) : state.getBlock();
            Plant plant = this.plants.get(Pos.of(b));
            if (plant != null && state.getBlockData() instanceof Ageable age && age.getAge() > plant.crop.maxAge) {
                age.setAge(plant.crop.maxAge);
                state.setBlockData(age);
                this.ripen(b, plant);
            }
        }
    }

    private void ripen(Block b, Plant plant) {
        if (!plant.ripe) {
            plant.ripe = true;
            this.save(b.getChunk());
        }
    }

    // ---- Protecting the plant (vanilla would drop its own produce) ----

    @EventHandler(ignoreCancelled = true)
    public void trample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && this.onFarmland(e.getClickedBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void trample(EntityInteractEvent e) {
        if (this.onFarmland(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    private boolean onFarmland(Block farmland) {
        return farmland.getType() == Material.FARMLAND && this.plants.containsKey(Pos.of(farmland.getRelative(0, 1, 0)));
    }

    @EventHandler(ignoreCancelled = true)
    public void water(BlockFromToEvent e) {
        Block to = e.getToBlock();
        if (this.plants.containsKey(Pos.of(to)) || this.plants.containsKey(Pos.of(to.getRelative(0, -1, 0))) && isBase(to)) {
            e.setCancelled(true);
        }
    }

    private boolean isBase(Block b) {
        Plant below = this.plants.get(Pos.of(b.getRelative(0, -1, 0)));
        return below != null && b.getType() == below.crop.base;
    }

    // ---- Fruit displays ----

    private void tick() {
        Iterator<Map.Entry<Pos, Plant>> it = this.plants.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Pos, Plant> entry = it.next();
            Block b = entry.getKey().block();
            if (b == null || !b.getWorld().isChunkLoaded(b.getX() >> 4, b.getZ() >> 4)) {
                continue;
            }
            Plant plant = entry.getValue();
            if (b.getType() != plant.crop.base) {
                // Gone some other way (creative break, piston, explosion...).
                removeFruits(plant);
                it.remove();
                this.save(b.getChunk());
                continue;
            }
            if (b.getBlockData() instanceof Ageable age && age.getAge() > plant.crop.maxAge) {
                setAge(b, plant.crop.maxAge);
                this.ripen(b, plant);
            }
            this.refresh(entry.getKey(), plant, b);
        }
    }

    private void refresh(Pos pos, Plant plant, Block b) {
        Crop crop = plant.crop;
        int want = plant.ripe ? 2 : b.getBlockData() instanceof Ageable age && age.getAge() >= crop.fruitAge ? 1 : 0;
        boolean missing = plant.fruits.stream().anyMatch(d -> !d.isValid());
        if (want == plant.shown && !missing) {
            return;
        }
        removeFruits(plant);
        plant.shown = want;
        if (want == 0) {
            return;
        }
        ItemStack fruit = head(want == 2 ? crop.ripeTexture : crop.greenTexture, want == 2 ? crop.id : "green_" + crop.id);
        // Same spots every time for the same plant, but different from plant to plant.
        java.util.Random random = new java.util.Random(pos.hashCode());
        for (double[] spot : crop.fruitSpots) {
            Location at = b.getLocation().add(spot[0], spot[1], spot[2]);
            float yaw = random.nextFloat() * 360f;
            plant.fruits.add(b.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(fruit);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                // The head model sits on the item's floor; lift it so the display's position is the fruit's center.
                d.setTransformation(new Transformation(new Vector3f(0f, 0.25f * crop.fruitScale.y, 0f),
                        new AxisAngle4f((float) Math.toRadians(yaw), 0f, 1f, 0f),
                        new Vector3f(crop.fruitScale), new AxisAngle4f()));
            }));
        }
    }

    private static void removeFruits(Plant plant) {
        plant.fruits.forEach(ItemDisplay::remove);
        plant.fruits.clear();
        plant.shown = 0;
    }

    /** {@code b} itself, or the block under it when {@code b} is the top half of a 2-tall plant. */
    private static Block lowerHalf(Block b) {
        return b.getBlockData() instanceof Bisected half && half.getHalf() == Bisected.Half.TOP ? b.getRelative(0, -1, 0) : b;
    }

    /** Sets the plant's age - on both halves of a 2-tall one, which both carry it. */
    private static void setAge(Block lower, int value) {
        setAgeOf(lower, value);
        Block upper = lower.getRelative(0, 1, 0);
        if (upper.getType() == lower.getType() && upper.getBlockData() instanceof Bisected) {
            setAgeOf(upper, value);
        }
    }

    private static void setAgeOf(Block b, int value) {
        BlockData data = b.getBlockData();
        if (data instanceof Ageable age) {
            age.setAge(value);
            b.setBlockData(age, false);
        }
    }

    // ---- Chunk storage ----

    @EventHandler
    public void chunkLoad(ChunkLoadEvent e) {
        this.load(e.getChunk());
    }

    @EventHandler
    public void chunkUnload(ChunkUnloadEvent e) {
        UUID world = e.getWorld().getUID();
        int cx = e.getChunk().getX();
        int cz = e.getChunk().getZ();
        this.plants.entrySet().removeIf(entry -> {
            Pos p = entry.getKey();
            if (p.world().equals(world) && p.x() >> 4 == cx && p.z() >> 4 == cz) {
                removeFruits(entry.getValue());
                return true;
            }
            return false;
        });
    }

    private void load(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        for (Crop crop : Crop.values()) {
            String data = chunk.getPersistentDataContainer().get(crop.chunkKey, PersistentDataType.STRING);
            if (data == null || data.isEmpty()) {
                continue;
            }
            for (String entry : data.split(";")) {
                String[] f = entry.split(",");
                if (f.length == 4) {
                    this.plants.putIfAbsent(new Pos(world, Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])), new Plant(crop, f[3].equals("1")));
                }
            }
        }
    }

    private void save(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        for (Crop crop : Crop.values()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Pos, Plant> entry : this.plants.entrySet()) {
                Pos p = entry.getKey();
                if (entry.getValue().crop == crop && p.world().equals(world) && p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ()) {
                    if (!sb.isEmpty()) {
                        sb.append(';');
                    }
                    sb.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(',').append(entry.getValue().ripe ? '1' : '0');
                }
            }
            if (sb.isEmpty()) {
                chunk.getPersistentDataContainer().remove(crop.chunkKey);
            } else {
                chunk.getPersistentDataContainer().set(crop.chunkKey, PersistentDataType.STRING, sb.toString());
            }
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
