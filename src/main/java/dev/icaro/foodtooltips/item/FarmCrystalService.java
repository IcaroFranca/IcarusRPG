package dev.icaro.foodtooltips.item;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
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
 * Farm Crystal (Pumpkin Collections M6) - represented by a custom head both as the held item
 * and, once placed, as the floating/spinning thing itself: an invisible {@link ArmorStand}
 * wearing the head (the classic "floating head" technique - an invisible armor stand still
 * renders its own equipped helmet), since a real vanilla entity can't be reskinned to show an
 * arbitrary head texture the way an {@code EnderCrystal} (this class's own first attempt)
 * could float/spin/beam "for free" but never looked like anything but the vanilla diamond
 * crystal model. {@link #spin} (a fast repeating task) rotates every tracked stand a little
 * each pass for the "girando" half of the spec, since nothing here still gets that for free.
 *
 * <p>Removable ({@link #interact}, sneak-right-click gives the item back and despawns the
 * stand) - no removal method was specified originally, so this stays a judgment call, now
 * made explicit per the player's own follow-up ("preciso que o cristal seja removível").
 * {@link #damage} still cancels every hit so the stand can't be destroyed by simply attacking
 * it, same reasoning as the original End-Crystal-explosion concern, even though a plain
 * ArmorStand has no explosion of its own to worry about.
 *
 * <p>{@link #pulse} still matures exactly one random immature crop within {@value #RANGE}
 * blocks in every direction (a cube) every {@value #PULSE_TICKS} ticks, but the vanilla
 * End Crystal beam ({@code EnderCrystal#setBeamTarget}) no longer exists to reuse now that the
 * entity isn't one - {@link #beamEffect} draws a straight one-shot particle trail
 * ({@link Particle#END_ROD}) from the stand to the crop instead, a brief flash rather than a
 * lingering beam (particles are transient, unlike that real vanilla beam render).
 */
public final class FarmCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "farm_crystal");
    private static final UUID ITEM_PROFILE = UUID.nameUUIDFromBytes("icarusrpg:farm_crystal".getBytes(StandardCharsets.UTF_8));
    private static final int PULSE_TICKS = 400;
    /** How often {@link #spin} nudges every tracked stand's own yaw - fast enough to read as continuous rotation without a task running every single tick. */
    private static final int SPIN_TICKS = 2;
    private static final float SPIN_DEGREES_PER_STEP = 6.0f;
    private static final int RANGE = 4;
    private static final double BEAM_PARTICLE_SPACING = 0.3;

    private final Plugin plugin;

    public FarmCrystalService(Plugin plugin) {
        this.plugin = plugin;
    }

    public static boolean isFarmCrystalItem(ItemStack item) {
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
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", HeadTexture.FARM_CRYSTAL));
        meta.setPlayerProfile(profile);
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Farm Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isFarmCrystalEntity(Entity e) {
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
        if (!isFarmCrystalItem(item)) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location spawnAt = clicked.getLocation().add(0.5, 2.5, 0.5);
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

    /** Sneak-right-click on a Farm Crystal to remove it, item back in hand. */
    @EventHandler(ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (!e.getPlayer().isSneaking() || !isFarmCrystalEntity(e.getRightClicked())) {
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
        if (isFarmCrystalEntity(e.getEntity())) {
            e.setCancelled(true);
        }
    }

    private void spin() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isFarmCrystalEntity(entity)) {
                    Location loc = entity.getLocation();
                    entity.setRotation((loc.getYaw() + SPIN_DEGREES_PER_STEP) % 360.0f, loc.getPitch());
                }
            }
        }
    }

    private void pulse() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isFarmCrystalEntity(entity)) {
                    this.pulseOne((ArmorStand) entity);
                }
            }
        }
    }

    private void pulseOne(ArmorStand stand) {
        Block crop = this.randomImmatureCrop(stand.getLocation());
        if (crop == null) {
            return;
        }
        Ageable ageable = (Ageable) crop.getBlockData();
        ageable.setAge(ageable.getMaximumAge());
        crop.setBlockData(ageable);
        this.beamEffect(stand.getLocation(), crop.getLocation().add(0.5, 0.5, 0.5));
    }

    /** A brief one-shot particle trail from {@code from} to {@code to} - see this class's own doc on why this replaces the real vanilla End Crystal beam. */
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

    /** One random immature {@link Ageable} crop within {@value #RANGE} blocks of {@code center} in every direction (a cube) - null if none. */
    private Block randomImmatureCrop(Location center) {
        List<Block> candidates = new ArrayList<>();
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dy = -RANGE; dy <= RANGE; dy++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    Block block = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    BlockData data = block.getBlockData();
                    if (data instanceof Ageable a && a.getAge() < a.getMaximumAge()) {
                        candidates.add(block);
                    }
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
