package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsEntry;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Tilling Hoe (Beetroot Collection M1) - one single craftable hoe whose own tilling area
 * grows live with the owner's own Beetroot Collection progress ({@link #areaRadius}, same
 * "live per-player check, not baked into the item at craft time" technique {@code
 * biome.BiomeWandService}/{@code AnimalCrystalService} already use), per the player's own
 * "uma enxada que ara 3x3, 5x5, 7x7 e 9x9" spec - one item, not four separate recipes, since
 * that's what "a hoe" (singular) that grows through the ladder actually means. M1 unlocks the
 * recipe at a 3x3 area; M3/M5/M7 are plain feature-unlock milestones (no recipe of their own,
 * same "Wardrobe column" shape {@code collections.CollectionsCatalog}'s own Leather entry
 * uses) that grow the SAME already-crafted hoe to 5x5/7x7/9x9 without ever needing to recraft.
 *
 * <p>{@link #till} replaces vanilla's own single-block till entirely (cancels the interaction,
 * same "cancel, then do our own thing" idiom this plugin's other custom tools use) - tilling
 * every {@link #TILLABLE} block in the area to {@link Material#FARMLAND}, centered on the
 * clicked block and at its own Y level (farmland is always a flat horizontal layer, so there's
 * no reason to till in 3D). Same "needs open air directly above" requirement real vanilla
 * tilling has, checked per block so the area naturally skips anything already built over.
 */
public final class TillingHoeService implements Listener {
    private static final NamespacedKey HOE_KEY = new NamespacedKey("foodtooltips", "tilling_hoe");
    private static final Set<Material> TILLABLE = EnumSet.of(Material.DIRT, Material.GRASS_BLOCK, Material.DIRT_PATH);

    /** Wired in after construction (it's built later in {@code FoodTooltipsPlugin#onEnable} than this service) - see {@link #areaRadius}. */
    private CollectionsProgressService collectionsProgress;

    /** Wired in after construction, same pattern as {@code biome.BiomeWandService#collectionsProgress}. */
    public void collectionsProgress(CollectionsProgressService collectionsProgress) {
        this.collectionsProgress = collectionsProgress;
    }

    public static boolean isTillingHoe(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(HOE_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack createItem() {
        ItemStack item = new ItemStack(Material.IRON_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(HOE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Tilling Hoe", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Tills a whole square of farmland at once,", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("centered on the block you right-click.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Area grows with your own Beetroot Collection:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("3x3 → 5x5 → 7x7 → 9x9.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Half the side of the square area {@link #till} tills, by {@code p}'s own current Beetroot
     * Collection milestone count - 1 (3x3) through 4 (9x9). Falls back to 1 if {@link
     * #collectionsProgress} hasn't been wired yet, same defensive shape as every other
     * live-checked item in this plugin.
     */
    private int areaRadius(Player p) {
        if (this.collectionsProgress == null) {
            return 1;
        }
        CollectionsEntry beetroot = CollectionsCatalog.find(Material.BEETROOTS).orElseThrow();
        return radiusFor(this.collectionsProgress.achieved(p, beetroot));
    }

    /** Pure milestone-to-radius math (no Bukkit statics touched) - see {@code TillingHoeServiceTest}. */
    static int radiusFor(int achieved) {
        if (achieved >= 7) {
            return 4;
        }
        if (achieved >= 5) {
            return 3;
        }
        if (achieved >= 3) {
            return 2;
        }
        return 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void till(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isTillingHoe(e.getItem())) {
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null || !TILLABLE.contains(clicked.getType())) {
            return;
        }
        e.setCancelled(true);
        Player p = e.getPlayer();
        int radius = this.areaRadius(p);
        World world = clicked.getWorld();
        int cx = clicked.getX();
        int cy = clicked.getY();
        int cz = clicked.getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block target = world.getBlockAt(cx + dx, cy, cz + dz);
                if (TILLABLE.contains(target.getType()) && target.getRelative(BlockFace.UP).getType().isAir()) {
                    target.setType(Material.FARMLAND);
                }
            }
        }
    }
}
