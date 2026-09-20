package dev.icaro.foodtooltips.item;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Farm Crystal (Pumpkin Collections M6) - a real vanilla {@link EnderCrystal} entity, not a
 * block: right-clicking a block with the item spawns one 2 blocks above it, tagged via {@link
 * #CRYSTAL_KEY} so {@link #pulse} (a repeating task, {@value #PULSE_TICKS} ticks - "a cada 5
 * segundos") can tell a genuine Farm Crystal apart from an unrelated End Crystal (the End
 * fight, a player-built one, etc.) - the vanilla entity already floats and spins on its own,
 * so no custom animation is needed for that half of the spec.
 *
 * <p>Never actually explodes ({@link #damage} cancels every {@link EntityDamageEvent} a
 * tagged crystal takes) - a real End Crystal's own vanilla "hit it and it explodes like TNT"
 * behavior would be a serious, unwanted grief vector for a purely decorative Farming reward.
 * Removed instead by sneak-right-clicking it ({@link #interact}), which gives the item back
 * and despawns the entity - no removal method was specified, so this is a judgment call.
 *
 * <p>{@link #pulse} matures exactly one random immature crop within {@value #RANGE} blocks in
 * every direction (a cube, per "alcance de 4 blocos para TODAS as direções", not a sphere) -
 * "um crop" in the player's own spec reads as singular, so only one per pulse, not every crop
 * in range at once. {@link EnderCrystal#setBeamTarget} is vanilla's own real End Crystal/Ender
 * Dragon beam rendering, reused as-is for "vai fazer um raio apontando para" - no custom
 * particle math needed.
 */
public final class FarmCrystalService implements Listener {
    private static final NamespacedKey CRYSTAL_KEY = new NamespacedKey("foodtooltips", "farm_crystal");
    private static final int PULSE_TICKS = 100;
    /** How long the vanilla beam stays visible before {@link #pulse} clears it - a brief "zap", not a permanent beam. */
    private static final int BEAM_TICKS = 20;
    private static final int RANGE = 4;

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
        var item = new ItemStack(Material.END_CRYSTAL);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Farm Crystal", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isFarmCrystalEntity(Entity e) {
        return e.getType() == EntityType.END_CRYSTAL && e.getPersistentDataContainer().has(CRYSTAL_KEY, PersistentDataType.BYTE);
    }

    /** Starts {@link #pulse}'s own repeating task - call once from {@code FoodTooltipsPlugin#onEnable}. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(this.plugin, this::pulse, PULSE_TICKS, PULSE_TICKS);
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
        EnderCrystal crystal = (EnderCrystal) clicked.getWorld().spawnEntity(spawnAt, EntityType.END_CRYSTAL);
        crystal.setShowingBottom(false);
        crystal.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BYTE, (byte) 1);
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

    private void pulse() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(EnderCrystal.class)) {
                if (isFarmCrystalEntity(entity)) {
                    this.pulseOne((EnderCrystal) entity);
                }
            }
        }
    }

    private void pulseOne(EnderCrystal crystal) {
        Block crop = this.randomImmatureCrop(crystal.getLocation());
        if (crop == null) {
            return;
        }
        Ageable ageable = (Ageable) crop.getBlockData();
        ageable.setAge(ageable.getMaximumAge());
        crop.setBlockData(ageable);
        Location beamAt = crop.getLocation().add(0.5, 0.5, 0.5);
        crystal.setBeamTarget(beamAt);
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (crystal.isValid()) {
                crystal.setBeamTarget(null);
            }
        }, BEAM_TICKS);
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
