package dev.icaro.foodtooltips.menu;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Applies the resource-pack menu canvas while preserving slots that contain real controls.
 *
 * <p>The IcarusTexture resource pack (a separate repo) registers one background glyph per
 * row count, 1 through 6 ({@code menu_base_1.png}..{@code menu_base_6.png}, mapped to
 * {@link #BASE_GLYPH_BY_ROWS}) - every inventory size that's a plain multiple of 9 works, not
 * just 3 and 6 rows. An earlier version of that pack only shipped {@code menu_base_3}/{@code
 * menu_base_6}, and the 3-row one was separately confirmed to render as a blank white screen
 * in practice - callers throughout this plugin worked around both gaps by always padding
 * every custom menu out to a fixed 54-slot (6-row) canvas, even when the real content needed
 * far fewer slots. Now that every row count 1-6 has its own working glyph, that padding is no
 * longer needed anywhere - see e.g. {@code skills.PersonalStorageService#totalSizeFor}.
 */
public final class MenuBackground {
    private static final Key BACKGROUND_ITEM_MODEL = Key.key("icarus", "menu_background");
    private static final char LEAD_SHIFT = '\uE001';
    private static final char BASE_RESET = '\uE002';
    /** {@code menu_base_1.png}..{@code menu_base_6.png}'s own glyphs, indexed by row count minus 1 - see this class's own doc. */
    private static final char[] BASE_GLYPH_BY_ROWS = {'\uE004', '\uE005', '\uE003', '\uE006', '\uE007', '\uE000'};
    private static final char FIRST_ROW_GLYPH = '\uE010';
    private static final char FIRST_COLUMN_SHIFT = '\uE020';
    private static final char FIRST_COLUMN_RESET = '\uE030';

    private MenuBackground() {
    }

    /**
     * Must be called immediately after opening an IcarusRPG inventory. The title is
     * redrawn with the continuous background and only occupied controls keep a slot.
     */
    public static void apply(Player player) {
        apply(player, new int[0]);
    }

    /** Keeps interactive empty slots visible in menus such as the virtual crafting table. */
    public static void apply(Player player, int... persistentSlots) {
        InventoryView view = player.getOpenInventory();
        Inventory inventory = view.getTopInventory();
        int rows = inventory.getSize() / 9;
        if (inventory.getSize() % 9 != 0 || rows < 1 || rows > 6) {
            return;
        }

        String plainTitle = undecoratedTitle(view.getTitle());
        Set<Integer> forced = IntStream.of(persistentSlots).boxed().collect(Collectors.toSet());
        StringBuilder title = new StringBuilder(plainTitle.length() + 192);
        title.append('\u00A7').append('f')
                .append(LEAD_SHIFT)
                .append(BASE_GLYPH_BY_ROWS[rows - 1])
                .append(BASE_RESET);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (forced.contains(slot) || isVisibleControl(item)) {
                int row = slot / 9;
                int column = slot % 9;
                title.append((char) (FIRST_COLUMN_SHIFT + column))
                        .append((char) (FIRST_ROW_GLYPH + row))
                        .append((char) (FIRST_COLUMN_RESET + column));
            } else if (item != null && item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                // The title texture removes the slot itself; this model makes the
                // filler pane icon disappear into the exact same background colour.
                item.setData(DataComponentTypes.ITEM_MODEL, BACKGROUND_ITEM_MODEL);
                inventory.setItem(slot, item);
            }
        }

        title.append('\u00A7').append('8').append(plainTitle);
        view.setTitle(title.toString());
    }

    private static String undecoratedTitle(String title) {
        int textMarker = title.lastIndexOf("\u00A78");
        if (title.indexOf(LEAD_SHIFT) >= 0 && textMarker >= 0) {
            return title.substring(textMarker + 2);
        }
        return title;
    }

    private static boolean isVisibleControl(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        String name = PlainTextComponentSerializer.plainText().serialize(
                Objects.requireNonNull(meta.displayName()));
        return !name.isBlank();
    }
}
