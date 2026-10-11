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
 * Prototype custom crop: the <b>Tomato</b>.
 *
 * <p>A Tomato Seed (Wheat Seeds with this class's mark, from {@code /tomatoseed}) planted on
 * farmland becomes a real Potatoes crop that this class remembers (in the chunk's own PDC) as a
 * tomato plant. Vanilla does the growing - light, water, Bone Meal - but the plant never reaches
 * Potatoes' last age (the one showing potatoes): the leafy age 6 is as far as it goes. Its fruit is
 * drawn by small {@link ItemDisplay} tomato heads among the leaves, like Hypixel's Greenhouse crops:
 * none while it's a sprout (age 0-3), {@value #FRUITS} green ones from age 4, red once it's ripe
 * (when vanilla would have grown it past age 6). Right click a ripe plant to pick
 * {@value #MIN_HARVEST}-{@value #MAX_HARVEST} Tomatoes; it drops back to age 4 and grows them
 * again. Breaking it gives the seed back (plus the Tomatoes if ripe), never potatoes.
 *
 * <p>Java only for now: Geyser doesn't render display entities, so Bedrock players see just the
 * leafy plant.
 */
public final class TomatoCropService implements Listener, CommandExecutor {
    static final String GREEN_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjc3YWQ3YjBlYmZiM2MyZWVhM2Y5Y2Q0NTI5ZTExNTMxOWFiYTNlNzEyZTMzMzU1MGM5OWQ1Y2YyNGIwODkifX19";
    static final String RIPE_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE4Njg2M2ZkZjNmM2UyOTc5ZWM2ZDk3OGEwODhkOGE0Zjg2NjRjNTBiNWI2ZDI5YWQ0YTdhYzI2NGEwMTcifX19";
    private static final NamespacedKey SEED_KEY = new NamespacedKey("foodtooltips", "tomato_seed");
    private static final NamespacedKey TOMATO_KEY = new NamespacedKey("foodtooltips", "tomato");
    /** A chunk's tomato plants: "x,y,z,ripe" entries (world coordinates) joined by ";". */
    private static final NamespacedKey CHUNK_KEY = new NamespacedKey("foodtooltips", "tomato_plants");
    static final int FRUIT_AGE = 4;
    static final int MAX_AGE = 6;
    static final int FRUITS = 3;
    static final int MIN_HARVEST = 2;
    static final int MAX_HARVEST = 4;
    /** Size of a tomato: a head is half a block wide, so this makes it {@code 0.5 * FRUIT_SCALE} blocks. */
    private static final float FRUIT_SCALE = 0.45f;
    /** Where each fruit hangs inside the plant's block (from its lower corner). */
    private static final double[][] FRUIT_SPOTS = {{0.28, 0.30, 0.32}, {0.70, 0.40, 0.55}, {0.42, 0.50, 0.74}};

    private final Plugin plugin;
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
        boolean ripe;
        /** What the fruits show now: 0 nothing, 1 green, 2 ripe. */
        int shown;
        final List<ItemDisplay> fruits = new ArrayList<>();

        Plant(boolean ripe) {
            this.ripe = ripe;
        }
    }

    public TomatoCropService(Plugin plugin) {
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
        this.plants.values().forEach(TomatoCropService::removeFruits);
        this.plants.clear();
    }

    // ---- Items ----

    public ItemStack seed(int amount) {
        ItemStack item = new ItemStack(Material.WHEAT_SEEDS, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(SEED_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Tomato Seed", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(line("Plant on farmland.", NamedTextColor.GRAY),
                line("Right click when ripe to pick the Tomatoes.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack tomato(int amount) {
        ItemStack item = head(RIPE_TEXTURE, "tomato");
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(TOMATO_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Tomato", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(3).saturation(3.6f).build());
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

    /** {@code /tomatoseed [player] [amount]} - admin, while the crop is a prototype. */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target = args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : sender instanceof Player p ? p : null;
        if (target == null) {
            sender.sendMessage(Component.text("Usage: /tomatoseed [player] [amount]", NamedTextColor.RED));
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
        target.getInventory().addItem(this.seed(amount));
        sender.sendMessage(Component.text(amount + " Tomato Seed given to " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    // ---- Planting / harvesting ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void plant(BlockPlaceEvent e) {
        if (has(e.getItemInHand(), TOMATO_KEY)) {
            e.setCancelled(true); // a Tomato is food, not a decoration block
            return;
        }
        if (!has(e.getItemInHand(), SEED_KEY) || e.getBlockPlaced().getType() != Material.WHEAT) {
            return;
        }
        Block b = e.getBlockPlaced();
        b.setType(Material.POTATOES);
        this.plants.put(Pos.of(b), new Plant(false));
        this.save(b.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void pick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) {
            return;
        }
        Block b = e.getClickedBlock();
        Plant plant = this.plants.get(Pos.of(b));
        if (plant == null || !plant.ripe) {
            return;
        }
        e.setCancelled(true);
        plant.ripe = false;
        setAge(b, FRUIT_AGE);
        Location at = b.getLocation().add(0.5, 0.4, 0.5);
        b.getWorld().dropItemNaturally(at, this.tomato(ThreadLocalRandom.current().nextInt(MIN_HARVEST, MAX_HARVEST + 1)));
        b.getWorld().playSound(at, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.0f);
        this.refresh(Pos.of(b), plant, b);
        this.save(b.getChunk());
    }

    /** Before anything else reads the drops: a tomato plant never drops potatoes - the seed back, plus its Tomatoes if ripe. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void drops(BlockDropItemEvent e) {
        Pos pos = Pos.of(e.getBlock());
        Plant plant = this.plants.remove(pos);
        if (plant == null) {
            return;
        }
        e.getItems().clear();
        removeFruits(plant);
        Location at = e.getBlock().getLocation().add(0.5, 0.3, 0.5);
        e.getBlock().getWorld().dropItemNaturally(at, this.seed(1));
        if (plant.ripe) {
            e.getBlock().getWorld().dropItemNaturally(at, this.tomato(ThreadLocalRandom.current().nextInt(MIN_HARVEST, MAX_HARVEST + 1)));
        }
        this.save(e.getBlock().getChunk());
    }

    // ---- Growth: never past the leafy age ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void grow(BlockGrowEvent e) {
        Plant plant = this.plants.get(Pos.of(e.getBlock()));
        if (plant != null && e.getNewState().getBlockData() instanceof Ageable age && age.getAge() > MAX_AGE) {
            e.setCancelled(true);
            setAge(e.getBlock(), MAX_AGE);
            this.ripen(e.getBlock(), plant);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void boneMeal(BlockFertilizeEvent e) {
        for (BlockState state : e.getBlocks()) {
            Plant plant = this.plants.get(Pos.of(state.getBlock()));
            if (plant != null && state.getBlockData() instanceof Ageable age && age.getAge() > MAX_AGE) {
                age.setAge(MAX_AGE);
                state.setBlockData(age);
                this.ripen(state.getBlock(), plant);
            }
        }
    }

    private void ripen(Block b, Plant plant) {
        if (!plant.ripe) {
            plant.ripe = true;
            this.save(b.getChunk());
        }
    }

    // ---- Protecting the plant (vanilla would drop potatoes) ----

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
        if (this.plants.containsKey(Pos.of(e.getToBlock()))) {
            e.setCancelled(true);
        }
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
            if (b.getType() != Material.POTATOES) {
                // Gone some other way (creative break, piston, explosion...).
                removeFruits(plant);
                it.remove();
                this.save(b.getChunk());
                continue;
            }
            if (b.getBlockData() instanceof Ageable age && age.getAge() > MAX_AGE) {
                setAge(b, MAX_AGE);
                this.ripen(b, plant);
            }
            this.refresh(entry.getKey(), plant, b);
        }
    }

    private void refresh(Pos pos, Plant plant, Block b) {
        int want = plant.ripe ? 2 : b.getBlockData() instanceof Ageable age && age.getAge() >= FRUIT_AGE ? 1 : 0;
        boolean missing = plant.fruits.stream().anyMatch(d -> !d.isValid());
        if (want == plant.shown && !missing) {
            return;
        }
        removeFruits(plant);
        plant.shown = want;
        if (want == 0) {
            return;
        }
        ItemStack fruit = head(want == 2 ? RIPE_TEXTURE : GREEN_TEXTURE, want == 2 ? "tomato" : "green_tomato");
        // Same spots every time for the same plant, but different from plant to plant.
        java.util.Random random = new java.util.Random(pos.hashCode());
        for (double[] spot : FRUIT_SPOTS) {
            Location at = b.getLocation().add(spot[0], spot[1], spot[2]);
            float yaw = random.nextFloat() * 360f;
            plant.fruits.add(b.getWorld().spawn(at, ItemDisplay.class, d -> {
                d.setItemStack(fruit);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setPersistent(false);
                // The head model sits on the item's floor; lift it so the display's position is the tomato's center.
                d.setTransformation(new Transformation(new Vector3f(0f, 0.25f * FRUIT_SCALE, 0f),
                        new AxisAngle4f((float) Math.toRadians(yaw), 0f, 1f, 0f),
                        new Vector3f(FRUIT_SCALE, FRUIT_SCALE, FRUIT_SCALE), new AxisAngle4f()));
            }));
        }
    }

    private static void removeFruits(Plant plant) {
        plant.fruits.forEach(ItemDisplay::remove);
        plant.fruits.clear();
        plant.shown = 0;
    }

    private static void setAge(Block b, int value) {
        if (b.getBlockData() instanceof Ageable age) {
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
        String data = chunk.getPersistentDataContainer().get(CHUNK_KEY, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return;
        }
        UUID world = chunk.getWorld().getUID();
        for (String entry : data.split(";")) {
            String[] f = entry.split(",");
            if (f.length == 4) {
                this.plants.putIfAbsent(new Pos(world, Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2])), new Plant(f[3].equals("1")));
            }
        }
    }

    private void save(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Pos, Plant> entry : this.plants.entrySet()) {
            Pos p = entry.getKey();
            if (p.world().equals(world) && p.x() >> 4 == chunk.getX() && p.z() >> 4 == chunk.getZ()) {
                if (!sb.isEmpty()) {
                    sb.append(';');
                }
                sb.append(p.x()).append(',').append(p.y()).append(',').append(p.z()).append(',').append(entry.getValue().ripe ? '1' : '0');
            }
        }
        if (sb.isEmpty()) {
            chunk.getPersistentDataContainer().remove(CHUNK_KEY);
        } else {
            chunk.getPersistentDataContainer().set(CHUNK_KEY, PersistentDataType.STRING, sb.toString());
        }
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
