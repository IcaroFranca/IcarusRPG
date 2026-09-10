package dev.icaro.foodtooltips.enchant;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

/**
 * A Grindstone screen for REMOVING enchantments - same layout as {@link
 * EnchantMenuService}'s main screen (item slot at {@link #ITEM_SLOT}, a 3x5 catalog
 * grid, scroll arrows), opened by right-clicking a real Grindstone block instead of
 * vanilla's own repair/disenchant UI (see {@code GrindstoneMenuListener}). Removing
 * used to be the Enchanting Table's job (a Remove button on its level-select screen)
 * - it's the Grindstone's alone now: placing an item shows only the entries it
 * actually has (see {@link #renderCatalog}), and clicking one arms a two-click
 * confirm on that same icon (mirroring the table's old {@code removeIcon}) before
 * actually removing it.
 */
public final class GrindstoneMenuService {
    public static final int ITEM_SLOT = 19;
    private static final int ICON_SLOT = 28;
    private static final int SCROLL_UP_SLOT = 17;
    private static final int SCROLL_DOWN_SLOT = 35;
    /** Where an item's applied entries sit - same 3 rows of 5 as {@code EnchantMenuService#CATALOG_SLOTS}, for the same "same layout" reason. */
    private static final int[] CATALOG_SLOTS = {12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34};
    private static final long REMOVE_CONFIRM_WINDOW_MILLIS = 10_000L;

    private final Plugin plugin;
    private final EnchantService enchants;
    private final Map<UUID, Integer> pages = new HashMap<>();
    /** First click of a remove confirmation, per player - scoped to a specific entry (not just the player), same reasoning as the table's old removeConfirm. */
    private final Map<UUID, RemoveArm> removeConfirm = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();

    public GrindstoneMenuService(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Pedra de Amolar", "Grindstone"));
        this.fill(v);
        v.setItem(ICON_SLOT, this.item(Material.GRINDSTONE,
                l.choose("Pedra de Amolar", "Grindstone"),
                List.of(this.text(l.choose("Apenas representação.", "Just a representation."), NamedTextColor.GRAY))));
        this.renderCatalog(v, p, 0);
        v.setItem(ITEM_SLOT, null);
        this.viewing.add(p.getUniqueId());
        this.pages.put(p.getUniqueId(), 0);
        p.openInventory(v);
    }

    /** Rebuilds just the catalog grid + scroll arrows, from whatever item is currently sitting in {@link #ITEM_SLOT} - shared by {@link #open} and {@link #scheduleCatalogRefresh}. */
    private void renderCatalog(Inventory v, Player p, int page) {
        boolean pt = Language.of(p) == Language.PT;
        List<Map.Entry<EnchantEntry, Integer>> entries = new ArrayList<>(this.enchants.levelsOf(v.getItem(ITEM_SLOT)).entrySet());
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            v.setItem(CATALOG_SLOTS[i], index < entries.size() ? this.entryIcon(p, entries.get(index), pt) : this.filler());
        }
        Language l = Language.of(p);
        v.setItem(SCROLL_UP_SLOT, page > 0 ? this.arrowIcon(l.choose("Página anterior", "Previous page")) : this.filler());
        v.setItem(SCROLL_DOWN_SLOT, (page + 1) * CATALOG_SLOTS.length < entries.size() ? this.arrowIcon(l.choose("Próxima página", "Next page")) : this.filler());
    }

    /** Same "next tick, after the click that changed it lands" pattern as {@code EnchantMenuService#scheduleCatalogRefresh}. */
    public void scheduleCatalogRefresh(Player p) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (!this.viewing.contains(p.getUniqueId())) {
                return;
            }
            Inventory v = p.getOpenInventory().getTopInventory();
            if (v.getSize() != 54) {
                return;
            }
            this.renderCatalog(v, p, 0);
            this.pages.put(p.getUniqueId(), 0);
        });
    }

    /**
     * First click on an entry arms a confirmation (see {@link #isRemoveArmed}); a
     * second click on the SAME entry within {@link #REMOVE_CONFIRM_WINDOW_MILLIS}
     * actually removes it from the item currently in {@link #ITEM_SLOT} and
     * refreshes the catalog/item display.
     */
    public void handleEntryClick(Player p, EnchantEntry entry) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        Inventory v = p.getOpenInventory().getTopInventory();
        ItemStack item = v.getItem(ITEM_SLOT);
        if (item == null || item.isEmpty()) {
            return;
        }
        if (this.isRemoveArmed(p, entry)) {
            this.removeConfirm.remove(p.getUniqueId());
            this.enchants.removeLevel(item, entry, pt);
            v.setItem(ITEM_SLOT, item);
            p.sendMessage(Component.text(l.choose("Removido: ", "Removed: ") + entry.catalogName(pt), NamedTextColor.RED));
            this.renderCatalog(v, p, 0);
            this.pages.put(p.getUniqueId(), 0);
            return;
        }
        this.removeConfirm.put(p.getUniqueId(), new RemoveArm(entry.id(), System.currentTimeMillis()));
        this.renderCatalog(v, p, this.pages.getOrDefault(p.getUniqueId(), 0));
    }

    private boolean isRemoveArmed(Player p, EnchantEntry entry) {
        RemoveArm arm = this.removeConfirm.get(p.getUniqueId());
        return arm != null && arm.entryId().equals(entry.id())
                && System.currentTimeMillis() - arm.armedAt() < REMOVE_CONFIRM_WINDOW_MILLIS;
    }

    /** The applied entry a click on {@code rawSlot} refers to, or null - used by {@code GrindstoneMenuListener}. */
    public EnchantEntry entryAt(Player p, int rawSlot) {
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            if (CATALOG_SLOTS[i] == rawSlot) {
                Inventory v = p.getOpenInventory().getTopInventory();
                List<Map.Entry<EnchantEntry, Integer>> entries = new ArrayList<>(this.enchants.levelsOf(v.getItem(ITEM_SLOT)).entrySet());
                int index = this.pages.getOrDefault(p.getUniqueId(), 0) * CATALOG_SLOTS.length + i;
                return index < entries.size() ? entries.get(index).getKey() : null;
            }
        }
        return null;
    }

    public boolean handleNav(Player p, int rawSlot) {
        int page = this.pages.getOrDefault(p.getUniqueId(), 0);
        Inventory v = p.getOpenInventory().getTopInventory();
        if (rawSlot == SCROLL_UP_SLOT && page > 0) {
            this.pages.put(p.getUniqueId(), page - 1);
            this.renderCatalog(v, p, page - 1);
            return true;
        } else if (rawSlot == SCROLL_DOWN_SLOT) {
            this.pages.put(p.getUniqueId(), page + 1);
            this.renderCatalog(v, p, page + 1);
            return true;
        }
        return false;
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.pages.remove(p.getUniqueId());
        this.removeConfirm.remove(p.getUniqueId());
    }

    private ItemStack entryIcon(Player p, Map.Entry<EnchantEntry, Integer> entry, boolean pt) {
        EnchantEntry e = entry.getKey();
        int level = entry.getValue();
        Language l = Language.of(p);
        List<Component> lore = new ArrayList<>(e.resolvedDescription(pt, level));
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        String name = e.leveledName(pt, level);
        if (this.isRemoveArmed(p, e)) {
            lore.add(this.text(l.choose("Clique de novo para confirmar.", "Click again to confirm."), NamedTextColor.RED));
            return this.item(Material.TNT, l.choose("Remover " + name + "?", "Remove " + name + "?"), lore);
        }
        lore.add(this.text(l.choose("Clique para remover este encantamento do item.", "Click to remove this enchantment from the item."), NamedTextColor.RED));
        return this.enchantedBook(name, lore);
    }

    private ItemStack enchantedBook(String name, List<Component> lore) {
        ItemStack item = this.item(Material.ENCHANTED_BOOK, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        return this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    /** A simple page-arrow placeholder icon - the Grindstone doesn't warrant the table's own custom head textures for something this secondary (an item rarely carries enough entries to need a second page at all). */
    private ItemStack arrowIcon(String name) {
        return this.item(Material.ARROW, name, List.of());
    }

    private void fill(Inventory v) {
        ItemStack filler = this.filler();
        for (int i = 0; i < v.getSize(); i++) {
            v.setItem(i, filler);
        }
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.text(name, NamedTextColor.GOLD));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private record RemoveArm(String entryId, long armedAt) {
    }
}
