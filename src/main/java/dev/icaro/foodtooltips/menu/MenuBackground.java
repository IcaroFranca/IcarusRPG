package dev.icaro.foodtooltips.menu;

import java.util.Objects;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Applies the resource-pack menu canvas while preserving slots that contain real controls. */
public final class MenuBackground {
    private static final Key BACKGROUND_ITEM_MODEL = Key.key("icarus", "menu_background");
    private static final char LEAD_SHIFT = '\uE001';
    private static final char BASE_SIX_ROWS = '\uE000';
    private static final char BASE_THREE_ROWS = '\uE003';
    private static final char BASE_RESET = '\uE002';
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
        InventoryView view = player.getOpenInventory();
        Inventory inventory = view.getTopInventory();
        int rows = inventory.getSize() / 9;
        if (rows != 3 && rows != 6) {
            return;
        }

        String plainTitle = view.getTitle();
        StringBuilder title = new StringBuilder(plainTitle.length() + 192);
        title.append('\u00A7').append('f')
                .append(LEAD_SHIFT)
                .append(rows == 3 ? BASE_THREE_ROWS : BASE_SIX_ROWS)
                .append(BASE_RESET);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isVisibleControl(item)) {
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
