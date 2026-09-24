package dev.icaro.foodtooltips.item;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Leaflet Armor: no full-set bonus, just a flat +{@value #FORAGING_FORTUNE_PER_PIECE}
 * Foraging Fortune per equipped piece (on top of each piece's own forced Health, baked
 * directly by {@code ForagingCollectionsItemsService#leafletPiece}/{@code #leafletHelmet}) -
 * same single-marker-key shape {@code LapisArmorService} uses for its own per-piece
 * bonuses, just without a Defense/Speed/XP-orb line since none of those were requested.
 */
public final class LeafletArmorService {
    /** See {@link #equippedForagingFortuneBonus}. */
    private static final int FORAGING_FORTUNE_PER_PIECE = 3;

    /** Marks a piece as Leaflet Armor - see {@link #isLeafletPiece}. */
    private static final NamespacedKey LEAFLET_ARMOR_KEY = new NamespacedKey("foodtooltips", "leaflet_armor_piece");

    public LeafletArmorService(Plugin plugin) {
        // No plugin-scoped state needed today (the PDC key above doesn't need the
        // plugin instance since it's a plain string namespace, same as every other
        // marker key in this package) - kept as a constructor parameter anyway to
        // match every other armor-set service's own shape, in case a future piece
        // needs it (a periodic effect, a recipe, etc).
    }

    public static void markLeafletPiece(ItemMeta meta) {
        meta.getPersistentDataContainer().set(LEAFLET_ARMOR_KEY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isLeafletPiece(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(LEAFLET_ARMOR_KEY, PersistentDataType.BYTE);
    }

    /** How many Leaflet Armor pieces {@code p} currently has equipped (0-4). */
    private int piecesEquipped(Player p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        if (isLeafletPiece(inv.getHelmet())) n++;
        if (isLeafletPiece(inv.getChestplate())) n++;
        if (isLeafletPiece(inv.getLeggings())) n++;
        if (isLeafletPiece(inv.getBoots())) n++;
        return n;
    }

    /** +{@value #FORAGING_FORTUNE_PER_PIECE} per equipped piece - wired into {@code GeneralSkillService#armorForagingFortuneBonus}. */
    public int equippedForagingFortuneBonus(Player p) {
        return this.piecesEquipped(p) * FORAGING_FORTUNE_PER_PIECE;
    }
}
