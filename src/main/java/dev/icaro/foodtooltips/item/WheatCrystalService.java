package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
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
 * Wheat Crystal (Cow/Leather Collection M1) - {@code FarmCrystalService}'s own "invisible
 * {@link ArmorStand} wearing a custom head" floating/spinning technique, copied wholesale (same
 * {@link #place}/{@link #interact}/{@link #damage}/{@link #spin} shape as that class and {@code
 * WoodcuttingCrystalService}), but {@link #pulse} spawns farm animals instead of maturing crops
 * or regrowing trees. The display name/texture ("Wheat Crystal", minecraft-heads.com Custom Head
 * ID 128322) is the exact one the player handed over for this - it names the head itself on that
 * site, not what it does in-game; the actual mechanic, per the player's own spec, is spawning
 * animals in a {@value #AREA_RADIUS}x2-blocks-per-side square around it, same shape as {@code
 * WoodcuttingCrystalService}'s own 20x20 tree-regrowth area.
 *
 * <p>Which species can come out of a given crystal is checked live against its own owner's
 * Collections progress on every pulse, not baked in at craft/place time - see {@link
 * #ANIMAL_UNLOCKS} and {@link #unlockedMobTypes}, same "live per-player check" technique {@code
 * biome.BiomeWandService} already uses for its own restricted wand. {@link #place} records the
 * placing player's UUID in the stand's own PDC ({@link #OWNER_KEY}) so this still works for an
 * offline owner (a crystal left running while its owner is logged off) - {@link
 * CollectionsProgressService}'s {@code OfflinePlayer}-typed read methods exist specifically for
 * this. Cow is always in the unlocked set for a legitimate owner (Cow Collection M1 is what
 * gates crafting the crystal itself, see {@code CollectionsCatalog}'s own Leather entry), and
 * Pig/Sheep/Chicken/Rabbit join it once that owner's own M1 for each is crossed - per the
 * player's own spec, every unlocked species has an equal chance each pulse (so once all five are
 * unlocked, each is a plain 1-in-5, 20%).
 *
 * <p>{@link #pulseOne} samples random columns within {@value #AREA_RADIUS} blocks (a square, not
 * a cube) and only spawns an animal on top of a bare {@link Material#GRASS_BLOCK} with at least
 * two blocks of open air above it (room for the mob to stand without suffocating) - same
 * {@link HeightMap#MOTION_BLOCKING_NO_LEAVES} sampling {@code WoodcuttingCrystalService} already
 * uses so a leaf canopy overhead doesn't block a valid spot. {@link #countAnimalsInArea} caps the
 * crystal's own area at {@value #MAX_ANIMALS_IN_AREA} animals total, any species combined
 * (counting every one already there, not just ones this crystal spawned - same "don't let an
 * unbounded herd pile up" reasoning as {@code WoodcuttingCrystalService#MAX_TREES_IN_AREA}), at
 * which point a pulse does nothing until some are cleared out (bred, killed, wandered off, etc).
 */
public final class WheatCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "wheat_crystal");
    /** The placing player's own UUID (as a plain string) - see this class's own doc on why the owner is tracked. */
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("foodtooltips", "wheat_crystal_owner");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:wheat_crystal".getBytes(StandardCharsets.UTF_8));
    /** 5 seconds, per the player's own explicit spec. */
    private static final int PULSE_TICKS = 100;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    /** Half the side of the square area this crystal spawns animals in - a 20x20 square, per the player's own spec. */
    private static final int AREA_RADIUS = 10;
    /** How many random columns {@link #pulseOne} samples per pulse before giving up for that pass. */
    private static final int SAMPLE_ATTEMPTS = 12;
    /** Max animals (any species combined) {@link #countAnimalsInArea} lets stand in the crystal's own area before {@link #pulseOne} stops spawning more - same spirit as {@code WoodcuttingCrystalService#MAX_TREES_IN_AREA}. */
    private static final int MAX_ANIMALS_IN_AREA = 10;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    /** One Farming Collections entry's own Milestone 1 unlocking a species on this crystal - see {@link #ANIMAL_UNLOCKS}. */
    private record AnimalUnlock(Material trackedMaterial, int milestoneNumber, EntityType mobType) {}

    /**
     * Every species this crystal can spawn, and the Farming Collections milestone that unlocks
     * each one for a given owner - checked live against {@link #collectionsProgress} on every
     * pulse (see this class's own doc), not baked into the item at craft time. Cow's own entry
     * is here too (not special-cased) purely for uniformity - a legitimate owner always clears
     * it, since Cow Collection M1 is what gates crafting the crystal itself.
     */
    private static final List<AnimalUnlock> ANIMAL_UNLOCKS = List.of(
            new AnimalUnlock(Material.LEATHER, 1, EntityType.COW),
            new AnimalUnlock(Material.PORKCHOP, 1, EntityType.PIG),
            new AnimalUnlock(Material.MUTTON, 1, EntityType.SHEEP),
            new AnimalUnlock(Material.CHICKEN, 1, EntityType.CHICKEN),
            new AnimalUnlock(Material.RABBIT, 1, EntityType.RABBIT));

    /** Every species {@link #ANIMAL_UNLOCKS} can ever produce - what {@link #countAnimalsInArea} counts towards the shared cap. */
    private static final Set<EntityType> ALL_ANIMAL_TYPES = EnumSet.copyOf(ANIMAL_UNLOCKS.stream().map(AnimalUnlock::mobType).toList());

    private final Plugin plugin;
    /** Wired in after construction (it's built later in {@code FoodTooltipsPlugin#onEnable} than this service) - see {@link #unlockedMobTypes}. */
    private CollectionsProgressService collectionsProgress;

    public WheatCrystalService(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Wired in after construction, same pattern as {@code biome.BiomeWandService#collectionsProgress}. */
    public void collectionsProgress(CollectionsProgressService collectionsProgress) {
        this.collectionsProgress = collectionsProgress;
    }

    public static boolean isWheatCrystalItem(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createItem() {
        var item = new ItemStack(Material.PLAYER_HEAD);
        var meta = (SkullMeta) item.getItemMeta();
        var profile = Bukkit.createProfile(ITEM_PROFILE);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.WHEAT_CRYSTAL));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Wheat Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Place on top of a block in a grassy area - spawns an", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("animal (Cow, Pig, Sheep, Chicken or Rabbit, as your own", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Farming Collections unlock them) somewhere in a " + (AREA_RADIUS * 2) + "x" + (AREA_RADIUS * 2), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("area every " + (PULSE_TICKS / 20) + "s, up to " + MAX_ANIMALS_IN_AREA + " animals.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Sneak + right-click it to remove.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isWheatCrystalEntity(Entity e) {
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
        if (!isWheatCrystalItem(item)) {
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
            p.sendMessage(Component.text("There's already a Wheat Crystal too close to here.", NamedTextColor.RED));
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
        stand.getEquipment().setHelmet(createItem());
        stand.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        stand.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, p.getUniqueId().toString());
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Sneak-right-click on a Wheat Crystal to remove it, item back in hand. */
    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().isSneaking() || !isWheatCrystalEntity(e.getRightClicked())) {
            return;
        }
        e.setCancelled(true);
        e.getRightClicked().remove();
        for (ItemStack overflow : e.getPlayer().getInventory().addItem(createItem()).values()) {
            e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), overflow);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (isWheatCrystalEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    private void spin() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isWheatCrystalEntity(entity)) {
                    Location loc = entity.getLocation();
                    entity.setRotation((loc.getYaw() + SPIN_DEGREES_PER_STEP) % 360.0f, loc.getPitch());
                }
            }
        }
    }

    private void pulse() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isWheatCrystalEntity(entity)) {
                    this.pulseOne((ArmorStand) entity);
                }
            }
        }
    }

    /**
     * Tries up to {@link #SAMPLE_ATTEMPTS} random columns within {@value #AREA_RADIUS} blocks of
     * {@code stand} - the first sampled column whose ground is bare grass with open air above it
     * gets an animal, same single-success-per-pulse shape as {@code
     * WoodcuttingCrystalService#pulseOne}. Which species is picked is uniform at random among
     * {@link #unlockedMobTypes} for this stand's own owner (see this class's own doc).
     */
    private void pulseOne(ArmorStand stand) {
        World world = stand.getWorld();
        Location origin = stand.getLocation();
        if (countAnimalsInArea(world, origin) >= MAX_ANIMALS_IN_AREA) {
            return;
        }
        for (int i = 0; i < SAMPLE_ATTEMPTS; i++) {
            int x = origin.getBlockX() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            int z = origin.getBlockZ() + ThreadLocalRandom.current().nextInt(-AREA_RADIUS, AREA_RADIUS + 1);
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (ground.getType() != Material.GRASS_BLOCK) {
                continue;
            }
            Block above = ground.getRelative(BlockFace.UP);
            Block aboveThat = above.getRelative(BlockFace.UP);
            if (!above.isEmpty() || !aboveThat.isEmpty()) {
                continue;
            }
            Location spawnAt = ground.getLocation().add(0.5, 1.0, 0.5);
            List<EntityType> unlocked = this.unlockedMobTypes(stand);
            EntityType chosen = unlocked.get(ThreadLocalRandom.current().nextInt(unlocked.size()));
            world.spawnEntity(spawnAt, chosen);
            this.beamEffect(origin, spawnAt);
            return;
        }
    }

    /**
     * Every species {@code stand}'s own owner (see {@link #OWNER_KEY}) has unlocked, per {@link
     * #ANIMAL_UNLOCKS} - an equal chance each, per the player's own spec (so this crystal always
     * picks uniformly among whatever this returns, never weighting Cow higher just because it's
     * everyone's own baseline). Falls back to {@code [COW]} alone if the owner can't be resolved
     * (missing PDC value on an item from before this field existed) or {@link
     * #collectionsProgress} hasn't been wired yet - same defensive fallback shape as {@code
     * biome.BiomeWandService#availableOptions}.
     */
    private List<EntityType> unlockedMobTypes(ArmorStand stand) {
        String rawOwner = stand.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        if (rawOwner == null || this.collectionsProgress == null) {
            return List.of(EntityType.COW);
        }
        OfflinePlayer owner = Bukkit.getOfflinePlayer(UUID.fromString(rawOwner));
        List<EntityType> unlocked = new ArrayList<>();
        for (AnimalUnlock unlock : ANIMAL_UNLOCKS) {
            Optional<CollectionsEntry> entry = CollectionsCatalog.find(unlock.trackedMaterial());
            if (entry.isPresent() && this.collectionsProgress.achieved(owner, entry.get()) >= unlock.milestoneNumber()) {
                unlocked.add(unlock.mobType());
            }
        }
        return unlocked.isEmpty() ? List.of(EntityType.COW) : unlocked;
    }

    /**
     * Counts every animal of any {@link #ALL_ANIMAL_TYPES} species (not just ones this crystal
     * spawned) within {@value #AREA_RADIUS} blocks of {@code origin} - same "cap on whatever's
     * already there, not a tracked total" reasoning as {@code
     * WoodcuttingCrystalService#countTreesInArea}. Stops early the moment {@value
     * #MAX_ANIMALS_IN_AREA} is reached.
     */
    private static int countAnimalsInArea(World world, Location origin) {
        int count = 0;
        for (Entity entity : world.getNearbyEntities(origin, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (ALL_ANIMAL_TYPES.contains(entity.getType())) {
                count++;
                if (count >= MAX_ANIMALS_IN_AREA) {
                    return count;
                }
            }
        }
        return count;
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

    /** Whether another Wheat Crystal already sits within {@value #AREA_RADIUS} blocks of {@code spawnAt} - keeps two crystals' own 20x20 areas from overlapping. */
    private boolean withinRangeOfAnother(Location spawnAt) {
        for (Entity entity : spawnAt.getWorld().getNearbyEntities(spawnAt, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (isWheatCrystalEntity(entity)) {
                return true;
            }
        }
        return false;
    }
}
