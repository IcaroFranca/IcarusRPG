package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.collections.CollectionsCatalog;
import dev.icaro.foodtooltips.collections.CollectionsProgressService;
import dev.icaro.foodtooltips.i18n.Language;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
 * A per-player armor-set storage and quick-equip board - unlocked by the Leather Farming
 * Collections entry (M1: 3 columns; M3/M5/M7: +2 each, up to 9 - see {@code
 * CollectionsCatalog}'s own Leather entry), opened from the Skills star menu's own slot 34.
 * Five fixed rows, exactly the player's own spec ("não haverá sexta fileira"): helmets,
 * chestplates, leggings, boots, then a selector row whose {@code column}-th button equips
 * that column's own stored set onto the player in one click, or takes it back off (bare,
 * never whatever was worn before) if that same column is already the active one ({@link
 * #select}/{@link #unequip}) - the column keeps showing that same set the whole time it's
 * worn (a clone goes on the player, not the stored item itself), so switching sets back and
 * forth never loses anything, and any cell that still holds the exact piece it's currently
 * worn as locks against removal ({@link #isLockedActiveSlot} in this board, {@code
 * WardrobeArmorLockListener} on the body itself) so that one stored master can never be
 * pulled out alongside its identical worn copy for a free duplicate - every other cell, worn
 * match or not, empty or full, stays completely free to edit, so any column can always be
 * freely built, rebuilt or mixed piece-by-piece from any set, active or not.
 *
 * <p>Persisted exactly like {@code QuiverService} (same {@code BukkitObjectOutputStream}-over-
 * {@code ItemStack[]} Base64 trick in the player's own PDC) - a full {@value #COLUMNS}-wide,
 * {@value #ARMOR_ROWS}-tall array regardless of how many columns are currently unlocked, since
 * Collections progress only ever goes up: a column that's locked today has never once been
 * writable, so it can never hold real data that unlocking later would need to reveal - locked
 * columns are always safe to just render as filler.
 *
 * <p>Any item can sit in an armor-row slot, not just the matching piece type - this is a
 * personal storage board, not a strict per-slot armor validator, and the player's own spec
 * never asked for one (unlike {@code QuiverService}'s arrow-only filter or {@code
 * PotionBagService}'s own restriction).
 */
public final class WardrobeService {
    public static final int COLUMNS = 9;
    private static final int ARMOR_ROWS = 4;
    private static final int TOTAL_SIZE = 45;
    private static final int HELMET_ROW = 0;
    private static final int CHESTPLATE_ROW = 1;
    private static final int LEGGINGS_ROW = 2;
    private static final int BOOTS_ROW = 3;
    private static final int SELECTOR_ROW = 4;
    /** Only ever a back button while at least one column is still locked (see this class's own doc on why a fully-unlocked board has no free cell left in its own 5 rows for one) - closing the screen normally (Esc/E) always works regardless, same as {@code QuiverService}'s own board. */
    private static final int BACK_SLOT = SELECTOR_ROW * 9 + (COLUMNS - 1);

    private final Plugin plugin;
    private final CollectionsProgressService collectionsProgress;
    private final NamespacedKey contentsKey = new NamespacedKey("foodtooltips", "wardrobe_contents");
    private final NamespacedKey activeColumnKey = new NamespacedKey("foodtooltips", "wardrobe_active_column");
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> viewing = new HashSet<>();
    /** Which column {@code p} is currently wearing (equipped via {@link #select}), if any - absent for a player wearing gear the Wardrobe never sourced. Loaded from {@link #activeColumnKey} the same lazy way {@link #inventoryFor} loads {@link #contentsKey}. See {@link #select}'s own doc on why the active column's storage is never actually emptied, and {@link #isLockedActiveSlot} on why that means it must stay locked. */
    private final Map<UUID, Integer> active = new HashMap<>();

    public WardrobeService(Plugin plugin, CollectionsProgressService collectionsProgress) {
        this.plugin = plugin;
        this.collectionsProgress = collectionsProgress;
    }

    public boolean unlocked(Player p) {
        return this.columns(p) > 0;
    }

    /** How many of the {@value #COLUMNS} columns {@code p} has actually unlocked - see this class's own doc on the Leather milestone ladder this reads. */
    public int columns(Player p) {
        int achieved = this.collectionsProgress.achieved(p, this.leatherEntry());
        if (achieved >= 7) return 9;
        if (achieved >= 5) return 7;
        if (achieved >= 3) return 5;
        if (achieved >= 1) return 3;
        return 0;
    }

    private dev.icaro.foodtooltips.collections.CollectionsEntry leatherEntry() {
        return CollectionsCatalog.find(Material.LEATHER).orElseThrow();
    }

    public static boolean isBackSlot(int slot) {
        return slot == BACK_SLOT;
    }

    /** Whether {@code slot} (a raw top-inventory slot) is a real storage/selector cell for {@code p}'s currently unlocked columns - false for anything in a locked column, or the decorative back-button cell. */
    public boolean isUsableSlot(Player p, int slot) {
        if (slot < 0 || slot >= TOTAL_SIZE || isBackSlot(slot)) {
            return false;
        }
        int column = slot % 9;
        int row = slot / 9;
        return row <= SELECTOR_ROW && column < this.columns(p);
    }

    /** Whether {@code slot} sits in the selector row (row-5) rather than one of the four armor-storage rows. */
    public static boolean isSelectorSlot(int slot) {
        return slot / 9 == SELECTOR_ROW;
    }

    /**
     * Whether {@code slot} (an armor-row cell) currently holds the exact same item {@code p}
     * has worn in that piece slot right now - the one real duplication risk {@link #select}
     * introduces (it clones the stored item onto the player instead of moving it, so the
     * "master" copy and the worn copy can briefly be identical at once - see {@link #select}'s
     * own doc). Locked against every inventory interaction (see {@code WardrobeListener}) only
     * for that exact match, never for the rest of that same column: an empty cell, or one
     * whose item no longer matches (the player changed it some other way, or it was never
     * equipped to begin with - e.g. an unused column the player merely clicked once, storing
     * nothing), stays fully free to edit. This is deliberately NOT "lock the whole active
     * column" (this method's own first version) - that also locked an empty column the moment
     * it became active, which made it impossible to ever store a new set there at all, directly
     * contradicting the player's own spec that any column should stay freely editable
     * ("devo ser livre para colocar os sets na coluna que eu quiser, e mesclar também").
     */
    public boolean isLockedActiveSlot(Player p, int slot) {
        if (isSelectorSlot(slot) || isBackSlot(slot)) {
            return false;
        }
        ItemStack stored = this.inventoryFor(p).getItem(slot);
        if (stored == null || stored.isEmpty()) {
            return false;
        }
        ItemStack worn = this.wornPiece(p, slot / 9);
        return worn != null && !worn.isEmpty() && stored.isSimilar(worn);
    }

    /** {@code p}'s own currently worn piece for armor {@code row} (helmet/chestplate/leggings/boots) - null for any other row (the selector row has no worn-piece counterpart). */
    private ItemStack wornPiece(Player p, int row) {
        PlayerInventory pinv = p.getInventory();
        return switch (row) {
            case HELMET_ROW -> pinv.getHelmet();
            case CHESTPLATE_ROW -> pinv.getChestplate();
            case LEGGINGS_ROW -> pinv.getLeggings();
            case BOOTS_ROW -> pinv.getBoots();
            default -> null;
        };
    }

    public void open(Player p) {
        Inventory inv = this.inventoryFor(p);
        this.render(p, inv);
        p.openInventory(inv);
        dev.icaro.foodtooltips.menu.MenuBackground.apply(p);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
    }

    public void handleQuit(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
        this.cache.remove(p.getUniqueId());
        this.active.remove(p.getUniqueId());
    }

    public void back(Player p) {
        this.viewing.remove(p.getUniqueId());
        this.persist(p);
    }

    /** Flushes every currently cached (online) player's Wardrobe to their PDC - see {@code QuiverService#saveAll}'s own doc, called from the same {@code FoodTooltipsPlugin#onDisable} spot. */
    public void saveAll() {
        for (UUID id : new ArrayList<>(this.cache.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                this.persist(p);
            }
        }
    }

    /**
     * Equips {@code column}'s stored helmet/chestplate/leggings/boots onto {@code p} (each
     * slot independently - an empty stored slot simply unequips that piece) and marks
     * {@code column} as the currently active one, then redraws the board. Unlike this
     * method's own first version, the stored items are CLONED onto the player rather than
     * moved - {@code column}'s own storage keeps holding them (see {@link #isLockedActiveSlot},
     * which locks those cells against removal the instant this makes them active), so the set
     * "stays represented" there for an instant, lossless re-equip later, exactly the player's
     * own spec ("continuar representado lá... para deixar a troca mais dinâmica").
     *
     * <p>Whatever {@code p} was wearing before this call needs nowhere to go if it came from
     * another Wardrobe column (its own storage was never emptied either, by this same rule -
     * the worn copy was only ever a clone, safe to just discard/overwrite here); it's only
     * given back to {@code p}'s own inventory (never silently lost) when nothing was
     * previously active, meaning the current gear isn't backed by any column's own storage at
     * all. No-op for a locked column.
     *
     * <p>Clicking the already-active column's own selector again is a toggle, not a re-equip -
     * see {@link #unequip} - per the player's own explicit spec: the only sanctioned way to
     * take an active set off is through this button, never by pulling pieces off the body
     * directly (see {@code WardrobeArmorLockListener}, which blocks that exact thing - without
     * it, a player could strip the worn clone via their own inventory screen and then also
     * pull the identical master copy out of this column's own storage, a free duplicate).
     */
    public void select(Player p, int column) {
        if (column < 0 || column >= this.columns(p)) {
            return;
        }
        UUID id = p.getUniqueId();
        if (Integer.valueOf(column).equals(this.active.get(id))) {
            this.unequip(p);
            return;
        }
        Inventory inv = this.inventoryFor(p);
        PlayerInventory pinv = p.getInventory();
        if (this.active.get(id) == null) {
            this.giveBack(p, pinv.getHelmet());
            this.giveBack(p, pinv.getChestplate());
            this.giveBack(p, pinv.getLeggings());
            this.giveBack(p, pinv.getBoots());
        }
        pinv.setHelmet(this.cloneOrNull(inv.getItem(HELMET_ROW * 9 + column)));
        pinv.setChestplate(this.cloneOrNull(inv.getItem(CHESTPLATE_ROW * 9 + column)));
        pinv.setLeggings(this.cloneOrNull(inv.getItem(LEGGINGS_ROW * 9 + column)));
        pinv.setBoots(this.cloneOrNull(inv.getItem(BOOTS_ROW * 9 + column)));
        this.active.put(id, column);
        this.persistActive(p);
        this.render(p, inv);
        p.updateInventory();
    }

    /**
     * Takes the currently active column's set off {@code p}'s body entirely (bare after this,
     * never whatever was worn before the player's very first {@link #select} - that original
     * gear was already returned to their inventory back then, so there's nothing meaningful
     * left to restore) and clears the active column - the column's own storage is never
     * touched, so {@link #isLockedActiveSlot} immediately stops locking it (nothing is worn to
     * match anymore), freeing the player to pull the real set out of the board and into their
     * inventory, exactly the player's own spec.
     */
    private void unequip(Player p) {
        PlayerInventory pinv = p.getInventory();
        pinv.setHelmet(null);
        pinv.setChestplate(null);
        pinv.setLeggings(null);
        pinv.setBoots(null);
        this.active.remove(p.getUniqueId());
        this.persistActive(p);
        this.render(p, this.inventoryFor(p));
        p.updateInventory();
    }

    /**
     * Whether {@code item} (typically an {@code InventoryClickEvent}'s own current item at an
     * armor slot) is the exact piece {@code p} currently has equipped from their active
     * Wardrobe column - what {@code WardrobeArmorLockListener} checks to block removing it
     * straight from the body, the whole reason {@link #select}/{@link #unequip} exist as the
     * only sanctioned way to change it (see {@link #select}'s own doc on the duplicate this
     * prevents). False whenever nothing is active, regardless of {@code item}.
     */
    public boolean isActiveWornPiece(Player p, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return false;
        }
        Integer column = this.active.get(p.getUniqueId());
        if (column == null) {
            return false;
        }
        Inventory inv = this.inventoryFor(p);
        for (int row = HELMET_ROW; row <= BOOTS_ROW; row++) {
            ItemStack stored = inv.getItem(row * 9 + column);
            if (stored != null && !stored.isEmpty() && stored.isSimilar(item)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack cloneOrNull(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    /** Adds {@code item} straight to {@code p}'s own inventory (dropping naturally at their feet only whatever doesn't fit) - the same "give, don't ever discard" rule {@code GeneralSkillListener#give} already applies to Telekinesis' own block drops, used here so gear {@link #select} takes off never just vanishes. */
    private void giveBack(Player p, ItemStack item) {
        if (item == null || item.isEmpty()) {
            return;
        }
        for (ItemStack overflow : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
    }

    private void persistActive(Player p) {
        Integer column = this.active.get(p.getUniqueId());
        if (column == null) {
            p.getPersistentDataContainer().remove(this.activeColumnKey);
        } else {
            p.getPersistentDataContainer().set(this.activeColumnKey, PersistentDataType.INTEGER, column);
        }
    }

    /** Rebuilds every locked-column filler cell and the selector row's own preview icons from {@code inv}'s current (real, undisturbed - see {@link #select}) contents - never touches an unlocked armor-row cell's real item. */
    private void render(Player p, Inventory inv) {
        Language l = Language.of(p);
        int columns = this.columns(p);
        Integer activeColumn = this.active.get(p.getUniqueId());
        for (int row = 0; row <= SELECTOR_ROW; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int slot = row * 9 + column;
                if (isBackSlot(slot)) {
                    continue;
                }
                if (column >= columns) {
                    inv.setItem(slot, this.lockedFiller(l));
                    continue;
                }
                if (row == SELECTOR_ROW) {
                    inv.setItem(slot, this.selectorIcon(inv, column, l, activeColumn != null && activeColumn == column));
                }
            }
        }
        if (columns < COLUMNS) {
            inv.setItem(BACK_SLOT, this.backButton(l));
        }
    }

    private ItemStack selectorIcon(Inventory inv, int column, Language l, boolean active) {
        ItemStack helmet = inv.getItem(HELMET_ROW * 9 + column);
        ItemStack base = helmet != null && !helmet.isEmpty() ? helmet.clone() : new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = base.getItemMeta();
        meta.displayName(Component.text(l.choose("Set ", "Set ") + (column + 1), active ? NamedTextColor.GREEN : NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (active) {
            lore.add(Component.text(l.choose("Atualmente equipado!", "Currently equipped!"), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(l.choose("Clique para remover este set!", "Click to remove this set!"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text(l.choose("Clique para equipar este set!", "Click to equip this set!"), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        base.setItemMeta(meta);
        return base;
    }

    private ItemStack lockedFiller(Language l) {
        ItemStack f = ItemStack.of(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        m.displayName(Component.text(l.choose("Bloqueado", "Locked"), NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        f.setItemMeta(m);
        return f;
    }

    private ItemStack backButton(Language l) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        var meta = (org.bukkit.inventory.meta.SkullMeta) i.getItemMeta();
        try {
            var profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", dev.icaro.foodtooltips.item.HeadTexture.BACK));
            meta.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the screen.
        }
        meta.displayName(Component.text(l.choose("Voltar às skills", "Back to skills"), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(meta);
        return i;
    }

    /** The Skills menu's own Wardrobe button icon - a purple leather chestplate, per the player's own spec ("representado por um peitoral de couro roxo"), not a custom head like every other button here. "Wardrobe" is kept as a proper name in both languages, same as every other English feature name already in this plugin's own reward text (e.g. "Farmer Boots"). */
    public static ItemStack menuIcon() {
        ItemStack item = ItemStack.of(Material.LEATHER_CHESTPLATE);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(org.bukkit.Color.fromRGB(0x80, 0x00, 0x80));
        }
        meta.displayName(Component.text("Wardrobe", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private Inventory inventoryFor(Player p) {
        return this.cache.computeIfAbsent(p.getUniqueId(), id -> {
            Language l = Language.of(p);
            Inventory inv = Bukkit.createInventory(null, TOTAL_SIZE, l.choose("Guarda-roupa", "Wardrobe"));
            ItemStack[] saved = this.load(p);
            if (saved != null) {
                int limit = Math.min(saved.length, ARMOR_ROWS * COLUMNS);
                for (int i = 0; i < limit; i++) {
                    inv.setItem(i, saved[i]);
                }
            }
            Integer storedActive = p.getPersistentDataContainer().get(this.activeColumnKey, PersistentDataType.INTEGER);
            if (storedActive != null) {
                this.active.put(id, storedActive);
            }
            return inv;
        });
    }

    private void persist(Player p) {
        Inventory inv = this.cache.get(p.getUniqueId());
        if (inv == null) {
            return;
        }
        ItemStack[] storage = Arrays.copyOfRange(inv.getContents(), 0, ARMOR_ROWS * COLUMNS);
        p.getPersistentDataContainer().set(this.contentsKey, PersistentDataType.STRING, this.serialize(storage));
    }

    private ItemStack[] load(Player p) {
        String data = p.getPersistentDataContainer().get(this.contentsKey, PersistentDataType.STRING);
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return this.deserialize(data);
        } catch (IOException | ClassNotFoundException ex) {
            this.plugin.getLogger().warning("Failed to load wardrobe contents for " + p.getUniqueId() + ": " + ex.getMessage());
            return null;
        }
    }

    private String serialize(ItemStack[] contents) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ItemStack[] deserialize(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data)); BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            int length = in.readInt();
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                contents[i] = (ItemStack) in.readObject();
            }
            return contents;
        }
    }
}
