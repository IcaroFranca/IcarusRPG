package dev.icaro.foodtooltips.item;

import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Sculptor's Axe (Birch Log Collections M4): right-click any log/stem turns it into its
 * own all-bark "wood"/"hyphae" variant (e.g. {@link Material#OAK_LOG} → {@link
 * Material#OAK_WOOD}), the same transformation an axe strips a log INTO bark-stripped form
 * for, just the opposite direction and every face instead of one. {@link #LOG_TO_WOOD} covers
 * every real wood-trunk Material in the game, Nether included (the two Nether stems map to
 * "hyphae", not "wood" - the only irregular pair). {@link #transform} preserves the clicked
 * block's own {@link Orientable} axis, so a horizontal log stays horizontal after conversion.
 */
public final class SculptorsAxeService {
    private static final NamespacedKey AXE_KEY = new NamespacedKey("foodtooltips", "sculptors_axe");

    private static final Map<Material, Material> LOG_TO_WOOD = Map.ofEntries(
            Map.entry(Material.OAK_LOG, Material.OAK_WOOD),
            Map.entry(Material.SPRUCE_LOG, Material.SPRUCE_WOOD),
            Map.entry(Material.BIRCH_LOG, Material.BIRCH_WOOD),
            Map.entry(Material.JUNGLE_LOG, Material.JUNGLE_WOOD),
            Map.entry(Material.ACACIA_LOG, Material.ACACIA_WOOD),
            Map.entry(Material.DARK_OAK_LOG, Material.DARK_OAK_WOOD),
            Map.entry(Material.MANGROVE_LOG, Material.MANGROVE_WOOD),
            Map.entry(Material.CHERRY_LOG, Material.CHERRY_WOOD),
            Map.entry(Material.PALE_OAK_LOG, Material.PALE_OAK_WOOD),
            Map.entry(Material.CRIMSON_STEM, Material.CRIMSON_HYPHAE),
            Map.entry(Material.WARPED_STEM, Material.WARPED_HYPHAE));

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.STONE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(AXE_KEY, PersistentDataType.BYTE, (byte) 1);
        meta.displayName(Component.text("Sculptor's Axe", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                this.line("Right-click a log to turn it into", NamedTextColor.GRAY),
                this.line("its all-bark wood/hyphae form.", NamedTextColor.GRAY)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSculptorsAxe(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(AXE_KEY, PersistentDataType.BYTE);
    }

    /** Turns {@code clicked} into its own {@link #LOG_TO_WOOD} target, preserving axis - a no-op (returns false) if {@code clicked} isn't a Material this axe knows how to convert. */
    public boolean transform(Block clicked) {
        Material target = LOG_TO_WOOD.get(clicked.getType());
        if (target == null) {
            return false;
        }
        BlockData current = clicked.getBlockData();
        BlockData replacement = target.createBlockData();
        if (current instanceof Orientable from && replacement instanceof Orientable to) {
            to.setAxis(from.getAxis());
        }
        clicked.setBlockData(replacement);
        return true;
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
