package dev.icaro.foodtooltips.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Treecapitator (Jungle Log Collections M7): a golden axe, unbreakable, with
 * +{@value #SWEEP_BONUS} Sweep (totaling {@value #BASE_SWEEP} + {@value #SWEEP_BONUS} =
 * 26 logs felled per swing) and +100 Foraging Fortune while held - same "Sweep" felling
 * mechanic and Swap Hands (F) throw ability as {@link SpruceAxeService} (see that
 * class's own doc for why F, not right-click), with the same "-50% do sweep" throw
 * penalty, just scaled up to this axe's own much larger base Sweep. Crafted from a
 * Spruce Axe plus 8 Jungle Cores, per explicit request.
 */
public final class TreecapitatorService {
    public static final int BASE_SWEEP = 1;
    public static final int SWEEP_BONUS = 25;
    /** "-50% do sweep" - half of {@link #BASE_SWEEP} + {@link #SWEEP_BONUS} (26), rounded, same formula {@link SpruceAxeService#THROWN_TOTAL} uses. */
    static final int THROWN_TOTAL = Math.round((BASE_SWEEP + SWEEP_BONUS) * 0.5f);

    private static final NamespacedKey AXE_KEY = new NamespacedKey("foodtooltips", "treecapitator");
    private static final int[] NEIGHBOR_OFFSETS = {-1, 0, 1};

    /** Players currently inside one of this class's own {@link #chop}/{@link #throwFell} felling passes - re-entrancy guard, same idea as {@code SpruceAxeService#fellingActive}. */
    private final Set<UUID> fellingActive = new HashSet<>();

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.GOLDEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(AXE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(true);
        meta.displayName(Component.text("Treecapitator", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                this.line("Sweep: +" + SWEEP_BONUS, NamedTextColor.DARK_GREEN),
                this.line("Foraging Fortune: +100", NamedTextColor.GOLD),
                Component.empty(),
                this.line("Swap Hands (F): throw the axe up to 50", NamedTextColor.GRAY),
                this.line("blocks to fell a tree, at half the Sweep.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTreecapitator(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(AXE_KEY, PersistentDataType.BYTE);
    }

    public boolean isFellingActive(Player p) {
        return this.fellingActive.contains(p.getUniqueId());
    }

    static boolean isFellable(Material m) {
        return m.name().endsWith("_LOG") || m.name().endsWith("_STEM");
    }

    /** Normal chop: {@code origin} isn't included here - this only schedules the EXTRA {@value #SWEEP_BONUS} logs {@link #connected} finds, breaking them one tick later - same "compute now, break next tick" split as {@code SpruceAxeService#chop}. */
    public void chop(Plugin plugin, Player p, Block origin) {
        List<Block> extra = this.connected(origin, SWEEP_BONUS);
        if (extra.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> this.breakAll(p, extra));
    }

    /** Thrown ability: {@code origin} is included, since nothing else breaks it - totals {@link #THROWN_TOTAL} logs including it. */
    public void throwFell(Player p, Block origin) {
        List<Block> targets = new ArrayList<>();
        targets.add(origin);
        targets.addAll(this.connected(origin, THROWN_TOTAL - 1));
        this.breakAll(p, targets);
    }

    private void breakAll(Player p, List<Block> blocks) {
        this.fellingActive.add(p.getUniqueId());
        try {
            for (Block b : blocks) {
                if (!b.getType().isAir()) {
                    p.breakBlock(b);
                }
            }
        } finally {
            this.fellingActive.remove(p.getUniqueId());
        }
    }

    /** 26-neighbor BFS from {@code origin}, restricted to blocks of {@code origin}'s own exact Material, up to {@code limit} blocks - same algorithm as {@code SpruceAxeService#connected}/Mining's own Vein. */
    private List<Block> connected(Block origin, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        Material material = origin.getType();
        List<Block> out = new ArrayList<>();
        Set<Pos> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(Pos.of(origin));
        block0:
        while (!queue.isEmpty() && out.size() < limit) {
            Block current = queue.removeFirst();
            for (int dx : NEIGHBOR_OFFSETS) {
                for (int dy : NEIGHBOR_OFFSETS) {
                    for (int dz : NEIGHBOR_OFFSETS) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        Block next = current.getRelative(dx, dy, dz);
                        if (!seen.add(Pos.of(next))) {
                            continue;
                        }
                        if (next.getType() != material) {
                            continue;
                        }
                        out.add(next);
                        queue.add(next);
                        if (out.size() >= limit) {
                            continue block0;
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for {@link #connected}'s flood fill. */
    private record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
