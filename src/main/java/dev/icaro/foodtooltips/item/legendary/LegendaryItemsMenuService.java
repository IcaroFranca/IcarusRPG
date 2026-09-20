package dev.icaro.foodtooltips.item.legendary;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * The {@code /rpgitems} admin-only menu: one tile per {@link LegendaryWeapon} plus one
 * each for the Miner's Armor ({@link #MINER_ARMOR_SLOT}) and Lapis Lazuli Armor ({@link
 * #LAPIS_ARMOR_SLOT}) sets and the Grand/Titanic Experience Bottles ({@link
 * #GRAND_BOTTLE_SLOT}/{@link #TITANIC_BOTTLE_SLOT}), click to receive a copy in your own
 * inventory (per-player decision - no target-player picker for now). Every {@link
 * LegendaryWeapon} is not craftable, dropped, or sold anywhere else - this menu is the
 * only way one enters the game. Everything else here has its own way in too (a
 * Zombie/Skeleton Miner drop, Lapis Lazuli Armor/the Experience Bottles' own crafting
 * recipes, the bottles' own fishing chance - see {@code LapisExperienceService#fish}) -
 * this menu is just a second, guaranteed way to get one instead of relying on a drop/
 * fishing chance or gathering the crafting materials.
 */
public final class LegendaryItemsMenuService {
    private static final Map<Integer, LegendaryWeapon> SLOTS = Map.of(
            10, LegendaryWeapon.KASAKA_VENOM_FANG,
            12, LegendaryWeapon.KNIGHT_KILLER,
            14, LegendaryWeapon.BARUKA_DAGGER,
            16, LegendaryWeapon.DEMON_KING_DAGGERS,
            29, LegendaryWeapon.DEMON_KING_LONGSWORD,
            31, LegendaryWeapon.KAMISH_WRATH,
            33, LegendaryWeapon.UNDEAD_SWORD);
    /** Top-center, apart from the weapon rows below - Miner's Armor is a full 4-piece set, not a single {@link LegendaryWeapon}, so it isn't part of {@link #SLOTS} at all (see {@link #minerArmor}, wired in from {@code FoodTooltipsPlugin} as a plain function to avoid this package depending on {@code combat} - {@code combat} already depends on this one). */
    private static final int MINER_ARMOR_SLOT = 4;
    /** Center of the grid, between the two weapon rows - see {@link #lapisArmor}, wired in from {@code FoodTooltipsPlugin} the same function-reference way as {@link #minerArmor} to avoid this package depending on {@code item} directly for it. */
    private static final int LAPIS_ARMOR_SLOT = 22;
    /** Flanking {@link #LAPIS_ARMOR_SLOT} on the same row - see {@link #grandBottle}. */
    private static final int GRAND_BOTTLE_SLOT = 20;
    /** Flanking {@link #LAPIS_ARMOR_SLOT} on the same row - see {@link #titanicBottle}. */
    private static final int TITANIC_BOTTLE_SLOT = 24;
    /** Opens {@code collections.CollectionsItemsMenuService} instead of handing over an item directly, per the player's own "TODOS os itens pegáveis pelo /rpgitems" spec - a separate paginated screen since the number of Collections-unlockable items keeps growing (see that class's own doc). */
    private static final int COLLECTIONS_ITEMS_SLOT = 40;

    private final LegendaryWeaponService weapons;
    private final Set<UUID> viewing = new HashSet<>();
    /** {@code MinerVariantService::createArmorSet} - takes the viewer so the helmet can use the Java or Bedrock representation appropriate to that player. See {@link #MINER_ARMOR_SLOT}'s own doc for why this is a function reference rather than a direct dependency. Defaults to an empty set so the tile never NPEs if this is somehow never wired. */
    private Function<Player, List<ItemStack>> minerArmor = p -> List.of();
    /** {@code LapisArmorService::createArmorSet} - same idea as {@link #minerArmor}, see {@link #LAPIS_ARMOR_SLOT}. */
    private Function<Player, List<ItemStack>> lapisArmor = p -> List.of();
    /** {@code LapisExperienceService::grandBottleGift} - same idea as {@link #minerArmor}, see {@link #GRAND_BOTTLE_SLOT}. A single-item "set" (see {@link #armorSetPreview}), not an actual armor set. */
    private Function<Player, List<ItemStack>> grandBottle = p -> List.of();
    /** {@code LapisExperienceService::titanicBottleGift} - see {@link #TITANIC_BOTTLE_SLOT}. */
    private Function<Player, List<ItemStack>> titanicBottle = p -> List.of();
    /** {@code CollectionsItemsMenuService::open} - see {@link #COLLECTIONS_ITEMS_SLOT}. Defaults to a no-op so the tile never fails if this is somehow never wired. */
    private java.util.function.Consumer<Player> collectionsItems = p -> {};

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

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Itens Lendários", "Legendary Items"));
        ItemStack filler = this.filler();
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        for (Map.Entry<Integer, LegendaryWeapon> e : SLOTS.entrySet()) {
            v.setItem(e.getKey(), this.preview(e.getValue(), l));
        }
        v.setItem(MINER_ARMOR_SLOT, this.armorSetPreview(this.minerArmor.apply(p), l));
        v.setItem(LAPIS_ARMOR_SLOT, this.armorSetPreview(this.lapisArmor.apply(p), l));
        v.setItem(GRAND_BOTTLE_SLOT, this.armorSetPreview(this.grandBottle.apply(p), l));
        v.setItem(TITANIC_BOTTLE_SLOT, this.armorSetPreview(this.titanicBottle.apply(p), l));
        v.setItem(COLLECTIONS_ITEMS_SLOT, this.collectionsItemsTile(l));
        p.openInventory(v);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public void handleClick(Player p, int slot) {
        Language l = Language.of(p);
        if (slot == MINER_ARMOR_SLOT) {
            this.giveSet(p, this.minerArmor.apply(p), l, "Miner's Armor");
            return;
        }
        if (slot == LAPIS_ARMOR_SLOT) {
            this.giveSet(p, this.lapisArmor.apply(p), l, "Lapis Lazuli Armor");
            return;
        }
        if (slot == GRAND_BOTTLE_SLOT) {
            this.giveSet(p, this.grandBottle.apply(p), l, "Grand Experience Bottle");
            return;
        }
        if (slot == TITANIC_BOTTLE_SLOT) {
            this.giveSet(p, this.titanicBottle.apply(p), l, "Titanic Experience Bottle");
            return;
        }
        if (slot == COLLECTIONS_ITEMS_SLOT) {
            this.viewing.remove(p.getUniqueId());
            this.collectionsItems.accept(p);
            return;
        }
        LegendaryWeapon w = SLOTS.get(slot);
        if (w == null) {
            return;
        }
        ItemStack item = this.weapons.create(w, l);
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        p.sendMessage(Component.text(l.choose("Recebido: ", "Received: ") + w.name(l == Language.PT), NamedTextColor.GREEN));
    }

    /** Hands every piece of {@code set} to {@code p} (overflow drops on the ground), then announces {@code label} - shared by both {@link #MINER_ARMOR_SLOT} and {@link #LAPIS_ARMOR_SLOT}. */
    private void giveSet(Player p, List<ItemStack> set, Language l, String label) {
        for (ItemStack piece : set) {
            for (ItemStack overflow : p.getInventory().addItem(piece).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        p.sendMessage(Component.text(l.choose("Recebido: ", "Received: ") + label, NamedTextColor.GREEN));
    }

    /** The menu tile: {@code w}'s real item plus one extra "click to receive" line. */
    private ItemStack preview(LegendaryWeapon w, Language l) {
        ItemStack item = this.weapons.create(w, l);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(Component.text(l.choose("Clique para receber.", "Click to receive."), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** A "click to receive" tile for either a multi-piece armor set or a single item ({@link #grandBottle}/{@link #titanicBottle} only ever hand over one) - the set's own first piece (its most recognizable one, or the only one) plus the usual "click to receive" line, and (only when there's more than one piece) a line noting it's a full set. Falls back to a plain filler pane if {@code set} is empty (its own supplier was never wired). */
    private ItemStack armorSetPreview(List<ItemStack> set, Language l) {
        if (set.isEmpty()) {
            return this.filler();
        }
        ItemStack item = set.get(0).clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>(meta.hasLore() ? meta.lore() : List.of());
        lore.add(Component.empty());
        if (set.size() > 1) {
            lore.add(Component.text(l.choose("Dá o set completo (" + set.size() + " peças).", "Gives the full set (" + set.size() + " pieces)."), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text(l.choose("Clique para receber.", "Click to receive."), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /** {@link #COLLECTIONS_ITEMS_SLOT}'s own tile - a Bundle, same icon {@code SkillsMenuService}'s own Collections button already uses, since this opens the same catalog's items rather than giving anything directly on click. */
    private ItemStack collectionsItemsTile(dev.icaro.foodtooltips.i18n.Language l) {
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        var meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
        try {
            var profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", dev.icaro.foodtooltips.item.HeadTexture.BUNDLE));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the menu.
        }
        meta.displayName(Component.text(l.choose("Itens de Coleções", "Collections Items"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(l.choose("Todo item craftável desbloqueado por Coleções.", "Every craftable item unlocked by Collections."), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(l.choose("Clique para abrir!", "Click to open!"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filler() {
        ItemStack f = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(" "));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        f.setItemMeta(m);
        return f;
    }
}
