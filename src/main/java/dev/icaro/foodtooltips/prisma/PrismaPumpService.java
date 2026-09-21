package dev.icaro.foodtooltips.prisma;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Prismapump: a Dark Prismarine block that, placed next to existing water, instantly
 * levels the whole connected pocket of air on that same horizontal layer into water source
 * blocks - built for trenches and dug-out pools that would otherwise need a bucket placed in
 * every single block for the water to end up uniform.
 *
 * <p>Stops at the first solid block in every direction (never drains through walls) and at
 * {@link #SEARCH_LIMIT} blocks visited, so a pump placed next to open ocean can't send the
 * server on an unbounded flood-fill of the whole sea.
 *
 * <p>Admin-only for now, same as the other world-editing tools - reachable only via {@code
 * FoodTooltipsPlugin}'s {@code /prismapump} executor. The Builder's Wand's own {@code WATER}
 * mode reuses {@link #fillAdjacentWater} directly, so the same logic backs both.
 */
public final class PrismaPumpService {
    /**
     * Total blocks visited by {@link #fillAdjacentWater}'s flood fill (air or water alike),
     * not just how many actually got converted - a pump placed beside a large lake would
     * otherwise BFS-walk the lake's entire connected surface (visiting thousands of already-
     * water blocks) before ever hitting a "blocks filled" cap. 4,000 is generous for any
     * reasonable trench or pool while keeping the worst case bounded.
     */
    private static final int SEARCH_LIMIT = 4_000;

    private static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final NamespacedKey pumpKey;

    public PrismaPumpService(Plugin plugin) {
        this.pumpKey = new NamespacedKey(plugin, "prisma_pump");
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.DARK_PRISMARINE, 16);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.pumpKey, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(this.line(l.choose("Prismapump", "Prismapump"), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Coloque do lado de uma água pra nivelar", "Place next to water to level"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("todo o poço/canal conectado com água.", "the whole connected pool/channel with water."), NamedTextColor.GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isPump(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.pumpKey, PersistentDataType.BYTE);
    }

    /**
     * Looks for a water block horizontally adjacent to {@code target} and, if found, flood-
     * fills every reachable air block on that same Y level with water source blocks -
     * stopping at any solid block and at {@link #SEARCH_LIMIT}. Returns how many blocks were
     * actually turned into water (0 if no adjacent water was found at all).
     */
    public int fillAdjacentWater(Block target) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Block neighbor = target.getRelative(face);
            if (neighbor.getType() == Material.WATER) {
                return this.floodFillWater(neighbor);
            }
        }
        return 0;
    }

    /**
     * BFS across the same Y level as {@code seed}: air blocks get turned into water and
     * counted, and anything else stops that branch cold. Existing water is deliberately
     * re-set to a plain full source too, not just left alone: a hole that was dug a while
     * ago often already has some natural flowing (non-source) water sitting in it from
     * vanilla's own spread physics, and leaving that as-is is exactly the "not uniform"
     * look this tool exists to get rid of - the whole point is a flat, level pool/channel,
     * not just air turned to water with old flowing water left lumpy in between. Mirrors
     * the same {@code Pos}-keyed flood-fill idiom {@code BuilderWandService}/{@code
     * DestroyerHandService} use for their own face fills.
     */
    private int floodFillWater(Block seed) {
        int y = seed.getY();
        Set<Pos> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(Pos.of(seed));
        int filled = 0;
        while (!queue.isEmpty() && visited.size() < SEARCH_LIMIT) {
            Block b = queue.poll();
            if (b.getY() != y) {
                continue;
            }
            Material type = b.getType();
            if (type == Material.AIR || type == Material.WATER) {
                b.setType(Material.WATER);
                filled++;
            } else {
                continue;
            }
            for (BlockFace dir : HORIZONTAL_FACES) {
                Block next = b.getRelative(dir);
                if (visited.add(Pos.of(next))) {
                    queue.add(next);
                }
            }
        }
        return filled;
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for the flood fill (see {@link #floodFillWater}). */
    private record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
