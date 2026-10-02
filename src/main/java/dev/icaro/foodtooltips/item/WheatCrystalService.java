package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
 * WoodcuttingCrystalService}), but {@link #pulse} spawns Cows instead of maturing crops or
 * regrowing trees. The display name/texture ("Wheat Crystal", minecraft-heads.com Custom Head ID
 * 128322) is the exact one the player handed over for this - it names the head itself on that
 * site, not what it does in-game; the actual mechanic, per the player's own spec, is spawning
 * cows in a {@value #AREA_RADIUS}x2-blocks-per-side square around it, same shape as {@code
 * WoodcuttingCrystalService}'s own 20x20 tree-regrowth area.
 *
 * <p>{@link #pulseOne} samples random columns within {@value #AREA_RADIUS} blocks (a square, not
 * a cube) and only spawns a Cow on top of a bare {@link Material#GRASS_BLOCK} with at least two
 * blocks of open air above it (room for the cow to stand without suffocating) - same
 * {@link HeightMap#MOTION_BLOCKING_NO_LEAVES} sampling {@code WoodcuttingCrystalService} already
 * uses so a leaf canopy overhead doesn't block a valid spot. {@link #countCowsInArea} caps the
 * crystal's own area at {@value #MAX_COWS_IN_AREA} Cows (counting every Cow in the area, not
 * just ones this crystal spawned - same "don't let an unbounded herd pile up" reasoning as that
 * class's own {@code MAX_TREES_IN_AREA}), at which point a pulse does nothing until some are
 * cleared out (bred, killed, wandered off, etc).
 */
public final class WheatCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "wheat_crystal");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:wheat_crystal".getBytes(StandardCharsets.UTF_8));
    private static final int PULSE_TICKS = 400;
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    /** Half the side of the square area this crystal spawns Cows in - a 20x20 square, per the player's own spec. */
    private static final int AREA_RADIUS = 10;
    /** How many random columns {@link #pulseOne} samples per pulse before giving up for that pass. */
    private static final int SAMPLE_ATTEMPTS = 12;
    /** Max Cows {@link #countCowsInArea} lets stand in the crystal's own area before {@link #pulseOne} stops spawning more - same spirit as {@code WoodcuttingCrystalService#MAX_TREES_IN_AREA}. */
    private static final int MAX_COWS_IN_AREA = 10;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private final Plugin plugin;

    public WheatCrystalService(Plugin plugin) {
        this.plugin = plugin;
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
                Component.text("Place on top of a block in a grassy area - spawns a", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Cow somewhere in a " + (AREA_RADIUS * 2) + "x" + (AREA_RADIUS * 2), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("area every " + (PULSE_TICKS / 20) + "s, up to " + MAX_COWS_IN_AREA + " cows.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
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
     * gets a Cow, same single-success-per-pulse shape as {@code WoodcuttingCrystalService#pulseOne}.
     */
    private void pulseOne(ArmorStand stand) {
        World world = stand.getWorld();
        Location origin = stand.getLocation();
        if (countCowsInArea(world, origin) >= MAX_COWS_IN_AREA) {
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
            world.spawnEntity(spawnAt, EntityType.COW);
            this.beamEffect(origin, spawnAt);
            return;
        }
    }

    /**
     * Counts every Cow (not just ones this crystal spawned) within {@value #AREA_RADIUS} blocks
     * of {@code origin} - same "cap on whatever's already there, not a tracked total" reasoning
     * as {@code WoodcuttingCrystalService#countTreesInArea}. Stops early the moment {@value
     * #MAX_COWS_IN_AREA} is reached.
     */
    private static int countCowsInArea(World world, Location origin) {
        int count = 0;
        for (Entity entity : world.getNearbyEntities(origin, AREA_RADIUS, AREA_RADIUS, AREA_RADIUS)) {
            if (entity.getType() == EntityType.COW) {
                count++;
                if (count >= MAX_COWS_IN_AREA) {
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
