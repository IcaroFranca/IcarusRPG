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
 * The Spruce Axe (Spruce Log Collections M2): +{@value #SWEEP_BONUS} Sweep (every player has
 * a baseline of {@value #BASE_SWEEP}, so this axe totals {@value #BASE_SWEEP} + {@value
 * #SWEEP_BONUS} = 5 logs felled per swing) and +50 Foraging Fortune while held. "Sweep" has
 * no relation to Fortune's own probabilistic drop-copy system ({@code
 * skills.GeneralSkillListener#drops}) - it's a deterministic multi-BLOCK felling mechanic,
 * modeled directly on Mining's own Vein ability ({@code skills.GeneralSkillListener#vein}/
 * {@code #connected}): a 26-neighbor BFS restricted to the exact same {@link Material} as
 * the block that started it, breaking each match via {@link Player#breakBlock(Block)} so
 * Foraging XP/Collections/Fortune all credit normally per felled log, with zero duplicated
 * logic. {@link #fellingActive} guards against that same {@code breakBlock} call re-entering
 * this class's own {@code BlockBreakEvent} listener recursively - same idea as Vein's own
 * {@code veinActive} guard.
 *
 * <p>Swap Hands (F) throws the axe instead of chopping normally (see {@code
 * SpruceAxeListener#launch}, the same {@code ItemDisplay}-ray-march visual {@code
 * skills.SwordThrowListener} already uses - deliberately {@link
 * org.bukkit.event.player.PlayerSwapHandItemsEvent}, not right-click: {@code
 * PlayerInteractEvent} simply doesn't reliably fire for a right-click into open air with
 * nothing in reach - a long-standing, Spigot-acknowledged "intended, no workaround"
 * client-side limitation - which would have made the throw effectively unusable unless
 * aimed at something within melee range, defeating the whole point of a ranged ability)
 * - felling only {@link #THROWN_TOTAL} logs ("-50% do sweep"), since nothing else breaks
 * the block the thrown axe actually hits.
 */
public final class SpruceAxeService {
    public static final int BASE_SWEEP = 1;
    public static final int SWEEP_BONUS = 4;
    /** "-50% do sweep" - half of {@link #BASE_SWEEP} + {@link #SWEEP_BONUS} (5), rounded up so the throw ability is never worth less than 1 extra log over just breaking the one block it hits. */
    static final int THROWN_TOTAL = Math.round((BASE_SWEEP + SWEEP_BONUS) * 0.5f);

    private static final NamespacedKey AXE_KEY = new NamespacedKey("foodtooltips", "spruce_axe");
    private static final int[] NEIGHBOR_OFFSETS = {-1, 0, 1};

    /** Players currently inside one of this class's own {@link #chop}/{@link #throwFell} felling passes - re-entrancy guard, see this class's own doc. */
    private final Set<UUID> fellingActive = new HashSet<>();

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(AXE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Spruce Axe", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                this.line("Sweep: +" + SWEEP_BONUS, NamedTextColor.DARK_GREEN),
                this.line("Foraging Fortune: +50", NamedTextColor.GOLD),
                Component.empty(),
                this.line("Swap Hands (F): throw the axe up to 50", NamedTextColor.GRAY),
                this.line("blocks to fell a tree, at half the Sweep.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSpruceAxe(ItemStack item) {
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

    /**
     * Normal chop: {@code origin} (the block a real {@code BlockBreakEvent} is already
     * about to break on its own) isn't included here - this only schedules the EXTRA {@value
     * #SWEEP_BONUS} logs {@link #connected} finds, breaking them one tick later (matching
     * Vein's own "compute now while the block state is still real, break next tick" split).
     */
    public void chop(Plugin plugin, Player p, Block origin) {
        List<Block> extra = this.connected(origin, SWEEP_BONUS);
        if (extra.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> this.breakAll(p, extra));
    }

    /** Thrown ability: {@code origin} (the block the thrown axe actually hit) is included, since nothing else breaks it - totals {@link #THROWN_TOTAL} logs including it. Runs immediately (already off the main {@code BlockBreakEvent} call stack, inside the throw's own scheduled ray-march tick). */
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

    /** 26-neighbor BFS from {@code origin}, restricted to blocks of {@code origin}'s own exact Material, up to {@code limit} blocks (origin itself never included in the result). Mirrors {@code skills.GeneralSkillListener#connected}'s own Vein algorithm. */
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
