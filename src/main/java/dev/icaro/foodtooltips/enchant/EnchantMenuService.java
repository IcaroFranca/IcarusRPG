package dev.icaro.foodtooltips.enchant;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

/**
 * The reworked Enchanting Table screen - opened by right-clicking a real Enchanting
 * Table block (see {@code EnchantMenuListener}) instead of vanilla's own random-offer
 * UI. Every {@link EnchantEntry} - the plugin's own {@link IcarusEnchant}s AND every
 * real vanilla enchantment (see {@link EnchantService#allEntries}) - is always visible
 * and picked explicitly (never random); picking one opens a level-select screen
 * ({@link #openLevelSelect}) showing every level's cost/effect, and applying a level
 * spends real vanilla XP levels (like an anvil) - see {@link EnchantService}. An item
 * can be brought back to the table and enchanted again later (each distinct entry
 * just needs a free slot the first time - see {@link EnchantService#slotLimit}, shared
 * between custom and vanilla entries).
 *
 * <p>v1 scope: this is the interface and application/storage layer only - none of
 * the plugin's own custom enchants (currently none defined - see {@link
 * IcarusEnchant}) actually change combat numbers yet, that wiring is a deliberate
 * follow-up. Vanilla enchantments applied here work exactly as they always have.
 */
public final class EnchantMenuService {
    public static final int ITEM_SLOT = 19;
    private static final int TABLE_ICON_SLOT = 28;
    private static final int BOOKSHELF_SLOT = 48;
    private static final int GUIDE_SLOT = 50;
    private static final int SCROLL_UP_SLOT = 17;
    private static final int SCROLL_DOWN_SLOT = 35;
    private static final int BACK_SLOT = 49;
    /** Where enchant books sit in the 54-slot grid - 3 rows of 5, matching the requested layout. */
    private static final int[] CATALOG_SLOTS = {12, 13, 14, 15, 16, 21, 22, 23, 24, 25, 30, 31, 32, 33, 34};
    /** Where each of an enchant's levels sits on the level-select screen (up to {@link EnchantEntry#maxLevel()} used - the max across every vanilla entry is 5, so this covers them all too). */
    private static final int[] LEVEL_SLOTS = {20, 21, 22, 23, 24};
    private static final int LEVEL_PREVIEW_SLOT = 4;

    private final Plugin plugin;
    private final EnchantService enchants;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, ItemStack> pendingItem = new HashMap<>();
    private final Map<UUID, Location> tableLocation = new HashMap<>();

    public EnchantMenuService(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
    }

    /** Opens the table for {@code p} - {@code table} is the physical block right-clicked, only used to show a flavor "Enchanting Power" (see {@link #bookshelfPower}), nothing is gated by it in v1. */
    public void open(Player p, Location table) {
        this.tableLocation.put(p.getUniqueId(), table);
        this.openMain(p, 0);
    }

    private void openMain(Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Mesa de Encantamento", "Enchanting Table"));
        this.fill(v);
        v.setItem(ITEM_SLOT, null);
        List<EnchantEntry> all = this.enchants.allEntries();
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            if (index >= all.size()) {
                break;
            }
            v.setItem(CATALOG_SLOTS[i], this.catalogIcon(all.get(index), l, pt));
        }
        if (page > 0) {
            v.setItem(SCROLL_UP_SLOT, this.head(HeadKind.SCROLL_UP, l.choose("Página anterior", "Previous page")));
        }
        if ((page + 1) * CATALOG_SLOTS.length < all.size()) {
            v.setItem(SCROLL_DOWN_SLOT, this.head(HeadKind.SCROLL_DOWN, l.choose("Próxima página", "Next page")));
        }
        v.setItem(TABLE_ICON_SLOT, this.item(Material.ENCHANTING_TABLE,
                l.choose("Mesa de Encantamento", "Enchanting Table"),
                List.of(this.text(l.choose("Apenas representação.", "Just a representation."), NamedTextColor.GRAY))));
        v.setItem(BOOKSHELF_SLOT, this.item(Material.BOOKSHELF,
                l.choose("Poder de Encantamento", "Enchanting Power"),
                List.of(this.text(l.choose("Estantes ao redor: ", "Bookshelves around: ") + this.bookshelfPower(p), NamedTextColor.AQUA),
                        this.text(l.choose("(sem efeito por enquanto)", "(no effect yet)"), NamedTextColor.DARK_GRAY))));
        v.setItem(GUIDE_SLOT, this.item(Material.BOOK,
                l.choose("Guia de Encantamentos", "Enchantment Guide"),
                List.of(this.text(l.choose("Clique para ver todos os encantamentos.", "Click to see every enchantment."), NamedTextColor.YELLOW))));
        ItemStack pending = this.pendingItem.remove(p.getUniqueId());
        if (pending != null) {
            v.setItem(ITEM_SLOT, pending);
        }
        p.openInventory(v);
        this.views.put(p.getUniqueId(), new View(Type.MAIN, page, null));
    }

    /** Called by the listener when a catalog icon is clicked with an item present - grabs the item off the (about to close) main screen and switches to the level-select screen for {@code enchant}. */
    public void chooseEnchant(Player p, ItemStack item, EnchantEntry enchant) {
        this.pendingItem.put(p.getUniqueId(), item);
        this.openLevelSelect(p, enchant);
    }

    private void openLevelSelect(Player p, EnchantEntry enchant) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        Inventory v = Bukkit.createInventory(null, 54, enchant.catalogName(pt));
        this.fill(v);
        ItemStack item = this.pendingItem.get(p.getUniqueId());
        v.setItem(LEVEL_PREVIEW_SLOT, item == null ? this.item(Material.BARRIER, l.choose("Nenhum item", "No item"), List.of()) : item.clone());
        int current = item == null ? 0 : this.enchants.levelOf(item, enchant);
        boolean hasFreeSlot = item != null && this.enchants.hasFreeSlot(item, enchant);
        for (int level = 1; level <= enchant.maxLevel() && level <= LEVEL_SLOTS.length; level++) {
            v.setItem(LEVEL_SLOTS[level - 1], this.levelIcon(enchant, level, current, hasFreeSlot, l, pt));
        }
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar", "Back"), List.of()));
        p.openInventory(v);
        this.views.put(p.getUniqueId(), new View(Type.LEVEL, 0, enchant));
    }

    /** Called by the listener when a clickable (next-applicable) level is clicked - validates, charges the XP, applies it, and returns to the main screen with the updated item. */
    public void applyLevel(Player p, EnchantEntry enchant, int level) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        ItemStack item = this.pendingItem.get(p.getUniqueId());
        if (item == null) {
            return;
        }
        int current = this.enchants.levelOf(item, enchant);
        if (level != current + 1) {
            return;
        }
        if (current == 0 && !this.enchants.hasFreeSlot(item, enchant)) {
            p.sendMessage(this.msg(l.choose("Este item já está com todos os slots de encantamento ocupados.", "This item's enchantment slots are all full."), NamedTextColor.RED));
            return;
        }
        if (enchant instanceof VanillaEnchantEntry v) {
            String blockReason = this.enchants.vanillaBlockReason(item, v.enchantment(), pt);
            if (blockReason != null) {
                p.sendMessage(this.msg(blockReason, NamedTextColor.RED));
                return;
            }
        }
        int cost = enchant.costAtLevel(level);
        if (p.getLevel() < cost) {
            p.sendMessage(this.msg(l.choose("Você precisa de " + cost + " níveis de XP.", "You need " + cost + " XP levels."), NamedTextColor.RED));
            return;
        }
        EnchantService.chargeXp(p, cost);
        this.enchants.setLevel(item, enchant, level, pt);
        p.sendMessage(this.msg(l.choose("Aplicado: ", "Applied: ") + enchant.leveledName(pt, level), NamedTextColor.GREEN));
        this.openMain(p, 0);
    }

    private void openGuide(Player p, int page) {
        Language l = Language.of(p);
        boolean pt = l == Language.PT;
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Guia de Encantamentos", "Enchantment Guide"));
        this.fill(v);
        List<EnchantEntry> all = this.enchants.allEntries();
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            int index = page * CATALOG_SLOTS.length + i;
            if (index >= all.size()) {
                break;
            }
            v.setItem(CATALOG_SLOTS[i], this.guideIcon(all.get(index), pt));
        }
        if (page > 0) {
            v.setItem(SCROLL_UP_SLOT, this.head(HeadKind.SCROLL_UP, l.choose("Página anterior", "Previous page")));
        }
        if ((page + 1) * CATALOG_SLOTS.length < all.size()) {
            v.setItem(SCROLL_DOWN_SLOT, this.head(HeadKind.SCROLL_DOWN, l.choose("Próxima página", "Next page")));
        }
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar", "Back"), List.of()));
        p.openInventory(v);
        this.views.put(p.getUniqueId(), new View(Type.GUIDE, page, null));
    }

    /** The entry a catalog-grid click on {@code rawSlot} (main or guide screen, same layout) refers to on {@code page}, or null if that slot isn't a catalog slot or is past the end of the list. */
    public EnchantEntry catalogEnchantAt(int page, int rawSlot) {
        for (int i = 0; i < CATALOG_SLOTS.length; i++) {
            if (CATALOG_SLOTS[i] == rawSlot) {
                int index = page * CATALOG_SLOTS.length + i;
                List<EnchantEntry> all = this.enchants.allEntries();
                return index < all.size() ? all.get(index) : null;
            }
        }
        return null;
    }

    /** The level (1-based) a level-select screen click on {@code rawSlot} refers to, or -1 if that slot isn't one of the level slots. */
    public int levelAt(int rawSlot) {
        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            if (LEVEL_SLOTS[i] == rawSlot) {
                return i + 1;
            }
        }
        return -1;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public View view(Player p) {
        return this.views.get(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
        this.pendingItem.remove(p.getUniqueId());
        this.tableLocation.remove(p.getUniqueId());
    }

    /**
     * Removes and returns whatever item is being carried across screens for {@code p}
     * (see {@link #chooseEnchant}), or null if there isn't one - used by {@code
     * EnchantMenuListener} to hand it back to the player if they close the menu
     * entirely while on the level-select or guide screen (where the real item isn't
     * sitting in any real, interactive slot - only a display clone is).
     */
    public ItemStack takePendingItem(Player p) {
        return this.pendingItem.remove(p.getUniqueId());
    }

    /** Routes a click by slot/view - used by {@code EnchantMenuListener}. Returns true if it handled navigation (caller doesn't need to do anything else). */
    public boolean handleNav(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        }
        switch (v.type()) {
            case MAIN -> {
                if (slot == GUIDE_SLOT) {
                    this.openGuide(p, 0);
                    return true;
                } else if (slot == SCROLL_UP_SLOT && v.page() > 0) {
                    this.openMain(p, v.page() - 1);
                    return true;
                } else if (slot == SCROLL_DOWN_SLOT) {
                    this.openMain(p, v.page() + 1);
                    return true;
                }
            }
            case LEVEL -> {
                if (slot == BACK_SLOT) {
                    this.openMain(p, 0);
                    return true;
                }
            }
            case GUIDE -> {
                if (slot == BACK_SLOT) {
                    this.pendingItem.remove(p.getUniqueId());
                    this.openMain(p, 0);
                    return true;
                } else if (slot == SCROLL_UP_SLOT && v.page() > 0) {
                    this.openGuide(p, v.page() - 1);
                    return true;
                } else if (slot == SCROLL_DOWN_SLOT) {
                    this.openGuide(p, v.page() + 1);
                    return true;
                }
            }
        }
        return false;
    }

    /** Simplified vanilla-style bookshelf count - every Bookshelf block in a 5x5 area (both the table's floor and the layer above, skipping the inner 3x3) around {@code p}'s current table, no line-of-sight check (this is purely a flavor number in v1 - see this class's own doc), capped at 15 same as vanilla's real cap. */
    private int bookshelfPower(Player p) {
        Location table = this.tableLocation.get(p.getUniqueId());
        if (table == null || table.getWorld() == null) {
            return 0;
        }
        World world = table.getWorld();
        int bx = table.getBlockX();
        int by = table.getBlockY();
        int bz = table.getBlockZ();
        int count = 0;
        for (int dy = 0; dy <= 1 && count < 15; dy++) {
            for (int dx = -2; dx <= 2 && count < 15; dx++) {
                for (int dz = -2; dz <= 2 && count < 15; dz++) {
                    if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                        continue;
                    }
                    if (world.getBlockAt(bx + dx, by + dy, bz + dz).getType() == Material.BOOKSHELF) {
                        count++;
                    }
                }
            }
        }
        return Math.min(15, count);
    }

    private ItemStack catalogIcon(EnchantEntry e, Language l, boolean pt) {
        List<Component> lore = new ArrayList<>();
        String desc = e.description(pt);
        if (desc != null) {
            lore.add(this.text(desc, NamedTextColor.GRAY));
            lore.add(Component.empty());
        }
        for (int level = 1; level <= e.maxLevel(); level++) {
            lore.add(this.text(this.levelLine(e, level) + " - " + e.costAtLevel(level) + " " + l.choose("XP", "XP"), NamedTextColor.DARK_AQUA));
        }
        lore.add(Component.empty());
        lore.add(this.text(l.choose("Clique para escolher o nível.", "Click to choose a level."), NamedTextColor.YELLOW));
        return this.enchantedBook(e.catalogName(pt), lore);
    }

    private ItemStack guideIcon(EnchantEntry e, boolean pt) {
        List<Component> lore = new ArrayList<>();
        String desc = e.description(pt);
        if (desc != null) {
            lore.add(this.text(desc, NamedTextColor.GRAY));
            lore.add(Component.empty());
        }
        for (int level = 1; level <= e.maxLevel(); level++) {
            lore.add(this.text(this.levelLine(e, level) + " (" + e.costAtLevel(level) + " XP)", NamedTextColor.DARK_AQUA));
        }
        return this.enchantedBook(e.catalogName(pt), lore);
    }

    /** "I: +20" for a custom entry, or just "I" for vanilla (no formula-derived value to show). */
    private String levelLine(EnchantEntry e, int level) {
        String value = e.formattedValue(level);
        return value.isEmpty() ? EnchantService.roman(level) : EnchantService.roman(level) + ": " + value;
    }

    private ItemStack levelIcon(EnchantEntry e, int level, int current, boolean hasFreeSlot, Language l, boolean pt) {
        List<Component> lore = new ArrayList<>();
        String value = e.formattedValue(level);
        if (!value.isEmpty()) {
            lore.add(this.text(value, NamedTextColor.AQUA));
        }
        lore.add(this.text(e.costAtLevel(level) + " " + l.choose("níveis de XP", "XP levels"), NamedTextColor.DARK_AQUA));
        String name = e.leveledName(pt, level);
        if (level <= current) {
            lore.add(this.text(l.choose("JÁ APLICADO", "ALREADY APPLIED"), NamedTextColor.GREEN));
            return this.item(Material.ENCHANTED_BOOK, name, lore);
        }
        if (level == current + 1) {
            if (current == 0 && !hasFreeSlot) {
                lore.add(this.text(l.choose("SEM SLOTS LIVRES", "NO FREE SLOTS"), NamedTextColor.RED));
                return this.item(Material.BOOK, name, lore);
            }
            lore.add(this.text(l.choose("Clique para aplicar!", "Click to apply!"), NamedTextColor.GOLD));
            return this.item(Material.ENCHANTED_BOOK, name, lore);
        }
        lore.add(this.text(l.choose("Requer o nível anterior primeiro.", "Requires the previous level first."), NamedTextColor.DARK_GRAY));
        return this.item(Material.BOOK, name, lore);
    }

    private ItemStack enchantedBook(String name, List<Component> lore) {
        ItemStack item = this.item(Material.ENCHANTED_BOOK, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    private void fill(Inventory v) {
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < v.getSize(); i++) {
            v.setItem(i, filler);
        }
    }

    private enum HeadKind { SCROLL_UP, SCROLL_DOWN }

    private ItemStack head(HeadKind kind, String name) {
        String texture = kind == HeadKind.SCROLL_UP
                ? "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGMzMGM0YWI3ZDAwZmI1NWUzOWIxY2RkM2NiYzkzNDJiMTYyYzc2MTY2ZDIyNDk3MmRlZmJiZjllYzdmZmZhOCJ9fX0="
                : "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmNmMTBiYzEwNDg3YmVhZDY2NGY2N2I0N2U4YjVhMTcwNTQyZGNjNTc5YTRjZjdjOTFjYjc1NWYwY2FiMWU3MyJ9fX0=";
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private Component msg(String s, NamedTextColor c) {
        return Component.text(s, c);
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

    public record View(Type type, int page, EnchantEntry enchant) {
    }

    public enum Type { MAIN, LEVEL, GUIDE }
}
