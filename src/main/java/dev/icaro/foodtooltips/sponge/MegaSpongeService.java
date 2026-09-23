package dev.icaro.foodtooltips.sponge;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

/**
 * The Mega Sponge: {@code dev.icaro.foodtooltips.prisma.PrismaPumpService}'s mirror
 * image - instead of leveling a connected pocket of air into water, it dries out a
 * whole connected body of water back into air. Right-click while aiming at any part of
 * the water (a raytrace that explicitly includes fluids, since the natural target - an
 * open sea's surface, as opposed to Prismapump's usual "next to a hole" placement -
 * usually has no solid block to aim at within reach) starts a full 3D flood fill (not
 * just a single Y level, unlike Prismapump - a sea has depth and slopes, not a flat
 * floor) that follows the water itself in every direction, converting each block it
 * finds to air.
 *
 * <p>A real sea can be enormous, so the fill is spread across several ticks instead of
 * running synchronously in one - see {@link #drain} - and still stops at {@link
 * #SEARCH_LIMIT}, generous enough to fully dry a large lake or bay in one go without
 * ever risking an unbounded fill on a whole ocean.
 *
 * <p>Admin-only for now, same as the other world-editing tools - reachable only via
 * {@code FoodTooltipsPlugin}'s {@code /megasponge} executor.
 */
public final class MegaSpongeService {
    /** How far a right-click's raytrace looks for water to start draining from. */
    private static final double RANGE = 64.0;

    /**
     * Total water blocks a single drain will ever turn into air. A connected sea can
     * easily run into the millions of blocks, so this isn't "the whole ocean" in the
     * most extreme case - it's a deliberately generous but still bounded ceiling (a very
     * large lake or bay's worth) that, combined with {@link #processBatch} spreading the
     * work across many ticks, keeps a single activation from ever running unbounded.
     */
    private static final int SEARCH_LIMIT = 200_000;

    /** How many water blocks {@link #processBatch} converts per tick - keeps each individual tick's cost small regardless of how big the overall drain ends up being. */
    private static final int BLOCKS_PER_TICK = 2_000;

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final Plugin plugin;
    private final NamespacedKey spongeKey;

    /** Players with a drain currently running - blocks a second one from being started (on themselves or, in practice, generally) before the first finishes. */
    private final Set<UUID> draining = new HashSet<>();

    public MegaSpongeService(Plugin plugin) {
        this.plugin = plugin;
        this.spongeKey = new NamespacedKey(plugin, "mega_sponge");
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.SPONGE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.spongeKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(this.line(l.choose("Mega Sponge", "Mega Sponge"), NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = List.of(
                this.line(l.choose("Clique direito mirando numa água pra secar", "Right-click while aiming at water to dry"), NamedTextColor.GRAY),
                this.line(l.choose("toda a água conectada a ela.", "the whole body of water connected to it."), NamedTextColor.GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSponge(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.spongeKey, PersistentDataType.BYTE);
    }

    /**
     * Raytraces from {@code p}'s eyes (fluids included, unlike most of this plugin's
     * other raytraces - the whole point here is finding water) and, if it lands on a
     * water block, starts draining the connected body it belongs to. Sends {@code p} an
     * actionbar explaining why nothing happened if there's no water in range or a drain
     * is already running for them; the eventual "done" actionbar is sent once the whole
     * fill finishes, which - being spread across ticks - is not necessarily this same
     * server tick.
     */
    public void drain(Player p) {
        Language l = Language.of(p);
        if (this.draining.contains(p.getUniqueId())) {
            p.sendActionBar(this.line(l.choose("Já tem uma secagem em andamento.", "A drain is already running."), NamedTextColor.RED));
            return;
        }
        RayTraceResult hit = p.rayTraceBlocks(RANGE, FluidCollisionMode.ALWAYS);
        Block seed = hit == null ? null : hit.getHitBlock();
        if (seed == null || seed.getType() != Material.WATER) {
            p.sendActionBar(this.line(l.choose("Mire numa água.", "Aim at water."), NamedTextColor.RED));
            return;
        }
        this.draining.add(p.getUniqueId());
        Set<Pos> visited = new HashSet<>();
        visited.add(Pos.of(seed));
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(seed);
        this.processBatch(p, l, queue, visited, 0);
    }

    /**
     * One batch of the drain's flood fill: pops up to {@link #BLOCKS_PER_TICK} blocks off
     * {@code queue}, turns any that are still water into air (re-checked here since many
     * ticks can pass between a block being queued and actually processed), and queues
     * their unvisited neighbors in all 6 directions. Reschedules itself one tick later if
     * there's still work left under {@link #SEARCH_LIMIT}; otherwise reports the final
     * total and frees {@code p} up to start another drain.
     */
    private void processBatch(Player p, Language l, ArrayDeque<Block> queue, Set<Pos> visited, int totalDried) {
        int dried = totalDried;
        int processed = 0;
        while (processed < BLOCKS_PER_TICK && !queue.isEmpty() && visited.size() < SEARCH_LIMIT) {
            Block b = queue.poll();
            processed++;
            if (b.getType() != Material.WATER) {
                continue;
            }
            b.setType(Material.AIR);
            dried++;
            for (BlockFace face : FACES) {
                Block next = b.getRelative(face);
                if (next.getType() == Material.WATER && visited.add(Pos.of(next))) {
                    queue.add(next);
                }
            }
        }
        int finalDried = dried;
        if (queue.isEmpty() || visited.size() >= SEARCH_LIMIT) {
            this.draining.remove(p.getUniqueId());
            if (p.isOnline()) {
                p.sendActionBar(this.line("-" + finalDried + " " + l.choose("água (seco)", "water (dried)"), NamedTextColor.AQUA));
            }
            return;
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                MegaSpongeService.this.processBatch(p, l, queue, visited, finalDried);
            }
        }.runTask(this.plugin);
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for the flood fill (see {@link #processBatch}). */
    private record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
