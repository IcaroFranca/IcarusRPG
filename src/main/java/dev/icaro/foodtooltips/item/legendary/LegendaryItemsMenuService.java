package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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
import org.bukkit.inventory.meta.SkullMeta;

/**
 * The {@code /rpgitems} admin-only menu - a category hub ({@link #open}, same "pick a category,
 * then browse it" shape {@code collections.CollectionsMenuService#openCategories} already uses,
 * {@link #centeredSlots} copied from that same class for the identical reason its own doc gives:
 * the two menus' data shapes differ enough that sharing a base would need more indirection than
 * it saves) rather than one single screen mixing every kind of give-away together - per the
 * player's own explicit "quero uma forma melhor de filtrar os itens aqui, ta tudo muito
 * bagunçado", reacting to a {@link #SLOTS}-style flat grid (the menu's own previous shape) that
 * crammed 7 {@link LegendaryWeapon}s, 2 armor sets, 2 Experience Bottles, the Grappling Hook and
 * a link into one visually undifferentiated 54-slot inventory.
 *
 * <p>Three categories: {@link #openWeapons} (every {@link LegendaryWeapon}, click for a copy -
 * these are never craftable, dropped, or sold anywhere else, so this menu is their only way into
 * the game), {@link #openSets} (the Miner's/Lapis Lazuli Armor sets, the Grand/Titanic Experience
 * Bottles, and the Grappling Hook - everything else here has its own real way in too, a mob drop
 * or a crafting recipe; this menu is just a second, guaranteed way to get one instead of relying
 * on a drop/fishing chance or gathering materials - the Grappling Hook is the one exception, per
 * the player's own explicit "não vao entrar em collections, serão pegos só pelo /rpgitems" for
 * this still-in-testing item, see {@code grapple.GrapplingHookService}'s own doc), and {@link
 * #collectionsItems} (unchanged - opens {@code collections.CollectionsItemsMenuService} instead
 * of handing over an item directly, since the number of Collections-unlockable items keeps
 * growing).
 */
public final class LegendaryItemsMenuService {
    private static final int BACK_SLOT = 49;

    private final LegendaryWeaponService weapons;
    private final Map<UUID, View> viewing = new HashMap<>();
    /** {@code MinerVariantService::createArmorSet} - takes the viewer so the helmet can use the Java or Bedrock representation appropriate to that player. Defaults to an empty set so a tile never NPEs if this is somehow never wired. */
    private Function<Player, List<ItemStack>> minerArmor = p -> List.of();
    /** {@code LapisArmorService::createArmorSet} - same idea as {@link #minerArmor}. */
    private Function<Player, List<ItemStack>> lapisArmor = p -> List.of();
    /** {@code LapisExperienceService::grandBottleGift} - same idea as {@link #minerArmor}. A single-item "set" (see {@link #armorSetPreview}), not an actual armor set. */
    private Function<Player, List<ItemStack>> grandBottle = p -> List.of();
    /** {@code LapisExperienceService::titanicBottleGift} - see {@link #grandBottle}. */
    private Function<Player, List<ItemStack>> titanicBottle = p -> List.of();
    /** {@code CollectionsItemsMenuService::open} - opens that separate catalog screen. Defaults to a no-op so the tile never fails if this is somehow never wired. */
    private java.util.function.Consumer<Player> collectionsItems = p -> {};
    /** {@code grapple.GrapplingHookService::create} - same idea as {@link #minerArmor}, a single-item "set" like {@link #grandBottle}/{@link #titanicBottle}. */
    private Function<Player, List<ItemStack>> grapplingHook = p -> List.of();

    public LegendaryItemsMenuService(LegendaryWeaponService weapons) {
        this.weapons = weapons;
    }

    /** Wired in after construction, same pattern as {@code ArmorDefenseService#defenseMultiplier} - see {@link #minerArmor}. */
    public void minerArmor(Function<Player, List<ItemStack>> minerArmor) {
        this.minerArmor = minerArmor;
    }

    /** Wired in after construction - see {@link #lapisArmor}. */
    public void lapisArmor(Function<Player, List<ItemStack>> lapisArmor) {
        this.lapisArmor = lapisArmor;
    }

    /** Wired in after construction - see {@link #grandBottle}. */
    public void grandBottle(Function<Player, List<ItemStack>> grandBottle) {
        this.grandBottle = grandBottle;
    }

    /** Wired in after construction - see {@link #titanicBottle}. */
    public void titanicBottle(Function<Player, List<ItemStack>> titanicBottle) {
        this.titanicBottle = titanicBottle;
    }

    /** Wired in after construction - see {@link #collectionsItems}. */
    public void collectionsItems(java.util.function.Consumer<Player> collectionsItems) {
        this.collectionsItems = collectionsItems;
    }

    /** Wired in after construction - see {@link #grapplingHook}. */
    public void grapplingHook(Function<Player, List<ItemStack>> grapplingHook) {
        this.grapplingHook = grapplingHook;
    }

    /** The category hub - {@code /rpgitems}'s own entry point, and where {@link #openWeapons}/{@link #openSets}'s own Back button returns to. */
    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, "Legendary Items");
        this.fill(inv);
        List<Integer> slots = this.centeredSlots(3);
        Map<Integer, Category> buttons = new HashMap<>();
        inv.setItem(slots.get(0), this.item(Material.NETHERITE_SWORD, "Legendary Weapons", List.of(
                this.text(LegendaryWeapon.values().length + " weapons", NamedTextColor.GRAY),
                this.text("Click to open!", NamedTextColor.YELLOW))));
        buttons.put(slots.get(0), Category.WEAPONS);
        inv.setItem(slots.get(1), this.item(Material.DIAMOND_CHESTPLATE, "Armor & Potions", List.of(
                this.text("Armor sets, Experience Bottles,", NamedTextColor.GRAY),
                this.text("and the Grappling Hook.", NamedTextColor.GRAY),
                this.text("Click to open!", NamedTextColor.YELLOW))));
        buttons.put(slots.get(1), Category.SETS);
        inv.setItem(slots.get(2), this.collectionsItemsTile());
        buttons.put(slots.get(2), Category.COLLECTIONS_ITEMS);
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.put(p.getUniqueId(), View.categories(buttons));
    }

    private void openWeapons(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, "Legendary Weapons");
        this.fill(inv);
        LegendaryWeapon[] all = LegendaryWeapon.values();
        List<Integer> slots = this.centeredSlots(all.length);
        Map<Integer, LegendaryWeapon> buttons = new HashMap<>();
        for (int i = 0; i < all.length; i++) {
            int slot = slots.get(i);
            inv.setItem(slot, this.preview(all[i], l));
            buttons.put(slot, all[i]);
        }
        inv.setItem(BACK_SLOT, this.customHead(dev.icaro.foodtooltips.item.HeadTexture.BACK, "Back", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.put(p.getUniqueId(), View.weapons(buttons));
    }

    private void openSets(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, 54, "Armor & Potions");
        this.fill(inv);
        SetItem[] all = SetItem.values();
        List<Integer> slots = this.centeredSlots(all.length);
        Map<Integer, SetItem> buttons = new HashMap<>();
        for (int i = 0; i < all.length; i++) {
            int slot = slots.get(i);
            inv.setItem(slot, this.armorSetPreview(this.setSupplier(all[i]).apply(p), l));
            buttons.put(slot, all[i]);
        }
        inv.setItem(BACK_SLOT, this.customHead(dev.icaro.foodtooltips.item.HeadTexture.BACK, "Back", List.of()));
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.put(p.getUniqueId(), View.sets(buttons));
    }

    public boolean viewing(Player p) {
        return this.viewing.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void handleClick(Player p, int slot) {
        View v = this.viewing.get(p.getUniqueId());
        if (v == null) {
            return;
        }
        switch (v.type()) {
            case CATEGORIES -> {
                Category c = v.categoryButtons().get(slot);
                if (c == null) {
                    return;
                }
                switch (c) {
                    case WEAPONS -> this.openWeapons(p);
                    case SETS -> this.openSets(p);
                    case COLLECTIONS_ITEMS -> {
                        this.viewing.remove(p.getUniqueId());
                        this.collectionsItems.accept(p);
                    }
                }
            }
            case WEAPONS -> {
                if (slot == BACK_SLOT) {
                    this.open(p);
                    return;
                }
                LegendaryWeapon w = v.weaponButtons().get(slot);
                if (w == null) {
                    return;
                }
                Language l = Language.of(p);
                ItemStack item = this.weapons.create(w, l);
                for (ItemStack overflow : p.getInventory().addItem(item).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                }
                p.sendMessage(Component.text("Received: " + w.name(l == Language.PT), NamedTextColor.GREEN));
            }
            case SETS -> {
                if (slot == BACK_SLOT) {
                    this.open(p);
                    return;
                }
                SetItem item = v.setButtons().get(slot);
                if (item == null) {
                    return;
                }
                this.giveSet(p, this.setSupplier(item).apply(p), this.setLabel(item));
            }
        }
    }

    private Function<Player, List<ItemStack>> setSupplier(SetItem item) {
        return switch (item) {
            case MINER_ARMOR -> this.minerArmor;
            case LAPIS_ARMOR -> this.lapisArmor;
            case GRAND_BOTTLE -> this.grandBottle;
            case TITANIC_BOTTLE -> this.titanicBottle;
            case GRAPPLING_HOOK -> this.grapplingHook;
        };
    }

    private String setLabel(SetItem item) {
        return switch (item) {
            case MINER_ARMOR -> "Miner's Armor";
            case LAPIS_ARMOR -> "Lapis Lazuli Armor";
            case GRAND_BOTTLE -> "Grand Experience Bottle";
            case TITANIC_BOTTLE -> "Titanic Experience Bottle";
            case GRAPPLING_HOOK -> "Grappling Hook";
        };
    }

    /** Hands every piece of {@code set} to {@code p} (overflow drops on the ground), then announces {@code label}. */
    private void giveSet(Player p, List<ItemStack> set, String label) {
        for (ItemStack piece : set) {
            for (ItemStack overflow : p.getInventory().addItem(piece).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        p.sendMessage(Component.text("Received: " + label, NamedTextColor.GREEN));
    }

    /** The menu tile: {@code w}'s real item plus one extra "click to receive" line. */
    private ItemStack preview(LegendaryWeapon w, Language l) {
        ItemStack item = this.weapons.create(w, l);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(Component.text("Click to receive.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** A "click to receive" tile for either a multi-piece armor set or a single item ({@link #grandBottle}/{@link #titanicBottle}/{@link #grapplingHook} only ever hand over one) - the set's own first piece (its most recognizable one, or the only one) plus the usual "click to receive" line, and (only when there's more than one piece) a line noting it's a full set. Falls back to a plain filler pane if {@code set} is empty (its own supplier was never wired). */
    private ItemStack armorSetPreview(List<ItemStack> set, Language l) {
        if (set.isEmpty()) {
            return this.filler();
        }
        ItemStack item = set.get(0).clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        if (set.size() > 1) {
            lore.add(Component.text(("Gives the full set (" + set.size() + " pieces)."), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("Click to receive.", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** The hub's own "Collections Items" tile - a custom head, same icon {@code skills.SkillsMenuService}'s own Collections button already uses, since this opens the same catalog's items rather than giving anything directly on click. */
    private ItemStack collectionsItemsTile() {
        List<Component> lore = List.of(
                this.text("Every craftable item unlocked by Collections.", NamedTextColor.GRAY),
                this.text("Click to open!", NamedTextColor.YELLOW));
        return this.customHead(dev.icaro.foodtooltips.item.HeadTexture.BUNDLE, "Collections Items", lore);
    }

    /**
     * Evenly centers {@code count} tiles across up to 5 rows of a 54-slot inventory - copied
     * verbatim from {@code collections.CollectionsMenuService#centeredSlots} (same "copied
     * rather than shared" reasoning this class's own doc gives).
     */
    private List<Integer> centeredSlots(int count) {
        List<Integer> out = new ArrayList<>();
        if (count <= 0) {
            return out;
        }
        int rows = Math.min(5, (int) Math.ceil(count / 7.0));
        int firstRow = (5 - rows) / 2;
        int base = count / rows;
        int extra = count % rows;
        for (int row = 0; row < rows; row++) {
            int amount = base + (row < extra ? 1 : 0);
            int firstColumn = (9 - amount) / 2;
            for (int col = 0; col < amount; col++) {
                out.add((firstRow + row) * 9 + firstColumn + col);
            }
        }
        return out;
    }

    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            var profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack filler() {
        ItemStack f = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(" "));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        f.setItemMeta(m);
        return f;
    }

    private void fill(Inventory inv) {
        ItemStack f = this.filler();
        for (int s = 0; s < inv.getSize(); s++) {
            inv.setItem(s, f);
        }
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private enum Category {
        WEAPONS, SETS, COLLECTIONS_ITEMS
    }

    private enum SetItem {
        MINER_ARMOR, LAPIS_ARMOR, GRAND_BOTTLE, TITANIC_BOTTLE, GRAPPLING_HOOK
    }

    private record View(ViewType type, Map<Integer, Category> categoryButtons, Map<Integer, LegendaryWeapon> weaponButtons,
                         Map<Integer, SetItem> setButtons) {
        static View categories(Map<Integer, Category> b) {
            return new View(ViewType.CATEGORIES, b, Map.of(), Map.of());
        }

        static View weapons(Map<Integer, LegendaryWeapon> b) {
            return new View(ViewType.WEAPONS, Map.of(), b, Map.of());
        }

        static View sets(Map<Integer, SetItem> b) {
            return new View(ViewType.SETS, Map.of(), Map.of(), b);
        }
    }

    private enum ViewType {
        CATEGORIES, WEAPONS, SETS
    }
}
